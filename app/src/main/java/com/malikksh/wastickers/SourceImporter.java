package com.malikksh.wastickers;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.io.*;
import java.util.*;

/** Temporary grants never become long-lived draft dependencies. Call on a worker. */
final class SourceImporter {
    static Intent importResult(Context context, Intent result, String project, int limit) throws IOException {
        List<Uri> uris = new ArrayList<>();
        ClipData clip = result.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount() && uris.size() < limit; i++) uris.add(clip.getItemAt(i).getUri());
        } else if (result.getData() != null && limit > 0) uris.add(result.getData());
        File root = new File(context.getFilesDir(), "project_sources/" + project);
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Source directory unavailable");
        List<File> created = new ArrayList<>();
        ClipData imported = null;
        try {
            for (Uri uri : uris) {
                if (uri == null) continue;
                if ((result.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
                    try { context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                    catch (RuntimeException ignored) { }
                }
                String mime = context.getContentResolver().getType(uri);
                String extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                if (extension == null) {
                    String last = uri.getLastPathSegment();
                    int dot = last == null ? -1 : last.lastIndexOf('.');
                    extension = dot < 0 ? "media" : last.substring(dot + 1).toLowerCase(Locale.ROOT);
                    if (!extension.matches("[a-z0-9]{1,8}")) extension = "media";
                }
                File destination = new File(root, UUID.randomUUID() + "." + extension);
                created.add(destination);
                try (InputStream input = context.getContentResolver().openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(destination)) {
                    if (input == null) throw new IOException("Source unavailable");
                    byte[] buffer = new byte[32768];
                    long bytes = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        bytes += count;
                        if (bytes > 512L * 1024 * 1024 || Thread.currentThread().isInterrupted())
                            throw new IOException("Source too large or import cancelled");
                        output.write(buffer, 0, count);
                    }
                    output.getFD().sync();
                }
                Uri local = Uri.fromFile(destination);
                if (imported == null) imported = ClipData.newRawUri("media", local);
                else imported.addItem(new ClipData.Item(local));
            }
        } catch (IOException | RuntimeException error) {
            for (File file : created) file.delete();
            throw error;
        }
        Intent intent = new Intent();
        if (imported != null) intent.setClipData(imported);
        return intent;
    }
}
