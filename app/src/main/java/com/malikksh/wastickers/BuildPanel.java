package com.malikksh.wastickers;

import android.app.AlertDialog;
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

    private final TextView[] stats = new TextView[4];
    private final Button[] tabs = new Button[3];
    private int selectedTab;
    private Snapshot currentSnapshot;
    private final BuildMotionPolicy<Uri> motionPolicy = new BuildMotionPolicy<>();
    private final List<String> journal = new ArrayList<>();
    private final java.util.Map<Uri, ItemPhase> previousItems = new java.util.HashMap<>();
    private Phase previousPhase;
    private boolean holding;
    private long finishAt;
    private boolean completionAnnounced;
    private int displayedPercent;
    private final Runnable reveal = () -> render(currentSnapshot);


    void itemStarted(Uri uri) {
        motionPolicy.started(uri, android.os.SystemClock.uptimeMillis(), Motion.enabled(getContext()));
    }
    void resetPresentation() {
        removeCallbacks(reveal); displayedPercent = 0; selectedTab = 0;
        motionPolicy.reset(); journal.clear(); previousItems.clear();
        previousPhase = null; finishAt = 0; holding = false; completionAnnounced = false;
        lastSignature = "";
    }
    void cancelPresentation() {
        motionPolicy.reset(); finishAt = 0; holding = false; completionAnnounced = true;
    }
    void beginBatch(boolean retry) {
        removeCallbacks(reveal); displayedPercent = 0;
        motionPolicy.reset(); finishAt = 0; completionAnnounced = false;
        if (retry) journal.add("Повторная обработка выбранных ошибок");
        lastSignature = "";
    }

    private String lastSignature = "";

    BuildPanel(Context context, PreviewLoader previewLoader, Host host) {
        super(context);
        this.previewLoader = previewLoader;
        this.host = host;

        setId(R.id.build_panel);
        setOrientation(VERTICAL);
        setPadding(dp(20), dp(18), dp(20), dp(22));
        setBackgroundColor(color(R.color.app_background));

        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        scroll.addView(body, new android.widget.ScrollView.LayoutParams(-1, -2));
        addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(buildHeader(), matchWrap());

        LinearLayout summary = UiComponents.card(context);
        summary.setId(R.id.build_summary);
        summary.setPadding(dp(12), dp(10), dp(12), dp(10));
        summary.setOrientation(HORIZONTAL);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams summaryParams = matchWrap();
        summaryParams.topMargin = dp(10);
        body.addView(summary, summaryParams);

        cover = new ImageView(context);
        cover.setId(R.id.build_cover);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(UiComponents.rounded(
                context, R.color.app_disabled_surface, R.dimen.radius_card));
        cover.setContentDescription("Обложка набора");
        if (Build.VERSION.SDK_INT >= 21) cover.setClipToOutline(true);
        summary.addView(cover, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout summaryText = new LinearLayout(context);
        summaryText.setOrientation(VERTICAL);
        LinearLayout.LayoutParams summaryTextParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        summaryTextParams.leftMargin = dp(14);
        summary.addView(summaryText, summaryTextParams);

        packName = UiComponents.cardTitle(context, "Мои стикеры");
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
        typeChip.setBackground(UiComponents.rounded(
                context, R.color.app_primary_container, R.dimen.radius_pill));
        summaryMeta.addView(typeChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)));

        LinearLayout progressCard = UiComponents.card(context);
        progressCard.setId(R.id.build_overall);
        progressCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams progressCardParams = matchWrap();
        progressCardParams.topMargin = dp(14);
        body.addView(progressCard, progressCardParams);

        LinearLayout progressHeader = new LinearLayout(context);
        progressHeader.setOrientation(HORIZONTAL);
        progressHeader.setGravity(Gravity.CENTER_VERTICAL);
        progressCard.addView(progressHeader, matchWrap());

        TextView progressTitle = UiComponents.cardTitle(context, "Сборка набора");
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

        stage = UiComponents.metadata(context, "Готово к запуску");
        stage.setId(R.id.build_stage);
        progressFooter.addView(stage, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        overallPercent = text("0%", 14, color(R.color.app_primary), Typeface.BOLD);
        progressFooter.addView(overallPercent, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout statistics = new LinearLayout(context);
        statistics.setTag("build_stats");
        String[] statNames = {"Всего", "Готово", "В процессе", "Ошибок"};
        for (int i = 0; i < stats.length; i++) {
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(VERTICAL);
            cell.setPadding(0, dp(10), 0, dp(6));
            stats[i] = text("0", 20, color(R.color.app_text_primary), Typeface.BOLD);
            stats[i].setGravity(Gravity.CENTER);
            cell.addView(stats[i], matchWrap());
            TextView label = text(statNames[i], 10, color(R.color.app_text_secondary), Typeface.NORMAL);
            label.setGravity(Gravity.CENTER);
            cell.addView(label, matchWrap());
            statistics.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
        body.addView(statistics, matchWrap());
        LinearLayout tabRow = new SegmentRow(context);
        String[] tabNames = {"Обзор", "Файлы", "Журнал"};
        for (int i = 0; i < tabs.length; i++) {
            final int tab = i;
            tabs[i] = new Button(context);
            tabs[i].setText(tabNames[i]);
            UiComponents.styleSegment(tabs[i], i == 0);
            tabs[i].setOnClickListener(v -> {
                if (selectedTab == tab) return;
                selectedTab = tab;
                lastSignature = "";
                changeTab();
            });
            tabRow.addView(tabs[i], new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        body.addView(tabRow, matchWrap());

        LinearLayout filesCard = UiComponents.card(context);
        LinearLayout.LayoutParams filesParams = matchWrap();
        filesParams.topMargin = dp(6);
        filesCard.setPadding(dp(4), dp(8), dp(4), dp(8));
        body.addView(filesCard, filesParams);

        LinearLayout filesHeader = new LinearLayout(context);
        filesHeader.setOrientation(HORIZONTAL);
        filesHeader.setGravity(Gravity.CENTER_VERTICAL);
        filesCard.addView(filesHeader, matchWrap());

        TextView filesTitle = UiComponents.cardTitle(context, "Файлы стикеров");
        filesHeader.addView(filesTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        fileCount = UiComponents.metadata(context, "0 файлов");
        filesHeader.addView(fileCount, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        fileList = new LinearLayout(context);
        fileList.setId(R.id.build_file_list);
        fileList.setOrientation(VERTICAL);
        LinearLayout.LayoutParams listParams = matchWrap();
        listParams.topMargin = dp(8);
        filesCard.addView(fileList, listParams);

        stateMessage = UiComponents.metadata(context, "");
        stateMessage.setId(R.id.build_status);
        stateMessage.setVisibility(GONE);
        stateMessage.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams stateParams = matchWrap();
        stateParams.topMargin = dp(14);
        body.addView(stateMessage, stateParams);

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
        currentSnapshot = snapshot;
        recordJournal(snapshot);
        Snapshot visual = visualSnapshot(snapshot);
        String signature = signature(visual) + selectedTab + holding + journal.size();
        if (getContext() instanceof MainActivity) {
            PackStore.Pack pack = ((MainActivity) getContext()).runtimeCurrentPack();
            if (pack != null) signature += WhatsAppSync.state(getContext(), pack).name();
        }
        if (signature.equals(lastSignature)) return;
        lastSignature = signature;

        packName.setText(snapshot.packName);
        packCount.setText(snapshot.successCount + " / " + snapshot.total);
        typeChip.setVisibility(GONE);
        progressTrailing.setText(snapshot.successCount + " из " + snapshot.total + " готовы");
        boolean rowHold = holding && finishAt == 0;
        int targetPercent = rowHold ? Math.min(99, snapshot.overallPercent) : snapshot.overallPercent;
        if (snapshot.phase == Phase.PROCESSING) targetPercent = Math.min(99, targetPercent);
        displayedPercent = snapshot.phase == Phase.IDLE ? targetPercent : Math.max(displayedPercent, targetPercent);
        overallProgress.setIndeterminate(rowHold || (snapshot.phase == Phase.PROCESSING && displayedPercent == 0));
        overallProgress.setProgress(displayedPercent, Motion.enabled(getContext()));
        overallPercent.setText(rowHold ? "…" : displayedPercent + "%");
        int running = 0;
        for (Item item : snapshot.items) if (snapshot.phase == Phase.PROCESSING
                && (item.phase == ItemPhase.PROCESSING || item.phase == ItemPhase.PENDING)) running++;
        int[] counts = {snapshot.total, snapshot.successCount, running, snapshot.failureCount};
        for (int i = 0; i < stats.length; i++) stats[i].setText(String.valueOf(counts[i]));
        for (int i = 0; i < tabs.length; i++) UiComponents.styleSegment(tabs[i], selectedTab == i);
        stage.setText(defaultStage(snapshot.phase));
        fileCount.setText(fileCountLabel(snapshot.total));
        loadPreview(snapshot.coverUri, cover);

        renderItems(visual);
        renderActions(snapshot);
        renderStateMessage(snapshot);
        if (holding && snapshot.phase == Phase.READY) {
            primary.setText("Завершение…");
            UiComponents.stylePrimaryButton(primary, false);
            primary.setOnClickListener(null);
        }
        if (!holding && snapshot.phase == Phase.READY && !completionAnnounced) {
            completionAnnounced = true;
            if (Motion.enabled(getContext())) { Motion.enter(primary); Motion.tick(primary); }
        }
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(getContext());
        logo.setImageResource(R.drawable.ic_app_logo_mark);
        logo.setPadding(dp(7), dp(7), dp(7), dp(7));
        logo.setBackground(UiComponents.rounded(
                getContext(), R.color.app_primary, R.dimen.radius_card));
        row.addView(logo, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout labels = new LinearLayout(getContext());
        labels.setOrientation(VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.leftMargin = dp(14);
        row.addView(labels, labelsParams);

        labels.addView(UiComponents.cardTitle(getContext(), "Сборка"), matchWrap());
        TextView subtitle = UiComponents.metadata(getContext(), "Подготовка набора");
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(2);
        labels.addView(subtitle, subtitleParams);
        FrameLayout actionSlot = new FrameLayout(getContext());
        actionSlot.setTag("shell_actions");
        row.addView(actionSlot, new LinearLayout.LayoutParams(dp(104), dp(48)));
        return row;
    }

    private void changeTab() {
        itemRows.clear(); itemRowKeys.clear(); fileList.removeAllViews();
        render(currentSnapshot);
        Motion.enter(fileList);
    }

    private final java.util.Map<Uri, View> itemRows = new java.util.HashMap<>();
    private final java.util.Map<Uri, String> itemRowKeys = new java.util.HashMap<>();
    private void renderItems(Snapshot snapshot) {
        if (selectedTab == 2) {
            fileList.removeAllViews(); itemRows.clear(); itemRowKeys.clear();
            if (journal.isEmpty()) addJournalLine("Событий пока нет");
            else for (String entry : journal) addJournalLine(entry);
            return;
        }
        List<Item> displayed = visibleItems(snapshot.items, selectedTab == 0);
        java.util.Set<Uri> visible = new java.util.HashSet<>();
        for (Item item : displayed) visible.add(item.uri);
        for (int n = fileList.getChildCount() - 1; n >= 0; n--) {
            View child = fileList.getChildAt(n);
            if (!visible.contains(child.getTag())) fileList.removeViewAt(n);
        }
        itemRows.keySet().retainAll(visible);
        itemRowKeys.keySet().retainAll(visible);
        if (snapshot.items.isEmpty()) {
            TextView empty = UiComponents.metadata(
                    getContext(), "Добавьте от 3 до 30 файлов в редакторе");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8), dp(20), dp(8), dp(20));
            fileList.addView(empty, matchWrap());
            return;
        }

        for (int i = 0; i < displayed.size(); i++) {
            Item item = displayed.get(i);
            int itemNumber = snapshot.items.indexOf(item) + 1;
            String key = i + ":" + item.phase + ":" + snapshot.phase;
            View previous = itemRows.get(item.uri);
            if (previous != null && key.equals(itemRowKeys.get(item.uri))) {
                ProgressBar progress = previous.findViewWithTag("item_progress");
                if (progress != null) {
                    progress.setIndeterminate(item.percent == 0 || item.percent >= 99);
                    progress.setProgress(Math.max(progress.getProgress(), item.percent), Motion.enabled(getContext()));
                }
                continue;
            }
            if (previous != null) fileList.removeView(previous);
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(VERTICAL);
            row.setPadding(dp(8), dp(6), dp(8), dp(6));
            row.setBackground(rounded(rowBackground(item.phase), 4));
            if (previous != null) Motion.crossfade(row);
            LinearLayout.LayoutParams rowParams = matchWrap();
            if (i > 0) rowParams.topMargin = dp(4);
            row.setTag(item.uri);
            fileList.addView(row, Math.min(i, fileList.getChildCount()), rowParams);
            itemRows.put(item.uri, row);
            itemRowKeys.put(item.uri, key);

            LinearLayout headline = new LinearLayout(getContext());
            headline.setOrientation(HORIZONTAL);
            headline.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(headline, matchWrap());

            FrameLayout thumb = new FrameLayout(getContext());
            ImageView preview = new ImageView(getContext());
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setBackground(rounded(color(R.color.app_disabled_surface), 12));
            if (Build.VERSION.SDK_INT >= 21) preview.setClipToOutline(true);
            thumb.addView(preview, new FrameLayout.LayoutParams(dp(40), dp(40)));
            headline.addView(thumb, new LinearLayout.LayoutParams(dp(40), dp(40)));
            loadPreview(item.uri, preview);

            LinearLayout texts = new LinearLayout(getContext());
            texts.setOrientation(VERTICAL);
            LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textsParams.leftMargin = dp(10);
            headline.addView(texts, textsParams);

            TextView name = text("Стикер " + itemNumber, 14, color(R.color.app_text_primary), Typeface.BOLD);
            name.setMaxLines(2);
            texts.addView(name, matchWrap());

            {
                String friendly = item.phase == ItemPhase.ERROR ? "Не удалось обработать файл"
                        : item.phase == ItemPhase.READY ? "Готово к использованию"
                        : item.phase == ItemPhase.PROCESSING ? "Подготовка стикера…" : "В очереди";
                TextView detail = UiComponents.metadata(getContext(), sourceType(item.name) + " · " + friendly);
                detail.setMaxLines(2);
                LinearLayout.LayoutParams detailParams = matchWrap();
                detailParams.topMargin = dp(3);
                texts.addView(detail, detailParams);
            }

            TextView chip = text(
                    itemChip(snapshot.phase, item.phase),
                    12,
                    chipTextColor(item.phase),
                    Typeface.BOLD
            );
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(9), 0, dp(9), 0);
            chip.setBackground(rounded(chipBackground(snapshot.phase, item.phase), 13));
            if (item.phase == ItemPhase.PROCESSING) {
                ProgressBar ring = new ProgressBar(getContext());
                ring.setContentDescription("Обработка");
                LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(dp(18), dp(18));
                ringParams.rightMargin = dp(4);
                headline.addView(ring, ringParams);
            }
            headline.addView(chip, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)));

            if (item.phase == ItemPhase.PROCESSING) {
                ProgressBar progress = new ProgressBar(
                        getContext(), null, android.R.attr.progressBarStyleHorizontal);
                progress.setTag("item_progress");
                progress.setMax(100);
                progress.setIndeterminate(item.percent == 0 || item.percent >= 99);
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
                UiComponents.styleOutlineButton(retry, true);
                retry.setOnClickListener(v -> host.onRetryItem(item.uri));
                itemActions.addView(retry, new LinearLayout.LayoutParams(0, dp(48), 1f));

                Button skip = new Button(getContext());
                skip.setText("Пропустить");
                skip.setAllCaps(false);
                UiComponents.styleOutlineButton(skip, true);
                skip.setOnClickListener(v -> host.onSkipItem(item.uri));
                LinearLayout.LayoutParams skipParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
                skipParams.leftMargin = dp(7);
                itemActions.addView(skip, skipParams);
            }
        }
    }

    static List<Item> visibleItems(List<Item> items, boolean overview) {
        if (!overview) return items;
        List<Item> result = new ArrayList<>();
        for (Item item : items) if ((item.phase == ItemPhase.ERROR || item.phase == ItemPhase.PROCESSING || item.phase == ItemPhase.SKIPPED) && result.size() < 6) result.add(item);
        for (int i = items.size() - 1; i >= 0 && result.size() < 6; i--)
            if (items.get(i).phase == ItemPhase.READY && !result.contains(items.get(i))) result.add(items.get(i));
        for (Item item : items) if (result.size() < 6 && !result.contains(item)) result.add(item);
        return result;
    }

    private void addJournalLine(String message) {
        TextView line = UiComponents.metadata(getContext(), message);
        line.setPadding(dp(8), dp(10), dp(8), dp(10));
        fileList.addView(line, matchWrap());
    }

    private void recordJournal(Snapshot snapshot) {
        for (int i = 0; i < snapshot.items.size(); i++) {
            Item item = snapshot.items.get(i);
            ItemPhase before = previousItems.put(item.uri, item.phase);
            if (before == item.phase) continue;
            if (item.phase == ItemPhase.ERROR) journal.add("Стикер " + (i + 1) + ": не удалось обработать. Можно повторить или пропустить.");
            if (item.phase == ItemPhase.SKIPPED) journal.add("Стикер " + (i + 1) + ": пропущен");
        }
        if (previousPhase != snapshot.phase) {
            if (snapshot.phase == Phase.STOPPING) journal.add("Обработка остановлена пользователем");
            if (snapshot.phase == Phase.FINALIZED) journal.add("Набор сохранён");
            previousPhase = snapshot.phase;
        }
        while (journal.size() > 120) journal.remove(0);
    }

    private Snapshot visualSnapshot(Snapshot actual) {
        boolean motion = Motion.enabled(getContext());
        boolean interrupted = actual.phase == Phase.STOPPING || actual.phase == Phase.PARTIAL
                || actual.phase == Phase.IDLE || actual.phase == Phase.FINALIZED || actual.failureCount > 0;
        long now = android.os.SystemClock.uptimeMillis();
        holding = false;
        List<Item> items = new ArrayList<>();
        for (Item item : actual.items) {
            if (item.phase == ItemPhase.READY && motionPolicy.holdSuccess(item.uri, now, motion, interrupted)) {
                holding = true;
                items.add(new Item(item.uri, item.name, ItemPhase.PROCESSING, 99, "Завершение…"));
            } else items.add(item);
        }
        if (!motion || interrupted) finishAt = 0;
        else if (actual.phase == Phase.READY && !holding && !completionAnnounced) {
            if (finishAt == 0) finishAt = now + 250;
            holding = now < finishAt;
        }
        removeCallbacks(reveal);
        if (holding) {
            long deadline = finishAt > now ? finishAt : motionPolicy.nextDeadline(now);
            if (deadline != Long.MAX_VALUE) postDelayed(reveal, Math.max(1, deadline - now));
        }
        return new Snapshot(actual.phase, actual.animated, actual.packName, actual.coverUri,
                actual.total, actual.successCount, actual.failureCount, actual.overallPercent,
                actual.stage, actual.canStart, actual.canFinalize, actual.hasRetryableFailures, items);
    }

    private void renderActions(Snapshot snapshot) {
        UiComponents.syncIndicator(primary, false);
        primary.setVisibility(VISIBLE);
        secondary.setVisibility(GONE);
        primary.setOnClickListener(null);
        secondary.setOnClickListener(null);

        switch (snapshot.phase) {
            case PROCESSING:
                primary.setText("Остановить обработку");
                styleDangerButton(primary, true);
                primary.setOnClickListener(v -> confirmCancellation());
                break;
            case STOPPING:
                primary.setText("Останавливаю…");
                styleDangerButton(primary, false);
                break;
            case PARTIAL:
                if (snapshot.canFinalize) {
                    primary.setText("Создать из готовых");
                    UiComponents.stylePrimaryButton(primary, true);
                    primary.setOnClickListener(v -> host.onFinalize());
                    if (snapshot.hasRetryableFailures) {
                        secondary.setVisibility(VISIBLE);
                        secondary.setText("Повторить ошибки");
                        UiComponents.styleOutlineButton(secondary, true);
                        secondary.setOnClickListener(v -> host.onRetryAll());
                    }
                } else {
                    primary.setText("Повторить ошибки");
                    UiComponents.stylePrimaryButton(primary, snapshot.hasRetryableFailures);
                    if (snapshot.hasRetryableFailures) primary.setOnClickListener(v -> host.onRetryAll());
                }
                break;
            case READY:
                primary.setText("Создать набор");
                UiComponents.stylePrimaryButton(primary, snapshot.canFinalize);
                if (snapshot.canFinalize) primary.setOnClickListener(v -> host.onFinalize());
                secondary.setVisibility(VISIBLE);
                secondary.setText("Отменить");
                UiComponents.styleOutlineButton(secondary, true);
                secondary.setOnClickListener(v -> host.onDiscard());
                break;
            case FINALIZED:
                PackStore.Pack pack = getContext() instanceof MainActivity
                        ? ((MainActivity) getContext()).runtimeCurrentPack() : null;
                primary.setText(pack == null ? "Добавить в WhatsApp" : WhatsAppSync.label(getContext(), pack));
                boolean syncing = pack != null && WhatsAppSync.state(getContext(), pack) == WhatsAppSync.State.SYNCING;
                UiComponents.stylePrimaryButton(primary, !syncing);
                UiComponents.syncIndicator(primary, syncing);
                primary.setOnClickListener(v -> host.onAddToWhatsApp());
                secondary.setVisibility(VISIBLE);
                secondary.setText("Открыть в «Наборах»");
                UiComponents.styleOutlineButton(secondary, true);
                secondary.setOnClickListener(v -> host.onOpenPacks());
                break;
            case IDLE:
            default:
                primary.setText("Создать набор");
                UiComponents.stylePrimaryButton(primary, snapshot.canStart);
                if (snapshot.canStart) primary.setOnClickListener(v -> host.onStart());
                break;
        }
    }

    private void confirmCancellation() {
        ThemedDialogs.confirm(getContext(), "Остановить обработку?",
                "Текущий файл будет остановлен. Уже готовые результаты сохранятся для этой сборки.",
                "Остановить", host::onCancel);
    }

    private void renderStateMessage(Snapshot snapshot) {
        String message = "";
        switch (snapshot.phase) {
            case PARTIAL:
                if (snapshot.canFinalize) {
                    message = "Готово " + snapshot.successCount + ", ошибок или пропущено "
                            + snapshot.failureCount + ". Можно собрать набор из готовых.";
                } else {
                    message = "Готово " + snapshot.successCount
                            + ". Для набора нужно минимум 3 готовых стикера.";
                }
                break;
            case READY:
                message = "Все файлы обработаны. Проверьте результат и создайте набор.";
                break;
            case FINALIZED:
                message = "✓ Набор сохранён локально и готов к добавлению в WhatsApp";
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

    private String sourceType(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".webp")) return "WebP";
        if (lower.endsWith(".gif")) return "GIF";
        if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov")) return "Видео";
        return "Фото";
    }

    private String fileCountLabel(int count) {
        int mod10 = count % 10;
        int mod100 = count % 100;
        if (mod10 == 1 && mod100 != 11) return count + " файл";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return count + " файла";
        return count + " файлов";
    }

    private String itemChip(Phase buildPhase, ItemPhase itemPhase) {
        switch (itemPhase) {
            case READY:
                return "✓ Готово";
            case PROCESSING:
                return "Обработка";
            case ERROR:
                return "! Ошибка";
            case SKIPPED:
                return "Пропущено";
            case PENDING:
            default:
                return buildPhase == Phase.IDLE ? "Готов к сборке" : "В очереди";
        }
    }

    private int rowBackground(ItemPhase phase) {
        switch (phase) {
            case ERROR: return color(R.color.app_error_container);
            case READY: return color(R.color.app_surface_variant);
            case PROCESSING: return color(R.color.app_primary_container);
            default: return color(R.color.app_surface_variant);
        }
    }

    private int chipBackground(Phase buildPhase, ItemPhase itemPhase) {
        if (itemPhase == ItemPhase.PENDING && buildPhase == Phase.IDLE) {
            return color(R.color.app_primary_container);
        }
        switch (itemPhase) {
            case ERROR: return color(R.color.app_error_container);
            case READY: return color(R.color.app_surface_variant);
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
            default: return color(R.color.app_primary);
        }
    }

    private void loadPreview(Uri uri, ImageView target) {
        if (previewLoader == null || uri == null) { target.setImageDrawable(null); target.setTag(null); return; }
        String key = previewLoader.requestKey(uri);
        if (key.equals(target.getTag())) return;
        target.setImageDrawable(null);
        target.setTag(key);
        previewLoader.load(uri, (loadedKey, bitmap) -> post(() -> {
            if (!loadedKey.equals(target.getTag())) return;
            if (bitmap != null) target.setImageBitmap(bitmap);
        }));
    }

    private TextView text(String value, int size, int textColor, int style) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(textColor);
        view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    private void styleDangerButton(Button button, boolean enabled) {
        UiComponents.centerButton(button);
        button.setEnabled(enabled);
        button.setTextSize(16);
        button.setTextColor(enabled
                ? color(R.color.app_error)
                : color(R.color.app_disabled_text));
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        GradientDrawable background = rounded(
                enabled ? color(R.color.app_error_container) : color(R.color.app_disabled_surface),
                18
        );
        background.setStroke(dp(1), enabled
                ? color(R.color.app_error)
                : color(R.color.app_disabled_surface));
        button.setBackground(background);
        button.setAlpha(enabled ? 1f : 0.7f);
    }

    private GradientDrawable rounded(int fillColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(reveal);
        super.onDetachedFromWindow();
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
