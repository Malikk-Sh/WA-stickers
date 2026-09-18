package com.malikksh.wastickers;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Final shell phase before legacy cleanup: Settings entry points and the global overflow menu. */
public class SettingsShellActivity extends PacksShellActivity {
    private String appliedAppearance;
    private boolean appliedCompact;
    private boolean firstResume = true;

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

        FrameLayout actions = new FrameLayout(this);
        FrameLayout.LayoutParams actionsParams = new FrameLayout.LayoutParams(dp(104), dp(52));
        actionsParams.gravity = Gravity.TOP | Gravity.END;
        actionsParams.topMargin = dp(18);
        actionsParams.rightMargin = dp(14);
        root.addView(actions, actionsParams);

        Button settings = topAction("⚙", "Настройки", R.id.app_settings);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        settingsParams.gravity = Gravity.START | Gravity.TOP;
        actions.addView(settings, settingsParams);

        Button overflow = topAction("⋮", "Ещё", R.id.app_overflow);
        overflow.setOnClickListener(this::showMainOverflow);
        FrameLayout.LayoutParams overflowParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        overflowParams.gravity = Gravity.END | Gravity.TOP;
        actions.addView(overflow, overflowParams);
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

    private void requestClearDraft() {
        if (isProcessing()) {
            Toast.makeText(this, "Сначала остановите обработку", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedUrisSnapshot().isEmpty()) {
            clearDraftNow();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Очистить черновик?")
                .setMessage("Выбранные медиа и настройки фрагментов будут сброшены. Сохранённые наборы останутся.")
                .setPositiveButton("Очистить", (dialog, which) -> clearDraftNow())
                .setNegativeButton("Отмена", null)
                .show();
    }

    @SuppressWarnings("unchecked")
    private void clearDraftNow() {
        try {
            List<Uri> selected = (List<Uri>) readMainField("selectedUris");
            if (selected != null) selected.clear();
            writeMainField("coverUri", null);

            Object name = readMainField("packName");
            if (name instanceof EditText) ((EditText) name).setText("");

            Object controllerValue = readMainField("editorStateController");
            if (controllerValue instanceof EditorStateController<?>) {
                EditorStateController<Uri> controller = (EditorStateController<Uri>) controllerValue;
                controller.capture(false, new ArrayList<>(), null, "");
                controller.capture(true, new ArrayList<>(), null, "");
            }

            invokeMain("discardPendingBuild");
            invokeMain("invalidateCurrentPack");
            invokeMain("renderPreviews");
            invokeMain("updateModeUi");
            invokeMain("updateUiState");

            EditorInstanceStateBridge.clearPersistent(this);
            VideoTrimStore.clear();
            VideoTrimStore.clearPersistent(this, AppSettings.TRIM_PERSISTENT_KEY);
            refreshShellPresentation();
            Toast.makeText(this, "Черновик очищен", Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not clear redesigned draft: " + error);
            Toast.makeText(this, "Не удалось очистить черновик", Toast.LENGTH_SHORT).show();
        }
    }

    private void restoreLastDraft() {
        if (!EditorInstanceStateBridge.hasPersistent(this)) {
            Toast.makeText(this, "Сохранённого черновика нет", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean restored = EditorInstanceStateBridge.restorePersistent(this);
        VideoTrimStore.restorePersistent(this, AppSettings.TRIM_PERSISTENT_KEY);
        refreshShellPresentation();
        Toast.makeText(this,
                restored ? "Черновик восстановлен" : "Не удалось восстановить черновик",
                Toast.LENGTH_SHORT).show();
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
        try {
            Method method = AppShellActivity.class.getDeclaredMethod("refreshShellState");
            method.setAccessible(true);
            method.invoke(this);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not refresh app shell after overflow action: " + error);
        }
        try {
            Method method = BuildShellActivity.class.getDeclaredMethod("refreshBuildPanelForTest");
            method.setAccessible(true);
            method.invoke(this);
        } catch (Throwable ignored) {
        }
        try {
            Method method = PacksShellActivity.class.getDeclaredMethod("refreshPacksPanelForTest");
            method.setAccessible(true);
            method.invoke(this);
        } catch (Throwable ignored) {
        }
    }

    private void invokeMain(String name) {
        try {
            Method method = MainActivity.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(this);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not invoke " + name + " from settings shell: " + error);
        }
    }

    private Object readMainField(String name) {
        try {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(this);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void writeMainField(String name, Object value) {
        try {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(this, value);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not update " + name + " from settings shell: " + error);
        }
    }

    private boolean isProcessing() {
        Object value = readMainField("processing");
        return value instanceof Boolean && (Boolean) value;
    }

    @SuppressWarnings("unchecked")
    private List<Uri> selectedUrisSnapshot() {
        Object value = readMainField("selectedUris");
        if (!(value instanceof List<?>)) return new ArrayList<>();
        return new ArrayList<>((List<Uri>) value);
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

    private Button unusedButtonForLint() {
        return null;
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