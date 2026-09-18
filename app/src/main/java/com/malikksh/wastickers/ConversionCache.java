package com.malikksh.wastickers;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Properties;

final class ConversionCache {
    static final long MAX_CACHE_BYTES = 64L * 1024L * 1024L;
    static final int CACHE_VERSION = 1;

    static final class Hit {
        final int fps;
        final int quality;
        final long bytes;

        Hit(int fps, int quality, long bytes) {
            this.fps = fps;
            this.quality = quality;
            this.bytes = bytes;
        }
    }

    private static final class SourceIdentity {
        final String displayName;
        final String mime;
        final long size;
        final String sampleDigest;

        SourceIdentity(String displayName, String mime, long size, String sampleDigest) {
            this.displayName = displayName;
            this.mime = mime;
            this.size = size;
            this.sampleDigest = sampleDigest;
        }
    }

    private ConversionCache() {}

    static Hit restore(Context context, Uri sourceUri, boolean animated,
                       long trimStartMs, File target, long maxBytes) {
        if (context == null || sourceUri == null || target == null) return null;
        try {
            String key = keyFor(context, sourceUri, animated, trimStartMs);
            File dir = cacheDir(context);
            File cached = new File(dir, key + ".webp");
            File metadata = new File(dir, key + ".properties");
            if (!cached.isFile() || !metadata.isFile()
                    || cached.length() <= 0 || cached.length() > maxBytes) {
                invalidate(cached, metadata);
                return null;
            }

            Properties props = new Properties();
            try (FileInputStream input = new FileInputStream(metadata)) {
                props.load(input);
            }
            int fps = parseInt(props.getProperty("fps"), 0);
            int quality = parseInt(props.getProperty("quality"), 0);
            long expectedBytes = parseLong(props.getProperty("bytes"), cached.length());
            if (expectedBytes != cached.length()) {
                invalidate(cached, metadata);
                return null;
            }

            copyFile(cached, target);
            long now = System.currentTimeMillis();
            //noinspection ResultOfMethodCallIgnored
            cached.setLastModified(now);
            //noinspection ResultOfMethodCallIgnored
            metadata.setLastModified(now);
            return new Hit(fps, quality, target.length());
        } catch (Throwable error) {
            BugLogStore.appendApp("Conversion cache restore skipped: " + error);
            return null;
        }
    }

    static void store(Context context, Uri sourceUri, boolean animated,
                      long trimStartMs, File converted, int fps, int quality,
                      long maxBytes) {
        if (context == null || sourceUri == null || converted == null
                || !converted.isFile() || converted.length() <= 0
                || converted.length() > maxBytes) {
            return;
        }
        try {
            String key = keyFor(context, sourceUri, animated, trimStartMs);
            File dir = cacheDir(context);
            if (!dir.mkdirs() && !dir.isDirectory()) return;

            File cached = new File(dir, key + ".webp");
            File metadata = new File(dir, key + ".properties");
            File temp = new File(dir, key + ".tmp");
            copyFile(converted, temp);
            if (cached.exists() && !cached.delete()) {
                // Keep going: copyFile below can still replace it.
            }
            if (!temp.renameTo(cached)) {
                copyFile(temp, cached);
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }

            Properties props = new Properties();
            props.setProperty("version", String.valueOf(CACHE_VERSION));
            props.setProperty("animated", String.valueOf(animated));
            props.setProperty("trimStartMs", String.valueOf(Math.max(0L, trimStartMs)));
            props.setProperty("fps", String.valueOf(Math.max(0, fps)));
            props.setProperty("quality", String.valueOf(Math.max(0, quality)));
            props.setProperty("bytes", String.valueOf(cached.length()));
            try (FileOutputStream output = new FileOutputStream(metadata, false)) {
                props.store(output, "WA Stickers conversion cache");
            }
            long now = System.currentTimeMillis();
            //noinspection ResultOfMethodCallIgnored
            cached.setLastModified(now);
            //noinspection ResultOfMethodCallIgnored
            metadata.setLastModified(now);
            prune(dir);
        } catch (Throwable error) {
            BugLogStore.appendApp("Conversion cache store skipped: " + error);
        }
    }

    private static String keyFor(Context context, Uri sourceUri,
                                 boolean animated, long trimStartMs) {
        SourceIdentity source = sourceIdentity(context, sourceUri);
        return ConversionCacheKey.build(
                sourceUri.toString(),
                animated,
                trimStartMs,
                source.size,
                source.mime,
                source.displayName,
                source.sampleDigest,
                CACHE_VERSION
        );
    }

    private static SourceIdentity sourceIdentity(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        String name = "";
        String mime = "";
        long size = -1L;
        try {
            String value = resolver.getType(uri);
            if (value != null) mime = value;
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
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex);
            }
        } catch (Throwable ignored) {
        }
        return new SourceIdentity(name, mime, size, sampleDigest(resolver, uri));
    }

    private static String sampleDigest(ContentResolver resolver, Uri uri) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int remaining = 64 * 1024;
            try (InputStream input = resolver.openInputStream(uri)) {
                if (input == null) return "";
                while (remaining > 0) {
                    int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                    if (read < 0) break;
                    digest.update(buffer, 0, read);
                    remaining -= read;
                }
            }
            byte[] bytes = digest.digest();
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) out.append(String.format(Locale.US, "%02x", item & 0xff));
            return out.toString();
        } catch (IOException | NoSuchAlgorithmException ignored) {
            return "";
        }
    }

    private static File cacheDir(Context context) {
        return new File(context.getCacheDir(), "sticker_conversion_cache_v" + CACHE_VERSION);
    }

    private static void prune(File dir) {
        File[] files = dir.listFiles((parent, name) -> name.endsWith(".webp"));
        if (files == null || files.length == 0) return;
        long total = 0L;
        for (File file : files) total += Math.max(0L, file.length());
        if (total <= MAX_CACHE_BYTES) return;

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            if (total <= MAX_CACHE_BYTES) break;
            long length = Math.max(0L, file.length());
            String name = file.getName();
            String stem = name.substring(0, name.length() - ".webp".length());
            File metadata = new File(dir, stem + ".properties");
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            //noinspection ResultOfMethodCallIgnored
            metadata.delete();
            total -= length;
        }
    }

    private static void invalidate(File data, File metadata) {
        if (data != null) {
            //noinspection ResultOfMethodCallIgnored
            data.delete();
        }
        if (metadata != null) {
            //noinspection ResultOfMethodCallIgnored
            metadata.delete();
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Не удалось создать папку кэша");
        }
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) output.write(buffer, 0, read);
            }
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
