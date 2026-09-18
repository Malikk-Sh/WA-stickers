package com.malikksh.wastickers;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Launcher wrapper that uses the system document picker directly for animated media.
 * Some OEM chooser/gallery handlers ignore EXTRA_ALLOW_MULTIPLE even when the nested
 * ACTION_OPEN_DOCUMENT intent requests it; going straight to DocumentsUI preserves
 * mixed GIF/WebP/video filtering and multi-selection.
 */
public class LauncherActivity extends HomeActivity {
    private static final int REQUEST_PICK_PHOTOS = 1001;
    private static final int REQUEST_PICK_ANIMATED = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EditorInstanceStateBridge.setGalleryClickListener(this, v -> openMediaPicker());
    }

    private void openMediaPicker() {
        boolean animated = EditorInstanceStateBridge.isAnimatedMode(this);
        int requestCode = animated ? REQUEST_PICK_ANIMATED : REQUEST_PICK_PHOTOS;
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
}
