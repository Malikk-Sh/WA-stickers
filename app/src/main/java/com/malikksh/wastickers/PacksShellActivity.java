package com.malikksh.wastickers;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            installPacksPanel();
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
    }

    @Override
    protected void onDestroy() {
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
                showPackDetails(pack);
            }

            @Override
            public void onShowPackActions(PackStore.Pack pack) {
                showPackActions(pack);
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

    private void refreshPacksPanel() {
        if (packsPanel == null) return;
        List<PackStore.Pack> packs = PackStore.getPacks(this);
        packsPanel.render(packs);
    }

    private void showCreatePackDialog() {
        String currentName = this.runtimePackName() == null
                ? ""
                : this.runtimePackName().getText().toString().trim();
        String initial = currentName.isEmpty() ? "Мои стикеры" : currentName;
        PackNameDialog.show(this, "Новый набор", initial, "Создать", name -> {
            if (this.runtimePackName() != null) this.runtimePackName().setText(name);
            this.runtimeInvalidateCurrentPack();
            this.runtimeDiscardPendingBuild();
            View create = findViewById(R.id.nav_create);
            if (create != null) create.performClick();
            refreshShellState();
            EditorInstanceStateBridge.savePersistent(this);
        });
    }

    private void showPacksOverflow(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Импортировать");
        menu.getMenu().add("Очистить недоступные");
        menu.getMenu().add("Помощь");
        menu.getMenu().add("О приложении");
        menu.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if ("Импортировать".equals(title)) {
                showImportNotice();
                return true;
            }
            if ("Очистить недоступные".equals(title)) {
                cleanupUnavailablePacks();
                return true;
            }
            if ("Помощь".equals(title)) {
                showPacksHelp();
                return true;
            }
            if ("О приложении".equals(title)) {
                showPacksAbout();
                return true;
            }
            return false;
        });
        menu.show();
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
        PackNameDialog.show(this, "Переименовать набор", pack.name, "Сохранить", name -> {
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
        PackStore.Pack copy = PackStore.duplicatePack(this, pack.id);
        if (copy == null) {
            Toast.makeText(this, "Не удалось дублировать набор", Toast.LENGTH_LONG).show();
            return;
        }
        notifyMetadataChanged();
        refreshPacksPanel();
        TransientFeedback.show(this, "Набор продублирован");
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
        new AlertDialog.Builder(this)
                .setTitle("Удалить набор?")
                .setMessage("Набор будет удалён с устройства.")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    if (PackStore.deletePack(this, pack.id)) {
                        this.runtimeClearCurrentPackIfId(pack.id);
                        notifyMetadataChanged();
                        refreshPacksPanel();
                        TransientFeedback.show(this, "Набор удалён");
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

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
        try {
            startActivityForResult(intent, REQUEST_ADD_TO_WHATSAPP);
        } catch (ActivityNotFoundException error) {
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
