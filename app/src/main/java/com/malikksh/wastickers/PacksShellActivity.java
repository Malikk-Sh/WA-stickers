package com.malikksh.wastickers;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Library/Home layer backed by the existing PackStore and WhatsApp integration. */
public class PacksShellActivity extends BuildShellActivity {
    private static final int REQUEST_ADD_TO_WHATSAPP = 200;

    private PacksPanel packsPanel;
    private View packsScreen;
    private final java.util.concurrent.ExecutorService packsWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private String pendingSyncId;
    private String pendingSyncVersion;
    private final PackStore.Listener packListener = () -> refreshPacksPanel();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            pendingSyncId = savedInstanceState.getString("sync.id");
            pendingSyncVersion = savedInstanceState.getString("sync.version");
        }
        try {
            installPacksPanel();
            PackStore.observe(packListener);
            refreshPacksPanel();
            if (savedInstanceState == null) openLibraryHome();
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not install redesigned Packs panel: " + error);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (packsPanel != null) packsPanel.post(this::refreshPacksPanel);
        packsWorker.execute(() -> {
            for (PackStore.Pack pack : PackStore.getPacks(this)) {
                if (WhatsAppSync.state(this, pack) == WhatsAppSync.State.SYNCING && pendingSyncId == null)
                    WhatsAppSync.set(this, pack.id, WhatsAppSync.State.SYNC_ERROR, null);
                else WhatsAppSync.reconcile(this, pack);
            }
        });
    }

    @Override
    protected void onDestroy() {
        packsWorker.shutdownNow();
        PackStore.stopObserving(packListener);
        if (packsPanel != null) packsPanel.close();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (packsPanel != null && packsPanel.closeSearchIfActive()) return;
        super.onBackPressed();
    }

    private void installPacksPanel() {
        FrameLayout host = findViewById(R.id.app_screen_host);
        if (host == null || host.getChildCount() < 4) {
            throw new IllegalStateException("Packs screen host is missing");
        }
        packsScreen = host.getChildAt(3);
        if (!(packsScreen instanceof ScrollView)) {
            throw new IllegalStateException("Unexpected Packs screen root");
        }

        packsPanel = new PacksPanel(this, new PacksPanel.Host() {
            @Override
            public void onAddToWhatsApp(PackStore.Pack pack) {
                addPackToWhatsApp(pack);
            }

            @Override
            public void onOpenPack(PackStore.Pack pack) {
                openPackEditor(pack);
            }

            @Override
            public void onShowPackActions(PackStore.Pack pack) {
                showPackActions(pack);
            }

            @Override
            public void onOpenDraft(String id) {
                if (runtimeIsProcessing()) return;
                EditorInstanceStateBridge.savePersistent(PacksShellActivity.this);
                runtimeClearEditorDraft();
                VideoTrimStore.clear();
                if (EditorInstanceStateBridge.restoreProject(PacksShellActivity.this, id)) {
                    VideoTrimStore.restorePersistent(PacksShellActivity.this, AppSettings.TRIM_PERSISTENT_KEY + "." + id);
                    enterCreateEditor();
                }
            }

            @Override
            public void onCreatePack() {
                showCreatePackDialog();
            }

            @Override
            public void onSettingsRequested() {
                startActivity(new Intent(PacksShellActivity.this, SettingsActivity.class));
            }

            @Override
            public void onOverflowRequested(View anchor) {
                showPacksOverflow(anchor);
            }
        });

        ScrollView scroll = (ScrollView) packsScreen;
        scroll.removeAllViews();
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(packsPanel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void openLibraryHome() {
        View packs = findViewById(R.id.nav_packs);
        if (packs != null) packs.performClick();
    }

    /** Transitional flow route used by Library create and restore paths until tabs are removed. */
    void enterCreateEditor() {
        View create = findViewById(R.id.nav_create);
        if (create != null) create.performClick();
        refreshShellState();
        // SettingsShellActivity owns this action container. It is hidden on Home, so make the
        // workflow actions visible synchronously when the route changes instead of waiting for
        // a layout callback that may not happen after a simple visibility switch.
        View overflow = findViewById(R.id.app_overflow);
        if (overflow != null) {
            ViewParent parent = overflow.getParent();
            if (parent instanceof View) ((View) parent).setVisibility(View.VISIBLE);
        }
    }

    private void refreshPacksPanel() {
        if (packsPanel == null) return;
        try {
            List<PackStore.Pack> packs = PackStore.getPacks(this);
            packsPanel.render(packs);
        } catch (RuntimeException error) {
            packsPanel.showLoadError();
            BugLogStore.appendApp("Could not load Library: " + error);
        }
    }

    private void showCreatePackDialog() {
        if (runtimeIsProcessing()) return;
        PackNameDialog.show(this, "Новый набор", PackStore.nextName(this, "Мои стикеры"), "Создать", name -> {
            if (!runtimeNewProject(name)) return;
            enterCreateEditor();
            EditorInstanceStateBridge.savePersistent(this);
        });
    }

    private void openPackEditor(PackStore.Pack pack) {
        if (runtimeIsProcessing()) return;
        EditorInstanceStateBridge.savePersistent(this);
        VideoTrimStore.savePersistent(this, AppSettings.TRIM_PERSISTENT_KEY + "." + runtimeProjectId());
        runtimeClearEditorDraft();
        runtimeSetProjectId(pack.id);
        VideoTrimStore.clear();
        if (EditorInstanceStateBridge.restoreProject(this, pack.id)) {
            VideoTrimStore.restorePersistent(this, AppSettings.TRIM_PERSISTENT_KEY + "." + pack.id);
            runtimePackName().setText(pack.name);
            enterCreateEditor();
            return;
        }
        // Older releases stored only exported media. Copy it to durable source storage before edits.
        packsWorker.execute(() -> {
            java.util.ArrayList<Uri> sources = new java.util.ArrayList<>();
            try {
                ProjectSources.Snapshot saved = ProjectSources.read(this, pack);
                if (saved != null) sources.addAll(saved.items);
                File root = new File(getFilesDir(), "project_sources/" + pack.id);
                for (int i = 1; sources.size() < pack.stickerCount && i <= pack.stickerCount; i++) {
                    File source = new File(root, i + ".webp");
                    PackStore.copyRecursively(PackStore.getStickerFile(this, pack.id, i + ".webp"), source);
                    sources.add(Uri.fromFile(source));
                }
                runOnUiThread(() -> {
                    if (isDestroyed() || !pack.id.equals(runtimeProjectId())) return;
                    runtimeRestoreEditorState(pack.animated, sources,
                            saved != null ? saved.cover : sources.get(0), pack.name, pack);
                    EditorInstanceStateBridge.savePersistent(this);
                    enterCreateEditor();
                });
            } catch (java.io.IOException error) {
                runOnUiThread(() -> TransientFeedback.show(this, "Не удалось открыть файлы набора"));
            }
        });
    }

    private void showPacksOverflow(View anchor) {
        showPacksHelp();
    }

    private void showImportNotice() {
        startActivity(InfoActivity.intent(
                this,
                "Импортировать",
                "Формат импорта сохранённых наборов ещё не определён. Создавайте наборы через кнопку «Создать набор», чтобы не потерять метаданные и совместимость с WhatsApp."
        ));
    }

    private void cleanupUnavailablePacks() {
        int removed = PackStore.removeUnavailablePacks(this);
        refreshPacksPanel();
        TransientFeedback.show(this,
                removed == 0
                        ? "Недоступных наборов не найдено"
                        : (removed == 1 ? "Удалён 1 недоступный набор" : "Удалено наборов: " + removed));
    }

    private void showPacksHelp() {
        startActivity(InfoActivity.intent(
                this,
                "Наборы",
                "Фильтруйте и ищите сохранённые наборы. Через меню набора можно переименовать, дублировать, удалить его или посмотреть детали. Недоступные локальные наборы остаются видимыми, но их нельзя отправить в WhatsApp."
        ));
    }

    private void showPacksAbout() {
        startActivity(InfoActivity.intent(
                this,
                "WA Stickers",
                "Все файлы обрабатываются локально.\n\nВерсия " + BuildConfig.VERSION_NAME
        ));
    }

    private void showPackActions(PackStore.Pack pack) {
        if (pack == null) return;
        PackActionsSheet.show(this, pack, new PackActionsSheet.Host() {
            @Override public void onEdit() { openPackEditor(pack); }
            @Override
            public void onRename() {
                showRenameDialog(pack);
            }

            @Override
            public void onDuplicate() {
                duplicatePack(pack);
            }

            @Override
            public void onDelete() {
                confirmDelete(pack);
            }

            @Override
            public void onDetails() {
                showPackDetails(pack);
            }
        });
    }

    private void showRenameDialog(PackStore.Pack pack) {
        PackNameDialog.show(this, "Переименовать набор", pack.name, "Сохранить", pack.id, name -> {
            PackStore.Pack renamed = PackStore.renamePack(this, pack.id, name);
            if (renamed != null) {
                this.runtimeReplaceCurrentPackIfId(pack.id, renamed);
                notifyMetadataChanged();
                refreshPacksPanel();
                TransientFeedback.show(this, "Набор переименован");
            }
        });
    }

    private void duplicatePack(PackStore.Pack pack) {
        packsWorker.execute(() -> {
            PackStore.Pack copy = PackStore.duplicatePack(this, pack.id);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                TransientFeedback.show(this, copy == null ? "Не удалось дублировать набор" : "Набор продублирован");
            });
        });
    }

    private void showPackDetails(PackStore.Pack pack) {
        String type = pack.animated ? "Анимация" : "Фото";
        startActivity(InfoActivity.intent(
                this,
                pack.name,
                "Тип: " + type
                        + "\nСтикеров: " + pack.stickerCount
                        + "\nХранение: локально на устройстве"
        ));
    }

    private void confirmDelete(PackStore.Pack pack) {
        ThemedDialogs.confirm(this, "Удалить набор?",
                "«" + pack.name + "» будет удалён с устройства.\nЭто действие нельзя отменить.", "Удалить", () -> {
                    if (PackStore.deletePack(this, pack.id)) {
                        runtimeClearCurrentPackIfId(pack.id);
                        TransientFeedback.show(this, "Набор удалён");
                    }
                });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("sync.id", pendingSyncId);
        outState.putString("sync.version", pendingSyncVersion);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ADD_TO_WHATSAPP && pendingSyncId != null) {
            WhatsAppSync.set(this, pendingSyncId,
                    resultCode == RESULT_OK ? WhatsAppSync.State.ADDED_SYNCED : WhatsAppSync.State.SYNC_ERROR,
                    resultCode == RESULT_OK ? pendingSyncVersion : null);
            pendingSyncId = null;
            pendingSyncVersion = null;
        }
    }

    @Override
    void runtimeAddCurrentPackToWhatsApp() { addPackToWhatsApp(runtimeCurrentPack()); }

    private void addPackToWhatsApp(PackStore.Pack pack) {
        if (pack == null) return;
        File firstSticker = PackStore.getStickerFile(this, pack.id, "1.webp");
        if (!firstSticker.isFile()) {
            Toast.makeText(this, "Файлы набора не найдены", Toast.LENGTH_LONG).show();
            return;
        }

        String authority = getPackageName() + ".stickercontentprovider";
        Intent intent = new Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
        intent.putExtra("sticker_pack_id", pack.id);
        intent.putExtra("sticker_pack_authority", authority);
        intent.putExtra("sticker_pack_name", pack.name);
        pendingSyncId = pack.id;
        pendingSyncVersion = pack.imageDataVersion;
        WhatsAppSync.set(this, pack.id, WhatsAppSync.State.SYNCING, null);
        try {
            startActivityForResult(intent, REQUEST_ADD_TO_WHATSAPP);
        } catch (ActivityNotFoundException error) {
            WhatsAppSync.set(this, pack.id, WhatsAppSync.State.SYNC_ERROR, null);
            pendingSyncId = null;
            Toast.makeText(this, "WhatsApp не найден", Toast.LENGTH_LONG).show();
        }
    }

    private void notifyMetadataChanged() {
        String authority = getPackageName() + ".stickercontentprovider";
        getContentResolver().notifyChange(Uri.parse("content://" + authority + "/metadata"), null);
    }

    // Instrumentation hook: presentation only.
    void refreshPacksPanelForTest() {
        refreshPacksPanel();
    }
}
