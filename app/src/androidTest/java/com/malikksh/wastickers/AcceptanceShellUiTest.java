package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(AndroidJUnit4.class)
public class AcceptanceShellUiTest {
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        EditorInstanceStateBridge.clearPersistent(context);
        clearPacks();
        createPack("acceptance_pack", "Acceptance pack");
    }

    @After
    public void tearDown() {
        EditorInstanceStateBridge.clearPersistent(context);
        clearPacks();
    }

    @Test
    public void primaryShellActionsUseAccessibleTouchTargets() {
        try (ActivityScenario<SettingsShellActivity> scenario = ActivityScenario.launch(SettingsShellActivity.class)) {
            scenario.onActivity(activity -> {
                View settings = activity.findViewById(R.id.app_settings);
                View overflow = activity.findViewById(R.id.app_overflow);
                assertNotNull(settings);
                assertNotNull(overflow);

                int min = Math.round(48 * activity.getResources().getDisplayMetrics().density);
                assertTrue(settings.getLayoutParams().width >= min);
                assertTrue(settings.getLayoutParams().height >= min);
                assertTrue(overflow.getLayoutParams().width >= min);
                assertTrue(overflow.getLayoutParams().height >= min);
                assertEquals("Настройки", settings.getContentDescription());
                assertEquals("Ещё", overflow.getContentDescription());
            });
        }
    }

    @Test
    public void savedPackActionsOpenAsBottomActionSheet() {
        try (ActivityScenario<SettingsShellActivity> scenario = ActivityScenario.launch(SettingsShellActivity.class)) {
            openPacks(scenario);

            onView(withContentDescription("Действия с набором Acceptance pack")).perform(click());
            onView(withText("Переименовать")).check(matches(isDisplayed()));
            onView(withText("Дублировать")).check(matches(isDisplayed()));
            onView(withText("Детали")).check(matches(isDisplayed()));
            onView(withText("Удалить")).check(matches(isDisplayed()));
            pressBack();
        }
    }

    @Test
    public void packDetailsOpenAsSecondaryScreenWithoutBottomNavigation() {
        try (ActivityScenario<SettingsShellActivity> scenario = ActivityScenario.launch(SettingsShellActivity.class)) {
            openPacks(scenario);

            onView(withContentDescription("Действия с набором Acceptance pack")).perform(click());
            onView(withText("Детали")).perform(click());
            onView(withText("Acceptance pack")).check(matches(isDisplayed()));
            onView(withText("Тип: Фото\nСтикеров: 3\nХранение: локально на устройстве"))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.nav_create)).check(doesNotExist());
            pressBack();
        }
    }

    @Test
    public void createHelpOpensSecondaryScreen() {
        try (ActivityScenario<SettingsShellActivity> ignored = ActivityScenario.launch(SettingsShellActivity.class)) {
            onView(withContentDescription("Ещё")).perform(click());
            onView(withText("Помощь")).perform(click());
            onView(withText("Помощь")).check(matches(isDisplayed()));
            onView(withText("Создать → настроить медиа → собрать → сохранить. Ошибки можно повторить отдельно. Фото и анимированные черновики сохраняются независимо."))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.nav_create)).check(doesNotExist());
            pressBack();
        }
    }

    private void openPacks(ActivityScenario<SettingsShellActivity> scenario) {
        scenario.onActivity(activity -> {
            View packs = activity.findViewById(R.id.nav_packs);
            assertNotNull(packs);
            packs.performClick();
            activity.refreshPacksPanelForTest();
        });
    }

    private void createPack(String id, String name) throws Exception {
        File dir = PackStore.getPackDir(context, id);
        assertTrue(dir.mkdirs() || dir.isDirectory());
        write(new File(dir, "tray.png"));
        write(new File(dir, "1.webp"));
        write(new File(dir, "2.webp"));
        write(new File(dir, "3.webp"));
        PackStore.addPack(context, new PackStore.Pack(id, name, 3, "1", false));
    }

    private void write(File file) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(new byte[]{1, 2, 3, 4});
        }
    }

    private void clearPacks() {
        for (PackStore.Pack pack : PackStore.getPacks(context)) {
            PackStore.deletePack(context, pack.id);
        }
    }
}
