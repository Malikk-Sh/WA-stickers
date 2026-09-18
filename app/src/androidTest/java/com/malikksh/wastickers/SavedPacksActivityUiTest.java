package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.hamcrest.Matchers.isA;

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
public class SavedPacksActivityUiTest {
    private static final String PACK_ID = "ui_test_pack";
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        clearSavedPacks();
        PackStore.addPack(context, new PackStore.Pack(
                PACK_ID,
                "Тестовый набор",
                3,
                "1",
                false
        ));
    }

    @After
    public void tearDown() {
        clearSavedPacks();
    }

    @Test
    public void savedPackCanBeRenamedAndDeleted() {
        try (ActivityScenario<SavedPacksActivity> ignored = ActivityScenario.launch(SavedPacksActivity.class)) {
            onView(withText("Тестовый набор")).check(matches(isDisplayed()));
            onView(withText("Фото · 3 стикеров")).check(matches(isDisplayed()));

            onView(withContentDescription("Действия с набором Тестовый набор")).perform(click());
            onView(withText("Переименовать")).perform(click());
            onView(isA(EditText.class)).perform(replaceText("Новый набор"));
            closeSoftKeyboard();
            onView(withText("Сохранить")).perform(click());
            onView(withText("Новый набор")).check(matches(isDisplayed()));

            onView(withContentDescription("Действия с набором Новый набор")).perform(click());
            onView(withText("Удалить")).perform(click());
            onView(withText("Удалить набор?")).check(matches(isDisplayed()));
            onView(withText("Удалить")).perform(click());
            onView(withText("Пока нет сохранённых наборов")).check(matches(isDisplayed()));
        }
    }

    private void clearSavedPacks() {
        for (PackStore.Pack pack : PackStore.getPacks(context)) {
            PackStore.deletePack(context, pack.id);
        }
    }
}
