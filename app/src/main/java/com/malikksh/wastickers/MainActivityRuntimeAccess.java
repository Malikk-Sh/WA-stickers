package com.malikksh.wastickers;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed compatibility boundary around MainActivity's migration surface.
 *
 * The redesigned shell and persistence layers depend only on the methods below. This facade no
 * longer uses reflection; MainActivity exposes a package-private typed runtime surface while state
 * ownership is incrementally extracted into dedicated controllers.
 */
final class MainActivityRuntimeAccess {
    private MainActivityRuntimeAccess() {}

    static EditText packName(MainActivity activity) {
        return activity == null ? null : activity.runtimePackName();
    }

    static Button photoModeButton(MainActivity activity) {
        return activity == null ? null : activity.runtimePhotoModeButton();
    }

    static Button animatedModeButton(MainActivity activity) {
        return activity == null ? null : activity.runtimeAnimatedModeButton();
    }

    static boolean isAnimatedMode(MainActivity activity) {
        return activity != null && activity.runtimeIsAnimatedMode();
    }

    static boolean isProcessing(MainActivity activity) {
        return activity != null && activity.runtimeIsProcessing();
    }

    static Uri coverUri(MainActivity activity) {
        return activity == null ? null : activity.runtimeCoverUri();
    }

    static List<Uri> selectedUrisSnapshot(MainActivity activity) {
        return activity == null ? new ArrayList<>() : activity.runtimeSelectedUrisSnapshot();
    }

    static EditorStateController<Uri> editorStateController(MainActivity activity) {
        return activity == null ? null : activity.runtimeEditorStateController();
    }

    static String enteredPackName(MainActivity activity) {
        return activity == null ? "" : activity.runtimeEnteredPackName();
    }

    static PackBuildSession<Uri> buildSession(MainActivity activity) {
        return activity == null ? null : activity.runtimeBuildSession();
    }

    static PackStore.Pack currentPack(MainActivity activity) {
        return activity == null ? null : activity.runtimeCurrentPack();
    }

    static boolean replaceCurrentPackIfId(
            MainActivity activity,
            String expectedId,
            PackStore.Pack replacement
    ) {
        return activity != null && activity.runtimeReplaceCurrentPackIfId(expectedId, replacement);
    }

    static boolean clearCurrentPackIfId(MainActivity activity, String expectedId) {
        return activity != null && activity.runtimeClearCurrentPackIfId(expectedId);
    }

    static TextView statusText(MainActivity activity) {
        return activity == null ? null : activity.runtimeStatusText();
    }

    static TextView progressText(MainActivity activity) {
        return activity == null ? null : activity.runtimeProgressText();
    }

    static List<TextView> fileProgressLabelsSnapshot(MainActivity activity) {
        return activity == null ? new ArrayList<>() : activity.runtimeFileProgressLabelsSnapshot();
    }

    static List<ProgressBar> fileProgressBarsSnapshot(MainActivity activity) {
        return activity == null ? new ArrayList<>() : activity.runtimeFileProgressBarsSnapshot();
    }

    static List<String> fileProgressNamesSnapshot(MainActivity activity) {
        return activity == null ? new ArrayList<>() : activity.runtimeFileProgressNamesSnapshot();
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
        if (activity == null) return;
        activity.runtimeInstallControls(
                packName,
                countText,
                statusText,
                mediaTitle,
                mediaHint,
                actionHint,
                progressText,
                progressBar,
                fileProgressContainer,
                photoModeButton,
                animatedModeButton,
                galleryButton,
                createButton,
                addButton,
                bugLogButton
        );
    }

    static void receivePickerResult(MainActivity activity, Intent data, boolean persistPermission) {
        if (activity != null) activity.runtimeReceivePickerResult(data, persistPermission);
    }

    static void setGalleryClickListener(MainActivity activity, View.OnClickListener listener) {
        if (activity != null) activity.runtimeSetGalleryClickListener(listener);
    }

    static void setAnimatedMode(MainActivity activity, boolean animated) {
        if (activity != null) activity.runtimeSetAnimatedMode(animated);
    }

    static void moveSticker(MainActivity activity, int fromIndex, int toIndex) {
        if (activity != null) activity.runtimeMoveSticker(fromIndex, toIndex);
    }

    static boolean selectCover(MainActivity activity, Uri uri) {
        return activity != null && activity.runtimeSelectCover(uri);
    }

    static boolean removeMediaAt(MainActivity activity, int index) {
        return activity != null && activity.runtimeRemoveMediaAt(index);
    }

    static boolean clearMedia(MainActivity activity) {
        return activity != null && activity.runtimeClearMedia();
    }

    static boolean clearEditorDraft(MainActivity activity) {
        return activity != null && activity.runtimeClearEditorDraft();
    }

    static boolean restoreEditorState(
            MainActivity activity,
            boolean animated,
            List<Uri> items,
            Uri cover,
            String name,
            PackStore.Pack currentPack
    ) {
        return activity != null && activity.runtimeRestoreEditorState(
                animated,
                items,
                cover,
                name,
                currentPack
        );
    }

    static void refreshAppShell(AppShellActivity activity) {
        if (activity == null) return;
        try {
            activity.refreshShellState();
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not refresh app shell through typed runtime surface: " + error);
        }
    }

    static void resetDiagnostics(MainActivity activity) {
        if (activity != null) activity.runtimeResetDiagnostics();
    }

    static void invalidateCurrentPack(MainActivity activity) {
        if (activity != null) activity.runtimeInvalidateCurrentPack();
    }

    static void startBatch(MainActivity activity, List<Uri> work, boolean retry) {
        if (activity != null) activity.runtimeStartBatch(work, retry);
    }

    static void updateUiState(MainActivity activity) {
        if (activity != null) activity.runtimeUpdateUiState();
    }

    static void cancelProcessing(MainActivity activity) {
        if (activity != null) activity.runtimeCancelProcessing();
    }

    static void finalizePendingPack(MainActivity activity) {
        if (activity != null) activity.runtimeFinalizePendingPack();
    }

    static void discardPendingBuild(MainActivity activity) {
        if (activity != null) activity.runtimeDiscardPendingBuild();
    }

    static void addCurrentPackToWhatsApp(MainActivity activity) {
        if (activity != null) activity.runtimeAddCurrentPackToWhatsApp();
    }
}
