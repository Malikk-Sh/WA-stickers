package com.malikksh.wastickers;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Build-phase migration layer. AppShellActivity still owns the four-tab shell while this class
 * replaces only the Build tab presentation and delegates conversion work to MainActivity.
 */
public class BuildShellActivity extends AppShellActivity {
    private static final int MIN_STICKERS = 3;
    private static final int MAX_STICKERS = 30;
    private static final long REFRESH_INTERVAL_MS = 300L;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Set<Uri> readyUris = new HashSet<>();
    private final Set<Uri> skippedUris = new HashSet<>();
    private final Set<Uri> finalizedExcludedUris = new HashSet<>();
    private final Map<Uri, String> detailByUri = new HashMap<>();
    private final Map<Uri, Integer> percentByUri = new HashMap<>();
    private final Map<Uri, String> nameByUri = new HashMap<>();

    private BuildPanel buildPanel;
    private PreviewLoader buildPreviewLoader;
    private View buildScreen;
    private List<Uri> activeWork = new ArrayList<>();
    private List<Uri> deferredFailures;
    private boolean previousProcessing;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
            refreshBuildPanel();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildPreviewLoader = new PreviewLoader(this);
        try {
            installBuildPanel();
            refreshBuildPanel();
            refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not install redesigned Build panel: " + error);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (buildPanel != null) buildPanel.post(this::refreshBuildPanel);
    }

    @Override
    protected void onDestroy() {
        refreshHandler.removeCallbacksAndMessages(null);
        if (buildPreviewLoader != null) buildPreviewLoader.close();
        super.onDestroy();
    }

    private void installBuildPanel() {
        FrameLayout host = findViewById(R.id.app_screen_host);
        if (host == null || host.getChildCount() < 3) {
            throw new IllegalStateException("Build screen host is missing");
        }
        buildScreen = host.getChildAt(2);
        if (!(buildScreen instanceof ScrollView)) {
            throw new IllegalStateException("Unexpected Build screen root");
        }

        buildPanel = new BuildPanel(this, buildPreviewLoader, new BuildPanel.Host() {
            @Override
            public void onStart() {
                startBuildFromPanel();
            }

            @Override
            public void onCancel() {
                invokeMainMethod("cancelProcessing", new Class<?>[0]);
                refreshBuildPanel();
            }

            @Override
            public void onRetryAll() {
                retryAllFromPanel();
            }

            @Override
            public void onRetryItem(Uri uri) {
                retryItemFromPanel(uri);
            }

            @Override
            public void onSkipItem(Uri uri) {
                if (uri == null) return;
                skippedUris.add(uri);
                detailByUri.put(uri, "Пропущено пользователем");
                refreshBuildPanel();
            }

            @Override
            public void onFinalize() {
                PackBuildSession<Uri> session = buildSession();
                if (session == null || !session.canFinalize()) return;
                finalizedExcludedUris.clear();
                finalizedExcludedUris.addAll(session.failures());
                invokeMainMethod("finalizePendingPackAsync", new Class<?>[0]);
                refreshBuildPanel();
            }

            @Override
            public void onDiscard() {
                invokeMainMethod("discardPendingBuild", new Class<?>[0]);
                clearBuildPresentationState();
                refreshBuildPanel();
            }

            @Override
            public void onAddToWhatsApp() {
                invokeMainMethod("addCurrentPackToWhatsApp", new Class<?>[0]);
            }

            @Override
            public void onOpenPacks() {
                View packs = findViewById(R.id.nav_packs);
                if (packs != null) packs.performClick();
            }
        });

        ScrollView scroll = (ScrollView) buildScreen;
        scroll.removeAllViews();
        scroll.setFillViewport(true);
        scroll.addView(buildPanel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void startBuildFromPanel() {
        if (isProcessing()) return;
        List<Uri> selected = selectedUrisSnapshot();
        if (selected.size() < MIN_STICKERS || selected.size() > MAX_STICKERS) return;

        invokeMainMethod("invalidateCurrentPack", new Class<?>[0]);
        PackBuildSession<Uri> session = buildSession();
        if (session == null) return;

        boolean animated = animatedMode();
        String name = enteredPackName();
        if (name.isEmpty()) name = animated ? "Мои анимированные стикеры" : "Мои стикеры";
        Uri cover = coverUriSnapshot();
        if (cover == null || !selected.contains(cover)) cover = selected.get(0);
        String packId = (animated ? "animated_" : "pack_") + System.currentTimeMillis();
        File packDir = PackStore.getPackDir(this, packId);

        session.begin(packId, name, packDir, animated, cover);
        session.setAutoFinalizeAllowed(false);
        writeFieldQuietly(MainActivity.class, "diagnosticItemIndex", -1);
        writeFieldQuietly(MainActivity.class, "diagnosticItemUri", null);
        BugLogStore.reset();
        BugLogStore.appendApp("Starting redesigned Build flow. mode="
                + (animated ? "animated" : "static") + ", items=" + selected.size());

        readyUris.clear();
        skippedUris.clear();
        finalizedExcludedUris.clear();
        detailByUri.clear();
        percentByUri.clear();
        activeWork = new ArrayList<>(selected);
        deferredFailures = null;
        invokeMainMethod("startBatch", new Class<?>[]{List.class, boolean.class},
                new ArrayList<>(activeWork), false);
        refreshBuildPanel();
    }

    private void retryAllFromPanel() {
        PackBuildSession<Uri> session = buildSession();
        if (session == null || isProcessing()) return;
        List<Uri> failures = session.failures();
        List<Uri> work = new ArrayList<>();
        List<Uri> deferred = new ArrayList<>();
        for (Uri uri : failures) {
            if (skippedUris.contains(uri)) deferred.add(uri);
            else work.add(uri);
        }
        startRetry(work, deferred);
    }

    private void retryItemFromPanel(Uri uri) {
        if (uri == null || isProcessing()) return;
        PackBuildSession<Uri> session = buildSession();
        if (session == null || !session.failures().contains(uri)) return;

        skippedUris.remove(uri);
        List<Uri> deferred = new ArrayList<>();
        for (Uri failed : session.failures()) {
            if (!uri.equals(failed)) deferred.add(failed);
        }
        List<Uri> work = new ArrayList<>();
        work.add(uri);
        startRetry(work, deferred);
    }

    private void startRetry(List<Uri> work, List<Uri> deferred) {
        if (work == null || work.isEmpty()) return;
        PackBuildSession<Uri> session = buildSession();
        if (session == null || !session.isActive()) return;
        session.setAutoFinalizeAllowed(false);
        activeWork = new ArrayList<>(work);
        deferredFailures = new ArrayList<>(deferred == null ? new ArrayList<>() : deferred);
        for (Uri uri : work) {
            detailByUri.put(uri, "Подготовка к повторной обработке…");
            percentByUri.put(uri, 0);
        }
        invokeMainMethod("startBatch", new Class<?>[]{List.class, boolean.class},
                new ArrayList<>(work), true);
        refreshBuildPanel();
    }

    private void reconcileDeferredFailuresAfterBatch() {
        if (deferredFailures == null) return;
        PackBuildSession<Uri> session = buildSession();
        if (session == null || !session.isActive()) {
            deferredFailures = null;
            return;
        }
        LinkedHashSet<Uri> combined = new LinkedHashSet<>(session.failures());
        combined.addAll(deferredFailures);
        session.setFailures(new ArrayList<>(combined));
        deferredFailures = null;
        invokeMainMethod("updateUiState", new Class<?>[0]);
    }

    private void refreshBuildPanel() {
        if (buildPanel == null) return;

        boolean processing = isProcessing();
        updateProgressCacheFromLegacy();
        if (previousProcessing && !processing) {
            reconcileDeferredFailuresAfterBatch();
            updateProgressCacheFromLegacy();
        }
        previousProcessing = processing;

        // Build state survives tab switches; only rendering work is skipped while another tab is visible.
        if (buildScreen != null && !buildScreen.isShown() && !processing) return;
        buildPanel.render(buildSnapshot(processing));
    }

    private BuildPanel.Snapshot buildSnapshot(boolean processing) {
        List<Uri> selected = selectedUrisSnapshot();
        PackBuildSession<Uri> session = buildSession();
        PackStore.Pack currentPack = currentPack();
        boolean animated = animatedMode();

        boolean finalized = currentPack != null && currentPack.animated == animated;
        int total = finalized ? currentPack.stickerCount : selected.size();
        int success = finalized
                ? currentPack.stickerCount
                : (session != null && session.isActive() ? session.successCount() : 0);
        int failureCount = session != null && session.isActive() ? session.failureCount() : 0;

        BuildPanel.Phase phase;
        if (finalized) {
            phase = BuildPanel.Phase.FINALIZED;
        } else if (processing) {
            phase = session != null && session.isCancelRequested()
                    ? BuildPanel.Phase.STOPPING
                    : BuildPanel.Phase.PROCESSING;
        } else if (session != null && session.isActive()) {
            if (session.hasFailures()) phase = BuildPanel.Phase.PARTIAL;
            else if (session.canFinalize()) phase = BuildPanel.Phase.READY;
            else phase = BuildPanel.Phase.IDLE;
        } else {
            phase = BuildPanel.Phase.IDLE;
        }

        String name = finalized
                ? currentPack.name
                : (session != null && session.isActive() ? session.packName() : enteredPackName());
        Uri cover = coverUriSnapshot();
        int percent = overallPercent(selected, session, finalized);
        String stage = stageText(phase);
        boolean canStart = phase == BuildPanel.Phase.IDLE
                && selected.size() >= MIN_STICKERS && selected.size() <= MAX_STICKERS;
        boolean canFinalize = session != null && session.isActive() && session.canFinalize();
        boolean hasRetryableFailures = false;
        if (session != null && session.isActive()) {
            for (Uri failure : session.failures()) {
                if (!skippedUris.contains(failure)) {
                    hasRetryableFailures = true;
                    break;
                }
            }
        }

        List<BuildPanel.Item> items = buildItems(selected, session, finalized, processing);
        return new BuildPanel.Snapshot(
                phase,
                animated,
                name,
                cover,
                total,
                success,
                failureCount,
                percent,
                stage,
                canStart,
                canFinalize,
                hasRetryableFailures,
                items
        );
    }

    private List<BuildPanel.Item> buildItems(List<Uri> selected,
                                             PackBuildSession<Uri> session,
                                             boolean finalized,
                                             boolean processing) {
        List<BuildPanel.Item> result = new ArrayList<>();
        Set<Uri> failures = session != null && session.isActive()
                ? new HashSet<>(session.failures())
                : new HashSet<>();

        for (Uri uri : selected) {
            BuildPanel.ItemPhase phase = BuildPanel.ItemPhase.PENDING;
            int percent = percentByUri.containsKey(uri) ? percentByUri.get(uri) : 0;
            String detail = detailByUri.containsKey(uri) ? detailByUri.get(uri) : "";

            if (finalized) {
                if (finalizedExcludedUris.contains(uri)) {
                    phase = BuildPanel.ItemPhase.SKIPPED;
                    if (detail.isEmpty()) detail = "Не вошёл в готовый набор";
                } else {
                    phase = BuildPanel.ItemPhase.READY;
                    percent = 100;
                    if (detail.isEmpty()) detail = "Готово к использованию";
                }
            } else if (skippedUris.contains(uri) && failures.contains(uri)) {
                phase = BuildPanel.ItemPhase.SKIPPED;
                detail = "Пропущено пользователем";
            } else if (failures.contains(uri)) {
                phase = BuildPanel.ItemPhase.ERROR;
                if (detail.isEmpty()) detail = "Не удалось оптимизировать файл";
            } else if (readyUris.contains(uri)) {
                phase = BuildPanel.ItemPhase.READY;
                percent = 100;
            } else if (processing && activeWork.contains(uri) && percent > 0) {
                phase = BuildPanel.ItemPhase.PROCESSING;
            } else if (processing && activeWork.contains(uri)
                    && detail != null && !detail.isEmpty() && !detail.contains("В очереди")) {
                phase = BuildPanel.ItemPhase.PROCESSING;
            }

            result.add(new BuildPanel.Item(
                    uri,
                    displayName(uri, result.size() + 1),
                    phase,
                    percent,
                    detail
            ));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void updateProgressCacheFromLegacy() {
        Object labelsValue = readFieldQuietly(MainActivity.class, "fileProgressLabels");
        Object barsValue = readFieldQuietly(MainActivity.class, "fileProgressBars");
        Object namesValue = readFieldQuietly(MainActivity.class, "fileProgressNames");
        if (!(labelsValue instanceof List<?>) || !(barsValue instanceof List<?>)) return;

        List<?> labels = (List<?>) labelsValue;
        List<?> bars = (List<?>) barsValue;
        List<?> names = namesValue instanceof List<?> ? (List<?>) namesValue : new ArrayList<>();
        int count = Math.min(activeWork.size(), Math.min(labels.size(), bars.size()));
        for (int i = 0; i < count; i++) {
            Uri uri = activeWork.get(i);
            Object labelValue = labels.get(i);
            Object barValue = bars.get(i);
            if (!(labelValue instanceof TextView) || !(barValue instanceof ProgressBar)) continue;

            String raw = ((TextView) labelValue).getText().toString();
            int newline = raw.indexOf('\n');
            String detail = newline >= 0 && newline + 1 < raw.length()
                    ? raw.substring(newline + 1).trim()
                    : raw;
            int percent = ((ProgressBar) barValue).getProgress();
            percentByUri.put(uri, percent);
            detailByUri.put(uri, detail);
            if (i < names.size() && names.get(i) instanceof String) {
                nameByUri.put(uri, (String) names.get(i));
            }
            if (raw.contains("· Готово")) {
                readyUris.add(uri);
                percentByUri.put(uri, 100);
            } else if (raw.contains("· Ошибка")) {
                readyUris.remove(uri);
            }
        }
    }

    private int overallPercent(List<Uri> selected,
                               PackBuildSession<Uri> session,
                               boolean finalized) {
        if (finalized) return 100;
        if (selected.isEmpty()) return 0;
        int successful = session != null && session.isActive() ? session.successCount() : 0;
        int partial = 0;
        for (Uri uri : activeWork) {
            if (!readyUris.contains(uri)) partial = Math.max(partial,
                    percentByUri.containsKey(uri) ? percentByUri.get(uri) : 0);
        }
        return Math.max(0, Math.min(100,
                (successful * 100 + partial) / Math.max(1, selected.size())));
    }

    private String stageText(BuildPanel.Phase phase) {
        if (phase == BuildPanel.Phase.PROCESSING) {
            Object progress = readFieldQuietly(MainActivity.class, "progressText");
            if (progress instanceof TextView) {
                String value = ((TextView) progress).getText().toString().trim();
                if (!value.isEmpty()) return value;
            }
            return "Оптимизация и подготовка файлов…";
        }
        if (phase == BuildPanel.Phase.STOPPING) return "Останавливаю…";
        if (phase == BuildPanel.Phase.PARTIAL) return "Есть файлы, требующие внимания";
        if (phase == BuildPanel.Phase.READY) return "Все файлы обработаны";
        if (phase == BuildPanel.Phase.FINALIZED) return "Набор сохранён";
        return "Готово к запуску";
    }

    @SuppressWarnings("unchecked")
    private PackBuildSession<Uri> buildSession() {
        Object value = readFieldQuietly(MainActivity.class, "buildSession");
        return value instanceof PackBuildSession<?> ? (PackBuildSession<Uri>) value : null;
    }

    private PackStore.Pack currentPack() {
        Object value = readFieldQuietly(MainActivity.class, "currentPack");
        return value instanceof PackStore.Pack ? (PackStore.Pack) value : null;
    }

    private boolean isProcessing() {
        Object value = readFieldQuietly(MainActivity.class, "processing");
        return value instanceof Boolean && (Boolean) value;
    }

    private boolean animatedMode() {
        Object value = readFieldQuietly(MainActivity.class, "animatedMode");
        return value instanceof Boolean && (Boolean) value;
    }

    private String enteredPackName() {
        Object value = readFieldQuietly(MainActivity.class, "packName");
        if (!(value instanceof EditText)) return "";
        return ((EditText) value).getText().toString().trim();
    }

    private Uri coverUriSnapshot() {
        Object value = readFieldQuietly(MainActivity.class, "coverUri");
        return value instanceof Uri ? (Uri) value : null;
    }

    @SuppressWarnings("unchecked")
    private List<Uri> selectedUrisSnapshot() {
        Object value = readFieldQuietly(MainActivity.class, "selectedUris");
        if (!(value instanceof List<?>)) return new ArrayList<>();
        List<Uri> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Uri) result.add((Uri) item);
        }
        return result;
    }

    private String displayName(Uri uri, int fallback) {
        if (uri == null) return "Файл " + fallback;
        String cached = nameByUri.get(uri);
        if (cached != null && !cached.trim().isEmpty()) return cached;
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && !cursor.isNull(index)) {
                    String name = cursor.getString(index);
                    if (name != null && !name.trim().isEmpty()) {
                        nameByUri.put(uri, name);
                        return name;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        String last = uri.getLastPathSegment();
        String result = last == null || last.trim().isEmpty() ? "Файл " + fallback : last;
        nameByUri.put(uri, result);
        return result;
    }

    private Object invokeMainMethod(String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = MainActivity.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(this, args);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not invoke " + name + " from Build shell: " + error);
            return null;
        }
    }

    private Object readFieldQuietly(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(this);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void writeFieldQuietly(Class<?> owner, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(this, value);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not update " + name + " from Build shell: " + error);
        }
    }

    private void clearBuildPresentationState() {
        readyUris.clear();
        skippedUris.clear();
        finalizedExcludedUris.clear();
        detailByUri.clear();
        percentByUri.clear();
        activeWork.clear();
        deferredFailures = null;
    }

    // Instrumentation hook: presentation only, no business mutation.
    void refreshBuildPanelForTest() {
        refreshBuildPanel();
    }
}
