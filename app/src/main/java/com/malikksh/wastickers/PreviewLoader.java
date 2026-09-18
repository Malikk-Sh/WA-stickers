package com.malikksh.wastickers;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.LruCache;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PreviewLoader {
    interface Callback {
        void onLoaded(String requestKey, Bitmap bitmap);
    }

    private static final int MAX_PREVIEW_SIDE = 360;
    private static final int CACHE_SIZE_KB = 8 * 1024;

    private final Context context;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(CACHE_SIZE_KB) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return Math.max(1, bitmap.getByteCount() / 1024);
        }
    };
    private final Map<String, List<Callback>> pending = new HashMap<>();
    private volatile boolean closed;

    PreviewLoader(Context context) {
        this.context = context;
    }

    String requestKey(Uri uri) {
        long startOffsetMs = VideoTrimStore.getStartOffsetMs(uri);
        return PreviewRequestKey.create(uri == null ? null : uri.toString(), startOffsetMs);
    }

    void load(Uri uri, Callback callback) {
        if (uri == null || callback == null || closed) return;

        long startOffsetMs = VideoTrimStore.getStartOffsetMs(uri);
        String key = PreviewRequestKey.create(uri.toString(), startOffsetMs);
        Bitmap cached = cache.get(key);
        if (cached != null) {
            callback.onLoaded(key, cached);
            return;
        }

        synchronized (pending) {
            cached = cache.get(key);
            if (cached != null) {
                callback.onLoaded(key, cached);
                return;
            }

            List<Callback> callbacks = pending.get(key);
            if (callbacks != null) {
                callbacks.add(callback);
                return;
            }

            callbacks = new ArrayList<>();
            callbacks.add(callback);
            pending.put(key, callbacks);
        }

        executor.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = decode(uri, startOffsetMs);
            } catch (Throwable ignored) {
            }
            complete(key, bitmap);
        });
    }

    private void complete(String key, Bitmap bitmap) {
        if (!closed && bitmap != null) cache.put(key, bitmap);

        List<Callback> callbacks;
        synchronized (pending) {
            callbacks = pending.remove(key);
        }
        if (closed || callbacks == null) return;
        for (Callback callback : callbacks) {
            callback.onLoaded(key, bitmap);
        }
    }

    private Bitmap decode(Uri uri, long startOffsetMs) throws IOException {
        Bitmap image = decodeSampled(uri, MAX_PREVIEW_SIDE);
        if (image != null) return image;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            Bitmap frame = retriever.getFrameAtTime(
                    Math.max(0L, startOffsetMs) * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            );
            return scaleDown(frame, MAX_PREVIEW_SIDE);
        } catch (Throwable ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private Bitmap decodeSampled(Uri uri, int maxSide) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) return null;
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > maxSide * 2) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) return null;
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            return scaleDown(bitmap, maxSide);
        }
    }

    private static Bitmap scaleDown(Bitmap source, int maxSide) {
        if (source == null) return null;
        int largest = Math.max(source.getWidth(), source.getHeight());
        if (largest <= maxSide) return source;

        float scale = maxSide / (float) largest;
        int width = Math.max(1, Math.round(source.getWidth() * scale));
        int height = Math.max(1, Math.round(source.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(source, width, height, true);
        if (scaled != source) source.recycle();
        return scaled;
    }

    void close() {
        closed = true;
        synchronized (pending) {
            pending.clear();
        }
        executor.shutdownNow();
        cache.evictAll();
    }
}
