package com.malikksh.wastickers;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;

import java.util.List;
import java.util.Locale;

/**
 * Lightweight content-aware source classification used by the unified editor before the full
 * export planner is introduced. It never infers animation from a filename extension.
 */
final class SourceAnimationDetector {
    enum Kind {
        STATIC,
        ANIMATED,
        UNKNOWN
    }

    enum ProjectKind {
        EMPTY,
        STATIC_ONLY,
        ANIMATED_ONLY,
        MIXED,
        UNKNOWN
    }

    private SourceAnimationDetector() {}

    static ProjectKind classifyProject(Context context, List<Uri> items) {
        if (items == null || items.isEmpty()) return ProjectKind.EMPTY;
        boolean hasStatic = false;
        boolean hasAnimated = false;
        boolean hasUnknown = false;
        for (Uri uri : items) {
            Kind kind = classify(context, uri);
            if (kind == Kind.STATIC) hasStatic = true;
            else if (kind == Kind.ANIMATED) hasAnimated = true;
            else hasUnknown = true;
            if (hasStatic && hasAnimated) return ProjectKind.MIXED;
        }
        if (hasUnknown) return ProjectKind.UNKNOWN;
        if (hasAnimated) return ProjectKind.ANIMATED_ONLY;
        return ProjectKind.STATIC_ONLY;
    }

    static Kind classify(Context context, Uri uri) {
        if (context == null || uri == null) return Kind.UNKNOWN;
        ContentResolver resolver = context.getContentResolver();
        String mime = null;
        try {
            mime = resolver.getType(uri);
        } catch (Throwable ignored) {
        }
        String normalized = mime == null ? "" : mime.toLowerCase(Locale.US);
        if (normalized.startsWith("video/")) return Kind.ANIMATED;
        if (normalized.startsWith("image/")
                && !normalized.contains("gif")
                && !normalized.contains("webp")) {
            return Kind.STATIC;
        }
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(resolver, uri);
                Drawable drawable = ImageDecoder.decodeDrawable(source);
                return drawable instanceof AnimatedImageDrawable ? Kind.ANIMATED : Kind.STATIC;
            } catch (Throwable ignored) {
            }
        }
        return Kind.UNKNOWN;
    }
}
