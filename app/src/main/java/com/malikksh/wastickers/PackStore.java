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
        final String generation;
        final int photoCount;
        final long updatedAt;

        Pack(String id, String name, int stickerCount, String imageDataVersion) {
            this(id, name, stickerCount, imageDataVersion, false);
        }

        Pack(String id, String name, int stickerCount, String imageDataVersion, boolean animated) {
            this(id, name, stickerCount, imageDataVersion, animated, "", animated ? 0 : stickerCount, 0);
        }

        Pack(String id, String name, int stickerCount, String imageDataVersion, boolean animated,
             String generation, int photoCount, long updatedAt) {
            this.generation = generation;
            this.photoCount = photoCount;
            this.updatedAt = updatedAt;
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
                        item.optBoolean("animated", false),
                        item.optString("generation", ""),
                        item.optInt("photoCount", item.optBoolean("animated", false) ? 0 : item.getInt("stickerCount")),
                        item.optLong("updatedAt", 0)
                ));
            }
        } catch (JSONException ignored) {
            throw new IllegalStateException("Не удалось прочитать сохранённые наборы", ignored);
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
        if (!PackNames.valid(pack.name) || !isNameAvailable(context, pack.name, pack.id)) {
            throw new IllegalArgumentException("Набор с таким названием уже существует");
        }
        packs.removeIf(existing -> existing.id.equals(pack.id));
        packs.add(pack);
        savePacks(context, packs);
    }

    static synchronized Pack renamePack(Context context, String id, String newName) {
        String trimmed = PackNames.display(newName);
        if (!PackNames.valid(trimmed) || !isNameAvailable(context, trimmed, id)) return null;

        List<Pack> packs = getPacks(context);
        Pack renamed = null;
        for (int i = 0; i < packs.size(); i++) {
            Pack item = packs.get(i);
            if (!item.id.equals(id)) continue;
            renamed = new Pack(
                    item.id,
                    trimmed,
                    item.stickerCount,
                    item.name.equals(trimmed) ? item.imageDataVersion : PackNames.nextVersion(item.imageDataVersion),
                    item.animated, item.generation, item.photoCount, System.currentTimeMillis()
            );
            packs.set(i, renamed);
            break;
        }
        if (renamed != null) {
            savePacks(context, packs);
            EditorInstanceStateBridge.renameProject(context, id, trimmed);
        }
        return renamed;
    }

    static synchronized Pack duplicatePack(Context context, String id) {
        Pack source = getPack(context, id);
        if (source == null) return null;
        File sourceDir = getPackDir(context, source.id);
        if (!sourceDir.isDirectory()) return null;

        String copyId = "pack_" + java.util.UUID.randomUUID();
        File destination = getPackDir(context, copyId);
        try {
            copyRecursively(sourceDir, destination);
        } catch (IOException error) {
            deleteRecursively(destination);
            return null;
        }

        Pack copy = new Pack(
                copyId,
                nextName(context, source.name + " — копия"),
                source.stickerCount,
                "1",
                source.animated, source.generation, source.photoCount, System.currentTimeMillis()
        );
        File copyGeneration = copy.generation.isEmpty() ? destination : new File(destination, copy.generation);
        new File(copyGeneration, "sources.json").delete();
        ProjectSources.Snapshot originals = ProjectSources.read(context, source);
        if (originals != null) {
            try {
                android.content.Intent selection = new android.content.Intent();
                android.content.ClipData clip = android.content.ClipData.newRawUri("media", originals.items.get(0));
                for (int i = 1; i < originals.items.size(); i++) clip.addItem(new android.content.ClipData.Item(originals.items.get(i)));
                selection.setClipData(clip);
                android.content.ClipData imported = SourceImporter.importResult(context, selection, copyId, 30).getClipData();
                List<android.net.Uri> items = new ArrayList<>();
                for (int i = 0; i < imported.getItemCount(); i++) items.add(imported.getItemAt(i).getUri());
                List<VideoTrimStore.Entry> trims = new ArrayList<>();
                for (VideoTrimStore.Entry trim : originals.trims) {
                    int index = originals.items.indexOf(trim.uri);
                    if (index >= 0) {
                        android.net.Uri uri = items.get(index);
                        trims.add(new VideoTrimStore.Entry(uri.toString(), uri, trim.displayName, trim.durationMs, trim.startOffsetMs, trim.endOffsetMs));
                    }
                }
                ProjectSources.write(copyGeneration, items, items.get(Math.max(0, originals.items.indexOf(originals.cover))), trims);
            } catch (IOException error) {
                deleteRecursively(destination);
                deleteRecursively(new File(context.getFilesDir(), "project_sources/" + copyId));
                return null;
            }
        }
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
        deleteRecursively(new File(context.getFilesDir(), "project_sources/" + removed.id));
        EditorInstanceStateBridge.removeProject(context, removed.id);
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
                object.put("generation", item.generation);
                object.put("photoCount", item.photoCount);
                object.put("updatedAt", item.updatedAt);
                array.put(object);
            } catch (JSONException ignored) {
            }
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String previous = prefs.getString(KEY_PACKS, "[]");
        if (!prefs.edit().putString(KEY_PACKS, array.toString()).commit()) {
            // SharedPreferences publishes to memory before disk; restore it on failed durable commit.
            prefs.edit().putString(KEY_PACKS, previous).commit();
            throw new IllegalStateException("Не удалось сохранить наборы");
        }
        notifyChanged(context);

    }

    static File getPackDir(Context context, String id) {
        return new File(new File(context.getFilesDir(), ROOT_DIR), id);
    }

    static synchronized File getStickerFile(Context context, String id, String fileName) {
        Pack pack = getPack(context, id);
        File root = getPackDir(context, id);
        return new File(pack == null || pack.generation.isEmpty() ? root : new File(root, pack.generation), fileName);
    }

    interface Listener { void onPacksChanged(); }
    private static final java.util.Set<Listener> listeners = new java.util.concurrent.CopyOnWriteArraySet<>();
    static void observe(Listener listener) { listeners.add(listener); }
    static void stopObserving(Listener listener) { listeners.remove(listener); }
    static void notifyChanged(Context context) {
        String base = "content://" + context.getPackageName() + ".stickercontentprovider";
        context.getContentResolver().notifyChange(android.net.Uri.parse(base), null);
        context.getContentResolver().notifyChange(android.net.Uri.parse(base + "/metadata"), null);
        for (Pack pack : getPacks(context)) {
            context.getContentResolver().notifyChange(android.net.Uri.parse(base + "/metadata/" + pack.id), null);
            context.getContentResolver().notifyChange(android.net.Uri.parse(base + "/stickers/" + pack.id), null);
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            for (Listener listener : listeners) listener.onPacksChanged();
        });
    }
    static boolean isNameAvailable(Context context, String name, String excludeId) {
        for (Pack pack : getPacks(context)) {
            if (!pack.id.equals(excludeId) && PackNames.key(pack.name).equals(PackNames.key(name))) return false;
        }
        return EditorInstanceStateBridge.draftNameAvailable(context, name, excludeId);
    }
    static String nextName(Context context, String base) {
        List<String> names = new ArrayList<>();
        for (Pack pack : getPacks(context)) names.add(pack.name);
        names.addAll(EditorInstanceStateBridge.draftNames(context));
        return PackNames.next(base, names);
    }
    static File stagingDir(Context context, String id) {
        return new File(getPackDir(context, id), "generation_" + java.util.UUID.randomUUID());
    }
    /** Only the durable manifest pointer publishes a generation. Failed builds leave the old one live. */
    static synchronized Pack commitGeneration(Context context, String id, String name, int count,
                                               boolean animated, File staging, int photos) throws IOException {
        if (count < 3 || count > 30 || !new File(staging, "tray.png").isFile()) {
            throw new IOException("Неполный набор");
        }
        for (int i = 1; i <= count; i++) {
            File sticker = new File(staging, i + ".webp");
            if (!sticker.isFile() || sticker.length() == 0 || sticker.length() > (animated ? 500 * 1024 : 100 * 1024))
                throw new IOException("Некорректный стикер " + i);
        }
        if (!staging.getCanonicalFile().getParentFile().equals(getPackDir(context, id).getCanonicalFile()))
            throw new IOException("Неверная папка сборки");
        for (int i = 0; i <= count; i++) {
            File file = new File(staging, i == 0 ? "tray.png" : i + ".webp");
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (i > 0) {
                MediaAnimationInspector.AnimationKind kind = new SourceAnimationDetector(context)
                        .classify(android.net.Uri.fromFile(file));
                if (kind != (animated ? MediaAnimationInspector.AnimationKind.ANIMATED : MediaAnimationInspector.AnimationKind.STATIC))
                    throw new IOException("Некорректный тип стикера");
            }
            int size = i == 0 ? 96 : 512;
            if (bounds.outWidth != size || bounds.outHeight != size || (i == 0 && file.length() > 50 * 1024))
                throw new IOException("Некорректный размер изображения");
            try (java.io.RandomAccessFile output = new java.io.RandomAccessFile(file, "rw")) { output.getFD().sync(); }
        }
        Pack previous = getPack(context, id);
        if (previous != null && previous.name.equals(PackNames.display(name))
                && previous.stickerCount == count && previous.animated == animated && previous.photoCount == photos) {
            boolean unchanged = sameFile(getStickerFile(context, id, "tray.png"), new File(staging, "tray.png"));
            for (int i = 1; i <= count && unchanged; i++)
                unchanged = sameFile(getStickerFile(context, id, i + ".webp"), new File(staging, i + ".webp"));
            if (unchanged && sameFile(getStickerFile(context, id, "sources.json"), new File(staging, "sources.json"))) {
                deleteRecursively(staging);
                return previous;
            }
        }
        Pack next = new Pack(id, PackNames.display(name), count,
                previous == null ? "1" : PackNames.nextVersion(previous.imageDataVersion), animated,
                staging.getName(), photos, System.currentTimeMillis());
        try { addPack(context, next); }
        catch (RuntimeException error) { throw new IOException(error.getMessage(), error); }
        // Legacy flat packs are retained for old URI references; generation sources live elsewhere.
        if (previous != null && !previous.generation.isEmpty() && !previous.generation.equals(next.generation)) {
            deleteRecursively(new File(getPackDir(context, id), previous.generation));
        }
        return next;
    }

    private static boolean sameFile(File first, File second) throws IOException {
        if (!first.isFile() || first.length() != second.length()) return false;
        try (java.io.BufferedInputStream a = new java.io.BufferedInputStream(new FileInputStream(first));
             java.io.BufferedInputStream b = new java.io.BufferedInputStream(new FileInputStream(second))) {
            int next;
            while ((next = a.read()) != -1) if (next != b.read()) return false;
            return b.read() == -1;
        }
    }

    static void copyRecursively(File source, File destination) throws IOException {
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
            output.getFD().sync();
        }
    }

    static void deleteRecursively(File file) {
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
