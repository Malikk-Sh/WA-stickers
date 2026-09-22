package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class FinalPolishUiTest {
    private static final String PREFIX = "final_polish_";
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        EditorInstanceStateBridge.clearPersistent(context);
        clearTestFiles();
    }

    @After
    public void tearDown() {
        EditorInstanceStateBridge.clearPersistent(context);
        clearTestFiles();
    }

    @Test
    public void autosavedFreshProjectDoesNotShowRestoredDraftBadge() throws Exception {
        List<Uri> photos = Arrays.asList(
                writeImage("fresh_one.png", 0xFF225544),
                writeImage("fresh_two.png", 0xFF337755),
                writeImage("fresh_three.png", 0xFF448866));

        try (ActivityScenario<SettingsShellActivity> scenario =
                     ActivityScenario.launch(SettingsShellActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.runtimeRestoreEditorState(
                        false, photos, photos.get(0), "Новый проект", null));
                EditorInstanceStateBridge.savePersistent(activity);
                activity.refreshShellState();

                View chip = activity.findViewById(R.id.create_draft_chip);
                assertNotNull(chip);
                assertEquals(View.GONE, chip.getVisibility());
            });
        }
    }

    @Test
    public void mediaTileActionsUseDrawablesInsteadOfTextGlyphs() throws Exception {
        List<Uri> photos = Arrays.asList(
                writeImage("media_one.png", 0xFF115544),
                writeImage("media_two.png", 0xFF227755),
                writeImage("media_three.png", 0xFF338866));

        try (ActivityScenario<SettingsShellActivity> scenario =
                     ActivityScenario.launch(SettingsShellActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.runtimeRestoreEditorState(
                        false, photos, photos.get(0), "Vector controls", null));
                activity.showEditorScreen();
                activity.refreshShellState();

                View drag = findByDescription(activity.getWindow().getDecorView(), "Перетащить файл 1");
                View remove = findByDescription(activity.getWindow().getDecorView(), "Удалить файл 1");
                View cover = findByDescription(activity.getWindow().getDecorView(), "Выбрать как обложку");
                assertEmptyTextWithDrawable(drag);
                assertEmptyTextWithDrawable(remove);
                assertEmptyTextWithDrawable(cover);
            });
        }
    }

    private void assertEmptyTextWithDrawable(View view) {
        assertNotNull(view);
        if (view instanceof android.widget.ImageView) {
            assertNotNull("Media action is missing its vector drawable", ((android.widget.ImageView) view).getDrawable());
            return;
        }
        assertTrue(view instanceof TextView);
        TextView text = (TextView) view;
        assertEquals("", text.getText().toString());
        boolean hasDrawable = false;
        for (android.graphics.drawable.Drawable drawable : text.getCompoundDrawables()) {
            if (drawable != null) {
                hasDrawable = true;
                break;
            }
        }
        assertTrue("Media action is missing its vector drawable", hasDrawable);
    }

    private Uri writeImage(String suffix, int color) throws Exception {
        File file = new File(context.getCacheDir(), PREFIX + suffix);
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            bitmap.recycle();
        }
        return Uri.fromFile(file);
    }

    private static View findByDescription(View view, String description) {
        CharSequence value = view.getContentDescription();
        if (value != null && description.contentEquals(value)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = findByDescription(group.getChildAt(i), description);
                if (match != null) return match;
            }
        }
        return null;
    }

    private void clearTestFiles() {
        File[] files = context.getCacheDir().listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.getName().startsWith(PREFIX)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }
}
