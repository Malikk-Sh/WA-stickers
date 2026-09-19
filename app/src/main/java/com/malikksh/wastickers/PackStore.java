package com.malikksh.wastickers;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
        final boolean animated;

        Pack(String id, String name, int stickerCount, String imageDataVersion) {
            this(id, name, stickerCount, imageDataVersion, false);
        }

        Pack(String id, String name, int stickerCount, String imageDataVersion, boolean animated) {
            this.id = id;
            this.name = name;
            this.stickerCount = stickerCount;
            this.imageDataVersion = imageDataVersion;
            this.animated = animated;
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
                        item.optString("imageDataVersion", "1"),
                        item.optBoolean("animated", false)
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

        while (packs.size() > 10) {
            Pack removed = packs.remove(0);
            deleteRecursively(getPackDir(context, removed.id));
        }

        savePacks(context, packs);
    }

    static synchronized Pack renamePack(Context context, String id, String newName) {
        String trimmed = newName == null ? "" : newName.trim();
        if (trimmed.isEmpty()) return null;

        List<Pack> packs = getPacks(context);
        Pack renamed = null;
        for (int i = 0; i < packs.size(); i++) {
            Pack item = packs.get(i);
            if (!item.id.equals(id)) continue;
            renamed = new Pack(
                    item.id,
                    trimmed,
                    item.stickerCount,
                    item.imageDataVersion,
                    item.animated
            );
            packs.set(i, renamed);
            break;
        }
        if (renamed != null) savePacks(context, packs);
        return renamed;
    }

    static synchronized Pack duplicatePack(Context context, String id) {
        Pack source = getPack(context, id);
        if (source == null) return null;
        File sourceDir = getPackDir(context, source.id);
        if (!sourceDir.isDirectory()) return null;

        String copyId = (source.animated ? "animated_copy_" : "pack_copy_")
                + System.currentTimeMillis();
        File destination = getPackDir(context, copyId);
        try {
            copyRecursively(sourceDir, destination);
        } catch (IOException error) {
            deleteRecursively(destination);
            return null;
        }

        Pack copy = new Pack(
                copyId,
                source.name + " — копия",
                source.stickerCount,
                source.imageDataVersion,
                source.animated
        );
        addPack(context, copy);
        return copy;
    }

    static synchronized int removeUnavailablePacks(Context context) {
        List<Pack> packs = getPacks(context);
        List<Pack> available = new ArrayList<>();
        int removed = 0;
        for (Pack pack : packs) {
            File firstSticker = getStickerFile(context, pack.id, "1.webp");
            File tray = getStickerFile(context, pack.id, "tray.png");
            if (firstSticker.isFile() && tray.isFile()) {
                available.add(pack);
            } else {
                removed++;
                deleteRecursively(getPackDir(context, pack.id));
            }
        }
        if (removed > 0) savePacks(context, available);
        return removed;
    }

    static synchronized boolean deletePack(Context context, String id) {
        List<Pack> packs = getPacks(context);
        Pack removed = null;
        for (int i = 0; i < packs.size(); i++) {
            if (packs.get(i).id.equals(id)) {
                removed = packs.remove(i);
                break;
            }
        }
        if (removed == null) return false;

        savePacks(context, packs);
        deleteRecursively(getPackDir(context, removed.id));
        return true;
    }

    private static void savePacks(Context context, List<Pack> packs) {
        JSONArray array = new JSONArray();
        for (Pack item : packs) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", item.id);
                object.put("name", item.name);
                object.put("stickerCount", item.stickerCount);
                object.put("imageDataVersion", item.imageDataVersion);
                object.put("animated", item.animated);
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

    private static void copyRecursively(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw new IOException("Could not create " + destination);
            }
            File[] children = source.listFiles();
            if (children == null) return;
            for (File child : children) {
                copyRecursively(child, new File(destination, child.getName()));
            }
            return;
        }

        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        byte[] buffer = new byte[32 * 1024];
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination, false)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) output.write(buffer, 0, read);
            }
        }
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
