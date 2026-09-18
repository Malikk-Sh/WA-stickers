package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SettingsShellActivityUiTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        AppSettings.setAppearance(context, AppSettings.APPEARANCE_SYSTEM);
        AppSettings.setCompactMode(context, false);
        AppSettings.setKeepDrafts(context, true);
        EditorInstanceStateBridge.clearPersistent(context);
        VideoTrimStore.clearPersistent(context, AppSettings.TRIM_PERSISTENT_KEY);
    }

    @After
    public void tearDown() {
        EditorInstanceStateBridge.clearPersistent(context);
        VideoTrimStore.clearPersistent(context, AppSettings.TRIM_PERSISTENT_KEY);
        AppSettings.setAppearance(context, AppSettings.APPEARANCE_SYSTEM);
        AppSettings.setCompactMode(context, false);
        AppSettings.setKeepDrafts(context, true);
    }

    @Test
    public void gearOpensSettingsAndOverflowClearsDraft() throws Exception {
        try (ActivityScenario<SettingsShellActivity> scenario = ActivityScenario.launch(SettingsShellActivity.class)) {
            scenario.onActivity(activity -> {
                View settings = activity.findViewById(R.id.app_settings);
                assertNotNull(settings);
                assertTrue(settings.performClick());
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            onView(withId(R.id.settings_title)).check(matches(withText("Настройки")));
            pressBack();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                try {
                    Uri item = writeImage(activity, "settings_shell_draft.png");
                    @SuppressWarnings("unchecked")
                    List<Uri> selected = (List<Uri>) getMainField(activity, "selectedUris");
                    selected.clear();
                    selected.add(item);
                    setMainField(activity, "coverUri", item);
                    EditorInstanceStateBridge.savePersistent(activity);
                    assertTrue(EditorInstanceStateBridge.hasPersistent(activity));
                    invokeShellRefresh(activity);
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });

            onView(withId(R.id.app_overflow)).check(matches(isDisplayed())).perform(click());
            onView(withText("Очистить черновик")).perform(click());
            onView(withText("Очистить черновик?")).check(matches(isDisplayed()));
            onView(withText("Очистить")).perform(click());
            onView(withId(R.id.create_media_counter)).check(matches(withText("0 / 30")));

            scenario.onActivity(activity -> {
                try {
                    @SuppressWarnings("unchecked")
                    List<Uri> selected = (List<Uri>) getMainField(activity, "selectedUris");
                    assertTrue(selected.isEmpty());
                    assertFalse(EditorInstanceStateBridge.hasPersistent(activity));
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
        }
    }

    private static Uri writeImage(SettingsShellActivity activity, String name) throws Exception {
        File file = new File(activity.getCacheDir(), name);
        Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xFF25D366);
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IllegalStateException("Could not create test image");
            }
        } finally {
            bitmap.recycle();
        }
        return Uri.fromFile(file);
    }

    private static Object getMainField(SettingsShellActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }

    private static void setMainField(SettingsShellActivity activity, String name, Object value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(activity, value);
    }

    private static void invokeShellRefresh(SettingsShellActivity activity) throws Exception {
        java.lang.reflect.Method method = AppShellActivity.class.getDeclaredMethod("refreshShellState");
        method.setAccessible(true);
        method.invoke(activity);
    }
}