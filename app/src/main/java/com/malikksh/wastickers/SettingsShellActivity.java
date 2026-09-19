package com.malikksh.wastickers;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

/** Final shell phase before legacy cleanup: Settings entry points and task-specific overflow menus. */
public class SettingsShellActivity extends PacksShellActivity {
    private String appliedAppearance;
    private boolean appliedCompact;
    private boolean firstResume = true;
    private FrameLayout topActions;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppSettings.wrapForAppearance(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppSettings.configureRuntime(this);
        AppSettings.applySystemBars(this);
        appliedAppearance = AppSettings.appearance(this);
        appliedCompact = AppSettings.compactMode(this);
        installTopActions();
        if (appliedCompact) applyCompactMode(findViewById(android.R.id.content));
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppSettings.configureRuntime(this);
        updateTopActionsVisibility();
        if (firstResume) {
            firstResume = false;
            return;
        }
        String currentAppearance = AppSettings.appearance(this);
        boolean currentCompact = AppSettings.compactMode(this);
        if (!currentAppearance.equals(appliedAppearance) || currentCompact != appliedCompact) {
            recreate();
        }
    }

    private void installTopActions() {
        FrameLayout root = findViewById(android.R.id.content);
        if (root == null) return;

        topActions = new FrameLayout(this);
        FrameLayout.LayoutParams actionsParams = new FrameLayout.LayoutParams(dp(104), dp(52));
        actionsParams.gravity = Gravity.TOP | Gravity.END;
        actionsParams.topMargin = dp(18);
        actionsParams.rightMargin = dp(14);
        root.addView(topActions, actionsParams);

        Button settings = topAction("⚙", "Настройки", R.id.app_settings);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        settingsParams.gravity = Gravity.START | Gravity.TOP;
        topActions.addView(settings, settingsParams);

        Button overflow = topAction("⋮", "Ещё", R.id.app_overflow);
        overflow.setOnClickListener(this::showMainOverflow);
        FrameLayout.LayoutParams overflowParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        overflowParams.gravity = Gravity.END | Gravity.TOP;
        topActions.addView(overflow, overflowParams);

        root.addOnLayoutChangeListener((v, left, top, right, bottom,
                                        oldLeft, oldTop, oldRight, oldBottom) ->
                updateTopActionsVisibility());
        root.post(this::updateTopActionsVisibility);
    }

    private void updateTopActionsVisibility() {
        if (topActions == null) return;
        View packsPanel = findViewById(R.id.packs_panel);
        topActions.setVisibility(packsPanel != null && packsPanel.isShown() ? View.GONE : View.VISIBLE);
    }

    private Button topAction(String glyph, String description, int id) {
        Button button = new Button(this);
        button.setId(id);
        button.setText(glyph);
        button.setContentDescription(description);
        button.setAllCaps(false);
        button.setTextSize(20);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(color(R.color.app_primary));
        button.setPadding(0, 0, 0, 0);
        button.setBackground(rounded(color(R.color.app_surface_variant), 16));
        return button;
    }

    private void showMainOverflow(View anchor) {
        if (isShown(R.id.media_grid)) {
            showMediaOverflow(anchor);
        } else if (isShown(R.id.build_panel)) {
            showBuildOverflow(anchor);
        } else {
            showCreateOverflow(anchor);
        }
    }

    private boolean isShown(int id) {
        View view = findViewById(id);
        return view != null && view.isShown();
    }

    private void showCreateOverflow(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Очистить черновик");
        if (EditorInstanceStateBridge.hasPersistent(this)) {
            menu.getMenu().add("Восстановить последний");
        }
        menu.getMenu().add("Помощь");
        menu.getMenu().add("О приложении");
        menu.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if ("Очистить черновик".equals(title)) {
                requestClearDraft();
                return true;
            }
            if ("Восстановить последний".equals(title)) {
                restoreLastDraft();
                return true;
            }
            if ("Помощь".equals(title)) {
                showHelp();
                return true;
            }
            if ("О приложении".equals(title)) {
                showAbout();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showMediaOverflow(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Выбрать всё");
        menu.getMenu().add("Сбросить порядок");
        menu.getMenu().add("Очистить");
        menu.getMenu().add("Помощь");
        menu.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if ("Выбрать всё".equals(title)) {
                MediaGridPanel panel = mediaPanel();
                int count = panel == null ? 0 : panel.itemCount();
                TransientFeedback.show(this,
                        count == 0 ? "Сначала добавьте файлы" : "Все файлы уже входят в набор");
                return true;
            }
            if ("Сбросить порядок".equals(title)) {
                MediaGridPanel panel = mediaPanel();
                if (panel == null || !panel.resetOrder()) {
                    TransientFeedback.show(this, "Порядок уже исходный");
                }
                return true;
            }
            if ("Очистить".equals(title)) {
                clearMediaFromOverflow();
                return true;
            }
            if ("Помощь".equals(title)) {
                showMediaHelp();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private MediaGridPanel mediaPanel() {
        View detail = findViewById(R.id.media_detail);
        if (detail == null) return null;
        ViewParent parent = detail.getParent();
        return parent instanceof MediaGridPanel ? (MediaGridPanel) parent : null;
    }

    private void clearMediaFromOverflow() {
        if (MainActivityRuntimeAccess.isProcessing(this)) {
            Toast.makeText(this, "Сначала остановите обработку", Toast.LENGTH_SHORT).show();
            return;
        }
        if (MainActivityRuntimeAccess.clearMedia(this)) {
            EditorInstanceStateBridge.savePersistent(this);
            refreshShellPresentation();
            TransientFeedback.show(this, "Медиа очищены");
        }
    }

    private void showMediaHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Медиа")
                .setMessage("Удерживайте карточку для сортировки, ★ выбирает обложку. Для длинного видео откройте карточку и измените фрагмент.")
                .setPositiveButton("Понятно", null)
                .show();
    }

    private void showBuildOverflow(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Повторить ошибки");
        menu.getMenu().add("Показать диагностику");
        menu.getMenu().add("Очистить сборку");
        menu.getMenu().add("Помощь");
        menu.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if ("Повторить ошибки".equals(title)) {
                retryBuildFailuresFromOverflow();
                return true;
            }
            if ("Показать диагностику".equals(title)) {
                showDiagnostics();
                return true;
            }
            if ("Очистить сборку".equals(title)) {
                clearBuildFromOverflow();
                return true;
            }
            if ("Помощь".equals(title)) {
                showBuildHelp();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void retryBuildFailuresFromOverflow() {
        Button secondary = findViewById(R.id.build_secondary);
        if (secondary != null && secondary.isShown() && secondary.isEnabled()
                && secondary.getText().toString().contains("Повторить ошибки")) {
            secondary.performClick();
            return;
        }
        Button primary = findViewById(R.id.build_primary);
        if (primary != null && primary.isShown() && primary.isEnabled()
                && primary.getText().toString().contains("Повторить ошибки")) {
            primary.performClick();
            return;
        }
        TransientFeedback.show(this, "Нет ошибок для повтора");
    }

    private void showDiagnostics() {
        String diagnostics = BugLogStore.snapshot().trim();
        if (diagnostics.isEmpty()) diagnostics = "Диагностика пока пуста.";
        new AlertDialog.Builder(this)
                .setTitle("Диагностика сборки")
                .setMessage(diagnostics)
                .setPositiveButton("Закрыть", null)
                .show();
    }

    private void clearBuildFromOverflow() {
        if (MainActivityRuntimeAccess.isProcessing(this)) {
            Toast.makeText(this, "Сначала остановите обработку", Toast.LENGTH_SHORT).show();
            return;
        }
        MainActivityRuntimeAccess.discardPendingBuild(this);
        refreshShellPresentation();
        TransientFeedback.show(this, "Сборка очищена");
    }

    private void showBuildHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Сборка")
                .setMessage("Следите за общим и пофайловым прогрессом. Ошибки можно повторять отдельно, а при трёх готовых стикерах — завершить частичный набор.")
                .setPositiveButton("Понятно", null)
                .show();
    }

    private void requestClearDraft() {
        if (MainActivityRuntimeAccess.isProcessing(this)) {
            Toast.makeText(this, "Сначала остановите обработку", Toast.LENGTH_SHORT).show();
            return;
        }
        if (MainActivityRuntimeAccess.selectedUrisSnapshot(this).isEmpty()) {
            clearDraftNow();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Очистить черновик?")
                .setMessage("Выбранные файлы и настройки будут удалены.")
                .setPositiveButton("Очистить", (dialog, which) -> clearDraftNow())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void clearDraftNow() {
        if (!MainActivityRuntimeAccess.clearEditorDraft(this)) {
            Toast.makeText(this, "Не удалось очистить черновик", Toast.LENGTH_SHORT).show();
            return;
        }

        EditorInstanceStateBridge.clearPersistent(this);
        VideoTrimStore.clear();
        VideoTrimStore.clearPersistent(this, AppSettings.TRIM_PERSISTENT_KEY);
        refreshShellPresentation();
        TransientFeedback.show(this, "Черновик очищен");
    }

    private void restoreLastDraft() {
        if (!EditorInstanceStateBridge.hasPersistent(this)) {
            TransientFeedback.show(this, "Сохранённого черновика нет");
            return;
        }
        boolean restored = EditorInstanceStateBridge.restorePersistent(this);
        VideoTrimStore.restorePersistent(this, AppSettings.TRIM_PERSISTENT_KEY);
        refreshShellPresentation();
        if (restored) {
            TransientFeedback.show(this, "Черновик восстановлен");
        } else {
            Toast.makeText(this, "Не удалось восстановить черновик", Toast.LENGTH_SHORT).show();
        }
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Помощь")
                .setMessage("Создать → настроить медиа → собрать → сохранить. Ошибочные файлы на экране сборки можно повторять отдельно.")
                .setPositiveButton("Понятно", null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("WA Stickers")
                .setMessage("Все файлы обрабатываются локально.\n\nВерсия " + BuildConfig.VERSION_NAME)
                .setPositiveButton("Закрыть", null)
                .show();
    }

    private void refreshShellPresentation() {
        MainActivityRuntimeAccess.refreshAppShell(this);
        try {
            refreshBuildPanelForTest();
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not refresh Build panel after overflow action: " + error);
        }
        try {
            refreshPacksPanelForTest();
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not refresh Packs panel after overflow action: " + error);
        }
        updateTopActionsVisibility();
    }

    private void applyCompactMode(View view) {
        if (view == null) return;
        int left = Math.round(view.getPaddingLeft() * 0.82f);
        int top = Math.round(view.getPaddingTop() * 0.82f);
        int right = Math.round(view.getPaddingRight() * 0.82f);
        int bottom = Math.round(view.getPaddingBottom() * 0.82f);
        view.setPadding(left, top, right, bottom);
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (text.getTextSize() > 0) {
                float sp = text.getTextSize() / getResources().getDisplayMetrics().scaledDensity;
                text.setTextSize(Math.max(11f, sp * 0.94f));
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyCompactMode(group.getChildAt(i));
            }
        }
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
}
