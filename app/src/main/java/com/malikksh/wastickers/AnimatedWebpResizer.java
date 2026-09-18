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
import java.util.ArrayList;
import java.util.List;

/**
 * High quality animated-WebP path used when FFmpeg cannot decode an animated WebP.
 * Frames are decoded and scaled exactly once. Encoding then uses at most three profiles
 * instead of repeatedly doing the expensive decode/scale pipeline for every profile.
 */
final class AnimatedWebpResizer {
    private static final int CANVAS_SIZE = 512;
    private static final int CONTENT_SIZE = 480;
    private static final int TARGET_BYTES = 490_000;

    // Three attempts maximum. The first preserves source smoothness and high detail.
    // Later attempts trade some smoothness before making quality aggressive.
    private static final ResizeProfile[] PROFILES = new ResizeProfile[]{
            new ResizeProfile(18, 94),
            new ResizeProfile(12, 90),
            new ResizeProfile(8, 84)
    };

    private static final class ResizeProfile {
        final int fps;
        final int quality;

        ResizeProfile(int fps, int quality) {
            this.fps = fps;
            this.quality = quality;
        }
    }

    private static final class PreparedFrame {
        final Bitmap bitmap;
        final long startTimestamp;

        PreparedFrame(Bitmap bitmap, long startTimestamp) {
            this.bitmap = bitmap;
            this.startTimestamp = startTimestamp;
        }
    }

    private static final class PreparedAnimation {
        final List<PreparedFrame> frames = new ArrayList<>();
        long durationMs;

        void recycle() {
            for (PreparedFrame frame : frames) {
                if (frame.bitmap != null && !frame.bitmap.isRecycled()) {
                    frame.bitmap.recycle();
                }
            }
            frames.clear();
        }
    }

    private AnimatedWebpResizer() {}

    static AnimatedStickerConverter.Result resize(
            Context context,
            File input,
            File output,
            int sourceApproxFps
    ) throws IOException {
        PreparedAnimation prepared = null;
        try {
            long decodeStarted = android.os.SystemClock.elapsedRealtime();
            prepared = decodeAndScaleOnce(context, input);
            long decodeMs = android.os.SystemClock.elapsedRealtime() - decodeStarted;

            BugLogStore.appendApp("libwebp decode+scale once: frames=" + prepared.frames.size()
                    + ", durationMs=" + prepared.durationMs
                    + ", elapsedMs=" + decodeMs);

            String lastError = null;
            int previousEffectiveFps = -1;

            for (int index = 0; index < PROFILES.length; index++) {
                ResizeProfile profile = PROFILES[index];
                int effectiveFps = Math.max(1, Math.min(profile.fps, Math.max(1, sourceApproxFps)));
                if (effectiveFps == previousEffectiveFps && index > 0) {
                    continue;
                }
                previousEffectiveFps = effectiveFps;

                long encodeStarted = android.os.SystemClock.elapsedRealtime();
                try {
                    long bytes = encodePrepared(context, prepared, output, effectiveFps, profile.quality);
                    long encodeMs = android.os.SystemClock.elapsedRealtime() - encodeStarted;
                    BugLogStore.appendApp("libwebp encode profile " + (index + 1) + "/" + PROFILES.length
                            + ": fps=" + effectiveFps
                            + ", quality=" + profile.quality
                            + ", bytes=" + bytes
                            + ", elapsedMs=" + encodeMs);

                    if (bytes > 0 && bytes <= TARGET_BYTES) {
                        return new AnimatedStickerConverter.Result(effectiveFps, profile.quality, bytes);
                    }
                } catch (Throwable error) {
                    lastError = describeThrowable(error);
                    BugLogStore.appendApp("libwebp encode failed: fps=" + effectiveFps
                            + ", quality=" + profile.quality + ": " + lastError);
                }

                //noinspection ResultOfMethodCallIgnored
                output.delete();
            }

            if (lastError != null) {
                throw new IOException("libwebp не смог собрать увеличенный animated WebP: " + lastError);
            }
            throw new IOException("Увеличенный animated WebP не помещается в лимит WhatsApp 500 КБ");
        } catch (OutOfMemoryError memoryError) {
            //noinspection ResultOfMethodCallIgnored
            output.delete();
            throw new IOException("Недостаточно памяти для увеличения animated WebP", memoryError);
        } catch (Throwable error) {
            //noinspection ResultOfMethodCallIgnored
            output.delete();
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Ошибка libwebp animated resize: " + describeThrowable(error), error);
        } finally {
            if (prepared != null) prepared.recycle();
        }
    }

    private static PreparedAnimation decodeAndScaleOnce(Context context, File input) throws IOException {
        WebPDecoder decoder = null;
        PreparedAnimation prepared = new PreparedAnimation();
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

            long previousEndTimestamp = 0;
            while (decoder.hasNextFrame()) {
                FrameDecodeResult decoded = decoder.decodeNextFrame();
                Bitmap frame = decoded.getFrame();
                if (frame == null) {
                    throw new IOException("libwebp вернул пустой кадр");
                }

                long endTimestamp = Math.max(previousEndTimestamp + 1L, decoded.getTimestamp());
                long startTimestamp = previousEndTimestamp;
                previousEndTimestamp = endTimestamp;

                Bitmap stickerFrame = scaleFrame(frame);
                prepared.frames.add(new PreparedFrame(stickerFrame, startTimestamp));
                prepared.durationMs = endTimestamp;
            }

            if (prepared.frames.isEmpty() || prepared.durationMs <= 0) {
                throw new IOException("libwebp не декодировал ни одного кадра");
            }
            return prepared;
        } catch (Throwable error) {
            prepared.recycle();
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("libwebp decode failed: " + describeThrowable(error), error);
        } finally {
            if (decoder != null) {
                try {
                    decoder.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static long encodePrepared(
            Context context,
            PreparedAnimation prepared,
            File output,
            int fps,
            int quality
    ) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        output.delete();

        WebPMuxAnimParams animParams = new WebPMuxAnimParams(0, 0);
        // minimizeSize=false matters a lot for speed. We already enforce the final 500 KB limit.
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
            encoder.configure(createFastHighQualityConfig(quality), WebPPreset.WEBP_PRESET_PICTURE);

            long frameInterval = Math.max(1L, Math.round(1000.0 / fps));
            long nextSampleTimestamp = 0;
            int addedFrames = 0;

            for (PreparedFrame frame : prepared.frames) {
                boolean include = addedFrames == 0 || frame.startTimestamp >= nextSampleTimestamp;
                if (!include) continue;

                encoder.addFrame(frame.startTimestamp, frame.bitmap);
                addedFrames++;

                if (nextSampleTimestamp <= frame.startTimestamp) {
                    do {
                        nextSampleTimestamp += frameInterval;
                    } while (nextSampleTimestamp <= frame.startTimestamp);
                }
            }

            if (addedFrames == 0) {
                throw new IOException("libwebp не выбрал ни одного кадра для кодирования");
            }

            encoder.assemble(prepared.durationMs, Uri.fromFile(output));
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

    private static WebPConfig createFastHighQualityConfig(int quality) {
        // method=4 and pass=2 are dramatically faster than method=6/pass=6 while keeping
        // very good visual quality. threadLevel=1 enables libwebp multithreading.
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

    private static String describeThrowable(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return error.getClass().getName()
                + (message == null || message.isEmpty() ? "" : ": " + message);
    }
}
