package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppShellActivityUiTest {
    private static final String TRIM_PERSISTENT_KEY = "home_video_trim";
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        EditorInstanceStateBridge.clearPersistent(context);
        VideoTrimStore.clearPersistent(context, TRIM_PERSISTENT_KEY);
        VideoTrimStore.clear();
        clearSavedPacks();
    }

    @After
    public void tearDown() {
        EditorInstanceStateBridge.clearPersistent(context);
        VideoTrimStore.clearPersistent(context, TRIM_PERSISTENT_KEY);
        VideoTrimStore.clear();
        clearSavedPacks();
    }

    @Test
    public void editorCombinesNameAndMediaWithoutVisibleWorkflowTabs() {
        try (ActivityScenario<AppShellActivity> scenario = ActivityScenario.launch(AppShellActivity.class)) {
            onView(withText("Название набора")).check(matches(isDisplayed()));
            onView(withId(R.id.create_media_counter)).check(matches(withText("0 / 30")));
            onView(withId(R.id.media_summary)).check(matches(isDisplayed()));
            onView(withId(R.id.media_grid)).check(matches(isDisplayed()));
            onView(withId(R.id.media_continue)).check(matches(isDisplayed()));

            scenario.onActivity(activity -> {
                int[] routeIds = new int[]{
                        R.id.nav_create,
                        R.id.nav_media,
                        R.id.nav_build,
                        R.id.nav_packs
                };
                for (int id : routeIds) {
                    View route = activity.findViewById(id);
                    assertNotNull(route);
                    assertEquals("Compatibility route must not render width", 0, route.getWidth());
                    assertEquals("Compatibility route must not render height", 0, route.getHeight());
                }
            });
        }
    }

    @Test
    public void editorModeSwitchUpdatesSingleMediaPanel() {
        try (ActivityScenario<AppShellActivity> ignored = ActivityScenario.launch(AppShellActivity.class)) {
            onView(withId(R.id.media_mode_animated)).perform(click());
            onView(withId(R.id.media_summary)).check(matches(withText("Выбрано анимаций: 0")));

            onView(withId(R.id.media_mode_photo)).perform(click());
            onView(withId(R.id.media_summary)).check(matches(withText("Выбрано фото: 0")));
        }
    }

    private void clearSavedPacks() {
        for (PackStore.Pack pack : PackStore.getPacks(context)) {
            PackStore.deletePack(context, pack.id);
        }
    }
}
