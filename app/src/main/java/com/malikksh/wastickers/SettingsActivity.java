package com.malikksh.wastickers;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
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
    private TextView cacheValue;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppSettings.wrapForAppearance(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppSettings.configureRuntime(this);
        setContentView(buildUi());
        AppSettings.applySystemBars(this);
        renderSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderSettings();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
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

        ImageButton back = new ImageButton(this);
        back.setId(R.id.settings_back);
        back.setImageResource(R.drawable.ic_back);
        back.setContentDescription("Назад");
        back.setPadding(dp(12), dp(12), dp(12), dp(12));
        back.setBackground(UiComponents.rounded(
                this, R.color.app_primary_container, R.dimen.radius_card));
        back.setOnClickListener(v -> finish());
        row.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.leftMargin = dp(14);
        row.addView(labels, labelsParams);

        TextView title = UiComponents.screenTitle(this, "Настройки");
        title.setId(R.id.settings_title);
        labels.addView(title, matchWrap());
        TextView subtitle = UiComponents.metadata(this, "Персонализация и обработка");
        LinearLayout.LayoutParams subParams = matchWrap();
        subParams.topMargin = dp(2);
        labels.addView(subtitle, subParams);
        return row;
    }

    private View sectionAppearance() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, dp(12), 0, dp(12));
        card.addView(UiComponents.sectionTitle(this, "Внешний вид"), matchWrap());
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
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, dp(12), 0, dp(12));
        card.addView(UiComponents.sectionTitle(this, "Обработка"), matchWrap());
        card.addView(label("Качество анимации"), topMargin(14));
        card.addView(UiComponents.metadata(this, "Баланс между размером и плавностью"), topMargin(3));

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



        draftsSwitch = switchRow(
                "Сохранять черновики",
                "Восстанавливать незавершённые наборы",
                R.id.settings_keep_drafts,
                checked -> AppSettings.setKeepDrafts(this, checked));
        card.addView(draftsSwitch, topMargin(12));
        return card;
    }

    private View sectionStorage() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, dp(12), 0, dp(12));
        card.addView(UiComponents.sectionTitle(this, "Хранилище"), matchWrap());

        cacheValue = valueRow("Кэш приложения", "0 B");
        card.addView(cacheValue, topMargin(12));

        Button clear = new Button(this);
        clear.setId(R.id.settings_clear_cache);
        clear.setText("Очистить кэш");
        clear.setAllCaps(false);
        UiComponents.styleOutlineButton(clear, true);
        clear.setOnClickListener(v -> clearCache());
        card.addView(clear, heightTop(50, 8));
        Button unavailable = secondaryButton("Очистить недоступные наборы");
        unavailable.setOnClickListener(v -> {
            int removed = PackStore.removeUnavailablePacks(this);
            TransientFeedback.show(this, "Удалено недоступных наборов: " + removed);
        });
        card.addView(unavailable, heightTop(48, 4));

        cleanupSwitch = switchRow(
                "Автоочистка временных файлов",
                "Удалять рабочие файлы после использования",
                R.id.settings_auto_cleanup,
                checked -> AppSettings.setAutoCleanup(this, checked));
        card.addView(cleanupSwitch, topMargin(14));
        return card;
    }

    private View sectionAbout() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, dp(12), 0, dp(12));
        card.addView(UiComponents.sectionTitle(this, "О приложении"), matchWrap());

        TextView version = valueRow("Версия", versionName());
        version.setId(R.id.settings_version);
        card.addView(version, topMargin(12));

        Button privacy = secondaryButton("Конфиденциальность  ›");
        privacy.setId(R.id.settings_privacy);
        privacy.setOnClickListener(v -> startActivity(InfoActivity.intent(
                this,
                "Конфиденциальность",
                "WA Stickers обрабатывает выбранные фото, GIF, WebP и видео локально на устройстве. "
                        + "Для основной работы приложению не нужен доступ в интернет; готовые наборы передаются WhatsApp через локальный content provider."
        )));
        card.addView(privacy, heightTop(50, 8));
        card.addView(UiComponents.metadata(this, "Все файлы обрабатываются локально"), topMargin(4));

        Button help = secondaryButton("Помощь");
        help.setId(R.id.settings_help);
        help.setOnClickListener(v -> startActivity(InfoActivity.intent(
                this,
                "Помощь",
                "Создайте набор, добавьте 3–30 файлов, настройте порядок и обложку в редакторе, "
                        + "при необходимости выберите фрагмент видео до 10 секунд, затем соберите набор. "
                        + "Ошибки отдельных файлов можно повторять или пропускать."
        )));
        card.addView(help, heightTop(50, 8));

        Button about = secondaryButton("О приложении");
        about.setId(R.id.settings_about);
        about.setOnClickListener(v -> startActivity(InfoActivity.intent(
                this,
                "WA Stickers",
                "Версия " + versionName() + "\n\nЛокальный offline-first инструмент для подготовки фото и анимированных наборов WhatsApp."
        )));
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
        if (themeSystem == null) return;
        String appearance = AppSettings.appearance(this);
        UiComponents.styleSegment(themeSystem, AppSettings.APPEARANCE_SYSTEM.equals(appearance));
        UiComponents.styleSegment(themeLight, AppSettings.APPEARANCE_LIGHT.equals(appearance));
        UiComponents.styleSegment(themeDark, AppSettings.APPEARANCE_DARK.equals(appearance));

        String quality = AppSettings.qualityPreset(this);
        UiComponents.styleSegment(qualitySmoother, AppSettings.QUALITY_SMOOTHER.equals(quality));
        UiComponents.styleSegment(qualityBalance, AppSettings.QUALITY_BALANCE.equals(quality));
        UiComponents.styleSegment(qualitySharper, AppSettings.QUALITY_SHARPER.equals(quality));

        compactSwitch.setChecked(AppSettings.compactMode(this));
        draftsSwitch.setChecked(AppSettings.keepDrafts(this));
        cleanupSwitch.setChecked(AppSettings.autoCleanup(this));
        if (cacheValue != null) {
            cacheValue.setText("Кэш приложения\n" + formatBytes(StorageStats.conversionCacheBytes(this)));
        }
    }

    private void clearCache() {
        long freed = AppSettings.clearConversionCache(this);
        AppSettings.cleanupTemporaryFiles(this);
        renderSettings();
        TransientFeedback.show(this, "Освобождено " + formatBytes(freed));
    }

    private LinearLayout segmentedRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        row.setBackground(UiComponents.rounded(
                this, R.color.app_surface_variant, R.dimen.radius_pill));
        return row;
    }

    private Button segment(String value, int id, Runnable action) {
        Button button = new Button(this);
        button.setId(id);
        button.setText(value);
        button.setAllCaps(false);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void addSegment(LinearLayout row, Button button, boolean withMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        if (withMargin) params.leftMargin = dp(4);
        row.addView(button, params);
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(14), 0, dp(14), 0);
        UiComponents.styleOutlineButton(button, true);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        return button;
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

    private TextView text(String value, int size, int textColor, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(textColor);
        view.setTypeface(Typeface.create("sans", style));
        return view;
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
        return UiComponents.matchWrap();
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

    private int color(int resource) {
        return UiComponents.color(this, resource);
    }

    private int dp(int value) {
        return UiComponents.dp(this, value);
    }

    private interface ToggleListener {
        void onChanged(boolean checked);
    }
}
