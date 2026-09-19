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
    private static final String TRIM_STATE_PREFIX = "home.video_trim.";

    private final ExecutorService shellSelectionExecutor = Executors.newSingleThreadExecutor();
    private boolean restoredPersistentDraftOnLaunch;

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
            VideoTrimStore.restorePersistent(this, AppSettings.TRIM_PERSISTENT_KEY);
            restoredPersistentDraftOnLaunch = EditorInstanceStateBridge.restorePersistent(this)
                    && !runtimeSelectedUrisSnapshot().isEmpty();
        }
        runtimeSetGalleryClickListener(v -> openMediaPicker());
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
        VideoTrimStore.savePersistent(this, AppSettings.TRIM_PERSISTENT_KEY);
        super.onPause();
    }

    /** Opens the redesigned shell picker without routing through a hidden legacy button. */
    protected final void openMediaPicker() {
        boolean animated = runtimeIsAnimatedMode();
        int requestCode = animated ? REQUEST_SHELL_PICK_ANIMATED : REQUEST_SHELL_PICK_PHOTOS;
        Intent intent = MediaPickerIntentFactory.createOpenDocumentIntent(animated);
        String title = animated ? "Выберите GIF, WebP или видео" : "Выберите фото";

        try {
            if (animated) {
                Toast.makeText(
                        this,
                        "Можно выбрать несколько файлов: удерживайте первый, затем отметьте остальные",
                        Toast.LENGTH_LONG
                ).show();
                startActivityForResult(intent, requestCode);
            } else {
                startActivityForResult(Intent.createChooser(intent, title), requestCode);
            }
        } catch (ActivityNotFoundException first) {
            Intent fallback = MediaPickerIntentFactory.createGetContentFallback(animated);
            try {
                startActivityForResult(Intent.createChooser(fallback, title), requestCode);
            } catch (ActivityNotFoundException second) {
                Toast.makeText(
                        this,
                        animated ? "Не найдено приложение для выбора файлов" : "Галерея не найдена",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == REQUEST_SHELL_PICK_PHOTOS || requestCode == REQUEST_SHELL_PICK_ANIMATED)
                && resultCode == RESULT_OK && data != null) {
            receiveShellPickerResult(data, requestCode == REQUEST_SHELL_PICK_ANIMATED);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void receiveShellPickerResult(Intent data, boolean animated) {
        try {
            // Shell pickers use ACTION_OPEN_DOCUMENT when available, so keep URI access for both
            // photo and animated drafts. GET_CONTENT fallback persistence failures are tolerated.
            runtimeReceivePickerResult(data, true);
            EditorInstanceStateBridge.savePersistent(this);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not apply redesigned picker result: " + error);
            return;
        }

        if (!animated) return;
        List<Uri> selected = runtimeSelectedUrisSnapshot();
        shellSelectionExecutor.execute(() -> {
            try {
                VideoTrimStore.prepare(this, selected);
                VideoTrimStore.savePersistent(this, AppSettings.TRIM_PERSISTENT_KEY);
            } catch (Throwable error) {
                BugLogStore.appendApp("Could not prepare video trim state: " + error);
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
