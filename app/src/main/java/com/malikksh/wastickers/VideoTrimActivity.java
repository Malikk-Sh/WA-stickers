package com.malikksh.wastickers;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
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

public class VideoTrimActivity extends Activity {
    static final String EXTRA_KEYS = "video_trim_keys";

    private static final int BG = 0xFFF5F8F6;
    private static final int CARD = 0xFFFFFFFF;
    private static final int TEXT = 0xFF14221D;
    private static final int MUTED = 0xFF6A7872;
    private static final int PRIMARY = 0xFF075E54;
    private static final int SOFT = 0xFFEAF5EF;

    private final ExecutorService previewExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();

        ArrayList<String> keys = getIntent().getStringArrayListExtra(EXTRA_KEYS);
        List<VideoTrimStore.Entry> entries = VideoTrimStore.getEntries(keys);
        if (entries.isEmpty()) {
            finish();
            return;
        }
        setContentView(buildUi(entries));
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 23) {
            window.getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private android.view.View buildUi(List<VideoTrimStore.Entry> entries) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = card();
        root.addView(header, matchWrap());

        TextView title = text("Выберите фрагмент", 22, TEXT, Typeface.BOLD);
        header.addView(title, matchWrap());

        TextView subtitle = text(
                "Для каждого длинного видео выберите начало фрагмента. В стикер попадут следующие 10 секунд.",
                13, MUTED, Typeface.NORMAL);
        subtitle.setLineSpacing(0, 1.08f);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(7);
        header.addView(subtitle, subtitleParams);

        for (VideoTrimStore.Entry entry : entries) {
            LinearLayout.LayoutParams cardParams = matchWrap();
            cardParams.topMargin = dp(12);
            root.addView(buildVideoCard(entry), cardParams);
        }

        Button done = new Button(this);
        done.setText("Готово");
        done.setAllCaps(false);
        done.setTextSize(16);
        done.setTextColor(Color.WHITE);
        done.setTypeface(Typeface.create("sans", Typeface.BOLD));
        done.setBackground(rounded(PRIMARY, 14));
        done.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        doneParams.topMargin = dp(16);
        root.addView(done, doneParams);

        return scroll;
    }

    private android.view.View buildVideoCard(VideoTrimStore.Entry entry) {
        LinearLayout card = card();

        TextView name = text(entry.displayName, 16, TEXT, Typeface.BOLD);
        name.setMaxLines(2);
        card.addView(name, matchWrap());

        TextView duration = text(
                "Длительность " + VideoTrimPolicy.formatTime(entry.durationMs),
                12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams durationParams = matchWrap();
        durationParams.topMargin = dp(4);
        card.addView(duration, durationParams);

        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(rounded(SOFT, 14));
        preview.setContentDescription("Кадр выбранного фрагмента " + entry.displayName);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(180));
        previewParams.topMargin = dp(12);
        card.addView(preview, previewParams);

        TextView range = text("", 14, PRIMARY, Typeface.BOLD);
        LinearLayout.LayoutParams rangeParams = matchWrap();
        rangeParams.topMargin = dp(10);
        card.addView(range, rangeParams);

        TextView hint = text(
                "Перемещайте ползунок — превью обновится после отпускания.",
                12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(4);
        card.addView(hint, hintParams);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(VideoTrimPolicy.seekBarMax(entry.durationMs));
        seekBar.setProgress(VideoTrimPolicy.seekBarProgress(entry.durationMs, entry.startOffsetMs));
        LinearLayout.LayoutParams seekParams = matchWrap();
        seekParams.topMargin = dp(8);
        card.addView(seekBar, seekParams);

        updateRangeLabel(range, entry.durationMs, entry.startOffsetMs);
        loadPreview(entry.uri, entry.startOffsetMs, preview);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                long start = VideoTrimPolicy.startFromSeekBar(entry.durationMs, progress);
                VideoTrimStore.setStartOffsetMs(entry.key, start);
                updateRangeLabel(range, entry.durationMs, start);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                long start = VideoTrimPolicy.startFromSeekBar(entry.durationMs, seekBar.getProgress());
                VideoTrimStore.setStartOffsetMs(entry.key, start);
                loadPreview(entry.uri, start, preview);
            }
        });

        return card;
    }

    private void updateRangeLabel(TextView label, long durationMs, long requestedStartMs) {
        long start = VideoTrimPolicy.clampStartMs(durationMs, requestedStartMs);
        long end = Math.min(durationMs, start + VideoTrimPolicy.CLIP_DURATION_MS);
        label.setText("Фрагмент: " + VideoTrimPolicy.formatTime(start)
                + " — " + VideoTrimPolicy.formatTime(end));
    }

    private void loadPreview(android.net.Uri uri, long startMs, ImageView target) {
        target.setTag(Long.valueOf(startMs));
        previewExecutor.execute(() -> {
            Bitmap frame = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(this, uri);
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
                    if (finalFrame != null) finalFrame.recycle();
                    return;
                }
                Object tag = target.getTag();
                if (!(tag instanceof Long) || ((Long) tag) != startMs) {
                    if (finalFrame != null) finalFrame.recycle();
                    return;
                }
                if (finalFrame != null) target.setImageBitmap(finalFrame);
            });
        });
    }

    private Bitmap scalePreview(Bitmap source) {
        int maxSide = 720;
        int largest = Math.max(source.getWidth(), source.getHeight());
        if (largest <= maxSide) return source;
        float scale = maxSide / (float) largest;
        int width = Math.max(1, Math.round(source.getWidth() * scale));
        int height = Math.max(1, Math.round(source.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(source, width, height, true);
        if (scaled != source) source.recycle();
        return scaled;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(CARD, 18));
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));
        return card;
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        previewExecutor.shutdownNow();
        super.onDestroy();
    }
}
