package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UnifiedHomeUiTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        EditorInstanceStateBridge.clearPersistent(context);
        for (PackStore.Pack pack : PackStore.getPacks(context)) {
            PackStore.deletePack(context, pack.id);
        }
    }

    @After
    public void tearDown() {
        EditorInstanceStateBridge.clearPersistent(context);
        for (PackStore.Pack pack : PackStore.getPacks(context)) {
            PackStore.deletePack(context, pack.id);
        }
    }

    @Test
    public void launcherStartsInLibraryAndSearchIsInline() {
        Intent intent = new Intent(context, SettingsShellActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<SettingsShellActivity> ignored = ActivityScenario.launch(intent)) {
            onView(withId(R.id.packs_panel)).check(matches(isDisplayed()));
            onView(withText("Мои наборы")).check(matches(isDisplayed()));
            onView(withId(R.id.packs_create_first)).check(matches(isDisplayed()));

            onView(withId(R.id.packs_search)).perform(click());
            onView(withId(R.id.packs_search_field)).check(matches(isDisplayed()));
            onView(withText("СБРОСИТЬ")).check(doesNotExist());
            onView(withText("НАЙТИ")).check(doesNotExist());

            onView(withId(R.id.packs_search_close)).perform(click());
            onView(withId(R.id.packs_search_field)).check(matches(withEffectiveVisibility(GONE)));
        }
    }

    @Test
    public void createPackUsesCompactNameDialogThenOpensUnifiedEditor() {
        Intent intent = new Intent(context, SettingsShellActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<SettingsShellActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.packs_create_first)).perform(click());
            onView(withId(R.id.pack_name_dialog)).check(matches(isDisplayed()));
            onView(withId(R.id.pack_name_dialog_input)).check(matches(withText("Мои стикеры")));
            onView(withId(R.id.pack_name_dialog_confirm)).perform(click());

            onView(withText("Название набора")).check(matches(org.hamcrest.Matchers.not(isDisplayed())));
            onView(withId(R.id.media_grid)).check(matches(isDisplayed()));
            onView(withId(R.id.media_continue)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                View create = activity.findViewById(R.id.nav_create);
                View media = activity.findViewById(R.id.nav_media);
                assertNotNull(create);
                assertNotNull(media);
                assertEquals(0, create.getWidth());
                assertEquals(0, create.getHeight());
                assertEquals(0, media.getWidth());
                assertEquals(0, media.getHeight());
            });
        }
    }
}
