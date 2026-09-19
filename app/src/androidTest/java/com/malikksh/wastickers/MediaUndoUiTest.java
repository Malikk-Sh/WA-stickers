package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;

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
public class MediaUndoUiTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        EditorInstanceStateBridge.clearPersistent(context);
        VideoTrimStore.clearPersistent(context, AppSettings.TRIM_PERSISTENT_KEY);
        VideoTrimStore.clear();
    }

    @After
    public void tearDown() {
        EditorInstanceStateBridge.clearPersistent(context);
        VideoTrimStore.clearPersistent(context, AppSettings.TRIM_PERSISTENT_KEY);
        VideoTrimStore.clear();
    }

    @Test
    public void removeUndoRestoresOrderCoverAndName() throws Exception {
        List<Uri> photos = Arrays.asList(
                writeImage("media_undo_one.png", 0xFF116644),
                writeImage("media_undo_two.png", 0xFF227755),
                writeImage("media_undo_three.png", 0xFF338866));

        try (ActivityScenario<SettingsShellActivity> scenario =
                     ActivityScenario.launch(SettingsShellActivity.class)) {
            scenario.onActivity(activity -> {
                activity.runtimeRestoreEditorState(false, photos, photos.get(0), "Undo pack", null);
                activity.refreshShellState();
                View media = activity.findViewById(R.id.nav_media);
                assertNotNull(media);
                assertTrue(media.performClick());
                activity.refreshShellState();
            });

            onView(withContentDescription("Удалить файл 1")).perform(click());
            scenario.onActivity(activity -> assertEquals(2, activity.runtimeSelectedUrisSnapshot().size()));

            onView(withText("Отменить")).perform(click());
            scenario.onActivity(activity -> {
                assertEquals(photos, activity.runtimeSelectedUrisSnapshot());
                assertEquals(photos.get(0), activity.runtimeCoverUri());
                assertEquals("Undo pack", activity.runtimePackName().getText().toString());
            });
        }
    }

    private Uri writeImage(String name, int color) throws Exception {
        File file = new File(context.getCacheDir(), name);
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IllegalStateException("Could not create test image " + name);
            }
        } finally {
            bitmap.recycle();
        }
        return Uri.fromFile(file);
    }
}
