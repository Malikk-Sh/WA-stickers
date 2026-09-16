package com.malikksh.wastickers;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class PackStore {
    static final String ROOT_DIR = "sticker_packs";
    private static final String PREFS = "sticker_pack_store";
    private static final String KEY_PACKS = "packs";

    static final class Pack {
        final String id;
        final String name;
        final int stickerCount;
        final String imageDataVersion;

        Pack(String id, String name, int stickerCount, String imageDataVersion) {
            this.id = id;
            this.name = name;
            this.stickerCount = stickerCount;
            this.imageDataVersion = imageDataVersion;
        }
    }

    private PackStore() {}

    static synchronized List<Pack> getPacks(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_PACKS, "[]");
        List<Pack> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new Pack(
                        item.getString("id"),
                        item.getString("name"),
                        item.getInt("stickerCount"),
                        item.optString("imageDataVersion", "1")
                ));
            }
        } catch (JSONException ignored) {
            prefs.edit().putString(KEY_PACKS, "[]").apply();
        }
        return result;
    }

    static synchronized Pack getPack(Context context, String id) {
        for (Pack pack : getPacks(context)) {
            if (pack.id.equals(id)) return pack;
        }
        return null;
    }

    static synchronized Pack getLatestPack(Context context) {
        List<Pack> packs = getPacks(context);
        return packs.isEmpty() ? null : packs.get(packs.size() - 1);
    }

    static synchronized void addPack(Context context, Pack pack) {
        List<Pack> packs = getPacks(context);
        packs.add(pack);

        // Keep the app lightweight while still allowing several previously-created packs.
        while (packs.size() > 10) {
            Pack removed = packs.remove(0);
            deleteRecursively(getPackDir(context, removed.id));
        }

        JSONArray array = new JSONArray();
        for (Pack item : packs) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", item.id);
                object.put("name", item.name);
                object.put("stickerCount", item.stickerCount);
                object.put("imageDataVersion", item.imageDataVersion);
                array.put(object);
            } catch (JSONException ignored) {
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PACKS, array.toString())
                .apply();
    }

    static File getPackDir(Context context, String id) {
        return new File(new File(context.getFilesDir(), ROOT_DIR), id);
    }

    static File getStickerFile(Context context, String id, String fileName) {
        return new File(getPackDir(context, id), fileName);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
