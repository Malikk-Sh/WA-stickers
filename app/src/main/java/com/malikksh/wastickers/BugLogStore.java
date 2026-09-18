package com.malikksh.wastickers;

final class BugLogStore {
    private static final int MAX_CHARS = 120_000;
    private static final StringBuilder LOG = new StringBuilder();

    private BugLogStore() {}

    /**
     * Kept for source compatibility with the diagnostics UI.
     *
     * IMPORTANT: this method must not touch FFmpegKit. Initializing FFmpegKit from
     * Activity.onCreate() can load native libraries before the UI exists and can
     * crash the whole app on a device with an ABI/native-library problem.
     * FFmpeg logs are appended lazily by AnimatedStickerConverter only while an
     * animated conversion is actually running.
     */
    static synchronized void install() {
        append("APP", "Bug logging ready; FFmpeg initialization deferred until conversion.");
    }

    static synchronized void reset() {
        LOG.setLength(0);
    }

    static void appendApp(String message) {
        append("APP", message);
    }

    static void appendFfmpeg(String message) {
        append("FFmpeg", message);
    }

    static synchronized String snapshot() {
        return LOG.toString();
    }

    private static synchronized void append(String source, String message) {
        if (message == null || message.isEmpty()) return;
        LOG.append('[').append(source).append("] ").append(message);
        if (!message.endsWith("\n")) LOG.append('\n');

        if (LOG.length() > MAX_CHARS) {
            int remove = LOG.length() - MAX_CHARS;
            LOG.delete(0, remove);
        }
    }
}
