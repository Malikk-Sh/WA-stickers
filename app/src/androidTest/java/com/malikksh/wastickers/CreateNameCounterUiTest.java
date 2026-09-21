package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertNotNull;

import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CreateNameCounterUiTest {
    @Test
    public void counterLivesInRenameDialogWhileEditorHasNoNameForm() {
        try (ActivityScenario<SettingsShellActivity> scenario = ActivityScenario.launch(SettingsShellActivity.class)) {
            scenario.onActivity(activity -> {
                activity.runtimeNewProject("Счётчик");
                activity.enterCreateEditor();
            });
            onView(withId(R.id.create_name_counter)).check(matches(withEffectiveVisibility(GONE)));
            onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Переименовать набор")).perform(click());
            onView(withId(R.id.pack_name_dialog_counter)).check(matches(isDisplayed()));
            onView(withId(R.id.pack_name_dialog_input)).perform(androidx.test.espresso.action.ViewActions.replaceText("x".repeat(48)));
            onView(withId(R.id.pack_name_dialog_counter)).check(matches(androidx.test.espresso.matcher.ViewMatchers.withText("48 / 60")));
        }
    }
}
