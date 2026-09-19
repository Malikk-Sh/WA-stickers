package com.malikksh.wastickers;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Packs-phase migration layer that moves SavedPacksActivity functionality into the main tab. */
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
            public void onShowPackActions(PackStore.Pack pack) {
                showPackActions(pack);
            }

            @Override
            public void onCreateFirstPack() {
                View create = findViewById(R.id.nav_create);
                if (create != null) create.performClick();
            }

            @Override
            public void onSearchRequested() {
                showSearchDialog();
            }

            @Override
            public void onOverflowRequested(View anchor) {
                showPacksOverflow(anchor);
            }
        });

        ScrollView scroll = (ScrollView) packsScreen;
        scroll.removeAllViews();
        scroll.setFillViewport(true);
        scroll.addView(packsPanel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void refreshPacksPanel() {
        if (packsPanel == null) return;
        List<PackStore.Pack> packs = PackStore.getPacks(this);
        packsPanel.render(packs);
    }

    private void showSearchDialog() {
        if (packsPanel == null) return;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Название набора");
        input.setText(packsPanel.query());
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable background = rounded(color(R.color.app_surface), 14);
        background.setStroke(dp(1), color(R.color.app_border));
        input.setBackground(background);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(20), dp(8), dp(20), 0);
        wrapper.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Поиск наборов")
                .setView(wrapper)
                .setPositiveButton("Найти", (d, which) -> packsPanel.setQuery(
                        input.getText().toString()))
                .setNeutralButton("Сбросить", (d, which) -> packsPanel.setQuery(""))
                .setNegativeButton("Отмена", null)
                .create();
        dialog.setOnShowListener(ignored -> input.requestFocus());
        dialog.show();
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
                "Формат импорта сохранённых наборов ещё не определён. Создавайте наборы через вкладку «Создать», чтобы не потерять метаданные и совместимость с WhatsApp."
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
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(pack.name);
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable background = rounded(color(R.color.app_surface), 14);
        background.setStroke(dp(1), color(R.color.app_border));
        input.setBackground(background);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(20), dp(8), dp(20), 0);
        wrapper.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        new AlertDialog.Builder(this)
                .setTitle("Переименовать набор")
                .setView(wrapper)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        TransientFeedback.show(this, "Название не может быть пустым");
                        return;
                    }
                    PackStore.Pack renamed = PackStore.renamePack(this, pack.id, name);
                    if (renamed != null) {
                        this.runtimeReplaceCurrentPackIfId(pack.id, renamed);
                        notifyMetadataChanged();
                        refreshPacksPanel();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
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

    private GradientDrawable rounded(int fillColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    @SuppressWarnings("deprecation")
    private int color(int resourceId) {
        return getResources().getColor(resourceId);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // Instrumentation hook: presentation only.
    void refreshPacksPanelForTest() {
        refreshPacksPanel();
    }
}
