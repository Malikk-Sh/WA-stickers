package com.malikksh.wastickers;

import android.content.ContentResolver;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Android URI adapter around the merged content inspector plus real video metadata inspection. */
final class SourceAnimationDetector {
    private static final int MAX_INSPECTION_BYTES = 16 * 1024 * 1024;

    private final Context context;
    private final Map<String, MediaAnimationInspector.AnimationKind> cache = new ConcurrentHashMap<>();

    SourceAnimationDetector(Context context) {
        this.context = context.getApplicationContext();
    }

    MediaAnimationInspector.AnimationKind classify(Uri uri) {
        if (uri == null) return MediaAnimationInspector.AnimationKind.UNKNOWN;
        String key = uri.toString();
        MediaAnimationInspector.AnimationKind cached = cache.get(key);
        if (cached != null) return cached;
        MediaAnimationInspector.AnimationKind detected = detect(uri);
        cache.put(key, detected);
        return detected;
    }

    void invalidate(Uri uri) {
        if (uri != null) cache.remove(uri.toString());
    }

    void clear() {
        cache.clear();
    }

    private MediaAnimationInspector.AnimationKind detect(Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        String mime = null;
        try {
            mime = resolver.getType(uri);
        } catch (Throwable ignored) {
        }
        String normalized = mime == null ? "" : mime.toLowerCase(java.util.Locale.US);
        if (normalized.startsWith("video/")) {
            return MediaAnimationInspector.AnimationKind.ANIMATED;
        }

        try (InputStream input = resolver.openInputStream(uri)) {
            MediaAnimationInspector.AnimationKind content = inspectContent(mime, input);
            if (content != MediaAnimationInspector.AnimationKind.UNKNOWN) return content;
        } catch (Throwable ignored) {
        }

        // A mislabeled video still gets a content/metadata chance before being rejected.
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (positive(width) && positive(height) && positive(duration)) {
                return MediaAnimationInspector.AnimationKind.ANIMATED;
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {
            }
        }
        return MediaAnimationInspector.AnimationKind.UNKNOWN;
    }

    private MediaAnimationInspector.AnimationKind inspectContent(String mime, InputStream input)
            throws java.io.IOException {
        if (input == null) return MediaAnimationInspector.AnimationKind.UNKNOWN;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            total += read;
            if (total > MAX_INSPECTION_BYTES) {
                BugLogStore.appendApp("Media structure inspection skipped: source exceeds 16 MB");
                return MediaAnimationInspector.AnimationKind.UNKNOWN;
            }
            bytes.write(buffer, 0, read);
            if (total >= 12 && isPngOrJpeg(bytes.toByteArray())) {
                return MediaAnimationInspector.inspect(mime, bytes.toByteArray());
            }
        }
        return MediaAnimationInspector.inspect(mime, bytes.toByteArray());
    }

    private boolean isPngOrJpeg(byte[] data) {
        if (data.length >= 8
                && (data[0] & 0xff) == 0x89
                && data[1] == 0x50
                && data[2] == 0x4e
                && data[3] == 0x47
                && data[4] == 0x0d
                && data[5] == 0x0a
                && data[6] == 0x1a
                && data[7] == 0x0a) {
            return true;
        }
        return data.length >= 3
                && (data[0] & 0xff) == 0xff
                && (data[1] & 0xff) == 0xd8
                && (data[2] & 0xff) == 0xff;
    }

    private boolean positive(String value) {
        if (value == null) return false;
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
