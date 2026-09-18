package com.malikksh.wastickers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;

import com.aureusapps.android.webpandroid.decoder.FrameDecodeResult;
import com.aureusapps.android.webpandroid.decoder.WebPDecoder;
import com.aureusapps.android.webpandroid.decoder.WebPInfo;
import com.aureusapps.android.webpandroid.encoder.WebPAnimEncoder;
import com.aureusapps.android.webpandroid.encoder.WebPAnimEncoderOptions;
import com.aureusapps.android.webpandroid.encoder.WebPConfig;
import com.aureusapps.android.webpandroid.encoder.WebPMuxAnimParams;
import com.aureusapps.android.webpandroid.encoder.WebPPreset;

import java.io.File;
import java.io.IOException;

/**
 * High quality animated-WebP path used when FFmpeg cannot decode an animated WebP.
 * Frames are decoded by libwebp, scaled up to a near-full 512x512 sticker canvas,
 * then re-encoded adaptively while trying to preserve detail before smoothness.
 */
final class AnimatedWebpResizer {
    private static final int CANVAS_SIZE = 512;
    private static final int CONTENT_SIZE = 480;
    private static final int TARGET_BYTES = 490_000;

    private static final ResizeProfile[] PROFILES = new ResizeProfile[]{
            new ResizeProfile(18, 96),
            new ResizeProfile(15, 96),
            new ResizeProfile(12, 96),
            new ResizeProfile(10, 94),
            new ResizeProfile(8, 94),
            new ResizeProfile(6, 92),
            new ResizeProfile(5, 90),
            new ResizeProfile(4, 88),
            new ResizeProfile(4, 82),
            new ResizeProfile(3, 78),
            new ResizeProfile(3, 72),
            new ResizeProfile(2, 68)
    };

    private static final class ResizeProfile {
        final int fps;
        final int quality;

        ResizeProfile(int fps, int quality) {
            this.fps = fps;
            this.quality = quality;
        }
    }

    private AnimatedWebpResizer() {}

    static AnimatedStickerConverter.Result resize(
            Context context,
            File input,
            File output,
            int sourceApproxFps
    ) throws IOException {
        WebPDecoder decoder = null;
        try {
            decoder = new WebPDecoder(context);
            decoder.setDataSource(Uri.fromFile(input));
            WebPInfo info = decoder.decodeInfo();
            if (!info.getHasAnimation()) {
                throw new IOException("libwebp: входной WebP не является анимированным");
            }

            BugLogStore.appendApp("libwebp animated decoder ready: "
                    + info.getWidth() + "x" + info.getHeight()
                    + ", frames=" + info.getFrameCount());

            String lastError = null;
            for (ResizeProfile profile : PROFILES) {
                // Do not invent a higher FPS than the source actually has.
                int effectiveFps = Math.max(1, Math.min(profile.fps, Math.max(1, sourceApproxFps)));
                try {
                    long bytes = encodeProfile(context, decoder, output, effectiveFps, profile.quality);
                    BugLogStore.appendApp("libwebp resize profile: fps=" + effectiveFps
                            + ", quality=" + profile.quality + ", bytes=" + bytes);
                    if (bytes > 0 && bytes <= TARGET_BYTES) {
                        return new AnimatedStickerConverter.Result(effectiveFps, profile.quality, bytes);
                    }
                } catch (Throwable error) {
                    lastError = describeThrowable(error);
                    BugLogStore.appendApp("libwebp resize profile failed: fps=" + effectiveFps
                            + ", quality=" + profile.quality + ": " + lastError);
                }

                //noinspection ResultOfMethodCallIgnored
                output.delete();
                decoder.reset();
            }

            if (lastError != null) {
                throw new IOException("libwebp не смог собрать увеличенный animated WebP: " + lastError);
            }
            throw new IOException("Увеличенный animated WebP не помещается в лимит WhatsApp 500 КБ");
        } catch (Throwable error) {
            //noinspection ResultOfMethodCallIgnored
            output.delete();
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Ошибка libwebp animated resize: " + describeThrowable(error), error);
        } finally {
            if (decoder != null) {
                try {
                    decoder.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static long encodeProfile(
            Context context,
            WebPDecoder decoder,
            File output,
            int fps,
            int quality
    ) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        output.delete();
        decoder.reset();

        WebPMuxAnimParams animParams = new WebPMuxAnimParams(0, 0);
        WebPAnimEncoderOptions options = new WebPAnimEncoderOptions(
                true,
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

            long previousEndTimestamp = 0;
            long finalEndTimestamp = 0;
            long nextSampleTimestamp = 0;
            long frameInterval = Math.max(1L, Math.round(1000.0 / fps));
            int addedFrames = 0;

            while (decoder.hasNextFrame()) {
                FrameDecodeResult decoded = decoder.decodeNextFrame();
                Bitmap frame = decoded.getFrame();
                if (frame == null) {
                    throw new IOException("libwebp вернул пустой кадр");
                }

                long endTimestamp = Math.max(previousEndTimestamp + 1L, decoded.getTimestamp());
                long startTimestamp = previousEndTimestamp;
                previousEndTimestamp = endTimestamp;
                finalEndTimestamp = endTimestamp;

                boolean include = addedFrames == 0 || startTimestamp >= nextSampleTimestamp;
                if (!include) continue;

                Bitmap stickerFrame = scaleFrame(frame);
                try {
                    encoder.addFrame(startTimestamp, stickerFrame);
                } finally {
                    stickerFrame.recycle();
                }
                addedFrames++;

                if (nextSampleTimestamp <= startTimestamp) {
                    do {
                        nextSampleTimestamp += frameInterval;
                    } while (nextSampleTimestamp <= startTimestamp);
                }
            }

            if (addedFrames == 0 || finalEndTimestamp <= 0) {
                throw new IOException("libwebp не декодировал ни одного кадра");
            }

            encoder.assemble(finalEndTimestamp, Uri.fromFile(output));
            if (!output.isFile() || output.length() <= 0) {
                throw new IOException("libwebp не создал выходной WebP");
            }
            return output.length();
        } finally {
            if (encoder != null) {
                try {
                    encoder.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Bitmap scaleFrame(Bitmap source) {
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

    private static WebPConfig createConfig(int quality) {
        // Kotlin data class has a full Java constructor. Keep alpha at maximum quality;
        // compress RGB adaptively and use the slowest method for the best size/detail ratio.
        return new WebPConfig(
                WebPConfig.COMPRESSION_LOSSY,
                (float) quality,
                6,
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
                6,
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

    private static String describeThrowable(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return error.getClass().getName()
                + (message == null || message.isEmpty() ? "" : ": " + message);
    }
}
