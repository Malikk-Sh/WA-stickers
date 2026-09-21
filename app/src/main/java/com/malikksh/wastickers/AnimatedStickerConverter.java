package com.malikksh.wastickers;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

final class AnimatedStickerConverter {
    static final int MAX_ANIMATED_BYTES = 500 * 1024;
    static final int MAX_DURATION_SECONDS = 10;

    private static final int TARGET_ANIMATED_BYTES = 490_000;
    private static final int REQUIRED_SIZE = 512;
    private static final int MIN_FRAME_DURATION_MS = 8;

    enum ProgressStage {
        PREPARING,
        INSPECTING,
        PASSTHROUGH,
        RESIZING,
        ENCODING,
        CHECKING,
        DONE
    }

    interface ProgressListener {
        void onProgress(Progress progress);
    }

    static final class Progress {
        final ProgressStage stage;
        final int percent;
        final int attempt;
        final int totalAttempts;
        final int fps;
        final int quality;
        final long candidateBytes;

        Progress(
                ProgressStage stage,
                int percent,
                int attempt,
                int totalAttempts,
                int fps,
                int quality,
                long candidateBytes
        ) {
            this.stage = stage;
            this.percent = Math.max(0, Math.min(100, percent));
            this.attempt = attempt;
            this.totalAttempts = totalAttempts;
            this.fps = fps;
            this.quality = quality;
            this.candidateBytes = candidateBytes;
        }
    }

    static final class Result {
        final int fps;
        final int quality;
        final long bytes;

        Result(int fps, int quality, long bytes) {
            this.fps = fps;
            this.quality = quality;
            this.bytes = bytes;
        }
    }

    private static final class EncodeOutcome {
        final boolean success;
        final String log;

        EncodeOutcome(boolean success, String log) {
            this.success = success;
            this.log = log;
        }
    }

    private static final class WebpInfo {
        boolean valid;
        boolean animated;
        int width;
        int height;
        int frameCount;
        long totalDurationMs;
        int minFrameDurationMs = Integer.MAX_VALUE;
        int maxFrameRight;
        int maxFrameBottom;

        int approximateFps() {
            if (frameCount <= 0 || totalDurationMs <= 0) return 1;
            return Math.max(1, Math.min(60,
                    Math.round(frameCount * 1000f / (float) totalDurationMs)));
        }
    }

    private AnimatedStickerConverter() {}

    static Result convert(Context context, Uri sourceUri, File output) throws IOException {
        return convert(context, sourceUri, output, null);
    }

    static Result convert(
            Context context,
            Uri sourceUri,
            File output,
            ProgressListener progressListener
    ) throws IOException {
        report(progressListener, ProgressStage.PREPARING, 0, 0, 0, 0, 0, 0);

        File tempDir = new File(context.getCacheDir(), "animated_sticker_work");
        if (!tempDir.mkdirs() && !tempDir.isDirectory()) {
            throw new IOException("Не удалось создать временную папку");
        }

        long sourceDurationMs = estimateSourceDurationMs(context, sourceUri);
        long startOffsetMs = resolveStartOffsetMs(sourceUri, sourceDurationMs);
        long expectedDurationMs = VideoTrimStore.getClipDurationMs(sourceUri, sourceDurationMs);
        if (startOffsetMs > 0) {
            BugLogStore.appendApp("Video trim selected: startMs=" + startOffsetMs
                    + ", sourceDurationMs=" + sourceDurationMs
                    + ", clipDurationMs=" + expectedDurationMs);
        }

        ConversionCache.Hit cacheHit = ConversionCache.restore(
                context,
                sourceUri,
                true,
                startOffsetMs,
                output,
                MAX_ANIMATED_BYTES
        );
        if (cacheHit != null) {
            BugLogStore.appendApp("Conversion cache hit: animated, bytes=" + cacheHit.bytes
                    + ", fps=" + cacheHit.fps + ", quality=" + cacheHit.quality
                    + ", startMs=" + startOffsetMs);
            report(progressListener, ProgressStage.PREPARING, 100, 0, 0,
                    cacheHit.fps, cacheHit.quality, cacheHit.bytes);
            report(progressListener, ProgressStage.DONE, 100, 0, 0,
                    cacheHit.fps, cacheHit.quality, cacheHit.bytes);
            return new Result(cacheHit.fps, cacheHit.quality, cacheHit.bytes);
        }

        File input = new File(tempDir, "input_" + System.nanoTime() + guessExtension(context, sourceUri));
        copyUri(context, sourceUri, input);
        report(progressListener, ProgressStage.PREPARING, 100, 0, 0, 0, 0, input.length());

        File candidate = new File(tempDir, "candidate_" + System.nanoTime() + ".webp");
        String lastLog = "";
        try {
            report(progressListener, ProgressStage.INSPECTING, 0, 0, 0, 0, 0, input.length());
            WebpInfo webp = inspectWebp(input);
            if (webp.valid && webp.animated && webp.totalDurationMs > 0) {
                sourceDurationMs = webp.totalDurationMs;
                startOffsetMs = resolveStartOffsetMs(sourceUri, sourceDurationMs);
                expectedDurationMs = VideoTrimStore.getClipDurationMs(sourceUri, sourceDurationMs);
            }
            report(progressListener, ProgressStage.INSPECTING, 100, 0, 0, 0, 0, input.length());

            if (webp.valid && webp.animated) {
                BugLogStore.appendApp("Animated WebP detected: " + webp.width + "x" + webp.height
                        + ", frames=" + webp.frameCount
                        + ", durationMs=" + webp.totalDurationMs
                        + ", minFrameMs=" + (webp.minFrameDurationMs == Integer.MAX_VALUE
                        ? "unknown" : webp.minFrameDurationMs)
                        + ", bytes=" + input.length());

                if (startOffsetMs == 0 && expectedDurationMs >= webp.totalDurationMs && isDirectlyWhatsAppCompatible(webp, input.length())) {
                    report(progressListener, ProgressStage.PASSTHROUGH, 40, 0, 0,
                            webp.approximateFps(), 100, input.length());
                    copyFile(input, output);
                    BugLogStore.appendApp("Animated WebP passthrough: already 512x512; original bytes preserved");
                    report(progressListener, ProgressStage.DONE, 100, 0, 0,
                            webp.approximateFps(), 100, output.length());
                    return cacheResult(context, sourceUri, startOffsetMs, output,
                            new Result(webp.approximateFps(), 100, output.length()));
                }

                if (startOffsetMs == 0 && expectedDurationMs >= webp.totalDurationMs && isAnimationStructureCompatible(webp)) {
                    try {
                        report(progressListener, ProgressStage.RESIZING, 10, 0, 0,
                                webp.approximateFps(), 100, input.length());
                        BugLogStore.appendApp("Animated WebP pixel resize via native libwebp: "
                                + webp.width + "x" + webp.height + " -> 512x512, visible content target 480px");
                        Result resized = AnimatedWebpResizer.resize(
                                context,
                                input,
                                output,
                                webp.approximateFps()
                        );
                        report(progressListener, ProgressStage.RESIZING, 90, 0, 0,
                                resized.fps, resized.quality, resized.bytes);
                        WebpInfo resizedInfo = inspectWebp(output);
                        if (isDirectlyWhatsAppCompatible(resizedInfo, output.length())) {
                            BugLogStore.appendApp("libwebp upscale success: 512x512, fps=" + resized.fps
                                    + ", quality=" + resized.quality + ", bytes=" + resized.bytes);
                            report(progressListener, ProgressStage.DONE, 100, 0, 0,
                                    resized.fps, resized.quality, resized.bytes);
                            return cacheResult(context, sourceUri, startOffsetMs, output, resized);
                        }
                        //noinspection ResultOfMethodCallIgnored
                        output.delete();
                        BugLogStore.appendApp("libwebp produced a file that failed WhatsApp validation; falling back to FFmpeg");
                    } catch (Throwable resizeError) {
                        //noinspection ResultOfMethodCallIgnored
                        output.delete();
                        BugLogStore.appendApp("libwebp animated resize failed: " + describeThrowable(resizeError)
                                + "; falling back to FFmpeg");
                    }
                }

                if (startOffsetMs > 0) {
                    BugLogStore.appendApp("Animated fast path skipped because a custom start offset is selected");
                }
                BugLogStore.appendApp("Animated WebP fallback: trying FFmpeg conversion");
            }

            for (int profileIndex = 0; profileIndex < AnimatedStickerProfiles.size(); profileIndex++) {
                AnimatedStickerProfiles.Profile profile = AnimatedStickerProfiles.get(profileIndex);
                int attempt = profileIndex + 1;
                if (candidate.exists()) candidate.delete();

                report(progressListener, ProgressStage.ENCODING, 0, attempt,
                        AnimatedStickerProfiles.size(), profile.fps, profile.quality, 0);
                EncodeOutcome outcome = encode(
                        input,
                        candidate,
                        profile,
                        "libwebp_anim",
                        startOffsetMs,
                        expectedDurationMs,
                        attempt,
                        progressListener
                );
                lastLog = outcome.log;

                if (!outcome.success) {
                    if (candidate.exists()) candidate.delete();
                    outcome = encode(
                            input,
                            candidate,
                            profile,
                            "libwebp",
                            startOffsetMs,
                            expectedDurationMs,
                            attempt,
                            progressListener
                    );
                    lastLog = outcome.log;
                }

                if (!outcome.success || !candidate.isFile() || candidate.length() == 0) {
                    String extra = "";
                    if (webp.valid && webp.animated) {
                        extra = " Исходный animated WebP: " + webp.width + "x" + webp.height
                                + ", " + webp.totalDurationMs + " мс, " + input.length() + " байт.";
                    }
                    throw new IOException("FFmpeg не смог создать WebP: " + compactLog(lastLog) + extra);
                }

                if (!isAnimatedWebp(candidate)) {
                    throw new IOException(
                            "Файл не удалось превратить в анимацию: итоговый WebP содержит только один кадр. "
                                    + "FFmpeg: " + compactLog(lastLog));
                }

                long size = candidate.length();
                report(progressListener, ProgressStage.CHECKING, 100, attempt,
                        AnimatedStickerProfiles.size(), profile.fps, profile.quality, size);
                if (size <= TARGET_ANIMATED_BYTES) {
                    copyFile(candidate, output);
                    report(progressListener, ProgressStage.DONE, 100, attempt,
                            AnimatedStickerProfiles.size(), profile.fps, profile.quality, size);
                    return cacheResult(context, sourceUri, startOffsetMs, output,
                            new Result(profile.fps, profile.quality, output.length()));
                }
            }

            throw new IOException(
                    "Даже после сильной оптимизации файл не помещается в лимит WhatsApp 500 КБ. "
                            + "Попробуйте более короткий или менее динамичный фрагмент.");
        } finally {
            //noinspection ResultOfMethodCallIgnored
            input.delete();
            //noinspection ResultOfMethodCallIgnored
            candidate.delete();
        }
    }

    private static Result cacheResult(Context context, Uri sourceUri, long startOffsetMs,
                                      File output, Result result) {
        ConversionCache.store(
                context,
                sourceUri,
                true,
                startOffsetMs,
                output,
                result.fps,
                result.quality,
                MAX_ANIMATED_BYTES
        );
        return result;
    }

    static void createTrayIcon(Context context, Uri sourceUri, File trayFile) throws IOException {
        File tempDir = new File(context.getCacheDir(), "animated_sticker_work");
        if (!tempDir.mkdirs() && !tempDir.isDirectory()) {
            throw new IOException("Не удалось создать временную папку");
        }

        long startOffsetMs = VideoTrimStore.getStartOffsetMs(sourceUri);
        File input = new File(tempDir, "tray_input_" + System.nanoTime() + guessExtension(context, sourceUri));
        copyUri(context, sourceUri, input);
        try {
            if (startOffsetMs == 0) {
                Bitmap firstFrame = BitmapFactory.decodeFile(input.getAbsolutePath());
                if (firstFrame != null) {
                    try {
                        writeTrayBitmap(firstFrame, trayFile);
                        BugLogStore.appendApp("Tray icon created with Android BitmapFactory");
                        return;
                    } finally {
                        firstFrame.recycle();
                    }
                }
            }

            String filter = "scale=96:96:force_original_aspect_ratio=decrease:flags=lanczos,"
                    + "pad=96:96:(ow-iw)/2:(oh-ih)/2:color=0x00000000";
            String seek = startOffsetMs > 0 ? " -ss " + secondsArg(startOffsetMs) : "";
            String command = "-y -hide_banner -loglevel error" + seek + " -i " + q(input)
                    + " -frames:v 1 -vf \"" + filter + "\" " + q(trayFile);

            try {
                var session = FFmpegKit.execute(command);
                String log = session.getAllLogsAsString();
                BugLogStore.appendFfmpeg("trayStartMs=" + startOffsetMs + "\n" + log);
                if (!ReturnCode.isSuccess(session.getReturnCode()) || !trayFile.isFile()) {
                    throw new IOException("Не удалось создать иконку набора: " + compactLog(log));
                }
            } catch (Throwable ffmpegError) {
                String message = "FFmpegKit не удалось запустить для иконки: " + describeThrowable(ffmpegError);
                BugLogStore.appendFfmpeg(message);
                if (ffmpegError instanceof IOException) throw (IOException) ffmpegError;
                throw new IOException(message, ffmpegError);
            }

            if (trayFile.length() > 50 * 1024) {
                throw new IOException("Иконка набора превышает 50 КБ");
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            input.delete();
        }
    }

    private static boolean isAnimationStructureCompatible(WebpInfo info) {
        return info.valid
                && info.animated
                && info.width > 0
                && info.height > 0
                && info.frameCount > 0
                && info.totalDurationMs > 0
                && info.totalDurationMs <= MAX_DURATION_SECONDS * 1000L
                && info.minFrameDurationMs != Integer.MAX_VALUE
                && info.minFrameDurationMs >= MIN_FRAME_DURATION_MS
                && info.maxFrameRight <= info.width
                && info.maxFrameBottom <= info.height;
    }

    private static boolean isDirectlyWhatsAppCompatible(WebpInfo info, long bytes) {
        return isAnimationStructureCompatible(info)
                && info.width == REQUIRED_SIZE
                && info.height == REQUIRED_SIZE
                && bytes > 0
                && bytes <= TARGET_ANIMATED_BYTES;
    }

    private static WebpInfo inspectWebp(File file) {
        WebpInfo info = new WebpInfo();
        if (!file.isFile() || file.length() < 20) return info;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (!"RIFF".equals(readFourCc(raf))) return info;
            readUInt32LE(raf);
            if (!"WEBP".equals(readFourCc(raf))) return info;

            boolean hasVp8x = false;
            boolean hasAnimChunk = false;
            boolean hasAnimationFlag = false;

            while (raf.getFilePointer() + 8 <= raf.length()) {
                String chunk = readFourCc(raf);
                long chunkSize = readUInt32LE(raf);
                long dataStart = raf.getFilePointer();
                long dataEnd = dataStart + chunkSize;
                if (chunkSize < 0 || dataEnd < dataStart || dataEnd > raf.length()) return info;

                if ("VP8X".equals(chunk) && chunkSize >= 10) {
                    hasVp8x = true;
                    int flags = raf.readUnsignedByte();
                    hasAnimationFlag = (flags & 0x02) != 0;
                    raf.skipBytes(3);
                    info.width = readUInt24LE(raf) + 1;
                    info.height = readUInt24LE(raf) + 1;
                } else if ("ANIM".equals(chunk) && chunkSize >= 6) {
                    hasAnimChunk = true;
                } else if ("ANMF".equals(chunk) && chunkSize >= 16) {
                    raf.seek(dataStart);
                    int x = readUInt24LE(raf) * 2;
                    int y = readUInt24LE(raf) * 2;
                    int frameWidth = readUInt24LE(raf) + 1;
                    int frameHeight = readUInt24LE(raf) + 1;
                    int duration = readUInt24LE(raf);

                    info.frameCount++;
                    info.totalDurationMs += duration;
                    info.minFrameDurationMs = Math.min(info.minFrameDurationMs, duration);
                    info.maxFrameRight = Math.max(info.maxFrameRight, x + frameWidth);
                    info.maxFrameBottom = Math.max(info.maxFrameBottom, y + frameHeight);
                }

                long next = dataEnd + (chunkSize & 1L);
                raf.seek(next);
            }

            info.animated = hasVp8x && hasAnimationFlag && hasAnimChunk && info.frameCount > 0;
            info.valid = hasVp8x
                    && info.width > 0
                    && info.height > 0
                    && (!info.animated
                    || (info.maxFrameRight <= info.width && info.maxFrameBottom <= info.height));
        } catch (IOException ignored) {
            return new WebpInfo();
        }

        return info;
    }

    private static EncodeOutcome encode(
            File input,
            File output,
            AnimatedStickerProfiles.Profile profile,
            String encoder,
            long startOffsetMs,
            long expectedDurationMs,
            int attempt,
            ProgressListener progressListener
    ) throws IOException {
        String filter = "fps=" + profile.fps
                + ",scale=512:512:force_original_aspect_ratio=decrease:flags=lanczos"
                + ",pad=512:512:(ow-iw)/2:(oh-ih)/2:color=0x00000000"
                + ",format=yuva420p";

        long durationMs = Math.max(1L, Math.min(expectedDurationMs, MAX_DURATION_SECONDS * 1000L));
        String seek = startOffsetMs > 0 ? " -ss " + secondsArg(startOffsetMs) : "";
        String command = "-y -hide_banner -loglevel error" + seek + " -i " + q(input)
                + " -t " + secondsArg(durationMs)
                + " -an -vf \"" + filter + "\""
                + " -c:v " + encoder
                + " -lossless 0 -preset picture -compression_level 6"
                + " -quality " + profile.quality
                + " -loop 0 -f webp " + q(output);

        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<FFmpegSession> sessionRef = new AtomicReference<>();
        long expectedFrames = Math.max(1L, Math.round(durationMs * profile.fps / 1000.0));

        try {
            FFmpegKit.executeAsync(
                    command,
                    session -> {
                        sessionRef.set(session);
                        completed.countDown();
                    },
                    log -> { },
                    statistics -> {
                        long frame = Math.max(0L, statistics.getVideoFrameNumber());
                        int percent = (int) Math.min(99L, (frame * 100L) / expectedFrames);
                        report(progressListener, ProgressStage.ENCODING, percent, attempt,
                                AnimatedStickerProfiles.size(), profile.fps, profile.quality,
                                output.isFile() ? output.length() : 0);
                    }
            );
        } catch (Throwable ffmpegError) {
            String message = "FFmpegKit startup/execute failure: " + describeThrowable(ffmpegError);
            BugLogStore.appendFfmpeg(message);
            return new EncodeOutcome(false, message);
        }

        try {
            completed.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Конвертация была прервана", interrupted);
        }

        FFmpegSession session = sessionRef.get();
        if (session == null) {
            String message = "FFmpeg session unavailable";
            BugLogStore.appendFfmpeg(message);
            return new EncodeOutcome(false, message);
        }

        String log = session.getAllLogsAsString();
        BugLogStore.appendFfmpeg("encoder=" + encoder
                + ", fps=" + profile.fps
                + ", quality=" + profile.quality
                + ", startMs=" + startOffsetMs
                + ", durationMs=" + durationMs + "\n" + log);
        boolean success = ReturnCode.isSuccess(session.getReturnCode())
                && output.isFile()
                && output.length() > 0;
        return new EncodeOutcome(success, log);
    }

    private static void report(
            ProgressListener listener,
            ProgressStage stage,
            int percent,
            int attempt,
            int totalAttempts,
            int fps,
            int quality,
            long candidateBytes
    ) {
        if (listener == null) return;
        listener.onProgress(new Progress(
                stage,
                percent,
                attempt,
                totalAttempts,
                fps,
                quality,
                candidateBytes
        ));
    }

    private static long resolveStartOffsetMs(Uri uri, long sourceDurationMs) {
        long requested = VideoTrimStore.getStartOffsetMs(uri);
        if (sourceDurationMs > 0) {
            return VideoTrimPolicy.clampRangeStartMs(sourceDurationMs, requested);
        }
        return Math.max(0L, requested);
    }

    private static long estimateSourceDurationMs(Context context, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String rawDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (rawDuration != null) {
                long duration = Long.parseLong(rawDuration);
                if (duration > 0) return duration;
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {
            }
        }
        return -1L;
    }

    private static String secondsArg(long milliseconds) {
        return String.format(Locale.US, "%.3f", Math.max(0L, milliseconds) / 1000.0);
    }

    private static void writeTrayBitmap(Bitmap source, File trayFile) throws IOException {
        Bitmap tray = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tray);
        canvas.drawColor(Color.TRANSPARENT);

        float scale = Math.min(96f / source.getWidth(), 96f / source.getHeight());
        float width = source.getWidth() * scale;
        float height = source.getHeight() * scale;
        float left = (96f - width) / 2f;
        float top = (96f - height) / 2f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, null, new RectF(left, top, left + width, top + height), paint);

        try (FileOutputStream output = new FileOutputStream(trayFile)) {
            if (!tray.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                tray.recycle();
                throw new IOException("Не удалось сохранить иконку набора");
            }
        }
        tray.recycle();

        if (trayFile.length() > 50 * 1024) {
            throw new IOException("Иконка набора превышает 50 КБ");
        }
    }

    private static boolean isAnimatedWebp(File file) {
        WebpInfo info = inspectWebp(file);
        return info.valid && info.animated;
    }

    private static String readFourCc(RandomAccessFile raf) throws IOException {
        byte[] bytes = new byte[4];
        raf.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static long readUInt32LE(RandomAccessFile raf) throws IOException {
        long b0 = raf.readUnsignedByte();
        long b1 = raf.readUnsignedByte();
        long b2 = raf.readUnsignedByte();
        long b3 = raf.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static int readUInt24LE(RandomAccessFile raf) throws IOException {
        int b0 = raf.readUnsignedByte();
        int b1 = raf.readUnsignedByte();
        int b2 = raf.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16);
    }

    private static String compactLog(String log) {
        if (log == null || log.trim().isEmpty()) return "неизвестная ошибка";
        String compact = log.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (compact.length() > 360) compact = compact.substring(compact.length() - 360);
        return compact;
    }

    private static String describeThrowable(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return error.getClass().getName() + (message == null || message.isEmpty() ? "" : ": " + message);
    }

    private static void copyUri(Context context, Uri uri, File target) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        try (InputStream input = resolver.openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("Файл недоступен");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        try (InputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
    }

    private static String guessExtension(Context context, Uri uri) {
        String name = uri.getLastPathSegment();
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        } catch (Exception ignored) {
        }

        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && dot < name.length() - 1) {
                String ext = name.substring(dot).toLowerCase(Locale.US);
                if (ext.length() <= 8) return ext;
            }
        }

        String mime = context.getContentResolver().getType(uri);
        if (mime == null) return ".bin";
        if (mime.contains("gif")) return ".gif";
        if (mime.contains("webp")) return ".webp";
        if (mime.contains("mp4")) return ".mp4";
        if (mime.contains("webm")) return ".webm";
        if (mime.contains("quicktime")) return ".mov";
        if (mime.contains("matroska")) return ".mkv";
        return ".bin";
    }

    private static String q(File file) {
        return "\"" + file.getAbsolutePath().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
