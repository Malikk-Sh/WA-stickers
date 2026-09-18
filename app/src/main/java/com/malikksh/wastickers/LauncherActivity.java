package com.malikksh.wastickers;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Launcher wrapper that uses the system document picker directly for animated media.
 *
 * The redesigned shell uses its own request codes so HomeActivity's legacy preflight dialog is
 * skipped. Selection still flows through MainActivity's existing receivePickerResult method, so
 * editor state, cover defaults and persisted URI grants keep the same behavior.
 */
public class LauncherActivity extends HomeActivity {
    private static final int REQUEST_PICK_PHOTOS = 1001;
    private static final int REQUEST_PICK_ANIMATED = 1002;
    private static final int REQUEST_SHELL_PICK_PHOTOS = 4101;
    private static final int REQUEST_SHELL_PICK_ANIMATED = 4102;
    private static final String TRIM_PERSISTENT_KEY = "home_video_trim";

    private final ExecutorService shellSelectionExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EditorInstanceStateBridge.setGalleryClickListener(this, v -> openMediaPicker());
    }

    private void openMediaPicker() {
        boolean animated = EditorInstanceStateBridge.isAnimatedMode(this);
        boolean redesignedShell = this instanceof AppShellActivity;
        int requestCode;
        if (redesignedShell) {
            requestCode = animated ? REQUEST_SHELL_PICK_ANIMATED : REQUEST_SHELL_PICK_PHOTOS;
        } else {
            requestCode = animated ? REQUEST_PICK_ANIMATED : REQUEST_PICK_PHOTOS;
        }

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
            boolean animated = requestCode == REQUEST_SHELL_PICK_ANIMATED;
            receiveShellPickerResult(data, animated);
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
                VideoTrimStore.savePersistent(this, TRIM_PERSISTENT_KEY);
            } catch (Throwable error) {
                BugLogStore.appendApp("Could not prepare video trim state: " + error);
            }
        });
    }

    @SuppressWarnings("unchecked")
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
        super.onDestroy();
    }
}
