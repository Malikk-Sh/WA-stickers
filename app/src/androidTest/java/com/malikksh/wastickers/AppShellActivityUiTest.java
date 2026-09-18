package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;

import android.content.Context;

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
    public void redesignedShellStartsOnCreateAndExposesFourTabs() {
        try (ActivityScenario<AppShellActivity> ignored = ActivityScenario.launch(AppShellActivity.class)) {
            onView(withText("WA Stickers")).check(matches(isDisplayed()));
            onView(withText("Ваши идеи в стикерах")).check(matches(isDisplayed()));
            onView(withText("Название набора")).check(matches(isDisplayed()));
            onView(withText("Добавьте фотографии")).check(matches(isDisplayed()));
            onView(withId(R.id.create_media_counter)).check(matches(withText("0 / 30")));
            onView(withId(R.id.create_continue)).check(matches(not(isEnabled())));

            onView(withId(R.id.nav_media)).perform(click());
            onView(withText("Выбранные файлы")).check(matches(isDisplayed()));

            onView(withId(R.id.nav_build)).perform(click());
            onView(withText("Подготовка набора")).check(matches(isDisplayed()));

            onView(withId(R.id.nav_packs)).perform(click());
            onView(withText("Сохранённые наборы")).check(matches(isDisplayed()));

            onView(withId(R.id.nav_create)).perform(click());
            onView(withText("Ваши идеи в стикерах")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void createModeSwitchUpdatesCompactSelectionCard() {
        try (ActivityScenario<AppShellActivity> ignored = ActivityScenario.launch(AppShellActivity.class)) {
            onView(withText("Анимация")).perform(click());
            onView(withText("Добавьте анимации")).check(matches(isDisplayed()));
            onView(withText("GIF, WebP и видео · до 10 секунд")).check(matches(isDisplayed()));
            onView(withId(R.id.create_pick_media)).check(matches(withText("＋  Выбрать файлы")));

            onView(withText("Фото")).perform(click());
            onView(withText("Добавьте фотографии")).check(matches(isDisplayed()));
            onView(withId(R.id.create_pick_media)).check(matches(withText("＋  Выбрать фото")));
        }
    }

    private void clearSavedPacks() {
        for (PackStore.Pack pack : PackStore.getPacks(context)) {
            PackStore.deletePack(context, pack.id);
        }
    }
}
