package com.malikksh.wastickers;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MediaPreflightAnalyzer {
    static final class Result {
        final String key;
        final Uri uri;
        final String displayName;
        final String mime;
        final long sizeBytes;
        final int width;
        final int height;
        final long durationMs;
        final boolean video;
        final boolean readable;
        final MediaPreflightPolicy.Assessment assessment;

        Result(String key, Uri uri, String displayName, String mime, long sizeBytes,
               int width, int height, long durationMs, boolean video, boolean readable) {
            this.key = key;
            this.uri = uri;
            this.displayName = displayName;
            this.mime = mime;
            this.sizeBytes = sizeBytes;
            this.width = width;
            this.height = height;
            this.durationMs = durationMs;
            this.video = video;
            this.readable = readable;
            this.assessment = MediaPreflightPolicy.assess(
                    readable, video, sizeBytes, width, height, durationMs);
        }

        String kindLabel() {
            if (video) return "Видео";
            if (mime != null && mime.toLowerCase(Locale.US).contains("gif")) return "GIF";
            if (mime != null && mime.toLowerCase(Locale.US).contains("webp")) return "WebP";
            return "Изображение";
        }

        String sizeLabel() {
            if (sizeBytes < 0) return "размер неизвестен";
            if (sizeBytes < 1024L) return sizeBytes + " B";
            if (sizeBytes < 1024L * 1024L) return Math.round(sizeBytes / 1024f) + " KB";
            return String.format(Locale.US, "%.1f MB", sizeBytes / (1024f * 1024f));
        }

        String dimensionsLabel() {
            if (width <= 0 || height <= 0) return "разрешение неизвестно";
            return width + "×" + height;
        }
    }

    private final Context appContext;
    private final Map<String, Result> cache = new HashMap<>();

    MediaPreflightAnalyzer(Context context) {
        appContext = context.getApplicationContext();
    }

    synchronized List<Result> analyze(List<Uri> uris) {
        List<Result> results = new ArrayList<>();
        if (uris == null) return results;
        for (Uri uri : uris) {
            if (uri == null) continue;
            String key = uri.toString();
            Result cached = cache.get(key);
            if (cached == null) {
                cached = inspect(uri);
                cache.put(key, cached);
            }
            results.add(cached);
        }
        return results;
    }

    private Result inspect(Uri uri) {
        ContentResolver resolver = appContext.getContentResolver();
        String displayName = uri.getLastPathSegment() == null ? "Файл" : uri.getLastPathSegment();
        long sizeBytes = "file".equals(uri.getScheme()) && uri.getPath() != null
                ? new java.io.File(uri.getPath()).length() : -1L;
        String mime = null;
        try {
            mime = resolver.getType(uri);
        } catch (Throwable ignored) {
        }

        try (Cursor cursor = resolver.query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    String value = cursor.getString(nameIndex);
                    if (value != null && !value.trim().isEmpty()) displayName = value;
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex);
            }
        } catch (Throwable ignored) {
        }

        if (mime == null && displayName != null) {
            int dot = displayName.lastIndexOf('.');
            if (dot >= 0) mime = android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(displayName.substring(dot + 1).toLowerCase(Locale.ROOT));
        }
        boolean video = isVideo(mime, displayName);
        int width = -1;
        int height = -1;
        long durationMs = -1L;
        boolean readable = false;

        if (video) {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(appContext, uri);
                width = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                height = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                durationMs = parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                readable = width > 0 && height > 0;
            } catch (Throwable ignored) {
            } finally {
                try {
                    retriever.release();
                } catch (Throwable ignored) {
                }
            }
        } else {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream input = resolver.openInputStream(uri)) {
                if (input != null) {
                    BitmapFactory.decodeStream(input, null, options);
                    width = options.outWidth;
                    height = options.outHeight;
                    readable = width > 0 && height > 0;
                }
            } catch (Throwable ignored) {
            }
        }

        return new Result(uri.toString(), uri, displayName, mime, sizeBytes,
                width, height, durationMs, video, readable);
    }

    private static boolean isVideo(String mime, String displayName) {
        if (mime != null && mime.toLowerCase(Locale.US).startsWith("video/")) return true;
        String name = displayName == null ? "" : displayName.toLowerCase(Locale.US);
        return name.endsWith(".mp4")
                || name.endsWith(".webm")
                || name.endsWith(".mov")
                || name.endsWith(".mkv")
                || name.endsWith(".m4v")
                || name.endsWith(".avi");
    }

    private static int parseInt(String value) {
        try {
            return value == null ? -1 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static long parseLong(String value) {
        try {
            return value == null ? -1L : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }
}
