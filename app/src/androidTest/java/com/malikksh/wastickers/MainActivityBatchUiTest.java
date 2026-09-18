package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class MainActivityBatchUiTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        clearSavedPacks();
        clearTestFiles();
    }

    @After
    public void tearDown() {
        clearSavedPacks();
        clearTestFiles();
    }

    @Test
    public void partialFailureOffersRetryAndRetriesOnlyFailedItem() throws Exception {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            AtomicReference<List<Uri>> sourcesRef = new AtomicReference<>();
            scenario.onActivity(activity -> {
                try {
                    List<Uri> sources = Arrays.asList(
                            writeTestImage(activity, "ui_test_one.png", 0xFF25D366),
                            writeTestImage(activity, "ui_test_two.png", 0xFF128C7E),
                            writeTestImage(activity, "ui_test_three.png", 0xFF075E54),
                            Uri.fromFile(new File(activity.getCacheDir(), "ui_test_missing.png"))
                    );
                    sourcesRef.set(sources);
                    seedSelection(activity, sources, sources.get(0), false);
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });

            onView(withText("Создать набор")).perform(scrollTo(), click());
            waitUntilProcessingStops(scenario);

            onView(withText("Повторить ошибки (1)")).check(matches(isDisplayed()));
            onView(withText("Создать из готовых (3)")).check(matches(isDisplayed()));
            onView(withText(containsString("Готово 3 стикеров, не удалось 1")))
                    .check(matches(isDisplayed()));

            onView(withText("Повторить ошибки (1)")).perform(scrollTo(), click());
            waitUntilProcessingStops(scenario);

            onView(withText("Повторить ошибки (1)")).check(matches(isDisplayed()));
            onView(withText("Создать из готовых (3)")).check(matches(isDisplayed()));
            assertEquals(4, sourcesRef.get().size());
        }
    }

    @Test
    public void cancelButtonMovesUiIntoStoppingState() throws Exception {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    List<Uri> sources = Arrays.asList(
                            writeTestImage(activity, "ui_test_cancel_one.png", Color.RED),
                            writeTestImage(activity, "ui_test_cancel_two.png", Color.GREEN),
                            writeTestImage(activity, "ui_test_cancel_three.png", Color.BLUE)
                    );
                    seedSelection(activity, sources, sources.get(0), false);
                    setField(activity, "processing", true);
                    invoke(activity, "updateUiState");
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });

            onView(withText("Отменить обработку")).perform(scrollTo(), click());
            onView(withText("Останавливаю…")).check(matches(not(isEnabled())));
            onView(withText(containsString("Останавливаю обработку")))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void reorderKeepsSelectionOrderAndCoverCanBeChangedFromUi() throws Exception {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            AtomicReference<List<Uri>> sourcesRef = new AtomicReference<>();
            scenario.onActivity(activity -> {
                try {
                    List<Uri> sources = Arrays.asList(
                            writeTestImage(activity, "ui_test_order_one.png", 0xFFAA3366),
                            writeTestImage(activity, "ui_test_order_two.png", 0xFF3366AA),
                            writeTestImage(activity, "ui_test_order_three.png", 0xFF66AA33)
                    );
                    sourcesRef.set(sources);
                    seedSelection(activity, sources, sources.get(0), false);
                    invoke(activity, "moveSticker", new Class<?>[]{int.class, int.class}, 0, 2);
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });

            AtomicReference<List<Uri>> reorderedRef = new AtomicReference<>();
            scenario.onActivity(activity -> {
                try {
                    reorderedRef.set(selectionSnapshot(activity));
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
            List<Uri> original = sourcesRef.get();
            assertEquals(Arrays.asList(original.get(1), original.get(2), original.get(0)), reorderedRef.get());

            onView(withContentDescription("Сделать стикер 2 обложкой")).perform(click());

            AtomicReference<Uri> coverRef = new AtomicReference<>();
            scenario.onActivity(activity -> {
                try {
                    coverRef.set((Uri) getField(activity, "coverUri"));
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
            assertEquals(original.get(2), coverRef.get());
            onView(withContentDescription("Текущая обложка набора")).check(matches(isDisplayed()));
        }
    }

    private void waitUntilProcessingStops(ActivityScenario<HomeActivity> scenario) {
        long deadline = SystemClock.uptimeMillis() + 15_000L;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicBoolean processing = new AtomicBoolean(true);
            scenario.onActivity(activity -> {
                try {
                    processing.set((Boolean) getField(activity, "processing"));
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
            if (!processing.get()) return;
            SystemClock.sleep(75L);
        }
        fail("Batch processing did not finish before the test deadline");
    }

    @SuppressWarnings("unchecked")
    private static void seedSelection(MainActivity activity,
                                      List<Uri> items,
                                      Uri cover,
                                      boolean animated) throws Exception {
        invoke(activity, "invalidateCurrentPack");
        List<Uri> selected = (List<Uri>) getField(activity, "selectedUris");
        selected.clear();
        selected.addAll(items);
        setField(activity, "animatedMode", animated);
        setField(activity, "coverUri", cover);
        invoke(activity, "renderPreviews");
        invoke(activity, "updateModeUi");
        invoke(activity, "updateUiState");
    }

    @SuppressWarnings("unchecked")
    private static List<Uri> selectionSnapshot(MainActivity activity) throws Exception {
        return new ArrayList<>((List<Uri>) getField(activity, "selectedUris"));
    }

    private static Uri writeTestImage(MainActivity activity, String name, int color) throws Exception {
        File file = new File(activity.getCacheDir(), name);
        Bitmap bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
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

    private static Object getField(MainActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }

    private static void setField(MainActivity activity, String name, Object value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(activity, value);
    }

    private static Object invoke(MainActivity activity, String name) throws Exception {
        return invoke(activity, name, new Class<?>[0]);
    }

    private static Object invoke(MainActivity activity,
                                 String name,
                                 Class<?>[] parameterTypes,
                                 Object... args) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(activity, args);
    }

    private void clearSavedPacks() {
        for (PackStore.Pack pack : PackStore.getPacks(context)) {
            PackStore.deletePack(context, pack.id);
        }
    }

    private void clearTestFiles() {
        File[] files = context.getCacheDir().listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.getName().startsWith("ui_test_")) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }
}
