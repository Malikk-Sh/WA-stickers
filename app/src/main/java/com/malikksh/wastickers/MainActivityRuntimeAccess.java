package com.malikksh.wastickers;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Button;

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

    static boolean isAnimatedMode(MainActivity activity) {
        Object value = getField(activity, "animatedMode");
        return value instanceof Boolean && (Boolean) value;
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
}
