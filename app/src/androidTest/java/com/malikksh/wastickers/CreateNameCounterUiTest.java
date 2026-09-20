package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CreateNameCounterUiTest {
    @Test
    public void counterIsHiddenFreshShownOnFocusAndAfterEightyPercent() {
        try (ActivityScenario<SettingsShellActivity> scenario = ActivityScenario.launch(SettingsShellActivity.class)) {
            onView(withId(R.id.create_name_counter)).check(matches(withEffectiveVisibility(GONE)));

            onView(withHint("Мои стикеры")).perform(click());
            onView(withId(R.id.create_name_counter)).check(matches(isDisplayed()));

            scenario.onActivity(activity -> {
                activity.runtimePackName().setText(repeat('x', 48));
                activity.runtimePackName().clearFocus();
                View content = activity.findViewById(android.R.id.content);
                content.setFocusableInTouchMode(true);
                content.requestFocus();
            });
            onView(withId(R.id.create_name_counter)).check(matches(isDisplayed()));
        }
    }

    private String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int index = 0; index < count; index++) builder.append(value);
        return builder.toString();
    }
}
