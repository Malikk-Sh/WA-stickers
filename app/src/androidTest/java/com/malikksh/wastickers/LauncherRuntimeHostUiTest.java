package com.malikksh.wastickers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

/** Regression coverage for the lightweight production runtime host. */
@RunWith(AndroidJUnit4.class)
public class LauncherRuntimeHostUiTest {
    @Test
    public void launcherBridgeInitializesStateControlsWithoutLegacyLongScroll() {
        try (ActivityScenario<LauncherTestHostActivity> scenario =
                     ActivityScenario.launch(LauncherTestHostActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(readMainField(activity, "packName"));
                assertNotNull(readMainField(activity, "photoModeButton"));
                assertNotNull(readMainField(activity, "animatedModeButton"));
                assertNotNull(readMainField(activity, "statusText"));
                assertNotNull(readMainField(activity, "fileProgressContainer"));

                ViewGroup content = activity.findViewById(android.R.id.content);
                assertNotNull(content);
                assertTrue(content.getChildCount() == 1);
                assertTrue(content.getChildAt(0) instanceof FrameLayout);
                assertFalse(containsText(content, "Стикеры из фото и видео"));
                assertFalse(containsText(content, "1  Название набора"));
                assertFalse(containsText(content, "3  Готовый набор"));
            });
        }
    }

    private static Object readMainField(MainActivity activity, String name) {
        try {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(activity);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Missing runtime field: " + name, error);
        }
    }

    private static boolean containsText(View view, String expected) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && expected.contentEquals(value)) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsText(group.getChildAt(i), expected)) return true;
            }
        }
        return false;
    }
}
