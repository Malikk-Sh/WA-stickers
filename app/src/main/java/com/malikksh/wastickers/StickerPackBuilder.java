package com.malikksh.wastickers;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Owns the media-to-sticker conversion details for one pack type.
 *
 * MainActivity remains responsible for batch orchestration, retries and UI state; this class owns
 * pixel decoding, WebP compression, animated conversion delegation and tray icon generation.
 */
final class StickerPackBuilder {
    private static final int STICKER_SIZE = 512;
    private static final int TRAY_SIZE = 96;
    private static final int MAX_STATIC_BYTES = 100 * 1024;
    private static final int MAX_TRAY_BYTES = 50 * 1024;

    interface ProgressListener {
        void onStaticProgress(int percent, String detail);
        void onAnimatedProgress(AnimatedStickerConverter.Progress progress);
    }

    static final class ItemResult {
        final long bytes;
        final int fps;
        final int quality;

        ItemResult(long bytes, int fps, int quality) {
            this.bytes = bytes;
            this.fps = fps;
            this.quality = quality;
        }

        boolean animated() {
            return fps > 0;
        }
    }

    private final Context context;
    private final boolean animated;

    StickerPackBuilder(Context context, boolean animated) {
        this.context = context.getApplicationContext();
        this.animated = animated;
    }

    ItemResult convert(Uri sourceUri, File target, ProgressListener listener) throws IOException {
        if (animated) {
            AnimatedStickerConverter.Result result = AnimatedStickerConverter.convert(
                    context,
                    sourceUri,
                    target,
                    progress -> {
                        if (listener != null) listener.onAnimatedProgress(progress);
                    }
            );
            return new ItemResult(result.bytes, result.fps, result.quality);
        }

        reportStatic(listener, 20, "Чтение изображения…");
        Bitmap sticker = makeSticker(sourceUri);
        try {
            reportStatic(listener, 65, "Сжатие WebP…");
            writeWebpUnderLimit(sticker, target);
        } finally {
            sticker.recycle();
        }
        reportStatic(listener, 100, "Готово");
        return new ItemResult(target.length(), 0, 0);
    }

    void createTrayIcon(Uri sourceUri, File trayFile) throws IOException {
        if (animated) {
            AnimatedStickerConverter.createTrayIcon(context, sourceUri, trayFile);
            return;
        }

        Bitmap source = makeSticker(sourceUri);
        Bitmap icon = Bitmap.createScaledBitmap(source, TRAY_SIZE, TRAY_SIZE, true);
        try (FileOutputStream output = new FileOutputStream(trayFile)) {
            if (!icon.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("Не удалось сохранить иконку набора");
            }
        } finally {
            source.recycle();
            if (icon != source) icon.recycle();
        }
        if (trayFile.length() > MAX_TRAY_BYTES) {
            throw new IOException("Иконка набора превышает 50 КБ");
        }
    }

    private void reportStatic(ProgressListener listener, int percent, String detail) {
        if (listener != null) listener.onStaticProgress(percent, detail);
    }

    private Bitmap makeSticker(Uri uri) throws IOException {
        Bitmap source = decodeSampled(uri, 1600);
        if (source == null) throw new IOException("Не удалось прочитать изображение");

        Bitmap output = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.TRANSPARENT);

        float scale = Math.min(
                STICKER_SIZE / (float) source.getWidth(),
                STICKER_SIZE / (float) source.getHeight()
        );
        float width = source.getWidth() * scale;
        float height = source.getHeight() * scale;
        float left = (STICKER_SIZE - width) / 2f;
        float top = (STICKER_SIZE - height) / 2f;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, null, new RectF(left, top, left + width, top + height), paint);
        source.recycle();
        return output;
    }

    private Bitmap decodeSampled(Uri uri, int maxSide) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("Файл недоступен");
            BitmapFactory.decodeStream(input, null, bounds);
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Неподдерживаемое изображение");
        }

        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > maxSide * 2) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("Файл недоступен");
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) throw new IOException("Неподдерживаемое изображение");
            return bitmap;
        }
    }

    private void writeWebpUnderLimit(Bitmap bitmap, File target) throws IOException {
        Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= 30
                ? Bitmap.CompressFormat.WEBP_LOSSY
                : Bitmap.CompressFormat.WEBP;

        byte[] best = null;
        for (int quality = 92; quality >= 8; quality -= 6) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!bitmap.compress(format, quality, bytes)) {
                throw new IOException("Ошибка конвертации WebP");
            }
            best = bytes.toByteArray();
            if (best.length <= MAX_STATIC_BYTES) break;
        }

        if (best == null || best.length > MAX_STATIC_BYTES) {
            throw new IOException("Стикер не удалось сжать до 100 КБ");
        }

        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(best);
        }
    }
}
