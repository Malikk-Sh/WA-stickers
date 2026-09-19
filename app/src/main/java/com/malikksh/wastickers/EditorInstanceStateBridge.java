package com.malikksh.wastickers;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists editor drafts across Activity recreation and fresh app launches.
 *
 * Runtime access uses MainActivity's package-private typed surface so persistence owns serialization only;
 * conversion/build logic and private activity implementation details stay outside this bridge.
 */
final class EditorInstanceStateBridge {
    private static final String PREFIX = "editor.instance.";
    private static final String KEY_ACTIVE_ANIMATED = PREFIX + "active_animated";
    private static final String KEY_WAS_PROCESSING = PREFIX + "was_processing";
    private static final String KEY_HAD_PENDING_BUILD = PREFIX + "had_pending_build";
    private static final String KEY_CURRENT_PACK_ID = PREFIX + "current_pack_id";
    private static final String PREFS = "editor_persistent_draft";
    private static final String PREF_STATE = "state_json";
    private static final int MAX_RESTORED_ITEMS = 30;

    private EditorInstanceStateBridge() {}

    static void save(MainActivity activity, Bundle outState) {
        if (activity == null || outState == null) return;
        try {
            boolean animated = activity.runtimeIsAnimatedMode();
            List<Uri> selected = activity.runtimeSelectedUrisSnapshot();
            Uri cover = activity.runtimeCoverUri();
            EditText packName = activity.runtimePackName();
            EditorStateController<Uri> controller =
                    activity.runtimeEditorStateController();
            if (controller == null) throw new IllegalStateException("Editor state controller is missing");

            controller.capture(
                    animated,
                    selected,
                    cover,
                    packName == null ? "" : packName.getText().toString()
            );
            writeSnapshot(outState, "photo", controller.snapshot(false));
            writeSnapshot(outState, "animated", controller.snapshot(true));
            outState.putBoolean(KEY_ACTIVE_ANIMATED, animated);
            outState.putBoolean(KEY_WAS_PROCESSING,
                    activity.runtimeIsProcessing());

            PackBuildSession<?> buildSession = activity.runtimeBuildSession();
            outState.putBoolean(KEY_HAD_PENDING_BUILD,
                    buildSession != null && buildSession.isActive());

            PackStore.Pack currentPack = activity.runtimeCurrentPack();
            if (currentPack != null) outState.putString(KEY_CURRENT_PACK_ID, currentPack.id);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not save editor instance state: " + error);
        }
    }

    static boolean restore(MainActivity activity, Bundle savedState) {
        if (activity == null || savedState == null || !savedState.containsKey(KEY_ACTIVE_ANIMATED)) {
            return false;
        }
        try {
            EditorStateController<Uri> controller =
                    activity.runtimeEditorStateController();
            if (controller == null) return false;
            restoreSnapshot(savedState, "photo", false, controller);
            restoreSnapshot(savedState, "animated", true, controller);

            boolean animated = savedState.getBoolean(KEY_ACTIVE_ANIMATED, false);
            EditorStateController.Snapshot<Uri> active = controller.snapshot(animated);

            String currentPackId = savedState.getString(KEY_CURRENT_PACK_ID);
            PackStore.Pack currentPack = currentPackId == null
                    ? null
                    : PackStore.getPack(activity, currentPackId);
            if (currentPack != null && currentPack.animated != animated) currentPack = null;

            if (!activity.runtimeRestoreEditorState(animated, active.items(), active.cover(), active.name(), currentPack)) {
                return false;
            }

            if (savedState.getBoolean(KEY_WAS_PROCESSING, false)
                    || savedState.getBoolean(KEY_HAD_PENDING_BUILD, false)) {
                TextView status = activity.runtimeStatusText();
                if (status != null) {
                    status.setText("Редактор восстановлен. Незавершённая обработка была остановлена — создайте набор снова.");
                }
            }
            return true;
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not restore editor instance state: " + error);
            return false;
        }
    }

    static void savePersistent(MainActivity activity) {
        if (activity == null) return;
        if (!AppSettings.keepDrafts(activity)) {
            clearPersistent(activity);
            return;
        }
        Bundle state = new Bundle();
        save(activity, state);
        if (!state.containsKey(KEY_ACTIVE_ANIMATED)) return;
        try {
            JSONObject root = new JSONObject();
            root.put("activeAnimated", state.getBoolean(KEY_ACTIVE_ANIMATED, false));
            root.put("wasProcessing", state.getBoolean(KEY_WAS_PROCESSING, false));
            root.put("hadPendingBuild", state.getBoolean(KEY_HAD_PENDING_BUILD, false));
            root.put("currentPackId", state.getString(KEY_CURRENT_PACK_ID));
            root.put("photo", snapshotToJson(state, "photo"));
            root.put("animated", snapshotToJson(state, "animated"));
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_STATE, root.toString())
                    .commit();
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not persist editor draft: " + error);
        }
    }

    static boolean restorePersistent(MainActivity activity) {
        if (activity == null || !AppSettings.keepDrafts(activity)) return false;
        SharedPreferences preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = preferences.getString(PREF_STATE, null);
        if (raw == null || raw.trim().isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(raw);
            Bundle state = new Bundle();
            state.putBoolean(KEY_ACTIVE_ANIMATED, root.optBoolean("activeAnimated", false));
            state.putBoolean(KEY_WAS_PROCESSING, root.optBoolean("wasProcessing", false));
            state.putBoolean(KEY_HAD_PENDING_BUILD, root.optBoolean("hadPendingBuild", false));
            String currentPackId = root.optString("currentPackId", null);
            if (currentPackId != null && !currentPackId.isEmpty()) {
                state.putString(KEY_CURRENT_PACK_ID, currentPackId);
            }
            jsonToSnapshot(activity, state, "photo", root.optJSONObject("photo"));
            jsonToSnapshot(activity, state, "animated", root.optJSONObject("animated"));
            return restore(activity, state);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not restore persistent editor draft: " + error);
            preferences.edit().remove(PREF_STATE).apply();
            return false;
        }
    }

    static boolean hasPersistent(Context context) {
        if (context == null || !AppSettings.keepDrafts(context)) return false;
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PREF_STATE, null);
        return raw != null && !raw.trim().isEmpty();
    }

    static void clearPersistent(Context context) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_STATE)
                .commit();
    }

    static boolean isAnimatedMode(MainActivity activity) {
        return activity.runtimeIsAnimatedMode();
    }

    static void setGalleryClickListener(MainActivity activity, View.OnClickListener listener) {
        if (activity == null || listener == null) return;
        activity.runtimeSetGalleryClickListener(listener);
    }

    static boolean canReadUri(Context context, Uri uri) {
        if (context == null || uri == null) return false;
        String scheme = uri.getScheme();
        if ("file".equalsIgnoreCase(scheme)) {
            String path = uri.getPath();
            return path != null && new File(path).isFile();
        }
        if ("content".equalsIgnoreCase(scheme)) {
            try {
                for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
                    if (permission.isReadPermission() && uri.equals(permission.getUri())) return true;
                }
            } catch (Throwable ignored) {
            }
            return false;
        }
        return false;
    }

    private static JSONObject snapshotToJson(Bundle state, String slot) throws Exception {
        JSONObject object = new JSONObject();
        JSONArray items = new JSONArray();
        ArrayList<String> storedItems = state.getStringArrayList(key(slot, "items"));
        if (storedItems != null) {
            for (String item : storedItems) if (item != null) items.put(item);
        }
        object.put("items", items);
        object.put("cover", state.getString(key(slot, "cover")));
        object.put("name", state.getString(key(slot, "name"), ""));
        return object;
    }

    private static void jsonToSnapshot(
            MainActivity activity,
            Bundle state,
            String slot,
            JSONObject object
    ) {
        ArrayList<String> items = new ArrayList<>();
        if (object != null) {
            JSONArray storedItems = object.optJSONArray("items");
            if (storedItems != null) {
                for (int i = 0; i < storedItems.length() && items.size() < MAX_RESTORED_ITEMS; i++) {
                    String raw = storedItems.optString(i, null);
                    if (raw == null) continue;
                    Uri uri = Uri.parse(raw);
                    if (canReadUri(activity, uri)) items.add(raw);
                }
            }
        }
        state.putStringArrayList(key(slot, "items"), items);

        String rawCover = object == null ? null : object.optString("cover", null);
        if (rawCover != null && items.contains(rawCover)) {
            state.putString(key(slot, "cover"), rawCover);
        }
        state.putString(key(slot, "name"), object == null ? "" : object.optString("name", ""));
    }

    private static void writeSnapshot(
            Bundle outState,
            String slot,
            EditorStateController.Snapshot<Uri> snapshot
    ) {
        ArrayList<String> items = new ArrayList<>();
        for (Uri uri : snapshot.items()) {
            if (uri != null && items.size() < MAX_RESTORED_ITEMS) items.add(uri.toString());
        }
        outState.putStringArrayList(key(slot, "items"), items);
        outState.putString(key(slot, "cover"),
                snapshot.cover() == null ? null : snapshot.cover().toString());
        outState.putString(key(slot, "name"), snapshot.name());
    }

    private static void restoreSnapshot(
            Bundle savedState,
            String slot,
            boolean animated,
            EditorStateController<Uri> controller
    ) {
        List<Uri> items = new ArrayList<>();
        ArrayList<String> storedItems = savedState.getStringArrayList(key(slot, "items"));
        if (storedItems != null) {
            for (String raw : storedItems) {
                if (raw == null || items.size() >= MAX_RESTORED_ITEMS) continue;
                items.add(Uri.parse(raw));
            }
        }

        Uri cover = null;
        String rawCover = savedState.getString(key(slot, "cover"));
        if (rawCover != null) {
            Uri candidate = Uri.parse(rawCover);
            if (items.contains(candidate)) cover = candidate;
        }
        controller.capture(animated, items, cover, savedState.getString(key(slot, "name")));
    }

    private static String key(String slot, String value) {
        return PREFIX + slot + "." + value;
    }
}
