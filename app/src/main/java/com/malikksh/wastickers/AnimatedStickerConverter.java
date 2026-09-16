package com.malikksh.wastickers;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class AnimatedStickerConverter {
    static final int MAX_ANIMATED_BYTES = 500 * 1024;
    static final int MAX_DURATION_SECONDS = 10;

    private static final Profile[] PROFILES = new Profile[]{
            new Profile(18, 90),
            new Profile(15, 88),
            new Profile(12, 86),
            new Profile(10, 84),
            new Profile(8, 82),
            new Profile(7, 78),
            new Profile(6, 74),
            new Profile(5, 68),
            new Profile(4, 58),
            new Profile(4, 48)
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

    private AnimatedStickerConverter() {}

    static Result convert(Context context, Uri sourceUri, File output) throws IOException {
        File tempDir = new File(context.getCacheDir(), "animated_sticker_work");
        if (!tempDir.mkdirs() && !tempDir.isDirectory()) {
            throw new IOException("Не удалось создать временную папку");
        }

        File input = new File(tempDir, "input_" + System.nanoTime() + guessExtension(context, sourceUri));
        copyUri(context, sourceUri, input);

        File candidate = new File(tempDir, "candidate_" + System.nanoTime() + ".webp");
        try {
            for (Profile profile : PROFILES) {
                if (candidate.exists()) candidate.delete();
                encode(input, candidate, profile);
                long size = candidate.length();
                if (size > 0 && size <= MAX_ANIMATED_BYTES) {
                    copyFile(candidate, output);
                    return new Result(profile.fps, profile.quality, size);
                }
            }
            throw new IOException("Файл слишком сложный для лимита WhatsApp 500 КБ. Попробуйте более короткий фрагмент.");
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
                    " -frames:v 1 -vf \"" + filter + "\" -compression_level 9 " + q(trayFile);
            var session = FFmpegKit.execute(command);
            if (!ReturnCode.isSuccess(session.getReturnCode()) || !trayFile.isFile()) {
                throw new IOException("Не удалось создать иконку анимированного набора");
            }
            if (trayFile.length() > 50 * 1024) {
                throw new IOException("Иконка набора превышает 50 КБ");
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            input.delete();
        }
    }

    private static void encode(File input, File output, Profile profile) throws IOException {
        String filter = "fps=" + profile.fps +
                ",scale=512:512:force_original_aspect_ratio=decrease:flags=lanczos" +
                ",pad=512:512:(ow-iw)/2:(oh-ih)/2:color=0x00000000" +
                ",format=yuva420p";

        String command = "-y -hide_banner -loglevel error -i " + q(input) +
                " -t " + MAX_DURATION_SECONDS +
                " -an -vf \"" + filter + "\"" +
                " -c:v libwebp_anim -lossless 0 -compression_level 6" +
                " -quality " + profile.quality +
                " -loop 0 " + q(output);

        var session = FFmpegKit.execute(command);
        if (!ReturnCode.isSuccess(session.getReturnCode()) || !output.isFile() || output.length() == 0) {
            throw new IOException("Не удалось преобразовать этот формат в анимированный WebP");
        }
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
        try (InputStream input = new java.io.FileInputStream(source);
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
