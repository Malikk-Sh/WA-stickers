package com.malikksh.wastickers;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends MainActivity {
    private static final int REQUEST_PICK_PHOTOS = 1001;
    private static final int REQUEST_PICK_ANIMATED = 1002;
    private static final int REQUEST_SAVED_PACKS = 3001;
    private static final int CARD = 0xFFFFFFFF;
    private static final int TEXT = 0xFF14221D;
    private static final int MUTED = 0xFF6A7872;
    private static final int PRIMARY = 0xFF075E54;
    private static final int SOFT = 0xFFEAF5EF;
    private static final int ERROR = 0xFFB3261E;
    private static final int WARNING = 0xFF8A5A00;

    private final ExecutorService selectionExecutor = Executors.newSingleThreadExecutor();
    private TextView packsMeta;
    private MediaPreflightAnalyzer preflightAnalyzer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        VideoTrimStore.clear();
        super.onCreate(savedInstanceState);
        preflightAnalyzer = new MediaPreflightAnalyzer(this);
        injectSavedPacksCard();
        updateSavedPacksSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSavedPacksSummary();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SAVED_PACKS && resultCode == RESULT_OK) {
            recreate();
            return;
        }

        if ((requestCode == REQUEST_PICK_PHOTOS || requestCode == REQUEST_PICK_ANIMATED)
                && resultCode == RESULT_OK && data != null) {
            List<Uri> incoming = extractUris(data);
            if (!incoming.isEmpty()) {
                prepareSelectionReport(incoming, requestCode == REQUEST_PICK_ANIMATED);
            }
        }
    }

    private List<Uri> extractUris(Intent data) {
        List<Uri> result = new ArrayList<>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null && !result.contains(uri)) result.add(uri);
            }
        } else if (data.getData() != null) {
            result.add(data.getData());
        }
        return result;
    }

    private void prepareSelectionReport(List<Uri> incoming, boolean animatedSelection) {
        selectionExecutor.execute(() -> {
            List<MediaPreflightAnalyzer.Result> results = preflightAnalyzer.analyze(incoming);
            List<String> adjustable = animatedSelection
                    ? VideoTrimStore.prepare(this, incoming)
                    : new ArrayList<>();
            runOnUiThread(() -> {
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                showPreflightDialog(results, adjustable);
            });
        });
    }

    private void showPreflightDialog(List<MediaPreflightAnalyzer.Result> results,
                                     List<String> adjustable) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpHome(20), dpHome(8), dpHome(20), dpHome(8));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        int warnings = 0;
        int errors = 0;
        for (MediaPreflightAnalyzer.Result result : results) {
            if (result.assessment.severity == MediaPreflightPolicy.Severity.ERROR) errors++;
            if (result.assessment.severity == MediaPreflightPolicy.Severity.WARNING) warnings++;
        }

        StringBuilder summaryText = new StringBuilder()
                .append("Проверено: ").append(results.size());
        if (warnings > 0) summaryText.append(" · предупреждений: ").append(warnings);
        if (errors > 0) summaryText.append(" · проблем: ").append(errors);
        if (!adjustable.isEmpty()) summaryText.append(" · длинных видео: ").append(adjustable.size());

        TextView summary = textHome(summaryText.toString(), 12, MUTED, Typeface.NORMAL);
        content.addView(summary, matchWrapHome());

        for (int i = 0; i < results.size(); i++) {
            MediaPreflightAnalyzer.Result result = results.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dpHome(12), dpHome(10), dpHome(12), dpHome(10));
            row.setBackground(roundedHome(0xFFF7FAF8, 14));
            LinearLayout.LayoutParams rowParams = matchWrapHome();
            rowParams.topMargin = dpHome(8);
            content.addView(row, rowParams);

            TextView name = textHome((i + 1) + ". " + result.displayName,
                    13, TEXT, Typeface.BOLD);
            name.setMaxLines(2);
            row.addView(name, matchWrapHome());

            StringBuilder metadata = new StringBuilder(result.kindLabel())
                    .append(" · ").append(result.sizeLabel())
                    .append(" · ").append(result.dimensionsLabel());
            if (result.video && result.durationMs >= 0) {
                metadata.append(" · ").append(MediaPreflightPolicy.formatDuration(result.durationMs));
            }
            TextView meta = textHome(metadata.toString(), 11, MUTED, Typeface.NORMAL);
            LinearLayout.LayoutParams metaParams = matchWrapHome();
            metaParams.topMargin = dpHome(3);
            row.addView(meta, metaParams);

            String noteText = result.assessment.message;
            if (result.video && result.durationMs > MediaPreflightPolicy.LONG_VIDEO_MS) {
                String trim = MediaPreflightPolicy.formatTrimWindow(
                        VideoTrimStore.getStartOffsetMs(result.uri), result.durationMs);
                if (!trim.isEmpty()) noteText += "\n" + trim + " будет конвертирован";
            }
            TextView note = textHome(noteText, 11,
                    preflightColor(result.assessment.severity), Typeface.NORMAL);
            note.setLineSpacing(0, 1.05f);
            LinearLayout.LayoutParams noteParams = matchWrapHome();
            noteParams.topMargin = dpHome(4);
            row.addView(note, noteParams);
        }

        boolean hasTrim = !adjustable.isEmpty();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Проверка выбранных файлов")
                .setView(scroll)
                .setPositiveButton(hasTrim ? "Настроить фрагменты" : "Готово", (d, which) -> {
                    if (hasTrim) launchVideoTrim(adjustable);
                })
                .create();
        dialog.setCanceledOnTouchOutside(!hasTrim);
        dialog.setCancelable(!hasTrim);
        dialog.show();
    }

    private int preflightColor(MediaPreflightPolicy.Severity severity) {
        switch (severity) {
            case ERROR:
                return ERROR;
            case WARNING:
                return WARNING;
            case INFO:
                return PRIMARY;
            case OK:
            default:
                return MUTED;
        }
    }

    private void launchVideoTrim(List<String> adjustable) {
        if (adjustable == null || adjustable.isEmpty()) return;
        Intent intent = new Intent(this, VideoTrimActivity.class);
        intent.putStringArrayListExtra(
                VideoTrimActivity.EXTRA_KEYS,
                new ArrayList<>(adjustable)
        );
        startActivity(intent);
    }

    private void injectSavedPacksCard() {
        FrameLayout content = findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        View first = content.getChildAt(0);
        if (!(first instanceof ScrollView)) return;

        ScrollView scroll = (ScrollView) first;
        if (scroll.getChildCount() == 0 || !(scroll.getChildAt(0) instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) scroll.getChildAt(0);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpHome(18), dpHome(16), dpHome(18), dpHome(16));
        card.setBackground(roundedHome(CARD, 20));
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dpHome(2));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(header, matchWrapHome());

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        header.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = textHome("Мои наборы", 17, TEXT, Typeface.BOLD);
        texts.addView(title, matchWrapHome());
        packsMeta = textHome("", 12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams metaParams = matchWrapHome();
        metaParams.topMargin = dpHome(3);
        texts.addView(packsMeta, metaParams);

        Button open = new Button(this);
        open.setText("Открыть");
        open.setAllCaps(false);
        open.setTextSize(14);
        open.setTextColor(Color.WHITE);
        open.setTypeface(Typeface.create("sans", Typeface.BOLD));
        open.setBackground(roundedHome(PRIMARY, 13));
        open.setOnClickListener(v -> startActivityForResult(
                new Intent(this, SavedPacksActivity.class), REQUEST_SAVED_PACKS));
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(dpHome(92), dpHome(46));
        openParams.leftMargin = dpHome(10);
        header.addView(open, openParams);

        TextView hint = textHome(
                "Повторно добавляйте наборы в WhatsApp, переименовывайте или удаляйте их.",
                12, MUTED, Typeface.NORMAL);
        hint.setLineSpacing(0, 1.06f);
        LinearLayout.LayoutParams hintParams = matchWrapHome();
        hintParams.topMargin = dpHome(8);
        card.addView(hint, hintParams);

        LinearLayout.LayoutParams cardParams = matchWrapHome();
        cardParams.topMargin = dpHome(12);
        int insertIndex = Math.min(1, root.getChildCount());
        root.addView(card, insertIndex, cardParams);
    }

    private void updateSavedPacksSummary() {
        if (packsMeta == null) return;
        List<PackStore.Pack> packs = PackStore.getPacks(this);
        if (packs.isEmpty()) {
            packsMeta.setText("Пока нет сохранённых наборов");
        } else {
            int animated = 0;
            for (PackStore.Pack pack : packs) {
                if (pack.animated) animated++;
            }
            int staticCount = packs.size() - animated;
            StringBuilder text = new StringBuilder().append(packs.size()).append(" сохранено");
            if (staticCount > 0) text.append(" · фото ").append(staticCount);
            if (animated > 0) text.append(" · анимация ").append(animated);
            packsMeta.setText(text.toString());
        }
    }

    private TextView textHome(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    private GradientDrawable roundedHome(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dpHome(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrapHome() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dpHome(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        selectionExecutor.shutdownNow();
        VideoTrimStore.clear();
        super.onDestroy();
    }
}
