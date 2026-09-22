package com.malikksh.wastickers;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Task-focused Media tab UI. Business/editor state remains owned by MainActivity. */
final class MediaGridPanel extends LinearLayout {
    private static final int MIN_STICKERS = 3;
    private static final int MAX_STICKERS = 30;
    private static final String TRIM_PERSISTENT_KEY = "home_video_trim";
    private static final int ACTION_MOVE_LEFT = 0x01020001;
    private static final int ACTION_MOVE_RIGHT = 0x01020002;

    interface Host {
        void onModeChanged(boolean animated);
        void onAddMedia();
        void onMoveMedia(int fromIndex, int toIndex);
        void onSelectCover(Uri uri);
        void onRemoveMedia(int index);
        void onClearMedia();
        void onContinue();
    }

    private final Host host;
    private final PreviewLoader previewLoader;
    private final MediaPreflightAnalyzer analyzer;
    private final ExecutorService metadataExecutor = Executors.newSingleThreadExecutor();

    private final Button photoButton;
    private final Button animatedButton;
    private final TextView summary;
    private final GridLayout grid;
    private final LinearLayout detailCard;
    private final TextView detailName;
    private final TextView detailMeta;
    private final TextView detailStatus;
    private final Button detailTrimButton;
    private final Button addMore;
    private final Button clear;
    private final Button continueButton;

    private final List<Uri> items = new ArrayList<>();
    private final List<Uri> photoBaselineOrder = new ArrayList<>();
    private final List<Uri> animatedBaselineOrder = new ArrayList<>();
    private Uri coverUri;
    private Uri selectedUri;
    private boolean animated;
    private long renderGeneration;

    MediaGridPanel(Context context, PreviewLoader previewLoader, Host host) {
        super(context);
        this.host = host;
        this.previewLoader = previewLoader;
        this.analyzer = new MediaPreflightAnalyzer(context);

        setOrientation(VERTICAL);
        setPadding(0, 0, 0, dp(12));

        LinearLayout modeRow = new LinearLayout(context);
        modeRow.setOrientation(HORIZONTAL);
        modeRow.setPadding(dp(4), dp(4), dp(4), dp(4));
        modeRow.setBackground(rounded(color(R.color.app_surface_variant), 18));
        addView(modeRow, matchWrap());

        photoButton = new Button(context);
        photoButton.setId(R.id.media_mode_photo);
        photoButton.setText("Фото");
        photoButton.setAllCaps(false);
        photoButton.setOnClickListener(v -> host.onModeChanged(false));
        modeRow.addView(photoButton, new LinearLayout.LayoutParams(0, dp(48), 1f));

        animatedButton = new Button(context);
        animatedButton.setId(R.id.media_mode_animated);
        animatedButton.setText("Анимация");
        animatedButton.setAllCaps(false);
        animatedButton.setOnClickListener(v -> host.onModeChanged(true));
        LinearLayout.LayoutParams animatedParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        animatedParams.leftMargin = dp(4);
        modeRow.addView(animatedButton, animatedParams);

        LinearLayout summaryRow = new LinearLayout(context);
        summaryRow.setOrientation(HORIZONTAL);
        summaryRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams summaryRowParams = matchWrap();
        summaryRowParams.topMargin = dp(14);
        addView(summaryRow, summaryRowParams);

        summary = text("Выбрано фото: 0", 15, color(R.color.app_text_primary), Typeface.BOLD);
        summary.setId(R.id.media_summary);
        summaryRow.addView(summary, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        addMore = new Button(context);
        addMore.setId(R.id.media_add_more);
        addMore.setText("Добавить ещё");
        addMore.setAllCaps(false);
        addMore.setOnClickListener(v -> host.onAddMedia());
        styleTertiaryButton(addMore);
        summaryRow.addView(addMore, new LinearLayout.LayoutParams(dp(150), dp(48)));

        grid = new GridLayout(context);
        grid.setId(R.id.media_grid);
        grid.setColumnCount(3);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setUseDefaultMargins(false);
        LinearLayout.LayoutParams gridParams = matchWrap();
        gridParams.topMargin = dp(12);
        addView(grid, gridParams);

        detailCard = UiComponents.card(context);
        detailCard.setId(R.id.media_detail);
        detailCard.setVisibility(GONE);
        LinearLayout.LayoutParams detailParams = matchWrap();
        detailParams.topMargin = dp(14);
        addView(detailCard, detailParams);

        detailName = text("", 16, color(R.color.app_text_primary), Typeface.BOLD);
        detailCard.addView(detailName, matchWrap());

        detailMeta = text("", 13, color(R.color.app_text_secondary), Typeface.NORMAL);
        detailMeta.setLineSpacing(0, 1.08f);
        LinearLayout.LayoutParams detailMetaParams = matchWrap();
        detailMetaParams.topMargin = dp(4);
        detailCard.addView(detailMeta, detailMetaParams);

        detailStatus = text("", 13, color(R.color.app_primary), Typeface.BOLD);
        LinearLayout.LayoutParams detailStatusParams = matchWrap();
        detailStatusParams.topMargin = dp(6);
        detailCard.addView(detailStatus, detailStatusParams);

        detailTrimButton = new Button(context);
        detailTrimButton.setId(R.id.media_edit_trim);
        detailTrimButton.setText("Изменить фрагмент");
        detailTrimButton.setAllCaps(false);
        UiComponents.styleOutlineButton(detailTrimButton, true);
        detailTrimButton.setVisibility(GONE);
        detailTrimButton.setOnClickListener(v -> openTrimForSelected());
        LinearLayout.LayoutParams trimParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        trimParams.topMargin = dp(10);
        detailCard.addView(detailTrimButton, trimParams);
        LinearLayout selectionActions = new LinearLayout(context);
        Button makeCover = new Button(context);
        makeCover.setText("Обложка");
        makeCover.setContentDescription("Выбрать выделенный стикер как обложку");
        UiComponents.styleOutlineButton(makeCover, true);
        makeCover.setOnClickListener(v -> { if (selectedUri != null) host.onSelectCover(selectedUri); });
        selectionActions.addView(makeCover, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button removeSelected = new Button(context);
        removeSelected.setText("Удалить");
        removeSelected.setContentDescription("Удалить выделенный стикер");
        UiComponents.styleDestructiveButton(removeSelected, true);
        removeSelected.setOnClickListener(v -> removeWithUndo(items.indexOf(selectedUri)));
        selectionActions.addView(removeSelected, new LinearLayout.LayoutParams(0, dp(48), 1));
        detailCard.addView(selectionActions, matchWrap());

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(14);
        addView(actions, actionsParams);

        clear = new Button(context);
        clear.setId(R.id.media_clear);
        clear.setText("Очистить");
        clear.setAllCaps(false);
        clear.setOnClickListener(v -> host.onClearMedia());
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(50), 1f));

        continueButton = new Button(context);
        continueButton.setId(R.id.media_continue);
        continueButton.setText("Далее к сборке");
        continueButton.setAllCaps(false);
        continueButton.setOnClickListener(v -> host.onContinue());
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(0, dp(50), 2f);
        continueParams.leftMargin = dp(8);
        actions.addView(continueButton, continueParams);
    }

    View detachActions() {
        View actions = (View) clear.getParent();
        removeView(actions);
        return actions;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw) post(this::renderGrid);
    }

    void render(List<Uri> newItems, Uri newCoverUri, boolean animatedMode) {
        renderGeneration++;
        List<Uri> incoming = new ArrayList<>();
        if (newItems != null) incoming.addAll(newItems);
        updateBaselineOrder(incoming, animatedMode);

        animated = animatedMode;
        items.clear();
        items.addAll(incoming);
        coverUri = newCoverUri != null && items.contains(newCoverUri)
                ? newCoverUri
                : (items.isEmpty() ? null : items.get(0));
        if (selectedUri != null && !items.contains(selectedUri)) selectedUri = null;

        UiComponents.styleSegment(photoButton, !animated);
        UiComponents.styleSegment(animatedButton, animated);
        summary.setText(animated
                ? "Выбрано анимаций: " + items.size()
                : "Выбрано фото: " + items.size());
        addMore.setEnabled(items.size() < MAX_STICKERS);
        addMore.setAlpha(addMore.isEnabled() ? 1f : 0.45f);
        UiComponents.styleOutlineButton(clear, !items.isEmpty());
        boolean wasEnabled = continueButton.isEnabled();
        UiComponents.stylePrimaryButton(continueButton,
                items.size() >= MIN_STICKERS && items.size() <= MAX_STICKERS);
        if (!wasEnabled && continueButton.isEnabled()) Motion.crossfade(continueButton);

        renderGrid();
        renderDetail();
    }

    int itemCount() {
        return items.size();
    }

    boolean resetOrder() {
        List<Uri> baseline = animated ? animatedBaselineOrder : photoBaselineOrder;
        if (items.size() < 2 || baseline.size() != items.size()
                || !sameMembers(baseline, items) || baseline.equals(items)) {
            return false;
        }

        List<Uri> working = new ArrayList<>(items);
        boolean changed = false;
        for (int target = 0; target < baseline.size(); target++) {
            Uri expected = baseline.get(target);
            int from = working.indexOf(expected);
            if (from < 0 || from == target) continue;
            host.onMoveMedia(from, target);
            working.remove(from);
            working.add(target, expected);
            changed = true;
        }
        return changed;
    }

    private void updateBaselineOrder(List<Uri> incoming, boolean animatedMode) {
        List<Uri> baseline = animatedMode ? animatedBaselineOrder : photoBaselineOrder;
        if (baseline.isEmpty() || !sameMembers(baseline, incoming)) {
            baseline.clear();
            baseline.addAll(incoming);
        }
    }

    private boolean sameMembers(List<Uri> first, List<Uri> second) {
        return first.size() == second.size()
                && first.containsAll(second)
                && second.containsAll(first);
    }

    private Uri lastRenderedCover;

    private void renderGrid() {
        java.util.Map<Uri, FrameLayout> oldTiles = new java.util.HashMap<>();
        for (int n = 0; n < grid.getChildCount(); n++) {
            View child = grid.getChildAt(n);
            if (child instanceof FrameLayout && child.getTag() instanceof Uri)
                oldTiles.put((Uri) child.getTag(), (FrameLayout) child);
        }
        Motion.reflow(grid, true);
        for (int n = grid.getChildCount() - 1; n >= 0; n--) {
            if (!items.contains(grid.getChildAt(n).getTag())) grid.removeViewAt(n);
        }
        int columns = gridColumnCount();
        grid.setColumnCount(columns);
        if (items.isEmpty()) {
            renderEmptyState(columns);
            return;
        }

        int available = availableGridWidth();
        int tileSize = (available - dp(columns * 6)) / columns;
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            final Uri uri = items.get(i);
            boolean isCover = uri.equals(coverUri);
            boolean isSelected = uri.equals(selectedUri);

            FrameLayout cachedTile = oldTiles.get(uri);
            final FrameLayout tile = cachedTile == null ? new FrameLayout(getContext()) : cachedTile;
            ImageView cachedImage = tile.getChildCount() > 0 ? (ImageView) tile.getChildAt(0) : null;
            while (tile.getChildCount() > 1) tile.removeViewAt(1);
            tile.setTag(uri);
            tile.setPadding(dp(2), dp(2), dp(2), dp(2));
            tile.setBackground(tileBackground(isCover, isSelected));
            tile.setClickable(true);
            tile.setFocusable(true);
            tile.setContentDescription(mediaAccessibilityLabel(uri, i, isCover));
            installReorderAccessibility(tile, index);
            tile.setOnClickListener(v -> {
                selectedUri = uri;
                renderGrid();
                renderDetail();
            });
            tile.setOnLongClickListener(v -> startTileDrag(tile, index));
            tile.setOnDragListener((v, event) -> handleDrag(tile, index, event));

            ImageView image = cachedImage == null ? new ImageView(getContext()) : cachedImage;
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(rounded(color(R.color.app_disabled_surface), 14));
            if (Build.VERSION.SDK_INT >= 21) image.setClipToOutline(true);
            FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            imageParams.setMargins(dp(2), dp(2), dp(2), dp(2));
            if (cachedImage == null) {
                tile.addView(image, imageParams);
                loadPreview(uri, image);
            }

            ImageView handle = new ImageView(getContext());
            handle.setImageResource(R.drawable.ic_drag_handle);
            handle.setColorFilter(color(R.color.app_text_primary));
            handle.setContentDescription("Перетащить файл " + (i + 1));
            handle.setOnLongClickListener(v -> startTileDrag(tile, index));
            handle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(dp(18), dp(18));
            handleParams.gravity = Gravity.TOP | Gravity.START;
            handleParams.setMargins(dp(4), dp(2), 0, 0);
            tile.addView(handle, handleParams);

            android.widget.ImageButton remove = iconOverlay(R.drawable.ic_close,
                    color(R.color.app_on_primary), color(R.color.app_overlay_dark));
            remove.setContentDescription("Удалить файл " + (i + 1));
            remove.setOnClickListener(v -> removeWithUndo(index));
            FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(dp(28), dp(28));
            removeParams.gravity = Gravity.TOP | Gravity.END;
            removeParams.setMargins(0, dp(2), dp(2), 0);
            tile.addView(remove, removeParams);

            android.widget.ImageButton cover = iconOverlay(R.drawable.ic_cover_star,
                    isCover ? color(R.color.app_primary) : color(R.color.app_text_primary),
                    color(R.color.app_overlay_light));
            cover.setContentDescription(isCover ? "Выбрано как обложка" : "Выбрать как обложку");
            cover.setOnClickListener(v -> { Motion.tick(v); host.onSelectCover(uri); });
            FrameLayout.LayoutParams coverParams = new FrameLayout.LayoutParams(dp(28), dp(28));
            coverParams.gravity = Gravity.BOTTOM | Gravity.END;
            coverParams.setMargins(0, 0, dp(2), dp(2));
            tile.addView(cover, coverParams);
            if (isCover && !uri.equals(lastRenderedCover)) Motion.cover(cover);

            TextView duration = overlay("", 8,
                    color(R.color.app_on_primary), color(R.color.app_overlay_badge));
            duration.setClickable(false);
            duration.setFocusable(false);
            duration.setTag(uri.toString());
            duration.setVisibility(VISIBLE);
            FrameLayout.LayoutParams durationParams = new FrameLayout.LayoutParams(
                    dp(28), dp(18));
            durationParams.gravity = Gravity.BOTTOM | Gravity.START;
            durationParams.setMargins(dp(2), 0, 0, dp(2));
            duration.setPadding(0, 0, 0, 0);
            duration.setSingleLine(true);
            duration.setIncludeFontPadding(false);
            tile.addView(duration, durationParams);
            loadDuration(uri, duration, renderGeneration);

            GridLayout.LayoutParams tileParams = new GridLayout.LayoutParams();
            tileParams.width = tileSize;
            tileParams.height = tileSize;
            tileParams.setMargins(dp(3), dp(3), dp(3), dp(3));
            if (grid.indexOfChild(tile) != i) {
                grid.removeView(tile);
                grid.addView(tile, Math.min(i, grid.getChildCount()), tileParams);
                if (cachedTile == null) Motion.tileEnter(tile, Math.min(i * 25, 250));
            } else tile.setLayoutParams(tileParams);
        }
        lastRenderedCover = coverUri;
    }

    private void removeWithUndo(int index) {
        if (index < 0 || index >= items.size()) return;

        MainActivity runtime = getContext() instanceof MainActivity
                ? (MainActivity) getContext()
                : null;
        List<Uri> previousItems = new ArrayList<>(items);
        Uri previousCover = coverUri;
        boolean previousAnimated = animated;
        String previousName = "";
        if (runtime != null && runtime.runtimePackName() != null) {
            previousName = runtime.runtimePackName().getText().toString();
        }
        final String nameSnapshot = previousName;
        final String projectSnapshot = runtime == null ? null : runtime.runtimeProjectId();

        host.onRemoveMedia(index);
        if (!(getContext() instanceof Activity)) return;
        Activity activity = (Activity) getContext();

        if (runtime == null) {
            TransientFeedback.show(activity, "Файл удалён");
            return;
        }

        TransientFeedback.show(activity, "Файл удалён", "Отменить", () -> {
            if (!java.util.Objects.equals(projectSnapshot, runtime.runtimeProjectId())) return;
            if (runtime.runtimeIsProcessing()) {
                TransientFeedback.show(activity, "Нельзя отменить во время обработки");
                return;
            }
            boolean restored = runtime.runtimeRestoreEditorState(
                    previousAnimated,
                    previousItems,
                    previousCover,
                    nameSnapshot,
                    null
            );
            if (!restored) {
                TransientFeedback.show(activity, "Не удалось восстановить файл");
                return;
            }
            EditorInstanceStateBridge.savePersistent(runtime);
            render(
                    runtime.runtimeSelectedUrisSnapshot(),
                    runtime.runtimeCoverUri(),
                    runtime.runtimeIsAnimatedMode()
            );
            TransientFeedback.show(activity, "Удаление отменено");
        });
    }

    private int availableGridWidth() {
        return getWidth() > 0 ? getWidth() - getPaddingLeft() - getPaddingRight()
                : getResources().getDisplayMetrics().widthPixels - dp(40);
    }

    private int gridColumnCount() {
        float widthDp = availableGridWidth() / getResources().getDisplayMetrics().density;
        return EditorGridPolicy.columns(widthDp, getResources().getConfiguration().fontScale);
    }

    private String mediaAccessibilityLabel(Uri uri, int index, boolean isCover) {
        String name = uri == null ? "Файл" : uri.getLastPathSegment();
        if (name == null || name.trim().isEmpty()) name = "Файл";
        return name + ", позиция " + (index + 1) + " из " + items.size()
                + (isCover ? ", выбрано как обложка" : ", не выбрано как обложка")
                + ". Удерживайте для сортировки.";
    }

    private void installReorderAccessibility(View tile, int index) {
        tile.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View hostView, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(hostView, info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.media_action_remove, "Удалить"));
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.media_action_cover, "Выбрать как обложку"));
                if (index > 0) {
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                            ACTION_MOVE_LEFT, "Переместить влево"));
                }
                if (index < items.size() - 1) {
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                            ACTION_MOVE_RIGHT, "Переместить вправо"));
                }
            }

            @Override
            public boolean performAccessibilityAction(View hostView, int action, Bundle args) {
                if (action == R.id.media_action_remove) { removeWithUndo(index); return true; }
                if (action == R.id.media_action_cover) { host.onSelectCover(items.get(index)); return true; }
                if (action == ACTION_MOVE_LEFT && index > 0) {
                    host.onMoveMedia(index, index - 1);
                    hostView.announceForAccessibility("Перемещено влево");
                    return true;
                }
                if (action == ACTION_MOVE_RIGHT && index < items.size() - 1) {
                    host.onMoveMedia(index, index + 1);
                    hostView.announceForAccessibility("Перемещено вправо");
                    return true;
                }
                return super.performAccessibilityAction(hostView, action, args);
            }
        });
    }

    private void renderEmptyState(int columns) {
        LinearLayout empty = UiComponents.card(getContext());
        empty.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = text("Пока ничего не выбрано", 17,
                color(R.color.app_text_primary), Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        empty.addView(title, matchWrap());

        TextView hint = text("Добавьте от 3 до 30 файлов", 13,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(4);
        empty.addView(hint, hintParams);

        Button pick = new Button(getContext());
        pick.setText("Выбрать файлы");
        pick.setAllCaps(false);
        UiComponents.stylePrimaryButton(pick, true);
        pick.setOnClickListener(v -> host.onAddMedia());
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(dp(220), dp(50));
        pickParams.topMargin = dp(12);
        empty.addView(pick, pickParams);

        GridLayout.LayoutParams emptyParams = new GridLayout.LayoutParams();
        emptyParams.columnSpec = GridLayout.spec(0, columns);
        emptyParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        emptyParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        grid.addView(empty, emptyParams);
    }

    private boolean startTileDrag(FrameLayout tile, int index) {
        ClipData dragData = ClipData.newPlainText("media-index", String.valueOf(index));
        tile.setElevation(dp(8));
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(tile);
        if (Build.VERSION.SDK_INT >= 24) {
            tile.startDragAndDrop(dragData, shadow, Integer.valueOf(index), 0);
        } else {
            //noinspection deprecation
            tile.startDrag(dragData, shadow, Integer.valueOf(index), 0);
        }
        return true;
    }

    private boolean handleDrag(FrameLayout tile, int targetIndex, DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return event.getLocalState() instanceof Integer;
            case DragEvent.ACTION_DRAG_ENTERED:
                tile.setAlpha(0.72f);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                tile.setAlpha(1f);
                return true;
            case DragEvent.ACTION_DROP:
                tile.setAlpha(1f);
                Object state = event.getLocalState();
                if (state instanceof Integer) {
                    host.onMoveMedia((Integer) state, targetIndex);
                    Motion.tick(tile);
                }
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                tile.setElevation(0);
                tile.setAlpha(1f);
                return true;
            default:
                return true;
        }
    }

    private void renderDetail() {
        detailTrimButton.setVisibility(GONE);
        detailTrimButton.setEnabled(true);
        if (selectedUri == null || !items.contains(selectedUri)) {
            detailCard.setVisibility(GONE);
            return;
        }

        detailCard.setVisibility(VISIBLE);
        final Uri uri = selectedUri;
        final long generation = renderGeneration;
        detailName.setText("Файл");
        detailMeta.setText(animated ? "Анимация · загрузка данных…" : "Фото · загрузка данных…");
        detailStatus.setTextColor(color(R.color.app_primary));
        detailStatus.setText(uri.equals(coverUri) ? "Выбрана как обложка" : "Готово к сборке");

        metadataExecutor.execute(() -> {
            List<MediaPreflightAnalyzer.Result> results = analyzer.analyze(Collections.singletonList(uri));
            if (results.isEmpty()) return;
            MediaPreflightAnalyzer.Result result = results.get(0);
            post(() -> {
                if (generation != renderGeneration || !uri.equals(selectedUri)) return;
                detailName.setText(result.displayName);
                StringBuilder meta = new StringBuilder(result.sizeLabel())
                        .append(" · ").append(result.dimensionsLabel());
                if (result.video && result.durationMs >= 0) {
                    meta.insert(0, MediaPreflightPolicy.formatDuration(result.durationMs) + " · ");
                    if (result.durationMs > MediaPreflightPolicy.LONG_VIDEO_MS) {
                        long start = VideoTrimStore.getStartOffsetMs(result.uri);
                        long end = VideoTrimStore.getEndOffsetMs(result.uri);
                        if (end < 0L) end = Math.min(result.durationMs, start + VideoTrimPolicy.CLIP_DURATION_MS);
                        meta.append("\nФрагмент ").append(VideoTrimPolicy.formatTime(start))
                                .append(" — ").append(VideoTrimPolicy.formatTime(end));
                        detailTrimButton.setText("Изменить фрагмент");
                        detailTrimButton.setVisibility(VISIBLE);
                    }
                }
                detailMeta.setText(meta.toString());
                if (uri.equals(coverUri)) {
                    detailStatus.setTextColor(color(R.color.app_primary));
                    detailStatus.setText("Выбрана как обложка");
                } else if (result.assessment.severity == MediaPreflightPolicy.Severity.ERROR) {
                    detailStatus.setTextColor(color(R.color.app_error));
                    detailStatus.setText(result.assessment.message);
                } else {
                    detailStatus.setTextColor(color(R.color.app_primary));
                    detailStatus.setText("Готово к сборке");
                }
            });
        });
    }

    private void openTrimForSelected() {
        if (getContext() instanceof MainActivity && ((MainActivity) getContext()).runtimeIsProcessing()) return;
        Uri uri = selectedUri;
        if (uri == null) return;
        detailTrimButton.setEnabled(false);
        detailTrimButton.setText("Подготавливаю…");
        metadataExecutor.execute(() -> {
            List<String> keys = VideoTrimStore.prepare(
                    getContext(), Collections.singletonList(uri));
            String project = getContext() instanceof MainActivity ? ((MainActivity) getContext()).runtimeProjectId() : null;
            VideoTrimStore.savePersistent(getContext(), project == null ? TRIM_PERSISTENT_KEY : TRIM_PERSISTENT_KEY + "." + project);
            post(() -> {
                detailTrimButton.setEnabled(true);
                detailTrimButton.setText("Изменить фрагмент");
                if (keys.isEmpty() || selectedUri == null || !uri.equals(selectedUri)) return;
                Intent intent = new Intent(getContext(), VideoTrimActivity.class);
                intent.putStringArrayListExtra(VideoTrimActivity.EXTRA_KEYS, new ArrayList<>(keys));
                if (getContext() instanceof MainActivity)
                    intent.putExtra(VideoTrimActivity.EXTRA_PROJECT_ID, ((MainActivity) getContext()).runtimeProjectId());
                getContext().startActivity(intent);
            });
        });
    }

    private void loadPreview(Uri uri, ImageView target) {
        if (previewLoader == null || uri == null) return;
        String key = previewLoader.requestKey(uri);
        target.setTag(key);
        previewLoader.load(uri, (loadedKey, bitmap) -> post(() -> {
            if (!loadedKey.equals(target.getTag())) return;
            if (bitmap != null) target.setImageBitmap(bitmap);
        }));
    }

    private void loadDuration(Uri uri, TextView badge, long generation) {
        metadataExecutor.execute(() -> {
            List<MediaPreflightAnalyzer.Result> results = analyzer.analyze(Collections.singletonList(uri));
            if (results.isEmpty()) return;
            MediaPreflightAnalyzer.Result result = results.get(0);
            post(() -> {
                if (generation != renderGeneration || !uri.toString().equals(badge.getTag())) return;
                String kind = result.kindLabel();
                String label = result.video ? "Видео" : "GIF".equalsIgnoreCase(kind) ? "GIF"
                        : "WebP".equalsIgnoreCase(kind) ? "WebP" : "Фото";
                badge.setText(label);
                badge.setContentDescription(result.video && result.durationMs >= 0
                        ? label + ", " + MediaPreflightPolicy.formatDuration(result.durationMs) : label);
            });
        });
    }

    private GradientDrawable tileBackground(boolean cover, boolean selected) {
        GradientDrawable background = rounded(
                cover ? color(R.color.app_primary_container) : color(R.color.app_surface), 16);
        if (cover || selected) {
            background.setStroke(dp(cover ? 2 : 1),
                    color(cover ? R.color.app_primary : R.color.app_secondary));
        } else {
            background.setStroke(dp(1), color(R.color.app_border));
        }
        return background;
    }

    private TextView overlay(String value, int size, int textColor, int backgroundColor) {
        TextView view = text(value, size, textColor, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(7), 0, dp(7), 0);
        view.setBackground(rounded(backgroundColor, 12));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private android.widget.ImageButton iconOverlay(int drawableRes, int tintColor, int backgroundColor) {
        android.widget.ImageButton view = new android.widget.ImageButton(getContext());
        view.setImageResource(drawableRes);
        view.setColorFilter(tintColor);
        view.setPadding(dp(4), dp(4), dp(4), dp(4));
        view.setBackground(rounded(backgroundColor, 9));
        return view;
    }

    private void styleTertiaryButton(Button button) {
        UiComponents.centerButton(button);
        button.setTextSize(13);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(color(R.color.app_primary));
        button.setBackground(rounded(color(R.color.app_transparent), 14));
    }

    private TextView text(String value, int size, int textColor, int style) {
        TextView view = new TextView(getContext());
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

    private int color(int resource) {
        return getContext().getColor(resource);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected void onDetachedFromWindow() {
        metadataExecutor.shutdownNow();
        super.onDetachedFromWindow();
    }
}
