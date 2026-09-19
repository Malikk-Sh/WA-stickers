package com.malikksh.wastickers;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Presentation-only Build tab. Conversion and build ownership remain in MainActivity. */
final class BuildPanel extends LinearLayout {
    enum Phase {
        IDLE,
        PROCESSING,
        STOPPING,
        PARTIAL,
        READY,
        FINALIZED
    }

    enum ItemPhase {
        PENDING,
        PROCESSING,
        READY,
        ERROR,
        SKIPPED
    }

    static final class Item {
        final Uri uri;
        final String name;
        final ItemPhase phase;
        final int percent;
        final String detail;

        Item(Uri uri, String name, ItemPhase phase, int percent, String detail) {
            this.uri = uri;
            this.name = name == null || name.trim().isEmpty() ? "Файл" : name;
            this.phase = phase == null ? ItemPhase.PENDING : phase;
            this.percent = Math.max(0, Math.min(100, percent));
            this.detail = detail == null ? "" : detail;
        }
    }

    static final class Snapshot {
        final Phase phase;
        final boolean animated;
        final String packName;
        final Uri coverUri;
        final int total;
        final int successCount;
        final int failureCount;
        final int overallPercent;
        final String stage;
        final boolean canStart;
        final boolean canFinalize;
        final boolean hasRetryableFailures;
        final List<Item> items;

        Snapshot(Phase phase,
                 boolean animated,
                 String packName,
                 Uri coverUri,
                 int total,
                 int successCount,
                 int failureCount,
                 int overallPercent,
                 String stage,
                 boolean canStart,
                 boolean canFinalize,
                 boolean hasRetryableFailures,
                 List<Item> items) {
            this.phase = phase == null ? Phase.IDLE : phase;
            this.animated = animated;
            this.packName = packName == null || packName.trim().isEmpty()
                    ? (animated ? "Мои анимированные стикеры" : "Мои стикеры")
                    : packName;
            this.coverUri = coverUri;
            this.total = Math.max(0, total);
            this.successCount = Math.max(0, successCount);
            this.failureCount = Math.max(0, failureCount);
            this.overallPercent = Math.max(0, Math.min(100, overallPercent));
            this.stage = stage == null ? "" : stage;
            this.canStart = canStart;
            this.canFinalize = canFinalize;
            this.hasRetryableFailures = hasRetryableFailures;
            this.items = Collections.unmodifiableList(new ArrayList<>(
                    items == null ? Collections.emptyList() : items));
        }
    }

    interface Host {
        void onStart();
        void onCancel();
        void onRetryAll();
        void onRetryItem(Uri uri);
        void onSkipItem(Uri uri);
        void onFinalize();
        void onDiscard();
        void onAddToWhatsApp();
        void onOpenPacks();
    }

    private final Host host;
    private final PreviewLoader previewLoader;

    private final ImageView cover;
    private final TextView packName;
    private final TextView packCount;
    private final TextView typeChip;
    private final TextView progressTrailing;
    private final ProgressBar overallProgress;
    private final TextView overallPercent;
    private final TextView stage;
    private final TextView fileCount;
    private final LinearLayout fileList;
    private final TextView stateMessage;
    private final Button primary;
    private final Button secondary;

    private String lastSignature = "";

    BuildPanel(Context context, PreviewLoader previewLoader, Host host) {
        super(context);
        this.previewLoader = previewLoader;
        this.host = host;

        setId(R.id.build_panel);
        setOrientation(VERTICAL);
        setPadding(dp(20), dp(18), dp(20), dp(22));
        setBackgroundColor(color(R.color.app_background));

        addView(buildHeader(), matchWrap());

        LinearLayout summary = card();
        summary.setId(R.id.build_summary);
        summary.setOrientation(HORIZONTAL);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams summaryParams = matchWrap();
        summaryParams.topMargin = dp(16);
        addView(summary, summaryParams);

        cover = new ImageView(context);
        cover.setId(R.id.build_cover);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(rounded(color(R.color.app_disabled_surface), 16));
        cover.setContentDescription("Обложка набора");
        if (Build.VERSION.SDK_INT >= 21) cover.setClipToOutline(true);
        summary.addView(cover, new LinearLayout.LayoutParams(dp(76), dp(76)));

        LinearLayout summaryText = new LinearLayout(context);
        summaryText.setOrientation(VERTICAL);
        LinearLayout.LayoutParams summaryTextParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        summaryTextParams.leftMargin = dp(14);
        summary.addView(summaryText, summaryTextParams);

        packName = text("Мои стикеры", 17, color(R.color.app_text_primary), Typeface.BOLD);
        packName.setId(R.id.build_pack_name);
        packName.setMaxLines(2);
        summaryText.addView(packName, matchWrap());

        LinearLayout summaryMeta = new LinearLayout(context);
        summaryMeta.setOrientation(HORIZONTAL);
        summaryMeta.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams summaryMetaParams = matchWrap();
        summaryMetaParams.topMargin = dp(7);
        summaryText.addView(summaryMeta, summaryMetaParams);

        packCount = text("0 / 0", 13, color(R.color.app_text_secondary), Typeface.BOLD);
        summaryMeta.addView(packCount, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        typeChip = text("Фото", 12, color(R.color.app_primary), Typeface.BOLD);
        typeChip.setId(R.id.build_type);
        typeChip.setGravity(Gravity.CENTER);
        typeChip.setPadding(dp(10), 0, dp(10), 0);
        typeChip.setBackground(rounded(color(R.color.app_primary_container), 14));
        summaryMeta.addView(typeChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)));

        LinearLayout progressCard = card();
        progressCard.setId(R.id.build_overall);
        LinearLayout.LayoutParams progressCardParams = matchWrap();
        progressCardParams.topMargin = dp(14);
        addView(progressCard, progressCardParams);

        LinearLayout progressHeader = new LinearLayout(context);
        progressHeader.setOrientation(HORIZONTAL);
        progressHeader.setGravity(Gravity.CENTER_VERTICAL);
        progressCard.addView(progressHeader, matchWrap());

        TextView progressTitle = text("Сборка набора", 17,
                color(R.color.app_text_primary), Typeface.BOLD);
        progressHeader.addView(progressTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        progressTrailing = text("0 из 0 готовы", 13,
                color(R.color.app_text_secondary), Typeface.BOLD);
        progressTrailing.setId(R.id.build_progress_label);
        progressHeader.addView(progressTrailing, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        overallProgress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        overallProgress.setId(R.id.build_progress);
        overallProgress.setMax(100);
        LinearLayout.LayoutParams overallParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(12));
        overallParams.topMargin = dp(13);
        progressCard.addView(overallProgress, overallParams);

        LinearLayout progressFooter = new LinearLayout(context);
        progressFooter.setOrientation(HORIZONTAL);
        progressFooter.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footerParams = matchWrap();
        footerParams.topMargin = dp(8);
        progressCard.addView(progressFooter, footerParams);

        stage = text("Готово к запуску", 13,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        stage.setId(R.id.build_stage);
        progressFooter.addView(stage, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        overallPercent = text("0%", 14, color(R.color.app_primary), Typeface.BOLD);
        progressFooter.addView(overallPercent, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout filesCard = card();
        LinearLayout.LayoutParams filesParams = matchWrap();
        filesParams.topMargin = dp(14);
        addView(filesCard, filesParams);

        LinearLayout filesHeader = new LinearLayout(context);
        filesHeader.setOrientation(HORIZONTAL);
        filesHeader.setGravity(Gravity.CENTER_VERTICAL);
        filesCard.addView(filesHeader, matchWrap());

        TextView filesTitle = text("Файлы стикеров", 17,
                color(R.color.app_text_primary), Typeface.BOLD);
        filesHeader.addView(filesTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        fileCount = text("0 файлов", 13,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        filesHeader.addView(fileCount, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        fileList = new LinearLayout(context);
        fileList.setId(R.id.build_file_list);
        fileList.setOrientation(VERTICAL);
        LinearLayout.LayoutParams listParams = matchWrap();
        listParams.topMargin = dp(8);
        filesCard.addView(fileList, listParams);

        stateMessage = text("", 13, color(R.color.app_text_secondary), Typeface.NORMAL);
        stateMessage.setId(R.id.build_status);
        stateMessage.setVisibility(GONE);
        stateMessage.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams stateParams = matchWrap();
        stateParams.topMargin = dp(14);
        addView(stateMessage, stateParams);

        primary = new Button(context);
        primary.setId(R.id.build_primary);
        primary.setAllCaps(false);
        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        primaryParams.topMargin = dp(14);
        addView(primary, primaryParams);

        secondary = new Button(context);
        secondary.setId(R.id.build_secondary);
        secondary.setAllCaps(false);
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        secondaryParams.topMargin = dp(8);
        addView(secondary, secondaryParams);
    }

    void render(Snapshot snapshot) {
        if (snapshot == null) return;
        String signature = signature(snapshot);
        if (signature.equals(lastSignature)) return;
        lastSignature = signature;

        packName.setText(snapshot.packName);
        packCount.setText(snapshot.successCount + " / " + snapshot.total);
        typeChip.setText(snapshot.animated ? "Анимация" : "Фото");
        progressTrailing.setText(snapshot.successCount + " из " + snapshot.total + " готовы");
        overallProgress.setProgress(snapshot.overallPercent);
        overallPercent.setText(snapshot.overallPercent + "%");
        stage.setText(snapshot.stage.isEmpty() ? defaultStage(snapshot.phase) : snapshot.stage);
        fileCount.setText(fileCountLabel(snapshot.total));
        loadPreview(snapshot.coverUri, cover);

        renderItems(snapshot);
        renderActions(snapshot);
        renderStateMessage(snapshot);
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(getContext());
        logo.setImageResource(R.drawable.ic_app_logo_mark);
        logo.setPadding(dp(7), dp(7), dp(7), dp(7));
        logo.setBackground(rounded(color(R.color.app_primary), 18));
        row.addView(logo, new LinearLayout.LayoutParams(dp(62), dp(62)));

        LinearLayout labels = new LinearLayout(getContext());
        labels.setOrientation(VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.leftMargin = dp(14);
        row.addView(labels, labelsParams);

        TextView title = text("Сборка", 28, color(R.color.app_text_primary), Typeface.BOLD);
        labels.addView(title, matchWrap());
        TextView subtitle = text("Подготовка набора", 14,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(2);
        labels.addView(subtitle, subtitleParams);
        return row;
    }

    private void renderItems(Snapshot snapshot) {
        fileList.removeAllViews();
        if (snapshot.items.isEmpty()) {
            TextView empty = text("Добавьте от 3 до 30 файлов во вкладке «Медиа»", 13,
                    color(R.color.app_text_secondary), Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8), dp(20), dp(8), dp(20));
            fileList.addView(empty, matchWrap());
            return;
        }

        for (int i = 0; i < snapshot.items.size(); i++) {
            Item item = snapshot.items.get(i);
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(VERTICAL);
            row.setPadding(dp(12), dp(11), dp(12), dp(11));
            row.setBackground(rounded(rowBackground(item.phase), 16));
            LinearLayout.LayoutParams rowParams = matchWrap();
            if (i > 0) rowParams.topMargin = dp(8);
            fileList.addView(row, rowParams);

            LinearLayout headline = new LinearLayout(getContext());
            headline.setOrientation(HORIZONTAL);
            headline.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(headline, matchWrap());

            FrameLayout thumb = new FrameLayout(getContext());
            ImageView preview = new ImageView(getContext());
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setBackground(rounded(color(R.color.app_disabled_surface), 12));
            if (Build.VERSION.SDK_INT >= 21) preview.setClipToOutline(true);
            thumb.addView(preview, new FrameLayout.LayoutParams(dp(52), dp(52)));
            headline.addView(thumb, new LinearLayout.LayoutParams(dp(52), dp(52)));
            loadPreview(item.uri, preview);

            LinearLayout texts = new LinearLayout(getContext());
            texts.setOrientation(VERTICAL);
            LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textsParams.leftMargin = dp(10);
            headline.addView(texts, textsParams);

            TextView name = text(item.name, 14, color(R.color.app_text_primary), Typeface.BOLD);
            name.setMaxLines(2);
            texts.addView(name, matchWrap());

            if (!item.detail.isEmpty()) {
                TextView detail = text(item.detail, 12, color(R.color.app_text_secondary), Typeface.NORMAL);
                detail.setMaxLines(3);
                LinearLayout.LayoutParams detailParams = matchWrap();
                detailParams.topMargin = dp(3);
                texts.addView(detail, detailParams);
            }

            TextView chip = text(itemChip(item.phase), 12, chipTextColor(item.phase), Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(9), 0, dp(9), 0);
            chip.setBackground(rounded(chipBackground(item.phase), 13));
            headline.addView(chip, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)));

            if (item.phase == ItemPhase.PROCESSING || item.percent > 0) {
                ProgressBar progress = new ProgressBar(
                        getContext(), null, android.R.attr.progressBarStyleHorizontal);
                progress.setMax(100);
                progress.setProgress(item.percent);
                LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(9));
                progressParams.topMargin = dp(9);
                row.addView(progress, progressParams);
            }

            if (item.phase == ItemPhase.ERROR) {
                LinearLayout itemActions = new LinearLayout(getContext());
                itemActions.setOrientation(HORIZONTAL);
                LinearLayout.LayoutParams actionsParams = matchWrap();
                actionsParams.topMargin = dp(8);
                row.addView(itemActions, actionsParams);

                Button retry = new Button(getContext());
                retry.setText("Повторить");
                retry.setAllCaps(false);
                styleTertiaryButton(retry);
                retry.setOnClickListener(v -> host.onRetryItem(item.uri));
                itemActions.addView(retry, new LinearLayout.LayoutParams(0, dp(48), 1f));

                Button skip = new Button(getContext());
                skip.setText("Пропустить");
                skip.setAllCaps(false);
                styleTertiaryButton(skip);
                skip.setOnClickListener(v -> host.onSkipItem(item.uri));
                LinearLayout.LayoutParams skipParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
                skipParams.leftMargin = dp(7);
                itemActions.addView(skip, skipParams);
            }
        }
    }

    private void renderActions(Snapshot snapshot) {
        primary.setVisibility(VISIBLE);
        secondary.setVisibility(GONE);
        primary.setOnClickListener(null);
        secondary.setOnClickListener(null);

        switch (snapshot.phase) {
            case PROCESSING:
                primary.setText("Остановить обработку");
                styleDangerButton(primary, true);
                primary.setOnClickListener(v -> host.onCancel());
                break;
            case STOPPING:
                primary.setText("Останавливаю…");
                styleDangerButton(primary, false);
                break;
            case PARTIAL:
                if (snapshot.canFinalize) {
                    primary.setText("Создать из готовых");
                    stylePrimaryButton(primary, true);
                    primary.setOnClickListener(v -> host.onFinalize());
                    if (snapshot.hasRetryableFailures) {
                        secondary.setVisibility(VISIBLE);
                        secondary.setText("Повторить ошибки");
                        styleSecondaryButton(secondary, true);
                        secondary.setOnClickListener(v -> host.onRetryAll());
                    }
                } else {
                    primary.setText("Повторить ошибки");
                    stylePrimaryButton(primary, snapshot.hasRetryableFailures);
                    if (snapshot.hasRetryableFailures) {
                        primary.setOnClickListener(v -> host.onRetryAll());
                    }
                }
                break;
            case READY:
                primary.setText("Создать набор");
                stylePrimaryButton(primary, snapshot.canFinalize);
                if (snapshot.canFinalize) primary.setOnClickListener(v -> host.onFinalize());
                secondary.setVisibility(VISIBLE);
                secondary.setText("Отменить");
                styleSecondaryButton(secondary, true);
                secondary.setOnClickListener(v -> host.onDiscard());
                break;
            case FINALIZED:
                primary.setText("Добавить в WhatsApp");
                stylePrimaryButton(primary, true);
                primary.setOnClickListener(v -> host.onAddToWhatsApp());
                secondary.setVisibility(VISIBLE);
                secondary.setText("Открыть в «Наборах»");
                styleSecondaryButton(secondary, true);
                secondary.setOnClickListener(v -> host.onOpenPacks());
                break;
            case IDLE:
            default:
                primary.setText("Создать набор");
                stylePrimaryButton(primary, snapshot.canStart);
                if (snapshot.canStart) primary.setOnClickListener(v -> host.onStart());
                break;
        }
    }

    private void renderStateMessage(Snapshot snapshot) {
        String message = "";
        switch (snapshot.phase) {
            case PARTIAL:
                if (snapshot.canFinalize) {
                    message = "Готово " + snapshot.successCount + ", ошибок или пропущено "
                            + snapshot.failureCount + ". Можно собрать набор из готовых.";
                } else {
                    message = "Готово " + snapshot.successCount + ". Для набора нужно минимум 3 готовых стикера.";
                }
                break;
            case READY:
                message = "Все файлы обработаны. Проверьте результат и создайте набор.";
                break;
            case FINALIZED:
                message = "✓ Набор готов";
                break;
            case STOPPING:
                message = "Останавливаю текущую операцию. Уже готовые стикеры сохранятся.";
                break;
            default:
                break;
        }
        stateMessage.setText(message);
        stateMessage.setVisibility(message.isEmpty() ? GONE : VISIBLE);
        if (!message.isEmpty()) {
            stateMessage.setBackground(rounded(
                    snapshot.phase == Phase.PARTIAL
                            ? color(R.color.app_warning_container)
                            : color(R.color.app_primary_container),
                    16));
            stateMessage.setTextColor(snapshot.phase == Phase.PARTIAL
                    ? color(R.color.app_warning)
                    : color(R.color.app_primary));
        }
    }

    private String defaultStage(Phase phase) {
        switch (phase) {
            case PROCESSING:
                return "Оптимизация и подготовка файлов…";
            case STOPPING:
                return "Останавливаю…";
            case PARTIAL:
                return "Есть файлы, требующие внимания";
            case READY:
                return "Все файлы обработаны";
            case FINALIZED:
                return "Набор сохранён";
            case IDLE:
            default:
                return "Готово к запуску";
        }
    }

    private String signature(Snapshot snapshot) {
        StringBuilder value = new StringBuilder();
        value.append(snapshot.phase).append('|')
                .append(snapshot.animated).append('|')
                .append(snapshot.packName).append('|')
                .append(snapshot.coverUri).append('|')
                .append(snapshot.total).append('|')
                .append(snapshot.successCount).append('|')
                .append(snapshot.failureCount).append('|')
                .append(snapshot.overallPercent).append('|')
                .append(snapshot.stage).append('|')
                .append(snapshot.canStart).append('|')
                .append(snapshot.canFinalize).append('|')
                .append(snapshot.hasRetryableFailures);
        for (Item item : snapshot.items) {
            value.append('|').append(item.uri).append(':').append(item.name)
                    .append(':').append(item.phase).append(':').append(item.percent)
                    .append(':').append(item.detail);
        }
        return value.toString();
    }

    private String fileCountLabel(int count) {
        int mod10 = count % 10;
        int mod100 = count % 100;
        if (mod10 == 1 && mod100 != 11) return count + " файл";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return count + " файла";
        return count + " файлов";
    }

    private String itemChip(ItemPhase phase) {
        switch (phase) {
            case READY: return "✓ Готово";
            case PROCESSING: return "Обработка";
            case ERROR: return "! Ошибка";
            case SKIPPED: return "Пропущено";
            case PENDING:
            default: return "В очереди";
        }
    }

    private int rowBackground(ItemPhase phase) {
        switch (phase) {
            case ERROR: return color(R.color.app_error_container);
            case READY: return color(R.color.app_success_container);
            case PROCESSING: return color(R.color.app_primary_container);
            default: return color(R.color.app_surface_variant);
        }
    }

    private int chipBackground(ItemPhase phase) {
        switch (phase) {
            case ERROR: return color(R.color.app_error_container);
            case READY: return color(R.color.app_success_container);
            case PROCESSING: return color(R.color.app_primary_container);
            case SKIPPED: return color(R.color.app_disabled_surface);
            default: return color(R.color.app_surface_strong);
        }
    }

    private int chipTextColor(ItemPhase phase) {
        switch (phase) {
            case ERROR: return color(R.color.app_error);
            case READY: return color(R.color.app_success);
            case PROCESSING: return color(R.color.app_primary);
            case SKIPPED: return color(R.color.app_text_secondary);
            default: return color(R.color.app_text_primary);
        }
    }

    private void loadPreview(Uri uri, ImageView target) {
        target.setImageDrawable(null);
        if (previewLoader == null || uri == null) return;
        String key = previewLoader.requestKey(uri);
        target.setTag(key);
        previewLoader.load(uri, (loadedKey, bitmap) -> post(() -> {
            if (!loadedKey.equals(target.getTag())) return;
            if (bitmap != null) target.setImageBitmap(bitmap);
        }));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(color(R.color.app_surface), 22));
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));
        return card;
    }

    private TextView text(String value, int size, int textColor, int style) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(textColor);
        view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    private void stylePrimaryButton(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setTextSize(16);
        button.setTextColor(enabled
                ? color(R.color.app_on_primary)
                : color(R.color.app_disabled_text));
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setBackground(rounded(
                enabled ? color(R.color.app_primary) : color(R.color.app_disabled_surface), 18));
        button.setAlpha(enabled ? 1f : 0.7f);
    }

    private void styleSecondaryButton(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setTextSize(15);
        button.setTextColor(enabled ? color(R.color.app_primary) : color(R.color.app_disabled_text));
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        GradientDrawable background = rounded(color(R.color.app_surface), 18);
        background.setStroke(dp(1), enabled ? color(R.color.app_border) : color(R.color.app_disabled_surface));
        button.setBackground(background);
        button.setAlpha(enabled ? 1f : 0.7f);
    }

    private void styleTertiaryButton(Button button) {
        button.setTextSize(13);
        button.setTextColor(color(R.color.app_primary));
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        GradientDrawable background = rounded(color(R.color.app_surface), 14);
        background.setStroke(dp(1), color(R.color.app_border));
        button.setBackground(background);
    }

    private void styleDangerButton(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setTextSize(16);
        button.setTextColor(enabled
                ? color(R.color.app_on_primary)
                : color(R.color.app_disabled_text));
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setBackground(rounded(
                enabled ? color(R.color.app_error) : color(R.color.app_disabled_surface), 18));
        button.setAlpha(enabled ? 1f : 0.7f);
    }

    private GradientDrawable rounded(int fillColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @SuppressWarnings("deprecation")
    private int color(int resourceId) {
        return getResources().getColor(resourceId);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
