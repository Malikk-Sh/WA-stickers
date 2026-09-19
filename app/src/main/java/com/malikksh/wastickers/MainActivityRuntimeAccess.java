package com.malikksh.wastickers;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Transitional adapter around MainActivity's private migration surface.
 *
 * Reflection stays isolated here while redesigned shell classes stop depending on field/method names
 * directly. A later state-owner refactor can replace this adapter without touching every screen.
 */
final class MainActivityRuntimeAccess {
    private MainActivityRuntimeAccess() {}

    static void setField(MainActivity activity, String name, Object value) {
        if (activity == null) throw new IllegalArgumentException("activity == null");
        try {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(activity, value);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not update MainActivity field " + name, error);
        }
    }

    static Object getField(MainActivity activity, String name) {
        if (activity == null) return null;
        try {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(activity);
        } catch (ReflectiveOperationException error) {
            BugLogStore.appendApp("Could not read MainActivity field " + name + ": " + error);
            return null;
        }
    }

    static EditText packName(MainActivity activity) {
        Object value = getField(activity, "packName");
        return value instanceof EditText ? (EditText) value : null;
    }

    static Button photoModeButton(MainActivity activity) {
        Object value = getField(activity, "photoModeButton");
        return value instanceof Button ? (Button) value : null;
    }

    static Button animatedModeButton(MainActivity activity) {
        Object value = getField(activity, "animatedModeButton");
        return value instanceof Button ? (Button) value : null;
    }

    static boolean isAnimatedMode(MainActivity activity) {
        Object value = getField(activity, "animatedMode");
        return value instanceof Boolean && (Boolean) value;
    }

    static boolean isProcessing(MainActivity activity) {
        Object value = getField(activity, "processing");
        return value instanceof Boolean && (Boolean) value;
    }

    static Uri coverUri(MainActivity activity) {
        Object value = getField(activity, "coverUri");
        return value instanceof Uri ? (Uri) value : null;
    }

    static List<Uri> selectedUrisSnapshot(MainActivity activity) {
        Object value = getField(activity, "selectedUris");
        if (!(value instanceof List<?>)) return new ArrayList<>();
        List<Uri> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Uri) result.add((Uri) item);
        }
        return result;
    }

    static void receivePickerResult(MainActivity activity, Intent data, boolean persistPermission) {
        invoke(activity, "receivePickerResult", new Class<?>[]{Intent.class, boolean.class}, data, persistPermission);
    }

    static void setGalleryClickListener(MainActivity activity, View.OnClickListener listener) {
        Object value = getField(activity, "galleryButton");
        if (value instanceof Button && listener != null) {
            ((Button) value).setOnClickListener(listener);
        }
    }

    static void setAnimatedMode(MainActivity activity, boolean animated) {
        invokeSafely(activity, "setAnimatedMode", new Class<?>[]{boolean.class}, animated);
    }

    static void moveSticker(MainActivity activity, int fromIndex, int toIndex) {
        invokeSafely(activity, "moveSticker", new Class<?>[]{int.class, int.class}, fromIndex, toIndex);
    }

    static boolean selectCover(MainActivity activity, Uri uri) {
        if (activity == null || uri == null || isProcessing(activity)) return false;
        List<Uri> selected = mutableSelectedUris(activity);
        if (selected == null || !selected.contains(uri)) return false;
        try {
            setField(activity, "coverUri", uri);
            syncMediaMutation(activity);
            return true;
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not select cover through runtime adapter: " + error);
            return false;
        }
    }

    static boolean removeMediaAt(MainActivity activity, int index) {
        if (activity == null || isProcessing(activity)) return false;
        List<Uri> selected = mutableSelectedUris(activity);
        if (selected == null || index < 0 || index >= selected.size()) return false;
        try {
            Uri removed = selected.remove(index);
            Uri cover = coverUri(activity);
            if (removed != null && removed.equals(cover)) {
                setField(activity, "coverUri", selected.isEmpty() ? null : selected.get(0));
            }
            syncMediaMutation(activity);
            return true;
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not remove media through runtime adapter: " + error);
            return false;
        }
    }

    static boolean clearMedia(MainActivity activity) {
        if (activity == null || isProcessing(activity)) return false;
        List<Uri> selected = mutableSelectedUris(activity);
        if (selected == null || selected.isEmpty()) return false;
        try {
            selected.clear();
            setField(activity, "coverUri", null);
            syncMediaMutation(activity);
            return true;
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not clear media through runtime adapter: " + error);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Uri> mutableSelectedUris(MainActivity activity) {
        Object value = getField(activity, "selectedUris");
        return value instanceof List<?> ? (List<Uri>) value : null;
    }

    private static void syncMediaMutation(MainActivity activity) {
        invoke(activity, "invalidateCurrentPack", new Class<?>[0]);
        invoke(activity, "renderPreviews", new Class<?>[0]);
        invoke(activity, "updateUiState", new Class<?>[0]);
    }

    static Object invoke(MainActivity activity, String name, Class<?>[] parameterTypes, Object... args) {
        if (activity == null) return null;
        try {
            Method method = MainActivity.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(activity, args);
        } catch (Throwable error) {
            throw new IllegalStateException("Could not invoke MainActivity." + name, error);
        }
    }

    private static Object invokeSafely(
            MainActivity activity,
            String name,
            Class<?>[] parameterTypes,
            Object... args
    ) {
        try {
            return invoke(activity, name, parameterTypes, args);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not invoke MainActivity." + name + ": " + error);
            return null;
        }
    }
}
