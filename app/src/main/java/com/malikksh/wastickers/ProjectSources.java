package com.malikksh.wastickers;

import android.content.Context;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Source metadata is committed with output, independently of optional editor drafts. */
final class ProjectSources {
    static final class Snapshot {
        final List<Uri> items;
        final Uri cover;
        Snapshot(List<Uri> items, Uri cover) { this.items = items; this.cover = cover; }
    }
    static void write(File generation, List<Uri> items, Uri cover) throws IOException {
        try {
            JSONObject object = new JSONObject();
            JSONArray sources = new JSONArray();
            for (Uri uri : items) sources.put(uri.toString());
            object.put("items", sources);
            object.put("cover", cover == null ? "" : cover.toString());
            try (FileOutputStream output = new FileOutputStream(new File(generation, "sources.json"))) {
                output.write(object.toString().getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
        } catch (JSONException error) { throw new IOException(error); }
    }
    static Snapshot read(Context context, PackStore.Pack pack) {
        File metadata = PackStore.getStickerFile(context, pack.id, "sources.json");
        try (FileInputStream input = new FileInputStream(metadata)) {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                data.write(buffer, 0, count);
                if (data.size() > 1024 * 1024) return null;
            }
            JSONObject root = new JSONObject(data.toString("UTF-8"));
            JSONArray array = root.getJSONArray("items");
            List<Uri> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                Uri uri = Uri.parse(array.getString(i));
                if (!EditorInstanceStateBridge.canReadUri(context, uri)) return null;
                items.add(uri);
            }
            Uri cover = Uri.parse(root.optString("cover", ""));
            if (items.size() != pack.stickerCount) return null;
            return new Snapshot(items, items.contains(cover) ? cover : items.get(0));
        } catch (Exception error) { return null; }
    }
}
