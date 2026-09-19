package com.malikksh.wastickers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

public class SettingsActivity extends Activity {
    private Button themeSystem;
    private Button themeLight;
    private Button themeDark;
    private Button qualitySmoother;
    private Button qualityBalance;
    private Button qualitySharper;
    private Switch compactSwitch;
    private Switch draftsSwitch;
    private Switch cleanupSwitch;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppSettings.wrapForAppearance(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppSettings.configureRuntime(this);
        AppSettings.applySystemBars(this);
        setContentView(buildUi());
        renderSettings();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(color(R.color.app_background));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(buildHeader(), matchWrap());
        root.addView(sectionAppearance(), topMargin(18));
        root.addView(sectionProcessing(), topMargin(14));
        root.addView(sectionStorage(), topMargin(14));
        root.addView(sectionAbout(), topMargin(14));
        return scroll;
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        Button back = new Button(this);
        back.setId(R.id.settings_back);
        back.setText("‹");
        back.setContentDescription("Назад");
        back.setTextSize(27);
        back.setAllCaps(false);
        back.setTextColor(color(R.color.app_primary));
        back.setBackground(rounded(color(R.color.app_primary_container), 17));
        back.setOnClickListener(v -> finish());
        row.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.leftMargin = dp(14);
        row.addView(labels, labelsParams);

        TextView title = text("Настройки", 27, color(R.color.app_text_primary), Typeface.BOLD);
        title.setId(R.id.settings_title);
        labels.addView(title, matchWrap());
        TextView subtitle = text("Персонализация и обработка", 14,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        LinearLayout.LayoutParams subParams = matchWrap();
        subParams.topMargin = dp(2);
        labels.addView(subtitle, subParams);
        return row;
    }

    private View sectionAppearance() {
        LinearLayout card = card();
        card.addView(sectionTitle("Внешний вид"), matchWrap());
        card.addView(label("Тема"), topMargin(14));

        LinearLayout theme = segmentedRow();
        themeSystem = segment("Системная", R.id.settings_theme_system,
                () -> changeAppearance(AppSettings.APPEARANCE_SYSTEM));
        themeLight = segment("Светлая", R.id.settings_theme_light,
                () -> changeAppearance(AppSettings.APPEARANCE_LIGHT));
        themeDark = segment("Тёмная", R.id.settings_theme_dark,
                () -> changeAppearance(AppSettings.APPEARANCE_DARK));
        addSegment(theme, themeSystem, false);
        addSegment(theme, themeLight, true);
        addSegment(theme, themeDark, true);
        card.addView(theme, topMargin(8));

        compactSwitch = switchRow(
                "Компактный режим",
                "Показывать больше контента на экране",
                R.id.settings_compact,
                checked -> AppSettings.setCompactMode(this, checked));
        card.addView(compactSwitch, topMargin(16));
        return card;
    }

    private View sectionProcessing() {
        LinearLayout card = card();
        card.addView(sectionTitle("Обработка"), matchWrap());
        card.addView(label("Качество анимации"), topMargin(14));
        TextView qualityHint = text("Баланс между размером и плавностью", 12,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        card.addView(qualityHint, topMargin(3));

        LinearLayout quality = segmentedRow();
        qualitySmoother = segment("Плавнее", R.id.settings_quality_smoother,
                () -> setQuality(AppSettings.QUALITY_SMOOTHER));
        qualityBalance = segment("Баланс", R.id.settings_quality_balance,
                () -> setQuality(AppSettings.QUALITY_BALANCE));
        qualitySharper = segment("Чётче", R.id.settings_quality_sharper,
                () -> setQuality(AppSettings.QUALITY_SHARPER));
        addSegment(quality, qualitySmoother, false);
        addSegment(quality, qualityBalance, true);
        addSegment(quality, qualitySharper, true);
        card.addView(quality, topMargin(9));

        card.addView(valueRow("Макс. длительность", "10 сек"), topMargin(16));

        draftsSwitch = switchRow(
                "Сохранять черновики",
                "Восстанавливать выбор после перезапуска",
                R.id.settings_keep_drafts,
                checked -> AppSettings.setKeepDrafts(this, checked));
        card.addView(draftsSwitch, topMargin(12));
        return card;
    }

    private View sectionStorage() {
        LinearLayout card = card();
        card.addView(sectionTitle("Хранилище"), matchWrap());

        Button clear = new Button(this);
        clear.setId(R.id.settings_clear_cache);
        clear.setAllCaps(false);
        clear.setText("Очистить кэш");
        clear.setTextSize(15);
        clear.setTypeface(Typeface.create("sans", Typeface.BOLD));
        clear.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        clear.setPadding(dp(14), 0, dp(14), 0);
        clear.setTextColor(color(R.color.app_primary));
        clear.setBackground(rounded(color(R.color.app_surface_variant), 16));
        clear.setOnClickListener(v -> clearCache());
        card.addView(clear, heightTop(52, 14));

        cleanupSwitch = switchRow(
                "Автоочистка временных файлов",
                "Удалять рабочие файлы после использования",
                R.id.settings_auto_cleanup,
                checked -> AppSettings.setAutoCleanup(this, checked));
        card.addView(cleanupSwitch, topMargin(14));
        return card;
    }

    private View sectionAbout() {
        LinearLayout card = card();
        card.addView(sectionTitle("О приложении"), matchWrap());

        TextView version = valueRow("Версия", versionName());
        version.setId(R.id.settings_version);
        card.addView(version, topMargin(14));

        TextView privacy = text("Все файлы обрабатываются локально", 14,
                color(R.color.app_text_primary), Typeface.NORMAL);
        privacy.setId(R.id.settings_privacy);
        privacy.setPadding(0, dp(12), 0, dp(12));
        card.addView(privacy, topMargin(4));

        Button help = secondaryButton("Помощь");
        help.setId(R.id.settings_help);
        help.setOnClickListener(v -> showHelp());
        card.addView(help, heightTop(50, 8));

        Button about = secondaryButton("О приложении");
        about.setId(R.id.settings_about);
        about.setOnClickListener(v -> showAbout());
        card.addView(about, heightTop(50, 8));
        return card;
    }

    private Switch switchRow(String title, String subtitle, int id, ToggleListener listener) {
        Switch control = new Switch(this);
        control.setId(id);
        control.setText(title + "\n" + subtitle);
        control.setTextSize(14);
        control.setTextColor(color(R.color.app_text_primary));
        control.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setMinHeight(dp(48));
        control.setPadding(0, dp(5), 0, dp(5));
        control.setOnCheckedChangeListener((buttonView, isChecked) -> listener.onChanged(isChecked));
        return control;
    }

    private void changeAppearance(String appearance) {
        if (appearance.equals(AppSettings.appearance(this))) return;
        AppSettings.setAppearance(this, appearance);
        recreate();
    }

    private void setQuality(String quality) {
        AppSettings.setQualityPreset(this, quality);
        renderSettings();
        TransientFeedback.show(this, "Качество будет применено к будущим конвертациям");
    }

    private void renderSettings() {
        String appearance = AppSettings.appearance(this);
        styleSegment(themeSystem, AppSettings.APPEARANCE_SYSTEM.equals(appearance));
        styleSegment(themeLight, AppSettings.APPEARANCE_LIGHT.equals(appearance));
        styleSegment(themeDark, AppSettings.APPEARANCE_DARK.equals(appearance));

        String quality = AppSettings.qualityPreset(this);
        styleSegment(qualitySmoother, AppSettings.QUALITY_SMOOTHER.equals(quality));
        styleSegment(qualityBalance, AppSettings.QUALITY_BALANCE.equals(quality));
        styleSegment(qualitySharper, AppSettings.QUALITY_SHARPER.equals(quality));

        if (compactSwitch != null) compactSwitch.setChecked(AppSettings.compactMode(this));
        if (draftsSwitch != null) draftsSwitch.setChecked(AppSettings.keepDrafts(this));
        if (cleanupSwitch != null) cleanupSwitch.setChecked(AppSettings.autoCleanup(this));
    }

    private void clearCache() {
        long freed = AppSettings.clearConversionCache(this);
        AppSettings.cleanupTemporaryFiles(this);
        TransientFeedback.show(this, "Освобождено " + formatBytes(freed));
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Помощь")
                .setMessage("Создайте набор, настройте медиа, соберите файлы и добавьте готовый набор в WhatsApp. Ошибки можно повторять отдельно.")
                .setPositiveButton("Понятно", null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("WA Stickers")
                .setMessage("Версия " + versionName() + "\n\nВсе файлы обрабатываются локально.")
                .setPositiveButton("Закрыть", null)
                .show();
    }

    private LinearLayout segmentedRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private Button segment(String value, int id, Runnable action) {
        Button button = new Button(this);
        button.setId(id);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void addSegment(LinearLayout row, Button button, boolean withMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        if (withMargin) params.leftMargin = dp(7);
        row.addView(button, params);
    }

    private void styleSegment(Button button, boolean selected) {
        if (button == null) return;
        button.setTextColor(selected
                ? color(R.color.app_on_primary)
                : color(R.color.app_primary));
        button.setBackground(rounded(
                selected ? color(R.color.app_primary) : color(R.color.app_surface_variant),
                15));
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(color(R.color.app_primary));
        button.setBackground(rounded(color(R.color.app_primary_container), 16));
        return button;
    }

    private TextView sectionTitle(String value) {
        return text(value, 18, color(R.color.app_text_primary), Typeface.BOLD);
    }

    private TextView label(String value) {
        return text(value, 14, color(R.color.app_text_primary), Typeface.BOLD);
    }

    private TextView valueRow(String label, String value) {
        TextView row = text(label + "\n" + value, 14,
                color(R.color.app_text_primary), Typeface.NORMAL);
        row.setLineSpacing(dp(2), 1f);
        row.setPadding(0, dp(5), 0, dp(5));
        return row;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(rounded(color(R.color.app_surface), 24));
        if (android.os.Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));
        return card;
    }

    private TextView text(String value, int size, int textColor, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(textColor);
        view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    private GradientDrawable rounded(int fillColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    @SuppressWarnings("deprecation")
    private int color(int resource) {
        return getResources().getColor(resource);
    }

    private String versionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "—" : info.versionName;
        } catch (Throwable ignored) {
            return "—";
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return Math.round(bytes / 1024f) + " KB";
        return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams topMargin(int value) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(value);
        return params;
    }

    private LinearLayout.LayoutParams heightTop(int height, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(height));
        params.topMargin = dp(top);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface ToggleListener {
        void onChanged(boolean checked);
    }
}
