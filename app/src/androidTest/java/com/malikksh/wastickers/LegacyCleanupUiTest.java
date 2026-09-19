package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LegacyCleanupUiTest {
    @Test
    public void redesignedShellDoesNotExposeLegacyLongScrollSections() {
        try (ActivityScenario<AppShellActivity> ignored = ActivityScenario.launch(AppShellActivity.class)) {
            onView(withText("Стикеры из фото и видео")).check(doesNotExist());
            onView(withText("3  Готовый набор")).check(doesNotExist());
            onView(withId(R.id.nav_build)).perform(click());
            onView(withText("Сборка")).check(matches(isDisplayed()));
            onView(withText("Подготовка сборки")).check(matches(isDisplayed()));
        }
    }
}
