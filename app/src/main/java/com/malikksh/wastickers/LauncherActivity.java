package com.malikksh.wastickers;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runtime bridge between the redesigned shell and MainActivity's conversion/editor state.
 *
 * This is an implementation-only base for the redesigned shell activities. It is not a standalone
 * screen: draft restore/save and picker persistence live here until editor state moves out of
 * MainActivity entirely.
 */
public abstract class LauncherActivity extends MainActivity {
    private static final int REQUEST_SHELL_PICK_PHOTOS = 4101;
    private static final int REQUEST_SHELL_PICK_ANIMATED = 4102;
    private static final String TRIM_STATE_PREFIX = "home.video_trim.";

    private final ExecutorService shellSelectionExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        VideoTrimStore.clear();
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            VideoTrimStore.restoreFromBundle(savedInstanceState, TRIM_STATE_PREFIX);
            EditorInstanceStateBridge.restore(this, savedInstanceState);
        } else {
            VideoTrimStore.restorePersistent(this, AppSettings.TRIM_PERSISTENT_KEY);
            EditorInstanceStateBridge.restorePersistent(this);
        }
        EditorInstanceStateBridge.setGalleryClickListener(this, v -> openMediaPicker());
    }

    /**
     * MainActivity still owns the conversion/editor state for the migration period, but production
     * shells no longer need its historical long-scroll screen. MainActivity calls this virtual
     * factory from onCreate; the debug-only LegacyMainTestHostActivity continues to use the full
     * legacy implementation because it extends MainActivity directly.
     */
    @Override
    protected View buildUi() {
        installRuntimeControls();
        FrameLayout host = new FrameLayout(this);
        host.setBackgroundColor(getColor(R.color.app_background));
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

        writeRuntimeField("packName", packName);
        writeRuntimeField("countText", countText);
        writeRuntimeField("statusText", statusText);
        writeRuntimeField("mediaTitle", mediaTitle);
        writeRuntimeField("mediaHint", mediaHint);
        writeRuntimeField("actionHint", actionHint);
        writeRuntimeField("progressText", progressText);
        writeRuntimeField("progressBar", progressBar);
        writeRuntimeField("previewContainer", null);
        writeRuntimeField("fileProgressContainer", fileProgressContainer);
        writeRuntimeField("photoModeButton", photoModeButton);
        writeRuntimeField("animatedModeButton", animatedModeButton);
        writeRuntimeField("galleryButton", galleryButton);
        writeRuntimeField("createButton", createButton);
        writeRuntimeField("addButton", addButton);
        writeRuntimeField("bugLogButton", bugLogButton);
    }

    private void writeRuntimeField(String name, Object value) {
        try {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(this, value);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not initialize runtime field " + name, error);
        }
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
        boolean animated = EditorInstanceStateBridge.isAnimatedMode(this);
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
            Method receive = MainActivity.class.getDeclaredMethod(
                    "receivePickerResult", Intent.class, boolean.class);
            receive.setAccessible(true);
            receive.invoke(this, data, animated);
            EditorInstanceStateBridge.savePersistent(this);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not apply redesigned picker result: " + error);
            return;
        }

        if (!animated) return;
        List<Uri> selected = selectedUrisSnapshot();
        shellSelectionExecutor.execute(() -> {
            try {
                VideoTrimStore.prepare(this, selected);
                VideoTrimStore.savePersistent(this, AppSettings.TRIM_PERSISTENT_KEY);
            } catch (Throwable error) {
                BugLogStore.appendApp("Could not prepare video trim state: " + error);
            }
        });
    }

    private List<Uri> selectedUrisSnapshot() {
        try {
            Field field = MainActivity.class.getDeclaredField("selectedUris");
            field.setAccessible(true);
            Object value = field.get(this);
            if (!(value instanceof List<?>)) return new ArrayList<>();
            List<Uri> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item instanceof Uri) result.add((Uri) item);
            }
            return result;
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not read redesigned picker selection: " + error);
            return new ArrayList<>();
        }
    }

    @Override
    protected void onDestroy() {
        shellSelectionExecutor.shutdownNow();
        VideoTrimStore.clear();
        super.onDestroy();
    }
}
