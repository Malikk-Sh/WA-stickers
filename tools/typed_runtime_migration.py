from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/malikksh/wastickers/MainActivity.java"
ACCESS = ROOT / "app/src/main/java/com/malikksh/wastickers/MainActivityRuntimeAccess.java"
SHELL = ROOT / "app/src/main/java/com/malikksh/wastickers/AppShellActivity.java"

marker = "Typed runtime surface for the redesigned shell"
main = MAIN.read_text()
if marker in main:
    raise SystemExit("MainActivity runtime surface already present")

runtime_block = r'''

    /**
     * Typed runtime surface for the redesigned shell and persistence bridges.
     *
     * Keep this package-private migration API small and behavior-preserving. It replaces reflective
     * access while editor/build state is gradually extracted from MainActivity into dedicated owners.
     */
    EditText runtimePackName() {
        return packName;
    }

    Button runtimePhotoModeButton() {
        return photoModeButton;
    }

    Button runtimeAnimatedModeButton() {
        return animatedModeButton;
    }

    boolean runtimeIsAnimatedMode() {
        return animatedMode;
    }

    boolean runtimeIsProcessing() {
        return processing;
    }

    Uri runtimeCoverUri() {
        return coverUri;
    }

    List<Uri> runtimeSelectedUrisSnapshot() {
        return new ArrayList<>(selectedUris);
    }

    EditorStateController<Uri> runtimeEditorStateController() {
        return editorStateController;
    }

    String runtimeEnteredPackName() {
        return packName == null ? "" : packName.getText().toString().trim();
    }

    PackBuildSession<Uri> runtimeBuildSession() {
        return buildSession;
    }

    PackStore.Pack runtimeCurrentPack() {
        return currentPack;
    }

    boolean runtimeReplaceCurrentPackIfId(String expectedId, PackStore.Pack replacement) {
        if (expectedId == null || replacement == null || currentPack == null
                || !expectedId.equals(currentPack.id)) {
            return false;
        }
        currentPack = replacement;
        return true;
    }

    boolean runtimeClearCurrentPackIfId(String expectedId) {
        if (expectedId == null || currentPack == null || !expectedId.equals(currentPack.id)) {
            return false;
        }
        currentPack = null;
        return true;
    }

    TextView runtimeStatusText() {
        return statusText;
    }

    TextView runtimeProgressText() {
        return progressText;
    }

    List<TextView> runtimeFileProgressLabelsSnapshot() {
        return new ArrayList<>(fileProgressLabels);
    }

    List<ProgressBar> runtimeFileProgressBarsSnapshot() {
        return new ArrayList<>(fileProgressBars);
    }

    List<String> runtimeFileProgressNamesSnapshot() {
        return new ArrayList<>(fileProgressNames);
    }

    void runtimeInstallControls(
            EditText runtimePackName,
            TextView runtimeCountText,
            TextView runtimeStatusText,
            TextView runtimeMediaTitle,
            TextView runtimeMediaHint,
            TextView runtimeActionHint,
            TextView runtimeProgressText,
            ProgressBar runtimeProgressBar,
            LinearLayout runtimeFileProgressContainer,
            Button runtimePhotoModeButton,
            Button runtimeAnimatedModeButton,
            Button runtimeGalleryButton,
            Button runtimeCreateButton,
            Button runtimeAddButton,
            Button runtimeBugLogButton
    ) {
        packName = runtimePackName;
        countText = runtimeCountText;
        statusText = runtimeStatusText;
        mediaTitle = runtimeMediaTitle;
        mediaHint = runtimeMediaHint;
        actionHint = runtimeActionHint;
        progressText = runtimeProgressText;
        progressBar = runtimeProgressBar;
        previewContainer = null;
        fileProgressContainer = runtimeFileProgressContainer;
        photoModeButton = runtimePhotoModeButton;
        animatedModeButton = runtimeAnimatedModeButton;
        galleryButton = runtimeGalleryButton;
        createButton = runtimeCreateButton;
        addButton = runtimeAddButton;
        bugLogButton = runtimeBugLogButton;
    }

    void runtimeReceivePickerResult(Intent data, boolean persistPermission) {
        receivePickerResult(data, persistPermission);
    }

    void runtimeSetGalleryClickListener(View.OnClickListener listener) {
        if (galleryButton != null && listener != null) galleryButton.setOnClickListener(listener);
    }

    void runtimeSetAnimatedMode(boolean animated) {
        setAnimatedMode(animated);
    }

    void runtimeMoveSticker(int fromIndex, int toIndex) {
        moveSticker(fromIndex, toIndex);
    }

    boolean runtimeSelectCover(Uri uri) {
        if (uri == null || processing || !selectedUris.contains(uri)) return false;
        coverUri = uri;
        invalidateCurrentPack();
        renderPreviews();
        updateUiState();
        return true;
    }

    boolean runtimeRemoveMediaAt(int index) {
        if (processing || index < 0 || index >= selectedUris.size()) return false;
        Uri removed = selectedUris.remove(index);
        if (removed != null && removed.equals(coverUri)) {
            coverUri = selectedUris.isEmpty() ? null : selectedUris.get(0);
        }
        invalidateCurrentPack();
        renderPreviews();
        updateUiState();
        return true;
    }

    boolean runtimeClearMedia() {
        if (processing || selectedUris.isEmpty()) return false;
        selectedUris.clear();
        coverUri = null;
        invalidateCurrentPack();
        renderPreviews();
        updateUiState();
        return true;
    }

    boolean runtimeClearEditorDraft() {
        if (processing) return false;
        try {
            selectedUris.clear();
            coverUri = null;
            if (packName != null) packName.setText("");
            editorStateController.capture(false, new ArrayList<>(), null, "");
            editorStateController.capture(true, new ArrayList<>(), null, "");
            discardPendingBuild();
            invalidateCurrentPack();
            renderPreviews();
            updateModeUi();
            updateUiState();
            return true;
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not clear editor draft through typed runtime surface: " + error);
            return false;
        }
    }

    boolean runtimeRestoreEditorState(
            boolean animated,
            List<Uri> items,
            Uri cover,
            String name,
            PackStore.Pack restoredPack
    ) {
        try {
            animatedMode = animated;
            selectedUris.clear();
            if (items != null) selectedUris.addAll(items);
            coverUri = cover;
            if (packName != null) packName.setText(name == null ? "" : name);
            currentPack = restoredPack;
            renderPreviews();
            updateModeUi();
            updateUiState();
            return true;
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not restore editor state through typed runtime surface: " + error);
            return false;
        }
    }

    void runtimeResetDiagnostics() {
        diagnosticItemIndex = -1;
        diagnosticItemUri = null;
    }

    void runtimeInvalidateCurrentPack() {
        invalidateCurrentPack();
    }

    void runtimeStartBatch(List<Uri> work, boolean retry) {
        startBatch(work, retry);
    }

    void runtimeUpdateUiState() {
        updateUiState();
    }

    void runtimeCancelProcessing() {
        cancelProcessing();
    }

    void runtimeFinalizePendingPack() {
        finalizePendingPackAsync();
    }

    void runtimeDiscardPendingBuild() {
        discardPendingBuild();
    }

    void runtimeAddCurrentPackToWhatsApp() {
        addCurrentPackToWhatsApp();
    }
'''

insert_at = main.rfind("\n}")
if insert_at < 0:
    raise SystemExit("Could not find MainActivity closing brace")
main = main[:insert_at] + runtime_block + main[insert_at:]
MAIN.write_text(main)

access = r'''package com.malikksh.wastickers;

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
'''
ACCESS.write_text(access)

shell = SHELL.read_text()
old_shell = "    private void refreshShellState() {"
new_shell = "    void refreshShellState() {"
if shell.count(old_shell) != 1:
    raise SystemExit("Unexpected AppShellActivity refreshShellState signature")
SHELL.write_text(shell.replace(old_shell, new_shell, 1))

print("Typed runtime migration prepared")
