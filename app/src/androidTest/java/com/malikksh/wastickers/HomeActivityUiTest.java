package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HomeActivityUiTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        clearSavedPacks();
    }

    @After
    public void tearDown() {
        clearSavedPacks();
    }

    @Test
    public void switchingModesRestoresIndependentDraftNames() {
        try (ActivityScenario<HomeActivity> ignored = ActivityScenario.launch(HomeActivity.class)) {
            onView(withHint("Мои стикеры"))
                    .perform(replaceText("Фото-черновик"));
            closeSoftKeyboard();

            onView(withText("Анимация")).perform(click());
            onView(withText("2  Анимации и видео")).check(matches(isDisplayed()));
            onView(withHint("Мои стикеры"))
                    .perform(replaceText("Анимация-черновик"));
            closeSoftKeyboard();

            onView(withText("Фото")).perform(click());
            onView(withHint("Мои стикеры"))
                    .check(matches(withText("Фото-черновик")));

            onView(withText("Анимация")).perform(click());
            onView(withHint("Мои стикеры"))
                    .check(matches(withText("Анимация-черновик")));
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

    private void clearSavedPacks() {
        for (PackStore.Pack pack : PackStore.getPacks(context)) {
            PackStore.deletePack(context, pack.id);
        }
    }
}
