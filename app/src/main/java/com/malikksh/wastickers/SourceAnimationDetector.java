package com.malikksh.wastickers;

import android.content.ContentResolver;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Android URI adapter around MediaStructureDetector plus real video metadata inspection. */
final class SourceAnimationDetector {
    private final Context context;
    private final Map<String, PackCompatibilityPlanner.SourceKind> cache = new ConcurrentHashMap<>();

    SourceAnimationDetector(Context context) {
        this.context = context.getApplicationContext();
    }

    PackCompatibilityPlanner.SourceKind classify(Uri uri) {
        if (uri == null) return PackCompatibilityPlanner.SourceKind.UNKNOWN;
        String key = uri.toString();
        PackCompatibilityPlanner.SourceKind cached = cache.get(key);
        if (cached != null) return cached;
        PackCompatibilityPlanner.SourceKind detected = detect(uri);
        cache.put(key, detected);
        return detected;
    }

    void invalidate(Uri uri) {
        if (uri != null) cache.remove(uri.toString());
    }

    void clear() {
        cache.clear();
    }

    private PackCompatibilityPlanner.SourceKind detect(Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        String mime = null;
        try {
            mime = resolver.getType(uri);
        } catch (Throwable ignored) {
        }
        String normalized = mime == null ? "" : mime.toLowerCase(java.util.Locale.US);
        if (normalized.startsWith("video/")) return PackCompatibilityPlanner.SourceKind.ANIMATED;

        try (InputStream input = resolver.openInputStream(uri)) {
            PackCompatibilityPlanner.SourceKind structure = MediaStructureDetector.detect(input);
            if (structure != PackCompatibilityPlanner.SourceKind.UNKNOWN) return structure;
        } catch (Throwable ignored) {
        }

        if (normalized.startsWith("image/")
                && !normalized.contains("gif")
                && !normalized.contains("webp")) {
            return PackCompatibilityPlanner.SourceKind.STATIC;
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (positive(width) && positive(height) && positive(duration)) {
                return PackCompatibilityPlanner.SourceKind.ANIMATED;
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {
            }
        }
        return PackCompatibilityPlanner.SourceKind.UNKNOWN;
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
