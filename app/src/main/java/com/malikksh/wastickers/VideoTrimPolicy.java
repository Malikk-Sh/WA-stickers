package com.malikksh.wastickers;

import java.util.Locale;

final class VideoTrimPolicy {
    static final long CLIP_DURATION_MS = 10_000L;
    static final long SEEK_STEP_MS = 100L;

    private VideoTrimPolicy() {}

    static long maxStartMs(long durationMs) {
        return Math.max(0L, durationMs - CLIP_DURATION_MS);
    }

    static long clampStartMs(long durationMs, long requestedStartMs) {
        long max = maxStartMs(durationMs);
        return Math.max(0L, Math.min(requestedStartMs, max));
    }

    static long clipDurationMs(long durationMs, long startMs) {
        if (durationMs <= 0L) return CLIP_DURATION_MS;
        long safeStart = clampStartMs(durationMs, startMs);
        return Math.max(1L, Math.min(CLIP_DURATION_MS, durationMs - safeStart));
    }

    static int seekBarMax(long durationMs) {
        return (int) Math.min(Integer.MAX_VALUE, maxStartMs(durationMs) / SEEK_STEP_MS);
    }

    static int seekBarProgress(long durationMs, long startMs) {
        return (int) Math.min(seekBarMax(durationMs),
                clampStartMs(durationMs, startMs) / SEEK_STEP_MS);
    }

    static long startFromSeekBar(long durationMs, int progress) {
        return clampStartMs(durationMs, Math.max(0L, progress) * SEEK_STEP_MS);
    }

    static String formatTime(long timeMs) {
        long safe = Math.max(0L, timeMs);
        long totalSeconds = safe / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        long tenths = (safe % 1000L) / 100L;
        return String.format(Locale.US, "%d:%02d.%d", minutes, seconds, tenths);
    }
}
