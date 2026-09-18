package com.malikksh.wastickers;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VideoTrimStore {
    static final class Entry {
        final String key;
        final Uri uri;
        final String displayName;
        final long durationMs;
        final long startOffsetMs;

        Entry(String key, Uri uri, String displayName, long durationMs, long startOffsetMs) {
            this.key = key;
            this.uri = uri;
            this.displayName = displayName;
            this.durationMs = durationMs;
            this.startOffsetMs = startOffsetMs;
        }
    }

    private static final class MutableEntry {
        final String key;
        final Uri uri;
        final String displayName;
        final long durationMs;
        long startOffsetMs;

        MutableEntry(String key, Uri uri, String displayName, long durationMs, long startOffsetMs) {
            this.key = key;
            this.uri = uri;
            this.displayName = displayName;
            this.durationMs = durationMs;
            this.startOffsetMs = startOffsetMs;
        }

        Entry snapshot() {
            return new Entry(key, uri, displayName, durationMs, startOffsetMs);
        }
    }

    private static final Map<String, MutableEntry> ENTRIES = new LinkedHashMap<>();

    private VideoTrimStore() {}

    static synchronized void clear() {
        ENTRIES.clear();
    }

    static List<String> prepare(Context context, List<Uri> uris) {
        List<String> adjustable = new ArrayList<>();
        if (context == null || uris == null) return adjustable;

        for (Uri uri : uris) {
            if (uri == null || !isVideo(context, uri)) continue;
            long durationMs = readDurationMs(context, uri);
            if (durationMs <= VideoTrimPolicy.CLIP_DURATION_MS) continue;

            String key = uri.toString();
            String displayName = readDisplayName(context, uri);
            synchronized (VideoTrimStore.class) {
                MutableEntry existing = ENTRIES.get(key);
                long start = existing == null ? 0L : existing.startOffsetMs;
                ENTRIES.put(key, new MutableEntry(
                        key,
                        uri,
                        displayName,
                        durationMs,
                        VideoTrimPolicy.clampStartMs(durationMs, start)
                ));
            }
            adjustable.add(key);
        }
        return adjustable;
    }

    static synchronized List<Entry> getEntries(List<String> keys) {
        List<Entry> result = new ArrayList<>();
        if (keys == null) return result;
        for (String key : keys) {
            MutableEntry item = ENTRIES.get(key);
            if (item != null) result.add(item.snapshot());
        }
        return result;
    }

    static synchronized long getStartOffsetMs(Uri uri) {
        if (uri == null) return 0L;
        MutableEntry entry = ENTRIES.get(uri.toString());
        if (entry == null) return 0L;
        return VideoTrimPolicy.clampStartMs(entry.durationMs, entry.startOffsetMs);
    }

    static synchronized void setStartOffsetMs(String key, long startOffsetMs) {
        MutableEntry entry = ENTRIES.get(key);
        if (entry == null) return;
        entry.startOffsetMs = VideoTrimPolicy.clampStartMs(entry.durationMs, startOffsetMs);
    }

    private static boolean isVideo(Context context, Uri uri) {
        String mime = null;
        try {
            mime = context.getContentResolver().getType(uri);
        } catch (Throwable ignored) {
        }
        if (mime != null && mime.toLowerCase().startsWith("video/")) return true;

        String name = readDisplayName(context, uri).toLowerCase();
        return name.endsWith(".mp4")
                || name.endsWith(".webm")
                || name.endsWith(".mov")
                || name.endsWith(".mkv")
                || name.endsWith(".m4v")
                || name.endsWith(".avi");
    }

    private static long readDurationMs(Context context, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (raw == null) return -1L;
            return Long.parseLong(raw);
        } catch (Throwable ignored) {
            return -1L;
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String readDisplayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && !cursor.isNull(index)) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) return value;
                }
            }
        } catch (Throwable ignored) {
        }
        return "Видео";
    }
}
