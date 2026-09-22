package com.malikksh.wastickers;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

/** Final shell phase: Settings entry points and task-specific overflow menus. */
public class SettingsShellActivity extends PacksShellActivity {
    private static final String STATE_DRAFT_RESTORED = "settings_shell.draft_restored";

    private String appliedAppearance;
    private boolean appliedCompact;
    private boolean firstResume = true;
    private boolean draftRestoredThisSession;
    private FrameLayout topActions;
    private TextView draftChip;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppSettings.wrapForAppearance(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        draftRestoredThisSession = savedInstanceState != null
                ? savedInstanceState.getBoolean(STATE_DRAFT_RESTORED, false)
                : restoredPersistentDraftOnLaunch();
        AppSettings.configureRuntime(this);
        AppSettings.applySystemBars(this);
        appliedAppearance = AppSettings.appearance(this);
        appliedCompact = AppSettings.compactMode(this);
        configureCreatePresentation();
        installTopActions();
        if (appliedCompact) applyCompactMode(findViewById(android.R.id.content));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_DRAFT_RESTORED, draftRestoredThisSession);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppSettings.configureRuntime(this);
        updateTopActionsVisibility();
        updateDraftChip();
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

    @Override
    void refreshShellState() {
        super.refreshShellState();
        updateTopActionsVisibility();
        updateDraftChip();
    }

    private void configureCreatePresentation() {
        EditText packName = this.runtimePackName();
        if (packName != null) {
            packName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});
            TextView nameCounter = findViewById(R.id.create_name_counter);
            CreateNameCounterPolicy.attach(packName, nameCounter);
        }
        rewriteText(findViewById(android.R.id.content),
                "Фото и анимированные стикеры сохраняются как отдельные наборы.",
                "Режимы сохраняются отдельно");
        hideExactText(findViewById(android.R.id.content), "Файлы останутся на устройстве");
        installDraftChip();
    }

    private void installDraftChip() {
        View counter = findViewById(R.id.create_media_counter);
        if (counter == null) return;
        ViewParent headerParent = counter.getParent();
        if (!(headerParent instanceof View)) return;
        ViewParent cardParent = ((View) headerParent).getParent();
        if (!(cardParent instanceof LinearLayout)) return;

        LinearLayout card = (LinearLayout) cardParent;
        draftChip = new TextView(this);
        draftChip.setId(R.id.create_draft_chip);
        draftChip.setText("Черновик");
        draftChip.setTextSize(12);
        draftChip.setTypeface(Typeface.create("sans", Typeface.BOLD));
        draftChip.setTextColor(color(R.color.app_primary));
        draftChip.setGravity(Gravity.CENTER);
        draftChip.setPadding(dp(10), dp(5), dp(10), dp(5));
        draftChip.setBackground(rounded(color(R.color.app_primary_container), 14));
        draftChip.setVisibility(View.GONE);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        card.addView(draftChip, Math.min(1, card.getChildCount()), params);
        updateDraftChip();
    }

    private void updateDraftChip() {
        if (draftChip == null) return;
        boolean visible = draftRestoredThisSession
                && EditorInstanceStateBridge.hasPersistent(this)
                && !this.runtimeSelectedUrisSnapshot().isEmpty();
        draftChip.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void rewriteText(View view, String from, String to) {
        if (view == null) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            CharSequence value = text.getText();
            if (value != null && from.contentEquals(value)) text.setText(to);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                rewriteText(group.getChildAt(i), from, to);
            }
        }
    }

    private void hideExactText(View view, String expected) {
        if (view == null) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            CharSequence value = text.getText();
            if (value != null && expected.contentEquals(value)) text.setVisibility(View.GONE);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                hideExactText(group.getChildAt(i), expected);
            }
        }
    }

    private void installTopActions() {
        FrameLayout root = findViewById(android.R.id.content);
        if (root == null) return;

        topActions = new FrameLayout(this);
        ImageButton settings = topAction(R.drawable.ic_settings, "Настройки", R.id.app_settings);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        settingsParams.gravity = Gravity.START | Gravity.TOP;
        topActions.addView(settings, settingsParams);

        ImageButton overflow = topAction(R.drawable.ic_more_vertical, "Ещё", R.id.app_overflow);
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
        FrameLayout screens = findViewById(R.id.app_screen_host);
        if (screens == null) return;
        FrameLayout slot = null;
        for (int i = 0; i < screens.getChildCount(); i++) {
            View screen = screens.getChildAt(i);
            if (screen.getVisibility() == View.VISIBLE) {
                slot = screen.findViewWithTag("shell_actions");
                break;
            }
        }
        boolean visible = slot != null;
        // Keep the entry points attached to the editor while the library owns its own header.
        if (slot == null && topActions.getParent() == null) slot = screens.getChildAt(0).findViewWithTag("shell_actions");
        if (slot != null && topActions.getParent() != slot) {
            if (topActions.getParent() instanceof ViewGroup)
                ((ViewGroup) topActions.getParent()).removeView(topActions);
            slot.addView(topActions, new FrameLayout.LayoutParams(-1, -1));
        }
        topActions.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private ImageButton topAction(int drawable, String description, int id) {
        ImageButton button = new ImageButton(this);
        button.setId(id);
        button.setImageResource(drawable);
        button.setContentDescription(description);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackground(UiComponents.rounded(
                this, R.color.app_surface_variant, R.dimen.radius_card));
        return button;
    }

    private void showMainOverflow(View anchor) {
        if (isShown(R.id.media_grid)) {
            showEditorOverflow(anchor);
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

    private void showEditorOverflow(View anchor) {
        PopupMenu menu = new TonalMenu(this, anchor);
        menu.getMenu().add("Переименовать");
        menu.getMenu().add("Очистить проект");
        menu.getMenu().add("Сбросить порядок");
        menu.getMenu().add("Помощь");
        menu.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if ("Переименовать".equals(title)) {
                renameEditorProject();
                return true;
            }
            if ("Очистить проект".equals(title)) {
                requestClearDraft();
                return true;
            }
            if ("Сбросить порядок".equals(title)) {
                MediaGridPanel panel = mediaPanel();
                if (panel == null || !panel.resetOrder()) {
                    TransientFeedback.show(this, "Порядок уже исходный");
                }
                return true;
            }
            if ("Помощь".equals(title)) {
                showHelp();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void renameEditorProject() {
        EditText nameField = this.runtimePackName();
        String current = nameField == null ? "" : nameField.getText().toString();
        PackNameDialog.show(this, "Переименовать набор", current, "Сохранить", name -> {
            EditText field = this.runtimePackName();
            if (field != null) field.setText(name);
            EditorInstanceStateBridge.savePersistent(this);
            refreshShellPresentation();
            TransientFeedback.show(this, "Набор переименован");
        });
    }

    private void showCreateOverflow(View anchor) {
        PopupMenu menu = new TonalMenu(this, anchor);
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
        PopupMenu menu = new TonalMenu(this, anchor);
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
        if (this.runtimeIsProcessing()) {
            Toast.makeText(this, "Сначала остановите обработку", Toast.LENGTH_SHORT).show();
            return;
        }
        if (this.runtimeClearMedia()) {
            EditorInstanceStateBridge.savePersistent(this);
            refreshShellPresentation();
            TransientFeedback.show(this, "Медиа очищены");
        }
    }

    private void showMediaHelp() {
        startActivity(InfoActivity.intent(
                this,
                "Медиа",
                "Добавьте от 3 до 30 файлов и удерживайте карточку для сортировки. Кнопка обложки отмечает единственную обложку набора. Для длинного видео откройте карточку и измените фрагмент. Удаление отдельного файла можно отменить."
        ));
    }

    private void showBuildOverflow(View anchor) {
        PopupMenu menu = new TonalMenu(this, anchor);
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
        startActivity(InfoActivity.intent(this, "Диагностика сборки", diagnostics));
    }

    private void clearBuildFromOverflow() {
        if (this.runtimeIsProcessing()) {
            Toast.makeText(this, "Сначала остановите обработку", Toast.LENGTH_SHORT).show();
            return;
        }
        this.runtimeDiscardPendingBuild();
        refreshShellPresentation();
        TransientFeedback.show(this, "Сборка очищена");
    }

    private void showBuildHelp() {
        startActivity(InfoActivity.intent(
                this,
                "Сборка",
                "Следите за общим и пофайловым прогрессом. Ошибки можно повторять отдельно, а при трёх готовых стикерах — завершить частичный набор. Активную обработку можно остановить после подтверждения."
        ));
    }

    private void requestClearDraft() {
        if (this.runtimeIsProcessing()) {
            Toast.makeText(this, "Сначала остановите обработку", Toast.LENGTH_SHORT).show();
            return;
        }
        if (this.runtimeSelectedUrisSnapshot().isEmpty()) {
            clearDraftNow();
            return;
        }
        ThemedDialogs.confirm(this, "Очистить черновик?", "Выбранные файлы и настройки будут удалены.",
                "Очистить", this::clearDraftNow);
    }

    private void clearDraftNow() {
        if (!this.runtimeClearEditorDraft()) {
            Toast.makeText(this, "Не удалось очистить черновик", Toast.LENGTH_SHORT).show();
            return;
        }

        draftRestoredThisSession = false;
        EditorInstanceStateBridge.removeProject(this, runtimeProjectId());
        VideoTrimStore.clear();
        VideoTrimStore.clearPersistent(this, AppSettings.TRIM_PERSISTENT_KEY + "." + runtimeProjectId());
        refreshShellPresentation();
        TransientFeedback.show(this, "Черновик очищен");
    }

    private void restoreLastDraft() {
        if (!EditorInstanceStateBridge.hasPersistent(this)) {
            TransientFeedback.show(this, "Сохранённого черновика нет");
            return;
        }
        boolean restored = EditorInstanceStateBridge.restorePersistent(this);
        VideoTrimStore.restorePersistent(this, AppSettings.TRIM_PERSISTENT_KEY + "." + runtimeProjectId());
        if (restored) draftRestoredThisSession = true;
        refreshShellPresentation();
        if (restored) {
            TransientFeedback.show(this, "Черновик восстановлен");
        } else {
            Toast.makeText(this, "Не удалось восстановить черновик", Toast.LENGTH_SHORT).show();
        }
    }

    private void showHelp() {
        startActivity(InfoActivity.intent(
                this,
                "Помощь",
                "Создайте набор, добавьте медиа, соберите и сохраните его. Ошибки можно повторить отдельно. Черновик сохраняется локально на устройстве."
        ));
    }

    private void showAbout() {
        startActivity(InfoActivity.intent(
                this,
                "WA Stickers",
                "Все файлы обрабатываются локально.\n\nВерсия " + BuildConfig.VERSION_NAME
        ));
    }

    private void refreshShellPresentation() {
        this.refreshShellState();
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
        updateDraftChip();
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
