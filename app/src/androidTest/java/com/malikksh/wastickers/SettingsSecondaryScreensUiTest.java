package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingsSecondaryScreensUiTest {
    @Test
    public void helpUsesSecondaryScreenInsteadOfDialog() {
        try (ActivityScenario<SettingsActivity> ignored = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.settings_help)).perform(scrollTo(), click());
            onView(withText("Помощь")).check(matches(isDisplayed()));
            onView(withText("Создайте набор, добавьте 3–30 файлов, настройте порядок и обложку в редакторе, при необходимости выберите фрагмент видео до 10 секунд, затем соберите набор. Ошибки отдельных файлов можно повторять или пропускать."))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void privacyUsesSecondaryScreen() {
        try (ActivityScenario<SettingsActivity> ignored = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.settings_privacy)).perform(scrollTo(), click());
            onView(withText("Конфиденциальность")).check(matches(isDisplayed()));
        }
    }
}
