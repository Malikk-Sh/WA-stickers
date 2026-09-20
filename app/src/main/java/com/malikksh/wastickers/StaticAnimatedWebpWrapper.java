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

import com.aureusapps.android.webpandroid.decoder.WebPDecoder;
import com.aureusapps.android.webpandroid.decoder.WebPInfo;
import com.aureusapps.android.webpandroid.encoder.WebPAnimEncoder;
import com.aureusapps.android.webpandroid.encoder.WebPAnimEncoderOptions;
import com.aureusapps.android.webpandroid.encoder.WebPConfig;
import com.aureusapps.android.webpandroid.encoder.WebPMuxAnimParams;
import com.aureusapps.android.webpandroid.encoder.WebPPreset;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Encodes a static image as a standards-valid animated WebP for an animated WhatsApp pack.
 *
 * This is a conversion strategy used by StickerPackBuilder; it does not own batch/build state.
 */
final class StaticAnimatedWebpWrapper {
    private static final int CANVAS_SIZE = 512;
    private static final int CONTENT_SIZE = 480;
    private static final int FRAME_INTERVAL_MS = 500;
    private static final int DURATION_MS = 1000;
    private static final int FPS = 2;
    private static final long TARGET_BYTES = 490_000L;
    private static final int[] QUALITY_PROFILES = new int[]{92, 84, 76};

    private StaticAnimatedWebpWrapper() {}

    static AnimatedStickerConverter.Result convert(
            Context context,
            Uri sourceUri,
            File output,
            AnimatedStickerConverter.ProgressListener progressListener
    ) throws IOException {
        if (context == null || sourceUri == null || output == null) {
            throw new IOException("Не удалось подготовить статичный стикер для анимированного набора");
        }
        Context appContext = context.getApplicationContext();
        report(progressListener, AnimatedStickerConverter.ProgressStage.PREPARING, 0, 0, 0, 0);

        ConversionCache.Hit cacheHit = ConversionCache.restore(
                appContext,
                sourceUri,
                true,
                0L,
                output,
                AnimatedStickerConverter.MAX_ANIMATED_BYTES
        );
        if (cacheHit != null && isValidWrappedWebp(appContext, output)) {
            report(progressListener, AnimatedStickerConverter.ProgressStage.DONE,
                    100, 0, cacheHit.quality, cacheHit.bytes);
            return new AnimatedStickerConverter.Result(
                    cacheHit.fps > 0 ? cacheHit.fps : FPS,
                    cacheHit.quality,
                    cacheHit.bytes
            );
        }
        if (output.exists() && !output.delete()) {
            BugLogStore.appendApp("Could not delete invalid cached static-animation wrapper");
        }

        Bitmap source = decodeSampled(appContext, sourceUri, 1600);
        Bitmap firstFrame = null;
        Bitmap secondFrame = null;
        try {
            report(progressListener, AnimatedStickerConverter.ProgressStage.PREPARING,
                    45, 0, 0, 0);
            firstFrame = scaleToCanvas(source);
            secondFrame = firstFrame.copy(Bitmap.Config.ARGB_8888, true);
            if (secondFrame == null) throw new IOException("Не удалось подготовить второй кадр");

            // Keep the rendered sticker visually static while guaranteeing that encoders do not
            // collapse two identical frames into a single still image. The changed pixel is in the
            // transparent 16 px safety border and has effectively invisible alpha.
            secondFrame.setPixel(0, 0, 0x08000000);

            String lastError = null;
            for (int index = 0; index < QUALITY_PROFILES.length; index++) {
                int quality = QUALITY_PROFILES[index];
                int attempt = index + 1;
                report(progressListener, AnimatedStickerConverter.ProgressStage.ENCODING,
                        0, attempt, quality, 0);
                try {
                    encode(appContext, firstFrame, secondFrame, output, quality);
                    long bytes = output.isFile() ? output.length() : 0L;
                    report(progressListener, AnimatedStickerConverter.ProgressStage.CHECKING,
                            100, attempt, quality, bytes);
                    if (bytes > 0 && bytes <= TARGET_BYTES && isValidWrappedWebp(appContext, output)) {
                        ConversionCache.store(
                                appContext,
                                sourceUri,
                                true,
                                0L,
                                output,
                                FPS,
                                quality,
                                AnimatedStickerConverter.MAX_ANIMATED_BYTES
                        );
                        BugLogStore.appendApp("Static source wrapped as animated WebP: quality="
                                + quality + ", bytes=" + bytes);
                        report(progressListener, AnimatedStickerConverter.ProgressStage.DONE,
                                100, attempt, quality, bytes);
                        return new AnimatedStickerConverter.Result(FPS, quality, bytes);
                    }
                    lastError = bytes > TARGET_BYTES
                            ? "выходной файл превышает целевой лимит"
                            : "выходной WebP не прошёл проверку анимации";
                } catch (Throwable error) {
                    lastError = describe(error);
                    BugLogStore.appendApp("Static animation wrapper attempt failed: quality="
                            + quality + ", error=" + lastError);
                }
                if (output.exists() && !output.delete()) {
                    BugLogStore.appendApp("Could not delete failed static-animation candidate");
                }
            }
            throw new IOException("Не удалось обернуть фото в animated WebP: "
                    + (lastError == null ? "неизвестная ошибка" : lastError));
        } finally {
            source.recycle();
            if (firstFrame != null && !firstFrame.isRecycled()) firstFrame.recycle();
            if (secondFrame != null && !secondFrame.isRecycled()) secondFrame.recycle();
        }
    }

    static boolean isValidWrappedWebp(Context context, File file) {
        if (context == null || file == null || !file.isFile()
                || file.length() <= 0 || file.length() > AnimatedStickerConverter.MAX_ANIMATED_BYTES) {
            return false;
        }
        WebPDecoder decoder = null;
        try {
            decoder = new WebPDecoder(context.getApplicationContext());
            decoder.setDataSource(Uri.fromFile(file));
            WebPInfo info = decoder.decodeInfo();
            if (!info.getHasAnimation()
                    || info.getFrameCount() < 2
                    || info.getWidth() != CANVAS_SIZE
                    || info.getHeight() != CANVAS_SIZE) {
                return false;
            }
            byte[] bytes = readAll(file);
            return MediaAnimationInspector.inspect("image/webp", bytes)
                    == MediaAnimationInspector.AnimationKind.ANIMATED;
        } catch (Throwable error) {
            return false;
        } finally {
            if (decoder != null) {
                try {
                    decoder.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void encode(
            Context context,
            Bitmap firstFrame,
            Bitmap secondFrame,
            File output,
            int quality
    ) throws IOException {
        if (output.exists() && !output.delete()) {
            throw new IOException("Не удалось заменить временный WebP");
        }
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Не удалось создать папку для WebP");
        }

        WebPMuxAnimParams animParams = new WebPMuxAnimParams(0, 0);
        WebPAnimEncoderOptions options = new WebPAnimEncoderOptions(
                false,
                null,
                null,
                false,
                false,
                animParams
        );
        WebPAnimEncoder encoder = null;
        try {
            encoder = new WebPAnimEncoder(context, CANVAS_SIZE, CANVAS_SIZE, options);
            encoder.configure(createConfig(quality), WebPPreset.WEBP_PRESET_PICTURE);
            encoder.addFrame(0L, firstFrame);
            encoder.addFrame(FRAME_INTERVAL_MS, secondFrame);
            encoder.assemble(DURATION_MS, Uri.fromFile(output));
            if (!output.isFile() || output.length() <= 0) {
                throw new IOException("libwebp не создал animated WebP");
            }
        } finally {
            if (encoder != null) {
                try {
                    encoder.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static WebPConfig createConfig(int quality) {
        return new WebPConfig(
                WebPConfig.COMPRESSION_LOSSY,
                (float) quality,
                4,
                null,
                null,
                null,
                70,
                null,
                null,
                null,
                true,
                WebPConfig.ALPHA_COMPRESSION_WITH_LOSSLESS,
                WebPConfig.ALPHA_FILTERING_BEST,
                100,
                2,
                null,
                WebPConfig.PREPROCESSING_NONE,
                null,
                null,
                null,
                1,
                false,
                null,
                true,
                false,
                true,
                null,
                null
        );
    }

    private static Bitmap decodeSampled(Context context, Uri uri, int maxSide) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = open(context, uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Неподдерживаемое статичное изображение");
        }

        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > maxSide * 2) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream input = open(context, uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) throw new IOException("Не удалось прочитать статичное изображение");
            return bitmap;
        }
    }

    private static InputStream open(Context context, Uri uri) throws IOException {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            if (path == null) throw new IOException("Файл недоступен");
            return new FileInputStream(new File(path));
        }
        ContentResolver resolver = context.getContentResolver();
        InputStream input = resolver.openInputStream(uri);
        if (input == null) throw new IOException("Файл недоступен");
        return input;
    }

    private static Bitmap scaleToCanvas(Bitmap source) {
        Bitmap target = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        canvas.drawColor(Color.TRANSPARENT);
        float scale = Math.min(
                CONTENT_SIZE / (float) Math.max(1, source.getWidth()),
                CONTENT_SIZE / (float) Math.max(1, source.getHeight())
        );
        float width = source.getWidth() * scale;
        float height = source.getHeight() * scale;
        float left = (CANVAS_SIZE - width) / 2f;
        float top = (CANVAS_SIZE - height) / 2f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(source, null, new RectF(left, top, left + width, top + height), paint);
        return target;
    }

    private static byte[] readAll(File file) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(file.length(), 512 * 1024L));
        byte[] buffer = new byte[8192];
        try (InputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }

    private static void report(
            AnimatedStickerConverter.ProgressListener listener,
            AnimatedStickerConverter.ProgressStage stage,
            int percent,
            int attempt,
            int quality,
            long bytes
    ) {
        if (listener == null) return;
        listener.onProgress(new AnimatedStickerConverter.Progress(
                stage,
                percent,
                attempt,
                QUALITY_PROFILES.length,
                FPS,
                quality,
                bytes
        ));
    }

    private static String describe(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return error.getClass().getSimpleName()
                + (message == null || message.trim().isEmpty() ? "" : ": " + message);
    }
}
