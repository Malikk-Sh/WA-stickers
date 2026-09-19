package com.malikksh.wastickers;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Task-focused editor for selecting the 10-second fragment used by long videos. */
public class VideoTrimActivity extends Activity {
    static final String EXTRA_KEYS = "video_trim_keys";

    private static final String TRIM_PERSISTENT_KEY = "home_video_trim";
    private static final String STATE_INDEX = "trim.current_index";
    private static final String STATE_ORIGINAL_STARTS = "trim.original_starts";
    private static final String STATE_WORKING_STARTS = "trim.working_starts";

    private final ExecutorService previewExecutor = Executors.newSingleThreadExecutor();

    private List<VideoTrimStore.Entry> entries;
    private long[] originalStarts;
    private long[] workingStarts;
    private int currentIndex;
    private long previewGeneration;
    private boolean finishingWithResult;

    private TextView indicator;
    private ImageView preview;
    private TextView filename;
    private TextView duration;
    private TextView range;
    private SeekBar seekBar;
    private Button stepBack;
    private Button stepForward;
    private Button previous;
    private Button next;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppSettings.wrapForAppearance(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppSettings.configureRuntime(this);
        AppSettings.applySystemBars(this);

        ArrayList<String> keys = getIntent().getStringArrayListExtra(EXTRA_KEYS);
        entries = VideoTrimStore.getEntries(keys);
        if (entries.isEmpty()) {
            VideoTrimStore.restorePersistent(this, TRIM_PERSISTENT_KEY);
            entries = VideoTrimStore.getEntries(keys);
        }
        if (entries.isEmpty()) {
            finish();
            return;
        }

        initializeState(savedInstanceState);
        setContentView(buildUi());
        renderCurrentEntry();
    }

    private void initializeState(Bundle savedInstanceState) {
        int count = entries.size();
        originalStarts = savedInstanceState == null
                ? null
                : savedInstanceState.getLongArray(STATE_ORIGINAL_STARTS);
        workingStarts = savedInstanceState == null
                ? null
                : savedInstanceState.getLongArray(STATE_WORKING_STARTS);

        if (originalStarts == null || originalStarts.length != count) {
            originalStarts = new long[count];
            for (int i = 0; i < count; i++) {
                originalStarts[i] = VideoTrimPolicy.clampStartMs(
                        entries.get(i).durationMs,
                        entries.get(i).startOffsetMs
                );
            }
        }
        if (workingStarts == null || workingStarts.length != count) {
            workingStarts = originalStarts.clone();
        }

        currentIndex = savedInstanceState == null
                ? 0
                : savedInstanceState.getInt(STATE_INDEX, 0);
        currentIndex = Math.max(0, Math.min(currentIndex, count - 1));

        for (int i = 0; i < count; i++) {
            workingStarts[i] = VideoTrimPolicy.clampStartMs(
                    entries.get(i).durationMs,
                    workingStarts[i]
            );
            VideoTrimStore.setStartOffsetMs(entries.get(i).key, workingStarts[i]);
        }
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

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, matchWrap());

        LinearLayout headerText = new LinearLayout(this);
        headerText.setOrientation(LinearLayout.VERTICAL);
        header.addView(headerText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = UiComponents.screenTitle(this, "Фрагмент видео");
        title.setId(R.id.trim_title);
        headerText.addView(title, matchWrap());

        TextView subtitle = UiComponents.metadata(this, "Выберите участок до 10 секунд");
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(2);
        headerText.addView(subtitle, subtitleParams);

        indicator = text("", 13, color(R.color.app_primary), Typeface.BOLD);
        indicator.setId(R.id.trim_indicator);
        indicator.setGravity(Gravity.CENTER);
        indicator.setPadding(dp(12), 0, dp(12), 0);
        indicator.setBackground(UiComponents.rounded(
                this, R.color.app_primary_container, R.dimen.radius_pill));
        header.addView(indicator, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));

        LinearLayout editorCard = UiComponents.card(this);
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.topMargin = dp(16);
        root.addView(editorCard, cardParams);

        preview = new ImageView(this);
        preview.setId(R.id.trim_preview);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(UiComponents.rounded(
                this, R.color.app_disabled_surface, R.dimen.radius_card));
        if (Build.VERSION.SDK_INT >= 21) preview.setClipToOutline(true);
        editorCard.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(240)));

        filename = UiComponents.cardTitle(this, "");
        filename.setId(R.id.trim_filename);
        filename.setMaxLines(2);
        LinearLayout.LayoutParams filenameParams = matchWrap();
        filenameParams.topMargin = dp(14);
        editorCard.addView(filename, filenameParams);

        duration = UiComponents.metadata(this, "");
        duration.setId(R.id.trim_duration);
        LinearLayout.LayoutParams durationParams = matchWrap();
        durationParams.topMargin = dp(4);
        editorCard.addView(duration, durationParams);

        range = text("", 16, color(R.color.app_primary), Typeface.BOLD);
        range.setId(R.id.trim_range);
        LinearLayout.LayoutParams rangeParams = matchWrap();
        rangeParams.topMargin = dp(16);
        editorCard.addView(range, rangeParams);

        TextView hint = UiComponents.metadata(
                this,
                "Перемещайте ползунок или используйте шаг ±0.1 с. Превью обновляется после выбора."
        );
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(4);
        editorCard.addView(hint, hintParams);

        seekBar = new SeekBar(this);
        seekBar.setId(R.id.trim_seek);
        seekBar.setContentDescription("Начало 10-секундного фрагмента");
        LinearLayout.LayoutParams seekParams = matchWrap();
        seekParams.topMargin = dp(8);
        editorCard.addView(seekBar, seekParams);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || entries == null || entries.isEmpty()) return;
                VideoTrimStore.Entry entry = entries.get(currentIndex);
                long start = VideoTrimPolicy.startFromSeekBar(entry.durationMs, progress);
                setWorkingStart(entry, start, false);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                VideoTrimStore.Entry entry = entries.get(currentIndex);
                long start = VideoTrimPolicy.startFromSeekBar(entry.durationMs, bar.getProgress());
                setWorkingStart(entry, start, true);
            }
        });

        LinearLayout stepRow = new LinearLayout(this);
        stepRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams stepParams = matchWrap();
        stepParams.topMargin = dp(10);
        editorCard.addView(stepRow, stepParams);

        stepBack = new Button(this);
        stepBack.setId(R.id.trim_step_back);
        stepBack.setText("−0.1 с");
        stepBack.setAllCaps(false);
        stepBack.setContentDescription("Сдвинуть начало фрагмента на 0,1 секунды назад");
        stepBack.setOnClickListener(v -> adjustStartBy(-VideoTrimPolicy.SEEK_STEP_MS));
        stepRow.addView(stepBack, new LinearLayout.LayoutParams(0, dp(48), 1f));

        stepForward = new Button(this);
        stepForward.setId(R.id.trim_step_forward);
        stepForward.setText("+0.1 с");
        stepForward.setAllCaps(false);
        stepForward.setContentDescription("Сдвинуть начало фрагмента на 0,1 секунды вперёд");
        stepForward.setOnClickListener(v -> adjustStartBy(VideoTrimPolicy.SEEK_STEP_MS));
        LinearLayout.LayoutParams forwardParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        forwardParams.leftMargin = dp(8);
        stepRow.addView(stepForward, forwardParams);

        LinearLayout pager = new LinearLayout(this);
        pager.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pagerParams = matchWrap();
        pagerParams.topMargin = dp(12);
        root.addView(pager, pagerParams);

        previous = new Button(this);
        previous.setId(R.id.trim_previous);
        previous.setText("← Предыдущее");
        previous.setAllCaps(false);
        previous.setOnClickListener(v -> showEntry(currentIndex - 1));
        pager.addView(previous, new LinearLayout.LayoutParams(0, dp(48), 1f));

        next = new Button(this);
        next.setId(R.id.trim_next);
        next.setText("Следующее →");
        next.setAllCaps(false);
        next.setOnClickListener(v -> showEntry(currentIndex + 1));
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        nextParams.leftMargin = dp(8);
        pager.addView(next, nextParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(14);
        root.addView(actions, actionsParams);

        Button cancel = new Button(this);
        cancel.setId(R.id.trim_cancel);
        cancel.setText("Отмена");
        cancel.setAllCaps(false);
        UiComponents.styleOutlineButton(cancel, true);
        cancel.setOnClickListener(v -> cancelAndFinish());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(54), 1f));

        Button done = new Button(this);
        done.setId(R.id.trim_done);
        done.setText("Готово");
        done.setAllCaps(false);
        UiComponents.stylePrimaryButton(done, true);
        done.setOnClickListener(v -> saveAndFinish());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(0, dp(54), 1f);
        doneParams.leftMargin = dp(8);
        actions.addView(done, doneParams);

        return scroll;
    }

    private void showEntry(int index) {
        if (entries == null || entries.isEmpty()) return;
        int target = Math.max(0, Math.min(index, entries.size() - 1));
        if (target == currentIndex) return;
        currentIndex = target;
        renderCurrentEntry();
    }

    private void renderCurrentEntry() {
        VideoTrimStore.Entry entry = entries.get(currentIndex);
        long start = VideoTrimPolicy.clampStartMs(entry.durationMs, workingStarts[currentIndex]);
        workingStarts[currentIndex] = start;

        indicator.setText((currentIndex + 1) + " из " + entries.size());
        filename.setText(entry.displayName);
        duration.setText("Длительность " + VideoTrimPolicy.formatTime(entry.durationMs));
        preview.setContentDescription("Кадр выбранного фрагмента " + entry.displayName);

        seekBar.setMax(VideoTrimPolicy.seekBarMax(entry.durationMs));
        seekBar.setProgress(VideoTrimPolicy.seekBarProgress(entry.durationMs, start));
        updateRangeLabel(entry.durationMs, start);
        updateStepButtons(entry.durationMs, start);
        loadPreview(entry, start);
        updatePagerButtons();
    }

    private void adjustStartBy(long deltaMs) {
        if (entries == null || entries.isEmpty()) return;
        VideoTrimStore.Entry entry = entries.get(currentIndex);
        long start = VideoTrimPolicy.clampStartMs(
                entry.durationMs,
                workingStarts[currentIndex] + deltaMs
        );
        setWorkingStart(entry, start, true);
        seekBar.setProgress(VideoTrimPolicy.seekBarProgress(entry.durationMs, start));
    }

    private void setWorkingStart(VideoTrimStore.Entry entry, long requestedStart, boolean refreshPreview) {
        long start = VideoTrimPolicy.clampStartMs(entry.durationMs, requestedStart);
        workingStarts[currentIndex] = start;
        VideoTrimStore.setStartOffsetMs(entry.key, start);
        updateRangeLabel(entry.durationMs, start);
        updateStepButtons(entry.durationMs, start);
        if (refreshPreview) loadPreview(entry, start);
    }

    private void updateStepButtons(long durationMs, long start) {
        if (stepBack == null || stepForward == null) return;
        long back = VideoTrimPolicy.clampStartMs(durationMs, start - VideoTrimPolicy.SEEK_STEP_MS);
        long forward = VideoTrimPolicy.clampStartMs(durationMs, start + VideoTrimPolicy.SEEK_STEP_MS);
        UiComponents.styleOutlineButton(stepBack, back != start);
        UiComponents.styleOutlineButton(stepForward, forward != start);
    }

    private void updatePagerButtons() {
        boolean multiple = entries.size() > 1;
        previous.setVisibility(multiple ? View.VISIBLE : View.GONE);
        next.setVisibility(multiple ? View.VISIBLE : View.GONE);
        UiComponents.styleOutlineButton(previous, currentIndex > 0);
        UiComponents.styleOutlineButton(next, currentIndex < entries.size() - 1);
    }

    private void updateRangeLabel(long durationMs, long requestedStartMs) {
        long start = VideoTrimPolicy.clampStartMs(durationMs, requestedStartMs);
        long end = Math.min(durationMs, start + VideoTrimPolicy.CLIP_DURATION_MS);
        range.setText("Фрагмент: " + VideoTrimPolicy.formatTime(start)
                + " — " + VideoTrimPolicy.formatTime(end));
    }

    private void loadPreview(VideoTrimStore.Entry entry, long startMs) {
        long generation = ++previewGeneration;
        String requestKey = entry.key + ":" + startMs + ":" + generation;
        preview.setTag(requestKey);
        preview.setImageDrawable(null);

        previewExecutor.execute(() -> {
            Bitmap frame = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(this, entry.uri);
                frame = retriever.getFrameAtTime(
                        Math.max(0L, startMs) * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                );
                if (frame != null) frame = scalePreview(frame);
            } catch (Throwable ignored) {
            } finally {
                try {
                    retriever.release();
                } catch (Throwable ignored) {
                }
            }

            Bitmap finalFrame = frame;
            runOnUiThread(() -> {
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                    recycle(finalFrame);
                    return;
                }
                Object tag = preview.getTag();
                if (!(tag instanceof String) || !requestKey.equals(tag)) {
                    recycle(finalFrame);
                    return;
                }
                if (finalFrame != null) preview.setImageBitmap(finalFrame);
            });
        });
    }

    private Bitmap scalePreview(Bitmap source) {
        int maxSide = 900;
        int largest = Math.max(source.getWidth(), source.getHeight());
        if (largest <= maxSide) return source;
        float scale = maxSide / (float) largest;
        int width = Math.max(1, Math.round(source.getWidth() * scale));
        int height = Math.max(1, Math.round(source.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(source, width, height, true);
        if (scaled != source) source.recycle();
        return scaled;
    }

    private void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    private void saveAndFinish() {
        finishingWithResult = true;
        for (int i = 0; i < entries.size(); i++) {
            VideoTrimStore.setStartOffsetMs(entries.get(i).key, workingStarts[i]);
        }
        VideoTrimStore.savePersistent(this, TRIM_PERSISTENT_KEY);
        setResult(RESULT_OK);
        finish();
    }

    private void cancelAndFinish() {
        restoreOriginalStarts();
        finishingWithResult = true;
        setResult(RESULT_CANCELED);
        finish();
    }

    private void restoreOriginalStarts() {
        if (entries == null || originalStarts == null) return;
        for (int i = 0; i < entries.size() && i < originalStarts.length; i++) {
            VideoTrimStore.setStartOffsetMs(entries.get(i).key, originalStarts[i]);
        }
    }

    @Override
    public void onBackPressed() {
        cancelAndFinish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_INDEX, currentIndex);
        outState.putLongArray(STATE_ORIGINAL_STARTS, originalStarts);
        outState.putLongArray(STATE_WORKING_STARTS, workingStarts);
        super.onSaveInstanceState(outState);
    }

    private TextView text(String value, int size, int textColor, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(textColor);
        view.setTypeface(Typeface.create("sans", style));
        return view;
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

    @Override
    protected void onDestroy() {
        previewGeneration++;
        previewExecutor.shutdownNow();
        if (!finishingWithResult && isFinishing()) {
            restoreOriginalStarts();
        }
        super.onDestroy();
    }
}
