package com.malikksh.wastickers;

import com.arthenica.ffmpegkit.FFmpegKitConfig;

final class BugLogStore {
    private static final int MAX_CHARS = 120_000;
    private static final StringBuilder LOG = new StringBuilder();
    private static boolean installed;

    private BugLogStore() {}

    static synchronized void install() {
        if (installed) return;
        FFmpegKitConfig.enableLogCallback(log -> {
            if (log != null) append("FFmpeg", log.getMessage());
        });
        installed = true;
    }

    static synchronized void reset() {
        LOG.setLength(0);
    }

    static void appendApp(String message) {
        append("APP", message);
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
