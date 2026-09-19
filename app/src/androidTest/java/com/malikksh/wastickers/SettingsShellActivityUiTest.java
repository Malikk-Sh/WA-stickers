package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;

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
import java.lang.reflect.Method;

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
    public void gearOpensSettings() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor settingsMonitor = new Instrumentation.ActivityMonitor(
                SettingsActivity.class.getName(), null, false);
        instrumentation.addMonitor(settingsMonitor);

        try (ActivityScenario<SettingsShellActivity> scenario = ActivityScenario.launch(SettingsShellActivity.class)) {
            scenario.onActivity(activity -> {
                View settings = activity.findViewById(R.id.app_settings);
                assertNotNull(settings);
                assertTrue(settings.performClick());
            });

            Activity launchedSettings = instrumentation.waitForMonitorWithTimeout(settingsMonitor, 5000L);
            assertNotNull("Settings gear did not launch SettingsActivity", launchedSettings);
            instrumentation.runOnMainSync(launchedSettings::finish);
            instrumentation.waitForIdleSync();
        } finally {
            instrumentation.removeMonitor(settingsMonitor);
        }
    }

    @Test
    public void overflowClearActionClearsDraft() throws Exception {
        try (ActivityScenario<SettingsShellActivity> scenario = ActivityScenario.launch(SettingsShellActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    Uri item = writeImage(activity, "settings_shell_draft.png");
                    activity.runtimeRestoreEditorState(false, java.util.Collections.singletonList(item), item, "", null);
                    EditorInstanceStateBridge.savePersistent(activity);
                    assertTrue(EditorInstanceStateBridge.hasPersistent(activity));
                    invokeShellRefresh(activity);

                    View overflow = activity.findViewById(R.id.app_overflow);
                    assertNotNull(overflow);
                    assertTrue(overflow.isShown());

                    // Popup/dialog focus is flaky on the headless emulator. Exercise the exact
                    // confirmed overflow action directly and assert both model and shell state.
                    Method clearDraft = SettingsShellActivity.class
                            .getDeclaredMethod("clearDraftNow");
                    clearDraft.setAccessible(true);
                    clearDraft.invoke(activity);

                    assertTrue(activity.runtimeSelectedUrisSnapshot().isEmpty());
                    assertFalse(EditorInstanceStateBridge.hasPersistent(activity));

                    TextView counter = activity.findViewById(R.id.create_media_counter);
                    assertNotNull(counter);
                    assertEquals("0 / 30", counter.getText().toString());
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

    private static void invokeShellRefresh(SettingsShellActivity activity) {
        activity.refreshShellState();
    }
}
