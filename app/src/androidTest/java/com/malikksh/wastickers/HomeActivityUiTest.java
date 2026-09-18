package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class HomeActivityUiTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        clearSavedPacks();
        VideoTrimStore.clear();
    }

    @After
    public void tearDown() {
        clearSavedPacks();
        VideoTrimStore.clear();
    }

    @Test
    public void switchingModesRestoresIndependentDraftNamesAcrossRecreate() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            onView(withHint("Мои стикеры"))
                    .perform(replaceText("Фото-черновик"));
            closeSoftKeyboard();

            onView(withText("Анимация")).perform(click());
            onView(withText("2  Анимации и видео")).check(matches(isDisplayed()));
            onView(withHint("Мои стикеры"))
                    .perform(replaceText("Анимация-черновик"));
            closeSoftKeyboard();

            scenario.recreate();
            onView(withText("2  Анимации и видео")).check(matches(isDisplayed()));
            onView(withHint("Мои стикеры"))
                    .check(matches(withText("Анимация-черновик")));

            onView(withText("Фото")).perform(click());
            onView(withHint("Мои стикеры"))
                    .check(matches(withText("Фото-черновик")));

            onView(withText("Анимация")).perform(click());
            onView(withHint("Мои стикеры"))
                    .check(matches(withText("Анимация-черновик")));
        }
    }

    @Test
    public void selectionOrderAndCoverSurviveRecreateForBothModes() {
        Uri photo1 = Uri.parse("content://ui.test/photo-1");
        Uri photo2 = Uri.parse("content://ui.test/photo-2");
        Uri photo3 = Uri.parse("content://ui.test/photo-3");
        Uri animated1 = Uri.parse("content://ui.test/animated-1");
        Uri animated2 = Uri.parse("content://ui.test/animated-2");
        Uri animated3 = Uri.parse("content://ui.test/animated-3");
        List<Uri> photos = Arrays.asList(photo1, photo2, photo3);
        List<Uri> animated = Arrays.asList(animated1, animated2, animated3);

        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    seedCurrentEditor(activity, photos, photo2, "Фото-порядок");
                    invoke(activity, "setAnimatedMode", new Class<?>[]{boolean.class}, true);
                    seedCurrentEditor(activity, animated, animated3, "Анимация-порядок");
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });

            scenario.recreate();

            scenario.onActivity(activity -> {
                try {
                    assertTrue((Boolean) getField(activity, "animatedMode"));
                    assertEquals(animated, selectionSnapshot(activity));
                    assertEquals(animated3, getField(activity, "coverUri"));
                    assertEquals("Анимация-порядок", ((EditText) getField(activity, "packName")).getText().toString());

                    invoke(activity, "setAnimatedMode", new Class<?>[]{boolean.class}, false);
                    assertEquals(photos, selectionSnapshot(activity));
                    assertEquals(photo2, getField(activity, "coverUri"));
                    assertEquals("Фото-порядок", ((EditText) getField(activity, "packName")).getText().toString());
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
        }
    }

    @Test
    public void videoTrimOffsetSurvivesRecreate() {
        Uri video = Uri.parse("content://ui.test/long-video.mp4");
        VideoTrimStore.Entry entry = new VideoTrimStore.Entry(
                video.toString(),
                video,
                "long-video.mp4",
                30_000L,
                7_000L
        );

        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> VideoTrimStore.replaceEntries(Arrays.asList(entry)));
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals(7_000L, VideoTrimStore.getStartOffsetMs(video)));
        }
    }

    @Test
    public void savedPacksCardOpensEmptyManager() {
        try (ActivityScenario<HomeActivity> ignored = ActivityScenario.launch(HomeActivity.class)) {
            onView(withText("Мои наборы")).check(matches(isDisplayed()));
            onView(withText("Пока нет сохранённых наборов")).check(matches(isDisplayed()));
            onView(withText("Открыть")).perform(click());
            onView(withText("Пока нет сохранённых наборов")).check(matches(isDisplayed()));
        }
    }

    @SuppressWarnings("unchecked")
    private static void seedCurrentEditor(
            MainActivity activity,
            List<Uri> items,
            Uri cover,
            String name
    ) throws Exception {
        List<Uri> selected = (List<Uri>) getField(activity, "selectedUris");
        selected.clear();
        selected.addAll(items);
        setField(activity, "coverUri", cover);
        ((EditText) getField(activity, "packName")).setText(name);
        invoke(activity, "renderPreviews", new Class<?>[0]);
        invoke(activity, "updateUiState", new Class<?>[0]);
    }

    @SuppressWarnings("unchecked")
    private static List<Uri> selectionSnapshot(MainActivity activity) throws Exception {
        return new ArrayList<>((List<Uri>) getField(activity, "selectedUris"));
    }

    private static Object getField(MainActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }

    private static void setField(MainActivity activity, String name, Object value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(activity, value);
    }

    private static Object invoke(
            MainActivity activity,
            String name,
            Class<?>[] parameterTypes,
            Object... args
    ) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(activity, args);
    }

    private void clearSavedPacks() {
        for (PackStore.Pack pack : PackStore.getPacks(context)) {
            PackStore.deletePack(context, pack.id);
        }
    }
}
