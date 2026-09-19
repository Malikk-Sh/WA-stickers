package com.malikksh.wastickers;

import android.content.Context;

import java.io.File;

/** Read-only storage metrics used by Settings presentation. */
final class StorageStats {
    private StorageStats() {}

    static long conversionCacheBytes(Context context) {
        if (context == null) return 0L;
        File root = context.getCacheDir();
        File[] children = root == null ? null : root.listFiles();
        if (children == null) return 0L;
        long total = 0L;
        for (File child : children) {
            if (child != null && child.getName().startsWith("sticker_conversion_cache_v")) {
                total += sizeOf(child);
            }
        }
        return total;
    }

    private static long sizeOf(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return Math.max(0L, file.length());
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) total += sizeOf(child);
        }
        return total;
    }
}
