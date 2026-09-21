package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(AndroidJUnit4.class)
public class SettingsActivityUiTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        resetSettings();
        clearSavedPacks();
    }

    @After
    public void tearDown() {
        resetSettings();
        clearSavedPacks();
        AppSettings.clearConversionCache(context);
    }

    @Test
    public void settingsValuesPersistAcrossRecreate() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.settings_title)).check(matches(withText("Настройки")));
            onView(withId(R.id.settings_privacy)).check(matches(withText("Конфиденциальность  ›")));

            onView(withId(R.id.settings_compact)).perform(click());
            onView(withId(R.id.settings_quality_sharper)).perform(click());
            onView(withId(R.id.settings_theme_dark)).perform(click());

            scenario.recreate();

            onView(withId(R.id.settings_compact)).check(matches(isChecked()));
            scenario.onActivity(activity -> {
                assertEquals(AppSettings.APPEARANCE_DARK, AppSettings.appearance(activity));
                assertEquals(AppSettings.QUALITY_SHARPER, AppSettings.qualityPreset(activity));
                assertTrue(AppSettings.compactMode(activity));
            });
        }
    }

    @Test
    public void clearCacheDoesNotDeleteSavedPacks() throws Exception {
        PackStore.addPack(context, new PackStore.Pack(
                "settings_pack",
                "Сохранённый набор",
                3,
                "1",
                false
        ));
        File cacheDir = new File(context.getCacheDir(), "sticker_conversion_cache_v1");
        assertTrue(cacheDir.mkdirs() || cacheDir.isDirectory());
        File cached = new File(cacheDir, "settings_test.webp");
        try (FileOutputStream output = new FileOutputStream(cached, false)) {
            output.write(new byte[]{1, 2, 3, 4});
        }
        assertTrue(cached.isFile());

        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.settings_clear_cache)).perform(click());
            scenario.onActivity(activity -> {
                assertNotNull(PackStore.getPack(activity, "settings_pack"));
                assertFalse(cached.exists());
            });
        }
    }

    private void resetSettings() {
        AppSettings.setAppearance(context, AppSettings.APPEARANCE_SYSTEM);
        AppSettings.setCompactMode(context, false);
        AppSettings.setQualityPreset(context, AppSettings.QUALITY_BALANCE);
        AppSettings.setKeepDrafts(context, true);
        AppSettings.setAutoCleanup(context, true);
    }

    private void clearSavedPacks() {
        for (PackStore.Pack pack : PackStore.getPacks(context)) {
            PackStore.deletePack(context, pack.id);
        }
    }
}