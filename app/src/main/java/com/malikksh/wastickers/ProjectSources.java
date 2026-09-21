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
        final List<VideoTrimStore.Entry> trims;
        Snapshot(List<Uri> items, Uri cover, List<VideoTrimStore.Entry> trims) {
            this.items = items; this.cover = cover; this.trims = trims;
        }
    }
    static void write(File generation, List<Uri> items, Uri cover) throws IOException {
        List<String> keys = new ArrayList<>();
        for (Uri uri : items) keys.add(uri.toString());
        write(generation, items, cover, VideoTrimStore.getEntries(keys));
    }
    static void write(File generation, List<Uri> items, Uri cover, List<VideoTrimStore.Entry> trims) throws IOException {
        try {
            JSONObject object = new JSONObject();
            JSONArray sources = new JSONArray();
            for (Uri uri : items) sources.put(uri.toString());
            object.put("items", sources);
            object.put("cover", cover == null ? "" : cover.toString());
            JSONArray trimValues = new JSONArray();
            for (VideoTrimStore.Entry entry : trims) {
                JSONObject trim = new JSONObject();
                trim.put("uri", entry.uri.toString());
                trim.put("duration", entry.durationMs);
                trim.put("start", entry.startOffsetMs);
                trimValues.put(trim);
            }
            object.put("trims", trimValues);
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
            List<VideoTrimStore.Entry> trims = new ArrayList<>();
            JSONArray trimValues = root.optJSONArray("trims");
            if (trimValues != null) for (int i = 0; i < trimValues.length(); i++) {
                JSONObject trim = trimValues.getJSONObject(i);
                Uri uri = Uri.parse(trim.getString("uri"));
                if (items.contains(uri)) trims.add(new VideoTrimStore.Entry(uri.toString(), uri, "Видео",
                        trim.getLong("duration"), trim.getLong("start")));
            }
            return new Snapshot(items, items.contains(cover) ? cover : items.get(0), trims);
        } catch (Exception error) { return null; }
    }
}
