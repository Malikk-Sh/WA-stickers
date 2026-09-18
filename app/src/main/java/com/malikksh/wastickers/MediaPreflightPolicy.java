package com.malikksh.wastickers;

import java.util.Locale;

final class MediaPreflightPolicy {
    static final long LONG_VIDEO_MS = 10_000L;
    static final long LARGE_VIDEO_BYTES = 100L * 1024L * 1024L;
    static final long LARGE_IMAGE_BYTES = 30L * 1024L * 1024L;
    static final int VERY_LARGE_SIDE = 4096;

    enum Severity {
        OK,
        INFO,
        WARNING,
        ERROR
    }

    static final class Assessment {
        final Severity severity;
        final String message;

        Assessment(Severity severity, String message) {
            this.severity = severity;
            this.message = message;
        }
    }

    private MediaPreflightPolicy() {}

    static Assessment assess(boolean readable, boolean video, long sizeBytes,
                             int width, int height, long durationMs) {
        if (!readable) {
            return new Assessment(Severity.ERROR, "Файл не удалось прочитать до начала обработки");
        }
        if (video && durationMs > LONG_VIDEO_MS) {
            return new Assessment(Severity.INFO, "Видео длиннее 10 секунд — будет использован выбранный фрагмент");
        }
        long largeThreshold = video ? LARGE_VIDEO_BYTES : LARGE_IMAGE_BYTES;
        if (sizeBytes > largeThreshold) {
            return new Assessment(Severity.WARNING, "Крупный исходник — обработка может занять больше времени");
        }
        if (Math.max(width, height) > VERY_LARGE_SIDE) {
            return new Assessment(Severity.INFO, "Высокое разрешение будет уменьшено до формата стикера");
        }
        return new Assessment(Severity.OK, "Готов к обработке");
    }

    static String formatDuration(long durationMs) {
        if (durationMs < 0) return "—";
        return String.format(Locale.US, "%.1f сек", durationMs / 1000.0);
    }

    static String formatTrimWindow(long startOffsetMs, long durationMs) {
        if (durationMs <= LONG_VIDEO_MS) return "";
        long safeStart = Math.max(0L,
                Math.min(startOffsetMs, Math.max(0L, durationMs - LONG_VIDEO_MS)));
        double start = safeStart / 1000.0;
        double end = Math.min(durationMs, safeStart + LONG_VIDEO_MS) / 1000.0;
        return String.format(Locale.US, "Фрагмент %.1f–%.1f сек", start, end);
    }
}
