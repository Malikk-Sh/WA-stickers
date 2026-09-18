package com.malikksh.wastickers;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class AnimatedStickerConverter {
    static final int MAX_ANIMATED_BYTES = 500 * 1024;
    static final int MAX_DURATION_SECONDS = 10;

    // A little headroom avoids WhatsApp builds that interpret "500 KB" more strictly.
    private static final int TARGET_ANIMATED_BYTES = 490_000;

    interface ProgressListener {
        void onProgress(Progress progress);
    }

    static final class Progress {
        final int attempt;
        final int totalAttempts;
        final int fps;
        final int quality;
        final int percent;
        final long encodedMs;
        final long expectedMs;
        final long candidateBytes;
        final boolean checkingSize;

        Progress(
                int attempt,
                int totalAttempts,
                int fps,
                int quality,
                int percent,
                long encodedMs,
                long expectedMs,
                long candidateBytes,
                boolean checkingSize
        ) {
            this.attempt = attempt;
            this.totalAttempts = totalAttempts;
            this.fps = fps;
            this.quality = quality;
            this.percent = percent;
            this.encodedMs = encodedMs;
            this.expectedMs = expectedMs;
            this.candidateBytes = candidateBytes;
            this.checkingSize = checkingSize;
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
        File tempDir = new File(context.getCacheDir(), "animated_sticker_work");
        if (!tempDir.mkdirs() && !tempDir.isDirectory()) {
            throw new IOException("Не удалось создать временную папку");
        }

        long expectedMs = estimateDurationMs(context, sourceUri);
        File input = new File(tempDir, "input_" + System.nanoTime() + guessExtension(context, sourceUri));
        copyUri(context, sourceUri, input);

        File candidate = new File(tempDir, "candidate_" + System.nanoTime() + ".webp");
        String lastLog = "";
        try {
            for (int profileIndex = 0; profileIndex < AnimatedStickerProfiles.size(); profileIndex++) {
                AnimatedStickerProfiles.Profile profile = AnimatedStickerProfiles.get(profileIndex);
                int attempt = profileIndex + 1;
                if (candidate.exists()) candidate.delete();

                report(progressListener, new Progress(
                        attempt,
                        AnimatedStickerProfiles.size(),
                        profile.fps,
                        profile.quality,
                        0,
                        0,
                        expectedMs,
                        0,
                        false
                ));

                EncodeOutcome outcome = encode(
                        input,
                        candidate,
                        profile,
                        "libwebp_anim",
                        attempt,
                        expectedMs,
                        progressListener
                );
                lastLog = outcome.log;

                // Some Android FFmpeg builds expose only the generic libwebp encoder.
                // Try it as a fallback, but still validate the resulting WebP is actually animated.
                if (!outcome.success) {
                    if (candidate.exists()) candidate.delete();
                    outcome = encode(
                            input,
                            candidate,
                            profile,
                            "libwebp",
                            attempt,
                            expectedMs,
                            progressListener
                    );
                    lastLog = outcome.log;
                }

                if (!outcome.success || !candidate.isFile() || candidate.length() == 0) {
                    throw new IOException("FFmpeg не смог создать WebP: " + compactLog(lastLog));
                }

                if (!isAnimatedWebp(candidate)) {
                    throw new IOException(
                            "Файл не удалось превратить в анимацию: итоговый WebP содержит только один кадр. " +
                            "Попробуйте другой GIF/WebP/видеофайл. FFmpeg: " + compactLog(lastLog));
                }

                long size = candidate.length();
                report(progressListener, new Progress(
                        attempt,
                        AnimatedStickerProfiles.size(),
                        profile.fps,
                        profile.quality,
                        100,
                        expectedMs,
                        expectedMs,
                        size,
                        true
                ));

                if (size <= TARGET_ANIMATED_BYTES) {
                    copyFile(candidate, output);
                    return new Result(profile.fps, profile.quality, size);
                }
            }

            throw new IOException(
                    "Даже после сильной оптимизации файл не помещается в лимит WhatsApp 500 КБ. " +
                    "Попробуйте более короткий или менее динамичный фрагмент.");
        } finally {
            //noinspection ResultOfMethodCallIgnored
            input.delete();
            //noinspection ResultOfMethodCallIgnored
            candidate.delete();
        }
    }

    static void createTrayIcon(Context context, Uri sourceUri, File trayFile) throws IOException {
        File tempDir = new File(context.getCacheDir(), "animated_sticker_work");
        if (!tempDir.mkdirs() && !tempDir.isDirectory()) {
            throw new IOException("Не удалось создать временную папку");
        }

        File input = new File(tempDir, "tray_input_" + System.nanoTime() + guessExtension(context, sourceUri));
        copyUri(context, sourceUri, input);
        try {
            String filter = "scale=96:96:force_original_aspect_ratio=decrease:flags=lanczos," +
                    "pad=96:96:(ow-iw)/2:(oh-ih)/2:color=0x00000000";
            String command = "-y -hide_banner -loglevel error -i " + q(input) +
                    " -frames:v 1 -vf \"" + filter + "\" " + q(trayFile);
            var session = FFmpegKit.execute(command);
            if (!ReturnCode.isSuccess(session.getReturnCode()) || !trayFile.isFile()) {
                throw new IOException("Не удалось создать иконку набора: " + compactLog(session.getAllLogsAsString()));
            }
            if (trayFile.length() > 50 * 1024) {
                throw new IOException("Иконка набора превышает 50 КБ");
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            input.delete();
        }
    }

    private static EncodeOutcome encode(
            File input,
            File output,
            AnimatedStickerProfiles.Profile profile,
            String encoder,
            int attempt,
            long expectedMs,
            ProgressListener progressListener
    ) throws IOException {
        String filter = "fps=" + profile.fps +
                ",scale=512:512:force_original_aspect_ratio=decrease:flags=lanczos" +
                ",pad=512:512:(ow-iw)/2:(oh-ih)/2:color=0x00000000" +
                ",format=yuva420p";

        String command = "-y -hide_banner -loglevel error -i " + q(input) +
                " -t " + MAX_DURATION_SECONDS +
                " -an -vf \"" + filter + "\"" +
                " -c:v " + encoder +
                " -lossless 0 -preset picture -compression_level 6" +
                " -quality " + profile.quality +
                " -loop 0 -f webp " + q(output);

        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<FFmpegSession> sessionRef = new AtomicReference<>();
        AtomicInteger lastPercent = new AtomicInteger(0);
        int totalAttempts = AnimatedStickerProfiles.size();
        long expectedFrames = Math.max(1L, Math.round((expectedMs / 1000.0) * profile.fps));

        FFmpegKit.executeAsync(
                command,
                session -> {
                    sessionRef.set(session);
                    completed.countDown();
                },
                log -> { },
                statistics -> {
                    long encodedMs = Math.max(0L, Math.round(statistics.getTime()));
                    long encodedFrames = Math.max(0, statistics.getVideoFrameNumber());
                    int framePercent = (int) Math.min(99L, (encodedFrames * 100L) / expectedFrames);
                    int timePercent = expectedMs <= 0
                            ? 0
                            : (int) Math.min(99L, (encodedMs * 100L) / expectedMs);
                    int candidatePercent = encodedFrames > 0 ? framePercent : timePercent;
                    int monotonicPercent = lastPercent.updateAndGet(previous ->
                            Math.max(previous, candidatePercent));

                    report(progressListener, new Progress(
                            attempt,
                            totalAttempts,
                            profile.fps,
                            profile.quality,
                            monotonicPercent,
                            encodedMs,
                            expectedMs,
                            output.isFile() ? output.length() : 0,
                            false
                    ));
                }
        );

        try {
            completed.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Конвертация была прервана", interrupted);
        }

        FFmpegSession session = sessionRef.get();
        if (session == null) {
            return new EncodeOutcome(false, "FFmpeg session unavailable");
        }

        boolean success = ReturnCode.isSuccess(session.getReturnCode())
                && output.isFile()
                && output.length() > 0;
        return new EncodeOutcome(success, session.getAllLogsAsString());
    }

    private static void report(ProgressListener listener, Progress progress) {
        if (listener != null) listener.onProgress(progress);
    }

    private static long estimateDurationMs(Context context, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (raw != null) {
                long duration = Long.parseLong(raw);
                if (duration > 0) {
                    return Math.min(duration, MAX_DURATION_SECONDS * 1000L);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {
            }
        }
        return MAX_DURATION_SECONDS * 1000L;
    }

    private static boolean isAnimatedWebp(File file) {
        if (!file.isFile() || file.length() < 32) return false;

        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            byte[] carry = new byte[8];
            int carryLength = 0;
            boolean hasAnim = false;
            boolean hasFrame = false;
            int read;

            while ((read = input.read(buffer)) != -1) {
                byte[] combined = new byte[carryLength + read];
                System.arraycopy(carry, 0, combined, 0, carryLength);
                System.arraycopy(buffer, 0, combined, carryLength, read);
                String chunk = new String(combined, StandardCharsets.ISO_8859_1);
                if (chunk.contains("ANIM")) hasAnim = true;
                if (chunk.contains("ANMF")) hasFrame = true;
                if (hasAnim && hasFrame) return true;

                carryLength = Math.min(carry.length, combined.length);
                System.arraycopy(combined, combined.length - carryLength, carry, 0, carryLength);
            }
        } catch (IOException ignored) {
        }

        return false;
    }

    private static String compactLog(String log) {
        if (log == null || log.trim().isEmpty()) return "неизвестная ошибка";
        String compact = log.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (compact.length() > 360) {
            compact = compact.substring(compact.length() - 360);
        }
        return compact;
    }

    private static void copyUri(Context context, Uri uri, File target) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        try (InputStream input = resolver.openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("Файл недоступен");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        try (InputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private static String guessExtension(Context context, Uri uri) {
        String name = null;
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
                String ext = name.substring(dot).toLowerCase();
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
