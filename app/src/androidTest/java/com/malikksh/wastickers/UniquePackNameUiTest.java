package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UniquePackNameUiTest {
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
    public void newPackSuggestsNextFreeNameAndBlocksNormalizedDuplicate() {
        PackStore.addPack(context, new PackStore.Pack(
                "existing", "Мои стикеры", 3, "1", false));

        Intent intent = launcherIntent();
        try (ActivityScenario<SettingsShellActivity> ignored = ActivityScenario.launch(intent)) {
            onView(withId(R.id.packs_create_first)).perform(androidx.test.espresso.action.ViewActions.click());
            onView(withId(R.id.pack_name_dialog_input))
                    .check(matches(withText("Мои стикеры 2")))
                    .perform(replaceText("  МОИ   СТИКЕРЫ  "), closeSoftKeyboard());

            onView(withText(PackNameService.DUPLICATE_ERROR)).check(matches(isDisplayed()));
            onView(withId(R.id.pack_name_dialog_confirm)).check(matches(not(isEnabled())));
            pressBack();
        }
    }

    @Test
    public void renameKeepsOwnNameButBlocksAnotherSavedPackName() {
        PackStore.addPack(context, new PackStore.Pack(
                "one", "Первый", 3, "1", false));
        PackStore.addPack(context, new PackStore.Pack(
                "two", "Второй", 3, "1", false));

        try (ActivityScenario<SettingsShellActivity> scenario = ActivityScenario.launch(launcherIntent())) {
            scenario.onActivity(activity -> PackNameDialog.show(
                    activity,
                    "Переименовать набор",
                    "Первый",
                    "Сохранить",
                    name -> { }
            ));

            onView(withId(R.id.pack_name_dialog_confirm)).check(matches(isEnabled()));
            onView(withId(R.id.pack_name_dialog_input))
                    .perform(replaceText(" второй "), closeSoftKeyboard());
            onView(withText(PackNameService.DUPLICATE_ERROR)).check(matches(isDisplayed()));
            onView(withId(R.id.pack_name_dialog_confirm)).check(matches(not(isEnabled())));
            pressBack();
        }
    }

    private Intent launcherIntent() {
        return new Intent(context, SettingsShellActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private void clearPacks() {
        for (PackStore.Pack pack : PackStore.getPacks(context)) {
            PackStore.deletePack(context, pack.id);
        }
    }
}
