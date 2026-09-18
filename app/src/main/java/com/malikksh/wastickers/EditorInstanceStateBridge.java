package com.malikksh.wastickers;

import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists the legacy programmatic editor across Activity recreation.
 *
 * MainActivity still owns its UI state privately. Keeping the reflection in one small bridge lets
 * lifecycle persistence stay isolated from conversion/build logic until that editor state moves to
 * a dedicated state holder.
 */
final class EditorInstanceStateBridge {
    private static final String PREFIX = "editor.instance.";
    private static final String KEY_ACTIVE_ANIMATED = PREFIX + "active_animated";
    private static final String KEY_WAS_PROCESSING = PREFIX + "was_processing";
    private static final String KEY_HAD_PENDING_BUILD = PREFIX + "had_pending_build";
    private static final String KEY_CURRENT_PACK_ID = PREFIX + "current_pack_id";
    private static final int MAX_RESTORED_ITEMS = 30;

    private EditorInstanceStateBridge() {}

    static void save(MainActivity activity, Bundle outState) {
        if (activity == null || outState == null) return;
        try {
            boolean animated = (Boolean) getField(activity, "animatedMode");
            @SuppressWarnings("unchecked")
            List<Uri> selected = (List<Uri>) getField(activity, "selectedUris");
            Uri cover = (Uri) getField(activity, "coverUri");
            EditText packName = (EditText) getField(activity, "packName");
            @SuppressWarnings("unchecked")
            EditorStateController<Uri> controller =
                    (EditorStateController<Uri>) getField(activity, "editorStateController");

            controller.capture(
                    animated,
                    selected,
                    cover,
                    packName == null ? "" : packName.getText().toString()
            );
            writeSnapshot(outState, "photo", controller.snapshot(false));
            writeSnapshot(outState, "animated", controller.snapshot(true));
            outState.putBoolean(KEY_ACTIVE_ANIMATED, animated);
            outState.putBoolean(KEY_WAS_PROCESSING, (Boolean) getField(activity, "processing"));

            PackBuildSession<?> buildSession =
                    (PackBuildSession<?>) getField(activity, "buildSession");
            outState.putBoolean(KEY_HAD_PENDING_BUILD,
                    buildSession != null && buildSession.isActive());

            PackStore.Pack currentPack = (PackStore.Pack) getField(activity, "currentPack");
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
            @SuppressWarnings("unchecked")
            EditorStateController<Uri> controller =
                    (EditorStateController<Uri>) getField(activity, "editorStateController");
            restoreSnapshot(savedState, "photo", false, controller);
            restoreSnapshot(savedState, "animated", true, controller);

            boolean animated = savedState.getBoolean(KEY_ACTIVE_ANIMATED, false);
            EditorStateController.Snapshot<Uri> active = controller.snapshot(animated);

            setField(activity, "animatedMode", animated);
            @SuppressWarnings("unchecked")
            List<Uri> selected = (List<Uri>) getField(activity, "selectedUris");
            selected.clear();
            selected.addAll(active.items());
            setField(activity, "coverUri", active.cover());

            EditText packName = (EditText) getField(activity, "packName");
            if (packName != null) packName.setText(active.name());

            String currentPackId = savedState.getString(KEY_CURRENT_PACK_ID);
            PackStore.Pack currentPack = currentPackId == null
                    ? null
                    : PackStore.getPack(activity, currentPackId);
            if (currentPack != null && currentPack.animated != animated) currentPack = null;
            setField(activity, "currentPack", currentPack);

            invoke(activity, "renderPreviews");
            invoke(activity, "updateModeUi");
            invoke(activity, "updateUiState");

            if (savedState.getBoolean(KEY_WAS_PROCESSING, false)
                    || savedState.getBoolean(KEY_HAD_PENDING_BUILD, false)) {
                TextView status = (TextView) getField(activity, "statusText");
                if (status != null) {
                    status.setText("Редактор восстановлен после пересоздания экрана. "
                            + "Незавершённая обработка была остановлена — создайте набор снова.");
                }
            }
            return true;
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not restore editor instance state: " + error);
            return false;
        }
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

    private static Object getField(MainActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }

    private static void setField(MainActivity activity, String name, Object value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(activity, value);
    }

    private static void invoke(MainActivity activity, String name) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(activity);
    }
}
