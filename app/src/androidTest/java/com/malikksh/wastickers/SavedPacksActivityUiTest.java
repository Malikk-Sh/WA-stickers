package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

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
    public void savedPackStoreChangesAreRendered() {
        try (ActivityScenario<SavedPacksActivity> scenario = ActivityScenario.launch(SavedPacksActivity.class)) {
            onView(withText("Тестовый набор")).check(matches(isDisplayed()));
            onView(withText("Фото · 3 стикеров")).check(matches(isDisplayed()));

            scenario.onActivity(activity -> {
                try {
                    PackStore.Pack renamed = PackStore.renamePack(activity, PACK_ID, "Новый набор");
                    assertNotNull(renamed);
                    invokeRender(activity);
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
            onView(withText("Новый набор")).check(matches(isDisplayed()));

            scenario.onActivity(activity -> {
                try {
                    assertTrue(PackStore.deletePack(activity, PACK_ID));
                    invokeRender(activity);
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
            onView(withText("Пока нет сохранённых наборов")).check(matches(isDisplayed()));
        }
    }

    private static void invokeRender(SavedPacksActivity activity) throws Exception {
        Method method = SavedPacksActivity.class.getDeclaredMethod("renderPacks");
        method.setAccessible(true);
        method.invoke(activity);
    }

    private void clearSavedPacks() {
        for (PackStore.Pack pack : PackStore.getPacks(context)) {
            PackStore.deletePack(context, pack.id);
        }
    }
}
