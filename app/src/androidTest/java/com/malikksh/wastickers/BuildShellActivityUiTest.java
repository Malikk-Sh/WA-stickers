package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;

import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class BuildShellActivityUiTest {
    @Test
    public void buildTabShowsSummaryProgressFilesAndEnablesStartForValidSelection() {
        try (ActivityScenario<BuildShellActivity> scenario = ActivityScenario.launch(BuildShellActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    List<Uri> items = Arrays.asList(
                            writeImage(activity, "build_shell_one.png", 0xFF25D366),
                            writeImage(activity, "build_shell_two.png", 0xFF128C7E),
                            writeImage(activity, "build_shell_three.png", 0xFF075E54)
                    );
                    seedSelection(activity, items, "Build UI test");
                    activity.refreshBuildPanelForTest();
                    View nav = activity.findViewById(R.id.nav_build);
                    assertNotNull(nav);
                    nav.performClick();
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });

            onView(withId(R.id.build_panel)).check(matches(isDisplayed()));
            onView(withId(R.id.build_pack_name)).check(matches(withText("Build UI test")));
            onView(withId(R.id.build_progress_label)).check(matches(withText("0 из 3 готовы")));
            onView(withText("Файлы стикеров")).check(matches(isDisplayed()));
            onView(withId(R.id.build_primary)).check(matches(withText("Создать набор")));
            onView(withId(R.id.build_primary)).check(matches(isEnabled()));
        }
    }

    @Test
    public void partialResultOffersFinalizeRetryAndPerItemSkip() {
        try (ActivityScenario<BuildShellActivity> scenario = ActivityScenario.launch(BuildShellActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    List<Uri> items = Arrays.asList(
                            writeImage(activity, "build_partial_one.png", 0xFF11AA66),
                            writeImage(activity, "build_partial_two.png", 0xFF228877),
                            writeImage(activity, "build_partial_three.png", 0xFF336688),
                            Uri.fromFile(new File(activity.getCacheDir(), "build_partial_missing.png"))
                    );
                    seedSelection(activity, items, "Partial pack");
                    PackBuildSession<Uri> session = buildSession(activity);
                    session.begin(
                            "build_ui_partial",
                            "Partial pack",
                            PackStore.getPackDir(activity, "build_ui_partial"),
                            false,
                            items.get(0)
                    );
                    session.setAutoFinalizeAllowed(false);
                    session.recordSuccess(items.get(0), 0, 0);
                    session.recordSuccess(items.get(1), 0, 0);
                    session.recordSuccess(items.get(2), 0, 0);
                    session.setFailures(new ArrayList<>(Arrays.asList(items.get(3))));
                    activity.refreshBuildPanelForTest();
                    View nav = activity.findViewById(R.id.nav_build);
                    assertNotNull(nav);
                    nav.performClick();
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });

            onView(withId(R.id.build_progress_label)).check(matches(withText("3 из 4 готовы")));
            onView(withId(R.id.build_primary)).check(matches(withText("Создать из готовых")));
            onView(withId(R.id.build_secondary)).check(matches(withText("Повторить ошибки")));
            onView(withText("! Ошибка")).check(matches(isDisplayed()));
            onView(withText("Пропустить")).perform(click());
            onView(withText("Пропущено")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void cancellationChangesPrimaryActionToStoppingState() {
        try (ActivityScenario<BuildShellActivity> scenario = ActivityScenario.launch(BuildShellActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    List<Uri> items = Arrays.asList(
                            writeImage(activity, "build_cancel_one.png", 0xFF884422),
                            writeImage(activity, "build_cancel_two.png", 0xFF448822),
                            writeImage(activity, "build_cancel_three.png", 0xFF224488)
                    );
                    seedSelection(activity, items, "Cancel pack");
                    PackBuildSession<Uri> session = buildSession(activity);
                    session.begin(
                            "build_ui_cancel",
                            "Cancel pack",
                            PackStore.getPackDir(activity, "build_ui_cancel"),
                            false,
                            items.get(0)
                    );
                    session.setAutoFinalizeAllowed(false);
                    setMainField(activity, "processing", true);
                    activity.refreshBuildPanelForTest();
                    View nav = activity.findViewById(R.id.nav_build);
                    assertNotNull(nav);
                    nav.performClick();
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });

            onView(withId(R.id.build_primary)).check(matches(withText("Остановить обработку")));
            onView(withId(R.id.build_primary)).perform(click());
            onView(withId(R.id.build_primary)).check(matches(withText("Останавливаю…")));
            onView(withId(R.id.build_primary)).check(matches(not(isEnabled())));
        }
    }

    @SuppressWarnings("unchecked")
    private static void seedSelection(BuildShellActivity activity,
                                      List<Uri> items,
                                      String name) throws Exception {
        List<Uri> selected = (List<Uri>) getMainField(activity, "selectedUris");
        selected.clear();
        selected.addAll(items);
        setMainField(activity, "animatedMode", false);
        setMainField(activity, "coverUri", items.isEmpty() ? null : items.get(0));
        EditText packName = (EditText) getMainField(activity, "packName");
        packName.setText(name);
    }

    @SuppressWarnings("unchecked")
    private static PackBuildSession<Uri> buildSession(BuildShellActivity activity) throws Exception {
        return (PackBuildSession<Uri>) getMainField(activity, "buildSession");
    }

    private static Object getMainField(BuildShellActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }

    private static void setMainField(BuildShellActivity activity, String name, Object value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(activity, value);
    }

    private static Uri writeImage(BuildShellActivity activity, String name, int color) throws Exception {
        File file = new File(activity.getCacheDir(), name);
        Bitmap bitmap = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IllegalStateException("Could not create test image " + name);
            }
        } finally {
            bitmap.recycle();
        }
        return Uri.fromFile(file);
    }
}
