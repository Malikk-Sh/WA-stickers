package com.malikksh.wastickers;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runtime host between the redesigned shell and MainActivity's conversion pipeline.
 *
 * Editor selection/draft and finalized-pack ownership now live in dedicated typed state owners.
 * This implementation-only base keeps lifecycle persistence and picker handling near the shell while
 * the remaining conversion orchestration is cleaned up independently.
 */
public abstract class LauncherActivity extends MainActivity {
    private static final int REQUEST_SHELL_PICK_PHOTOS = 4101;
    private static final int REQUEST_SHELL_PICK_ANIMATED = 4102;
    private static final int REQUEST_SHELL_PICK_UNIFIED = 4103;
    private static final String TRIM_STATE_PREFIX = "home.video_trim.";

    private final ExecutorService shellSelectionExecutor = Executors.newSingleThreadExecutor();
    private boolean restoredPersistentDraftOnLaunch;
    private boolean restoredDraftFeedbackShown;
    private boolean initialShellFocusApplied;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        VideoTrimStore.clear();
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        );
        super.onCreate(savedInstanceState);
        AppSettings.applySystemBars(this);
        if (savedInstanceState != null) {
            VideoTrimStore.restoreFromBundle(savedInstanceState, TRIM_STATE_PREFIX);
            EditorInstanceStateBridge.restore(this, savedInstanceState);
        } else {
            restoredPersistentDraftOnLaunch = EditorInstanceStateBridge.restorePersistent(this)
                    && !runtimeSelectedUrisSnapshot().isEmpty();
            VideoTrimStore.restorePersistent(this, AppSettings.TRIM_PERSISTENT_KEY + "." + runtimeProjectId());
        }
        runtimeSetGalleryClickListener(v -> openMediaPicker());
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        applyInitialShellFocusOnce();
        if (!restoredPersistentDraftOnLaunch || restoredDraftFeedbackShown) return;
        View createAction = findViewById(R.id.create_continue);
        if (createAction == null) createAction = findViewById(R.id.media_continue);
        if (createAction == null) return;
        restoredDraftFeedbackShown = true;
        createAction.post(() -> TransientFeedback.show(this, "Черновик восстановлен"));
    }

    private void applyInitialShellFocusOnce() {
        if (initialShellFocusApplied) return;
        View content = findViewById(android.R.id.content);
        if (content == null) return;
        initialShellFocusApplied = true;

        EditText packName = runtimePackName();
        if (packName != null) packName.clearFocus();
        content.setFocusableInTouchMode(true);
        content.requestFocus();

        android.view.inputmethod.InputMethodManager inputMethodManager =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (inputMethodManager != null && content.getWindowToken() != null) {
            inputMethodManager.hideSoftInputFromWindow(content.getWindowToken(), 0);
        }
    }

    /** True only for a fresh launch that restored a readable persistent editor draft. */
    protected final boolean restoredPersistentDraftOnLaunch() {
        return restoredPersistentDraftOnLaunch;
    }

    /**
     * Production shells never construct MainActivity's historical long-scroll presentation.
     * MainActivity calls this virtual factory from onCreate; the debug-only LegacyMainTestHostActivity
     * continues to use the full legacy implementation while its conversion regression coverage is
     * still useful.
     */
    @Override
    protected View buildUi() {
        installRuntimeControls();
        FrameLayout host = new FrameLayout(this);
        host.setBackgroundColor(getColor(R.color.app_background));
        host.setFocusableInTouchMode(true);
        host.requestFocus();
        return host;
    }

    private void installRuntimeControls() {
        EditText packName = new EditText(this);
        packName.setSingleLine(true);
        packName.setHint("Мои стикеры");
        packName.setTextSize(16);
        packName.setTextColor(getColor(R.color.app_text_primary));
        packName.setHintTextColor(getColor(R.color.app_disabled_text));
        packName.setPadding(runtimeDp(14), 0, runtimeDp(14), 0);
        GradientDrawable input = new GradientDrawable();
        input.setColor(getColor(R.color.app_surface));
        input.setCornerRadius(runtimeDp(14));
        input.setStroke(runtimeDp(1), getColor(R.color.app_border));
        packName.setBackground(input);

        Button photoModeButton = new Button(this);
        photoModeButton.setText("Фото");
        photoModeButton.setAllCaps(false);
        Button animatedModeButton = new Button(this);
        animatedModeButton.setText("Анимация");
        animatedModeButton.setAllCaps(false);
        Button galleryButton = new Button(this);
        Button createButton = new Button(this);
        Button addButton = new Button(this);
        Button bugLogButton = new Button(this);

        TextView countText = new TextView(this);
        TextView statusText = new TextView(this);
        TextView mediaTitle = new TextView(this);
        TextView mediaHint = new TextView(this);
        TextView actionHint = new TextView(this);
        TextView progressText = new TextView(this);
        ProgressBar progressBar = new ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout fileProgressContainer = new LinearLayout(this);
        fileProgressContainer.setOrientation(LinearLayout.VERTICAL);

        runtimeInstallControls(
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

    private int runtimeDp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        EditorInstanceStateBridge.save(this, outState);
        VideoTrimStore.saveToBundle(outState, TRIM_STATE_PREFIX);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        EditorInstanceStateBridge.savePersistent(this);
        VideoTrimStore.savePersistent(this, AppSettings.TRIM_PERSISTENT_KEY + "." + runtimeProjectId());
        super.onPause();
    }

    /** Opens the current legacy-mode picker for compatibility paths that still delegate here. */
    protected final void openMediaPicker() {
        openModePicker(runtimeIsAnimatedMode());
    }

    /** Opens images from the gallery without changing the project's current runtime mode. */
    protected final void openGallerySourcePicker() {
        startShellPicker(
                MediaPickerIntentFactory.createUnifiedGetContentFallback(),
                MediaPickerIntentFactory.createSystemMediaPicker(),
                REQUEST_SHELL_PICK_UNIFIED,
                "Фото и видео"
        );
    }

    /** Opens all source types supported by the existing static/animated pipelines. */
    protected final void openFileSourcePicker() {
        startShellPicker(
                MediaPickerIntentFactory.createUnifiedOpenDocumentIntent(),
                MediaPickerIntentFactory.createUnifiedGetContentFallback(),
                REQUEST_SHELL_PICK_UNIFIED,
                "Выберите файлы"
        );
    }

    private void openModePicker(boolean animated) {
        int requestCode = animated ? REQUEST_SHELL_PICK_ANIMATED : REQUEST_SHELL_PICK_PHOTOS;
        String title = animated ? "Выберите GIF, WebP или видео" : "Выберите фото";
        startShellPicker(
                MediaPickerIntentFactory.createOpenDocumentIntent(animated),
                MediaPickerIntentFactory.createGetContentFallback(animated),
                requestCode,
                title
        );
    }

    private void startShellPicker(Intent intent, Intent fallback, int requestCode, String title) {
        try {
            startActivityForResult(Intent.createChooser(intent, title), requestCode);
        } catch (ActivityNotFoundException first) {
            try {
                startActivityForResult(Intent.createChooser(fallback, title), requestCode);
            } catch (ActivityNotFoundException second) {
                Toast.makeText(this, "Не найдено приложение для выбора файлов", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == REQUEST_SHELL_PICK_PHOTOS
                || requestCode == REQUEST_SHELL_PICK_ANIMATED
                || requestCode == REQUEST_SHELL_PICK_UNIFIED)
                && resultCode == RESULT_OK && data != null) {
            receiveShellPickerResult(data, requestCode != REQUEST_SHELL_PICK_PHOTOS);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void receiveShellPickerResult(Intent data, boolean prepareAnimatedMetadata) {
        String project = runtimeProjectId();
        int remaining = Math.max(0, 30 - runtimeSelectedUrisSnapshot().size());
        shellSelectionExecutor.execute(() -> {
            try {
                Intent imported = SourceImporter.importResult(this, data, project, remaining);
                runOnUiThread(() -> {
                    if (isDestroyed() || !project.equals(runtimeProjectId())) return;
                    runtimeReceivePickerResult(imported, false);
                    EditorInstanceStateBridge.savePersistent(this);
                    if (this instanceof AppShellActivity) ((AppShellActivity) this).refreshShellState();
                });
            } catch (Exception error) {
                BugLogStore.appendApp("Import failed: " + error);
                runOnUiThread(() -> TransientFeedback.show(this, "Не удалось прочитать выбранные файлы"));
            }
        });
    }

    @Override
    protected void onDestroy() {
        shellSelectionExecutor.shutdownNow();
        VideoTrimStore.clear();
        super.onDestroy();
    }
}
