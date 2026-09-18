package com.malikksh.wastickers;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
    interface Host {
        void onModeChanged(boolean animated);
        void onAddMedia();
        void onMoveMedia(int fromIndex, int toIndex);
        void onSelectCover(Uri uri);
        void onRemoveMedia(int index);
        void onClearMedia();
        void onContinue();
    }

    private static final int MIN_STICKERS = 3;
    private static final int MAX_STICKERS = 30;

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
    private final Button addMore;
    private final Button clear;
    private final Button continueButton;

    private final List<Uri> items = new ArrayList<>();
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
        modeRow.setBackground(rounded(color(R.color.app_disabled_surface), 18));
        addView(modeRow, matchWrap());

        photoButton = new Button(context);
        photoButton.setId(R.id.media_mode_photo);
        photoButton.setText("Фото");
        photoButton.setAllCaps(false);
        photoButton.setOnClickListener(v -> host.onModeChanged(false));
        modeRow.addView(photoButton, new LinearLayout.LayoutParams(0, dp(46), 1f));

        animatedButton = new Button(context);
        animatedButton.setId(R.id.media_mode_animated);
        animatedButton.setText("Анимация");
        animatedButton.setAllCaps(false);
        animatedButton.setOnClickListener(v -> host.onModeChanged(true));
        LinearLayout.LayoutParams animatedParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        animatedParams.leftMargin = dp(6);
        modeRow.addView(animatedButton, animatedParams);

        LinearLayout summaryRow = new LinearLayout(context);
        summaryRow.setOrientation(HORIZONTAL);
        summaryRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams summaryRowParams = matchWrap();
        summaryRowParams.topMargin = dp(14);
        addView(summaryRow, summaryRowParams);

        summary = text("Выбрано фото: 0", 15, color(R.color.app_text_primary), Typeface.BOLD);
        summary.setId(R.id.media_summary);
        summaryRow.addView(summary, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        addMore = new Button(context);
        addMore.setId(R.id.media_add_more);
        addMore.setText("＋ Добавить ещё");
        addMore.setAllCaps(false);
        addMore.setOnClickListener(v -> host.onAddMedia());
        styleTertiaryButton(addMore);
        summaryRow.addView(addMore, new LinearLayout.LayoutParams(dp(150), dp(44)));

        grid = new GridLayout(context);
        grid.setId(R.id.media_grid);
        grid.setColumnCount(3);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setUseDefaultMargins(false);
        LinearLayout.LayoutParams gridParams = matchWrap();
        gridParams.topMargin = dp(12);
        addView(grid, gridParams);

        detailCard = new LinearLayout(context);
        detailCard.setId(R.id.media_detail);
        detailCard.setOrientation(VERTICAL);
        detailCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        detailCard.setBackground(rounded(color(R.color.app_surface), 20));
        detailCard.setVisibility(GONE);
        if (Build.VERSION.SDK_INT >= 21) detailCard.setElevation(dp(1));
        LinearLayout.LayoutParams detailParams = matchWrap();
        detailParams.topMargin = dp(14);
        addView(detailCard, detailParams);

        detailName = text("", 16, color(R.color.app_text_primary), Typeface.BOLD);
        detailCard.addView(detailName, matchWrap());
        detailMeta = text("", 13, color(R.color.app_text_secondary), Typeface.NORMAL);
        LinearLayout.LayoutParams detailMetaParams = matchWrap();
        detailMetaParams.topMargin = dp(4);
        detailCard.addView(detailMeta, detailMetaParams);
        detailStatus = text("", 13, color(R.color.app_primary), Typeface.BOLD);
        LinearLayout.LayoutParams detailStatusParams = matchWrap();
        detailStatusParams.topMargin = dp(6);
        detailCard.addView(detailStatus, detailStatusParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(14);
        addView(actions, actionsParams);

        clear = new Button(context);
        clear.setId(R.id.media_clear);
        clear.setText("Очистить");
        clear.setAllCaps(false);
        styleSecondaryButton(clear);
        clear.setOnClickListener(v -> host.onClearMedia());
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(50), 1f));

        continueButton = new Button(context);
        continueButton.setId(R.id.media_continue);
        continueButton.setText("Далее к сборке  →");
        continueButton.setAllCaps(false);
        continueButton.setOnClickListener(v -> host.onContinue());
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(0, dp(50), 2f);
        continueParams.leftMargin = dp(8);
        actions.addView(continueButton, continueParams);
    }

    void render(List<Uri> newItems, Uri newCoverUri, boolean animatedMode) {
        renderGeneration++;
        animated = animatedMode;
        items.clear();
        if (newItems != null) items.addAll(newItems);
        coverUri = newCoverUri != null && items.contains(newCoverUri)
                ? newCoverUri
                : (items.isEmpty() ? null : items.get(0));
        if (selectedUri != null && !items.contains(selectedUri)) selectedUri = null;

        styleModeButton(photoButton, !animated);
        styleModeButton(animatedButton, animated);
        summary.setText(animated
                ? "Выбрано анимаций: " + items.size()
                : "Выбрано фото: " + items.size());
        addMore.setEnabled(items.size() < MAX_STICKERS);
        addMore.setAlpha(addMore.isEnabled() ? 1f : 0.45f);
        clear.setEnabled(!items.isEmpty());
        clear.setAlpha(clear.isEnabled() ? 1f : 0.45f);
        boolean canContinue = items.size() >= MIN_STICKERS && items.size() <= MAX_STICKERS;
        stylePrimaryButton(continueButton, canContinue);

        renderGrid();
        renderDetail();
    }

    private void renderGrid() {
        grid.removeAllViews();
        if (items.isEmpty()) {
            LinearLayout empty = new LinearLayout(getContext());
            empty.setOrientation(VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(26), dp(16), dp(26));
            empty.setBackground(rounded(color(R.color.app_surface), 20));
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
            stylePrimaryButton(pick, true);
            pick.setOnClickListener(v -> host.onAddMedia());
            LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(dp(220), dp(50));
            pickParams.topMargin = dp(12);
            empty.addView(pick, pickParams);

            GridLayout.LayoutParams emptyParams = new GridLayout.LayoutParams();
            emptyParams.columnSpec = GridLayout.spec(0, 3);
            emptyParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            emptyParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            grid.addView(empty, emptyParams);
            return;
        }

        int available = getResources().getDisplayMetrics().widthPixels - dp(52);
        int tileSize = Math.max(dp(92), (available - dp(16)) / 3);
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            final Uri uri = items.get(i);
            boolean isCover = uri.equals(coverUri);
            boolean isSelected = uri.equals(selectedUri);

            FrameLayout tile = new FrameLayout(getContext());
            tile.setPadding(dp(2), dp(2), dp(2), dp(2));
            tile.setBackground(tileBackground(isCover, isSelected));
            tile.setClickable(true);
            tile.setFocusable(true);
            tile.setContentDescription("Медиа " + (i + 1) + " из " + items.size()
                    + (isCover ? ", выбрано как обложка" : "")
                    + ". Удерживайте для изменения порядка.");
            tile.setOnClickListener(v -> {
                selectedUri = uri;
                renderGrid();
                renderDetail();
            });
            tile.setOnLongClickListener(v -> {
                ClipData dragData = ClipData.newPlainText("media-index", String.valueOf(index));
                View.DragShadowBuilder shadow = new View.DragShadowBuilder(tile);
                if (Build.VERSION.SDK_INT >= 24) {
                    tile.startDragAndDrop(dragData, shadow, Integer.valueOf(index), 0);
                } else {
                    //noinspection deprecation
                    tile.startDrag(dragData, shadow, Integer.valueOf(index), 0);
                }
                return true;
            });
            tile.setOnDragListener((v, event) -> handleDrag(tile, index, event));

            ImageView image = new ImageView(getContext());
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(rounded(color(R.color.app_disabled_surface), 14));
            if (Build.VERSION.SDK_INT >= 21) image.setClipToOutline(true);
            FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            imageParams.setMargins(dp(2), dp(2), dp(2), dp(2));
            tile.addView(image, imageParams);
            loadPreview(uri, image);

            TextView handle = overlay("⋮⋮", 16, color(R.color.app_text_primary), 0xEFFFFFFF);
            handle.setContentDescription("Перетащить файл " + (i + 1));
            FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(dp(34), dp(30));
            handleParams.gravity = Gravity.TOP | Gravity.START;
            handleParams.setMargins(dp(6), dp(6), 0, 0);
            tile.addView(handle, handleParams);

            TextView remove = overlay("×", 19, Color.WHITE, 0xCC17231F);
            remove.setContentDescription("Удалить файл " + (i + 1));
            remove.setOnClickListener(v -> host.onRemoveMedia(index));
            FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(dp(30), dp(30));
            removeParams.gravity = Gravity.TOP | Gravity.END;
            removeParams.setMargins(0, dp(6), dp(6), 0);
            tile.addView(remove, removeParams);

            TextView cover = overlay(isCover ? "★" : "☆", 18,
                    isCover ? color(R.color.app_primary) : color(R.color.app_text_primary),
                    0xEFFFFFFF);
            cover.setContentDescription(isCover ? "Выбрано как обложка" : "Выбрать как обложку");
            cover.setOnClickListener(v -> host.onSelectCover(uri));
            FrameLayout.LayoutParams coverParams = new FrameLayout.LayoutParams(dp(32), dp(30));
            coverParams.gravity = Gravity.TOP | Gravity.END;
            coverParams.setMargins(0, dp(6), dp(42), 0);
            tile.addView(cover, coverParams);

            TextView duration = overlay(animated ? "▶" : "", 11, Color.WHITE, 0xB814221D);
            duration.setTag(uri.toString());
            duration.setVisibility(animated ? VISIBLE : GONE);
            FrameLayout.LayoutParams durationParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(26));
            durationParams.gravity = Gravity.BOTTOM | Gravity.START;
            durationParams.setMargins(dp(6), 0, 0, dp(6));
            tile.addView(duration, durationParams);
            if (animated) loadDuration(uri, duration, renderGeneration);

            GridLayout.LayoutParams tileParams = new GridLayout.LayoutParams();
            tileParams.width = tileSize;
            tileParams.height = tileSize;
            int margin = dp(3);
            tileParams.setMargins(margin, margin, margin, margin);
            grid.addView(tile, tileParams);
        }
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
                if (state instanceof Integer) host.onMoveMedia((Integer) state, targetIndex);
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                tile.setAlpha(1f);
                return true;
            default:
                return true;
        }
    }

    private void renderDetail() {
        if (selectedUri == null || !items.contains(selectedUri)) {
            detailCard.setVisibility(GONE);
            return;
        }
        detailCard.setVisibility(VISIBLE);
        final Uri uri = selectedUri;
        final long generation = renderGeneration;
        detailName.setText("Файл");
        detailMeta.setText(animated ? "Анимация · загрузка данных…" : "Фото · загрузка данных…");
        detailStatus.setText(uri.equals(coverUri) ? "★ Выбрана как обложка" : "Готово к сборке");
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
                        String trim = MediaPreflightPolicy.formatTrimWindow(
                                VideoTrimStore.getStartOffsetMs(result.uri), result.durationMs);
                        if (!trim.isEmpty()) meta.append("\nФрагмент ").append(trim);
                    }
                }
                detailMeta.setText(meta.toString());
                if (uri.equals(coverUri)) {
                    detailStatus.setText("★ Выбрана как обложка");
                } else if (result.assessment.severity == MediaPreflightPolicy.Severity.ERROR) {
                    detailStatus.setText(result.assessment.message);
                } else {
                    detailStatus.setText("Готово к сборке");
                }
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
                if (result.video && result.durationMs >= 0) {
                    badge.setText("▶ " + MediaPreflightPolicy.formatDuration(result.durationMs));
                } else {
                    badge.setText(result.kindLabel());
                }
            });
        });
    }

    private GradientDrawable tileBackground(boolean cover, boolean selected) {
        GradientDrawable background = rounded(
                cover ? color(R.color.app_primary_container) : color(R.color.app_surface), 16);
        if (cover || selected) {
            background.setStroke(dp(cover ? 2 : 1),
                    color(cover ? R.color.app_primary : R.color.app_secondary));
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

    private void styleModeButton(Button button, boolean active) {
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(active ? Color.WHITE : color(R.color.app_primary));
        GradientDrawable background = rounded(
                active ? color(R.color.app_primary) : Color.TRANSPARENT, 14);
        button.setBackground(background);
    }

    private void stylePrimaryButton(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(enabled ? Color.WHITE : color(R.color.app_disabled_text));
        button.setBackground(rounded(
                enabled ? color(R.color.app_primary) : color(R.color.app_disabled_surface), 16));
    }

    private void styleSecondaryButton(Button button) {
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(color(R.color.app_primary));
        button.setBackground(rounded(color(R.color.app_primary_container), 16));
    }

    private void styleTertiaryButton(Button button) {
        button.setTextSize(13);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(color(R.color.app_primary));
        button.setBackground(rounded(Color.TRANSPARENT, 14));
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
