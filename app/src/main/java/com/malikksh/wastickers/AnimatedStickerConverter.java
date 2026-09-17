package com.malikksh.wastickers;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class AnimatedStickerConverter {
    static final int MAX_ANIMATED_BYTES = 500 * 1024;
    static final int MAX_DURATION_SECONDS = 10;

    // A little headroom avoids WhatsApp builds that interpret "500 KB" more strictly.
    private static final int TARGET_ANIMATED_BYTES = 490_000;

    // Preserve per-frame detail first. FPS is reduced before quality becomes aggressive.
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
            for (Profile profile : PROFILES) {
                if (candidate.exists()) candidate.delete();

                EncodeOutcome outcome = encode(input, candidate, profile, "libwebp_anim");
                lastLog = outcome.log;

                // Some Android FFmpeg builds expose only the generic libwebp encoder.
                // Try it as a fallback, but still validate the resulting WebP is actually animated.
                if (!outcome.success) {
                    if (candidate.exists()) candidate.delete();
                    outcome = encode(input, candidate, profile, "libwebp");
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

    private static EncodeOutcome encode(File input, File output, Profile profile, String encoder) {
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

        var session = FFmpegKit.execute(command);
        boolean success = ReturnCode.isSuccess(session.getReturnCode())
                && output.isFile()
                && output.length() > 0;
        return new EncodeOutcome(success, session.getAllLogsAsString());
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
