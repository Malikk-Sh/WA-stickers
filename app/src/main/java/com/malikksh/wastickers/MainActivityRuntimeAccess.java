package com.malikksh.wastickers;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Typed compatibility boundary around MainActivity's private migration surface.
 *
 * The redesigned shell and persistence layers depend only on the typed methods below. Reflection is
 * deliberately confined to this class for the 1.6 runtime; CI prevents new production reflection
 * from spreading outside this boundary until MainActivity state is extracted into a dedicated owner.
 */
final class MainActivityRuntimeAccess {
    private MainActivityRuntimeAccess() {}

    private static void setField(MainActivity activity, String name, Object value) {
        if (activity == null) throw new IllegalArgumentException("activity == null");
        try {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(activity, value);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not update MainActivity field " + name, error);
        }
    }

    private static Object getField(MainActivity activity, String name) {
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

    @SuppressWarnings("unchecked")
    static EditorStateController<Uri> editorStateController(MainActivity activity) {
        Object value = getField(activity, "editorStateController");
        return value instanceof EditorStateController<?>
                ? (EditorStateController<Uri>) value
                : null;
    }

    static String enteredPackName(MainActivity activity) {
        EditText input = packName(activity);
        return input == null ? "" : input.getText().toString().trim();
    }

    @SuppressWarnings("unchecked")
    static PackBuildSession<Uri> buildSession(MainActivity activity) {
        Object value = getField(activity, "buildSession");
        return value instanceof PackBuildSession<?> ? (PackBuildSession<Uri>) value : null;
    }

    static PackStore.Pack currentPack(MainActivity activity) {
        Object value = getField(activity, "currentPack");
        return value instanceof PackStore.Pack ? (PackStore.Pack) value : null;
    }

    static boolean replaceCurrentPackIfId(
            MainActivity activity,
            String expectedId,
            PackStore.Pack replacement
    ) {
        if (activity == null || expectedId == null || replacement == null) return false;
        PackStore.Pack current = currentPack(activity);
        if (current == null || !expectedId.equals(current.id)) return false;
        try {
            setField(activity, "currentPack", replacement);
            return true;
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not replace current pack: " + error);
            return false;
        }
    }

    static boolean clearCurrentPackIfId(MainActivity activity, String expectedId) {
        if (activity == null || expectedId == null) return false;
        PackStore.Pack current = currentPack(activity);
        if (current == null || !expectedId.equals(current.id)) return false;
        try {
            setField(activity, "currentPack", null);
            return true;
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not clear current pack: " + error);
            return false;
        }
    }

    static TextView statusText(MainActivity activity) {
        Object value = getField(activity, "statusText");
        return value instanceof TextView ? (TextView) value : null;
    }

    static TextView progressText(MainActivity activity) {
        Object value = getField(activity, "progressText");
        return value instanceof TextView ? (TextView) value : null;
    }

    static List<TextView> fileProgressLabelsSnapshot(MainActivity activity) {
        Object value = getField(activity, "fileProgressLabels");
        List<TextView> result = new ArrayList<>();
        if (!(value instanceof List<?>)) return result;
        for (Object item : (List<?>) value) {
            if (item instanceof TextView) result.add((TextView) item);
        }
        return result;
    }

    static List<ProgressBar> fileProgressBarsSnapshot(MainActivity activity) {
        Object value = getField(activity, "fileProgressBars");
        List<ProgressBar> result = new ArrayList<>();
        if (!(value instanceof List<?>)) return result;
        for (Object item : (List<?>) value) {
            if (item instanceof ProgressBar) result.add((ProgressBar) item);
        }
        return result;
    }

    static List<String> fileProgressNamesSnapshot(MainActivity activity) {
        Object value = getField(activity, "fileProgressNames");
        List<String> result = new ArrayList<>();
        if (!(value instanceof List<?>)) return result;
        for (Object item : (List<?>) value) {
            if (item instanceof String) result.add((String) item);
        }
        return result;
    }

    static void installRuntimeControls(
            MainActivity activity,
            EditText packName,
            TextView countText,
            TextView statusText,
            TextView mediaTitle,
            TextView mediaHint,
            TextView actionHint,
            TextView progressText,
            ProgressBar progressBar,
            LinearLayout fileProgressContainer,
            Button photoModeButton,
            Button animatedModeButton,
            Button galleryButton,
            Button createButton,
            Button addButton,
            Button bugLogButton
    ) {
        setField(activity, "packName", packName);
        setField(activity, "countText", countText);
        setField(activity, "statusText", statusText);
        setField(activity, "mediaTitle", mediaTitle);
        setField(activity, "mediaHint", mediaHint);
        setField(activity, "actionHint", actionHint);
        setField(activity, "progressText", progressText);
        setField(activity, "progressBar", progressBar);
        setField(activity, "previewContainer", null);
        setField(activity, "fileProgressContainer", fileProgressContainer);
        setField(activity, "photoModeButton", photoModeButton);
        setField(activity, "animatedModeButton", animatedModeButton);
        setField(activity, "galleryButton", galleryButton);
        setField(activity, "createButton", createButton);
        setField(activity, "addButton", addButton);
        setField(activity, "bugLogButton", bugLogButton);
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

    static boolean clearEditorDraft(MainActivity activity) {
        if (activity == null || isProcessing(activity)) return false;
        try {
            List<Uri> selected = mutableSelectedUris(activity);
            if (selected != null) selected.clear();
            setField(activity, "coverUri", null);

            EditText input = packName(activity);
            if (input != null) input.setText("");

            EditorStateController<Uri> controller = editorStateController(activity);
            if (controller != null) {
                controller.capture(false, new ArrayList<>(), null, "");
                controller.capture(true, new ArrayList<>(), null, "");
            }

            discardPendingBuild(activity);
            invalidateCurrentPack(activity);
            invokeSafely(activity, "renderPreviews", new Class<?>[0]);
            invokeSafely(activity, "updateModeUi", new Class<?>[0]);
            updateUiState(activity);
            return true;
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not clear editor draft through runtime adapter: " + error);
            return false;
        }
    }

    static boolean restoreEditorState(
            MainActivity activity,
            boolean animated,
            List<Uri> items,
            Uri cover,
            String name,
            PackStore.Pack currentPack
    ) {
        if (activity == null) return false;
        try {
            setField(activity, "animatedMode", animated);
            List<Uri> selected = mutableSelectedUris(activity);
            if (selected == null) return false;
            selected.clear();
            if (items != null) selected.addAll(items);
            setField(activity, "coverUri", cover);

            EditText input = packName(activity);
            if (input != null) input.setText(name == null ? "" : name);
            setField(activity, "currentPack", currentPack);

            invokeSafely(activity, "renderPreviews", new Class<?>[0]);
            invokeSafely(activity, "updateModeUi", new Class<?>[0]);
            updateUiState(activity);
            return true;
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not restore editor state through runtime adapter: " + error);
            return false;
        }
    }

    static void refreshAppShell(AppShellActivity activity) {
        if (activity == null) return;
        try {
            Method method = AppShellActivity.class.getDeclaredMethod("refreshShellState");
            method.setAccessible(true);
            method.invoke(activity);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not refresh app shell through runtime adapter: " + error);
        }
    }

    static void resetDiagnostics(MainActivity activity) {
        try {
            setField(activity, "diagnosticItemIndex", -1);
            setField(activity, "diagnosticItemUri", null);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not reset Build diagnostics: " + error);
        }
    }

    static void invalidateCurrentPack(MainActivity activity) {
        invokeSafely(activity, "invalidateCurrentPack", new Class<?>[0]);
    }

    static void startBatch(MainActivity activity, List<Uri> work, boolean retry) {
        invokeSafely(activity, "startBatch", new Class<?>[]{List.class, boolean.class}, work, retry);
    }

    static void updateUiState(MainActivity activity) {
        invokeSafely(activity, "updateUiState", new Class<?>[0]);
    }

    static void cancelProcessing(MainActivity activity) {
        invokeSafely(activity, "cancelProcessing", new Class<?>[0]);
    }

    static void finalizePendingPack(MainActivity activity) {
        invokeSafely(activity, "finalizePendingPackAsync", new Class<?>[0]);
    }

    static void discardPendingBuild(MainActivity activity) {
        invokeSafely(activity, "discardPendingBuild", new Class<?>[0]);
    }

    static void addCurrentPackToWhatsApp(MainActivity activity) {
        invokeSafely(activity, "addCurrentPackToWhatsApp", new Class<?>[0]);
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

    private static Object invoke(MainActivity activity, String name, Class<?>[] parameterTypes, Object... args) {
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
