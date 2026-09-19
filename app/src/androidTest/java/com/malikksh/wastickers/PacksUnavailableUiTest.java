package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
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
public class PacksUnavailableUiTest {
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        clearPacks();

        String id = "packs_unavailable_ui";
        File dir = PackStore.getPackDir(context, id);
        assertTrue(dir.mkdirs() || dir.isDirectory());
        try (FileOutputStream output = new FileOutputStream(new File(dir, "tray.png"), false)) {
            output.write(new byte[]{1, 2, 3});
        }
        // Metadata intentionally references a missing sticker file.
        PackStore.addPack(context, new PackStore.Pack(id, "Недоступный тест", 1, "1", false));
    }

    @After
    public void tearDown() {
        clearPacks();
    }

    @Test
    public void unavailablePackRemainsManageableButCannotBeSentToWhatsApp() {
        try (ActivityScenario<SettingsShellActivity> scenario = ActivityScenario.launch(SettingsShellActivity.class)) {
            scenario.onActivity(activity -> {
                View packs = activity.findViewById(R.id.nav_packs);
                assertNotNull(packs);
                packs.performClick();
                activity.refreshPacksPanelForTest();
            });

            onView(withText("Недоступный тест")).check(matches(isDisplayed()));
            onView(withText("Недоступен")).check(matches(isDisplayed()));
            onView(withText("Файлы набора не найдены на устройстве")).check(matches(isDisplayed()));
            onView(withText("В WhatsApp")).check(matches(not(isEnabled())));
        }
    }

    private void clearPacks() {
        for (PackStore.Pack pack : PackStore.getPacks(context)) {
            PackStore.deletePack(context, pack.id);
        }
    }
}
