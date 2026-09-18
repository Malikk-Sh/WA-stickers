package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PacksShellActivityUiTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        clearPacks();
    }

    @After
    public void tearDown() {
        clearPacks();
    }

    @Test
    public void packsTabShowsStatsCardsAndTypeFilters() {
        PackStore.addPack(context, new PackStore.Pack("packs_photo", "Photo pack", 3, "1", false));
        PackStore.addPack(context, new PackStore.Pack("packs_anim", "Anim pack", 5, "1", true));

        try (ActivityScenario<PacksShellActivity> scenario = ActivityScenario.launch(PacksShellActivity.class)) {
            scenario.onActivity(activity -> {
                activity.refreshPacksPanelForTest();
                assertNotNull(activity.findViewById(R.id.nav_packs));
                activity.findViewById(R.id.nav_packs).performClick();
            });

            onView(withId(R.id.packs_panel)).check(matches(isDisplayed()));
            onView(withText("2 набора")).check(matches(isDisplayed()));
            onView(withText("8 стикеров")).check(matches(isDisplayed()));
            onView(withText("Photo pack")).check(matches(isDisplayed()));
            onView(withText("Anim pack")).check(matches(isDisplayed()));
            onView(withContentDescription("Добавить набор Photo pack в WhatsApp"))
                    .check(matches(isDisplayed()));

            onView(withId(R.id.packs_filter_photo)).perform(click());
            onView(withText("Photo pack")).check(matches(isDisplayed()));
            onView(withText("Anim pack")).check(doesNotExist());

            onView(withId(R.id.packs_filter_animated)).perform(click());
            onView(withText("Anim pack")).check(matches(isDisplayed()));
            onView(withText("Photo pack")).check(doesNotExist());
        }
    }

    @Test
    public void packCanBeRenamedAndDeletedWithConfirmation() {
        PackStore.addPack(context, new PackStore.Pack("packs_edit", "Editable pack", 4, "1", false));

        try (ActivityScenario<PacksShellActivity> scenario = ActivityScenario.launch(PacksShellActivity.class)) {
            scenario.onActivity(activity -> {
                activity.refreshPacksPanelForTest();
                activity.findViewById(R.id.nav_packs).performClick();
            });

            onView(withContentDescription("Действия с набором Editable pack")).perform(click());
            onView(withText("Переименовать")).perform(click());
            onView(isAssignableFrom(EditText.class)).perform(replaceText("Renamed pack"));
            onView(withText("Сохранить")).perform(click());
            onView(withText("Renamed pack")).check(matches(isDisplayed()));

            onView(withContentDescription("Действия с набором Renamed pack")).perform(click());
            onView(withText("Удалить")).perform(click());
            onView(withText("Удалить набор?")).check(matches(isDisplayed()));
            onView(withText("Удалить")).perform(click());

            onView(withText("Пока нет наборов")).check(matches(isDisplayed()));
            onView(withText("Созданные наборы появятся здесь")).check(matches(isDisplayed()));
            onView(withId(R.id.packs_create_first)).check(matches(isDisplayed()));
        }
    }

    private void clearPacks() {
        for (PackStore.Pack pack : PackStore.getPacks(context)) {
            PackStore.deletePack(context, pack.id);
        }
    }
}
