package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
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
public class UnifiedEditorUiTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        EditorInstanceStateBridge.clearPersistent(context);
        clearPacks();
    }

    @After
    public void tearDown() {
        EditorInstanceStateBridge.clearPersistent(context);
        clearPacks();
    }

    @Test
    public void createFlowOpensUnifiedEditorWithoutWorkflowTabs() {
        try (ActivityScenario<SettingsShellActivity> ignored =
                     ActivityScenario.launch(SettingsShellActivity.class)) {
            onView(withId(R.id.packs_create_first)).perform(click());
            onView(withId(R.id.pack_name_dialog_confirm)).perform(click());

            onView(withId(R.id.editor_screen)).check(matches(isDisplayed()));
            onView(withId(R.id.media_grid)).check(matches(isDisplayed()));
            onView(withId(R.id.media_mode_photo)).check(matches(not(isDisplayed())));
            onView(withId(R.id.media_mode_animated)).check(matches(not(isDisplayed())));
            onView(withId(R.id.nav_create)).check(matches(not(isDisplayed())));
            onView(withId(R.id.nav_media)).check(matches(not(isDisplayed())));
            onView(withId(R.id.nav_build)).check(matches(not(isDisplayed())));
            onView(withId(R.id.nav_packs)).check(matches(not(isDisplayed())));
        }
    }

    @Test
    public void addMediaUsesSourceBottomSheetAndBackReturnsToLibrary() {
        try (ActivityScenario<SettingsShellActivity> ignored =
                     ActivityScenario.launch(SettingsShellActivity.class)) {
            onView(withId(R.id.packs_create_first)).perform(click());
            onView(withId(R.id.pack_name_dialog_confirm)).perform(click());

            onView(withText("Выбрать файлы")).perform(click());
            onView(withText("Галерея")).check(matches(isDisplayed()));
            onView(withText("Файл")).check(matches(isDisplayed()));
            androidx.test.espresso.Espresso.pressBack();

            onView(withId(R.id.editor_back)).perform(click());
            onView(withId(R.id.packs_panel)).check(matches(isDisplayed()));
        }
    }

    private void clearPacks() {
        for (PackStore.Pack pack : PackStore.getPacks(context)) {
            PackStore.deletePack(context, pack.id);
        }
    }
}
