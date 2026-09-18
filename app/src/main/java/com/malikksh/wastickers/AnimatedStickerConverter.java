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
import android.net.Uri;
import android.provider.OpenableColumns;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

final class AnimatedStickerConverter {
    static final int MAX_ANIMATED_BYTES = 500 * 1024;
    static final int MAX_DURATION_SECONDS = 10;

    private static final int TARGET_ANIMATED_BYTES = 490_000;
    private static final int REQUIRED_SIZE = 512;
    private static final int MIN_FRAME_DURATION_MS = 8;

    private static final Profile[] PROFILES = new Profile[]{
            new Profile(18, 92),
            new Profile(14, 92),
            new Profile(10, 92),
            new Profile(8, 92),
            new Profile(6, 92),
            new Profile(5, 92),
            new Profile(4, 92),
            new Profile(3, 92),
            new Profile(2, 92),
            new Profile(1, 92),
            new Profile(3, 84),
            new Profile(2, 84),
            new Profile(1, 84),
            new Profile(2, 76),
            new Profile(1, 76),
            new Profile(1, 66),
            new Profile(1, 56),
            new Profile(1, 46),
            new Profile(1, 36)
    };

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

    private static final class Profile {
        final int fps;
        final int quality;

        Profile(int fps, int quality) {
            this.fps = fps;
            this.quality = quality;
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
        File tempDir = new File(context.getCacheDir(), "animated_sticker_work");
        if (!tempDir.mkdirs() && !tempDir.isDirectory()) {
            throw new IOException("Не удалось создать временную папку");
        }

        File input = new File(tempDir, "input_" + System.nanoTime() + guessExtension(context, sourceUri));
        copyUri(context, sourceUri, input);

        File candidate = new File(tempDir, "candidate_" + System.nanoTime() + ".webp");
        String lastLog = "";
        try {
            WebpInfo webp = inspectWebp(input);
            if (webp.valid && webp.animated) {
                BugLogStore.appendApp("Animated WebP detected: " + webp.width + "x" + webp.height
                        + ", frames=" + webp.frameCount
                        + ", durationMs=" + webp.totalDurationMs
                        + ", minFrameMs=" + (webp.minFrameDurationMs == Integer.MAX_VALUE
                        ? "unknown" : webp.minFrameDurationMs)
                        + ", bytes=" + input.length());

                if (isAnimationPayloadWhatsAppCompatible(webp, input.length())) {
                    if (webp.width == REQUIRED_SIZE && webp.height == REQUIRED_SIZE) {
                        copyFile(input, output);
                        BugLogStore.appendApp("Animated WebP passthrough: already 512x512; original bytes preserved");
                        return new Result(webp.approximateFps(), 100, input.length());
                    }

                    if (webp.width <= REQUIRED_SIZE && webp.height <= REQUIRED_SIZE) {
                        normalizeAnimatedWebpCanvas(input, output, webp);
                        WebpInfo normalized = inspectWebp(output);
                        if (isDirectlyWhatsAppCompatible(normalized, output.length())) {
                            BugLogStore.appendApp("Animated WebP lossless canvas normalization: "
                                    + webp.width + "x" + webp.height + " -> 512x512; frame bitstreams unchanged");
                            return new Result(normalized.approximateFps(), 100, output.length());
                        }
                        //noinspection ResultOfMethodCallIgnored
                        output.delete();
                        BugLogStore.appendApp("Lossless WebP canvas normalization validation failed; trying FFmpeg");
                    }
                }

                BugLogStore.appendApp("Animated WebP requires pixel re-encode; trying FFmpeg conversion");
            }

            for (Profile profile : PROFILES) {
                if (candidate.exists()) candidate.delete();

                EncodeOutcome outcome = encode(input, candidate, profile, "libwebp_anim");
                lastLog = outcome.log;

                if (!outcome.success) {
                    if (candidate.exists()) candidate.delete();
                    outcome = encode(input, candidate, profile, "libwebp");
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
                if (size <= TARGET_ANIMATED_BYTES) {
                    copyFile(candidate, output);
                    return new Result(profile.fps, profile.quality, size);
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

    static void createTrayIcon(Context context, Uri sourceUri, File trayFile) throws IOException {
        File tempDir = new File(context.getCacheDir(), "animated_sticker_work");
        if (!tempDir.mkdirs() && !tempDir.isDirectory()) {
            throw new IOException("Не удалось создать временную папку");
        }

        File input = new File(tempDir, "tray_input_" + System.nanoTime() + guessExtension(context, sourceUri));
        copyUri(context, sourceUri, input);
        try {
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

            String filter = "scale=96:96:force_original_aspect_ratio=decrease:flags=lanczos,"
                    + "pad=96:96:(ow-iw)/2:(oh-ih)/2:color=0x00000000";
            String command = "-y -hide_banner -loglevel error -i " + q(input)
                    + " -frames:v 1 -vf \"" + filter + "\" " + q(trayFile);

            try {
                var session = FFmpegKit.execute(command);
                String log = session.getAllLogsAsString();
                BugLogStore.appendFfmpeg(log);
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

    private static boolean isAnimationPayloadWhatsAppCompatible(WebpInfo info, long bytes) {
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
                && info.maxFrameBottom <= info.height
                && bytes > 0
                && bytes <= TARGET_ANIMATED_BYTES;
    }

    private static boolean isDirectlyWhatsAppCompatible(WebpInfo info, long bytes) {
        return isAnimationPayloadWhatsAppCompatible(info, bytes)
                && info.width == REQUIRED_SIZE
                && info.height == REQUIRED_SIZE;
    }

    /**
     * Enlarges an animated WebP canvas to 512x512 without decoding/re-encoding a single frame.
     * VP8/VP8L frame payloads remain byte-for-byte unchanged. We only change the VP8X canvas
     * size and translate every ANMF frame rectangle by the same even-pixel offset.
     */
    private static void normalizeAnimatedWebpCanvas(File input, File output, WebpInfo sourceInfo)
            throws IOException {
        if (sourceInfo.width <= 0 || sourceInfo.height <= 0
                || sourceInfo.width > REQUIRED_SIZE || sourceInfo.height > REQUIRED_SIZE) {
            throw new IOException("WebP canvas нельзя расширить lossless до 512x512");
        }

        copyFile(input, output);

        int dx = evenCenterOffset(REQUIRED_SIZE, sourceInfo.width);
        int dy = evenCenterOffset(REQUIRED_SIZE, sourceInfo.height);
        boolean patchedVp8x = false;
        int patchedFrames = 0;

        try (RandomAccessFile raf = new RandomAccessFile(output, "rw")) {
            if (!"RIFF".equals(readFourCc(raf))) throw new IOException("Некорректный RIFF WebP");
            readUInt32LE(raf);
            if (!"WEBP".equals(readFourCc(raf))) throw new IOException("Некорректный WEBP контейнер");

            while (raf.getFilePointer() + 8 <= raf.length()) {
                String chunk = readFourCc(raf);
                long chunkSize = readUInt32LE(raf);
                long dataStart = raf.getFilePointer();
                long dataEnd = dataStart + chunkSize;
                if (chunkSize < 0 || dataEnd < dataStart || dataEnd > raf.length()) {
                    throw new IOException("Повреждённый WebP chunk: " + chunk);
                }

                if ("VP8X".equals(chunk) && chunkSize >= 10) {
                    raf.seek(dataStart);
                    int flags = raf.readUnsignedByte();
                    raf.seek(dataStart);
                    raf.write(flags | 0x02); // animation feature flag
                    writeUInt24LE(raf, dataStart + 4, REQUIRED_SIZE - 1);
                    writeUInt24LE(raf, dataStart + 7, REQUIRED_SIZE - 1);
                    patchedVp8x = true;
                } else if ("ANMF".equals(chunk) && chunkSize >= 16) {
                    raf.seek(dataStart);
                    int oldX = readUInt24LE(raf) * 2;
                    int oldY = readUInt24LE(raf) * 2;
                    int frameWidth = readUInt24LE(raf) + 1;
                    int frameHeight = readUInt24LE(raf) + 1;

                    int newX = oldX + dx;
                    int newY = oldY + dy;
                    if ((newX & 1) != 0 || (newY & 1) != 0
                            || newX < 0 || newY < 0
                            || newX + frameWidth > REQUIRED_SIZE
                            || newY + frameHeight > REQUIRED_SIZE) {
                        throw new IOException("Не удалось корректно центрировать ANMF кадр в 512x512");
                    }

                    writeUInt24LE(raf, dataStart, newX / 2);
                    writeUInt24LE(raf, dataStart + 3, newY / 2);
                    patchedFrames++;
                }

                long next = dataEnd + (chunkSize & 1L);
                raf.seek(next);
            }
        }

        if (!patchedVp8x || patchedFrames != sourceInfo.frameCount) {
            //noinspection ResultOfMethodCallIgnored
            output.delete();
            throw new IOException("Не удалось обновить все chunks animated WebP");
        }
    }

    private static int evenCenterOffset(int target, int source) {
        int offset = Math.max(0, (target - source) / 2);
        return offset & ~1;
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
                    && (!info.animated || (info.maxFrameRight <= info.width && info.maxFrameBottom <= info.height));
        } catch (IOException ignored) {
            return new WebpInfo();
        }

        return info;
    }

    private static EncodeOutcome encode(File input, File output, Profile profile, String encoder) {
        String filter = "fps=" + profile.fps
                + ",scale=512:512:force_original_aspect_ratio=decrease:flags=lanczos"
                + ",pad=512:512:(ow-iw)/2:(oh-ih)/2:color=0x00000000"
                + ",format=yuva420p";

        String command = "-y -hide_banner -loglevel error -i " + q(input)
                + " -t " + MAX_DURATION_SECONDS
                + " -an -vf \"" + filter + "\""
                + " -c:v " + encoder
                + " -lossless 0 -preset picture -compression_level 6"
                + " -quality " + profile.quality
                + " -loop 0 -f webp " + q(output);

        try {
            var session = FFmpegKit.execute(command);
            String log = session.getAllLogsAsString();
            BugLogStore.appendFfmpeg("encoder=" + encoder + ", fps=" + profile.fps
                    + ", quality=" + profile.quality + "\n" + log);
            boolean success = ReturnCode.isSuccess(session.getReturnCode())
                    && output.isFile()
                    && output.length() > 0;
            return new EncodeOutcome(success, log);
        } catch (Throwable ffmpegError) {
            String message = "FFmpegKit startup/execute failure: " + describeThrowable(ffmpegError);
            BugLogStore.appendFfmpeg(message);
            return new EncodeOutcome(false, message);
        }
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

    private static void writeUInt24LE(RandomAccessFile raf, long position, int value) throws IOException {
        if (value < 0 || value > 0xFFFFFF) throw new IOException("24-bit WebP value out of range");
        raf.seek(position);
        raf.write(value & 0xFF);
        raf.write((value >> 8) & 0xFF);
        raf.write((value >> 16) & 0xFF);
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
