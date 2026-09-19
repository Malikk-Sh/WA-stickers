package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
        try (ActivityScenario<LauncherActivity> scenario = ActivityScenario.launch(LauncherActivity.class)) {
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
                    clickText(activity, "Создать набор");
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });

            waitUntilProcessingStops(scenario);
            scenario.onActivity(activity -> {
                assertShownText(activity, "Повторить ошибки (1)");
                assertShownText(activity, "Создать из готовых (3)");
                assertShownTextContaining(activity, "Готово 3 стикеров, не удалось 1");
                PackBuildSession<?> session = buildSession(activity);
                assertEquals(3, session.successCount());
                assertEquals(1, session.failureCount());
                clickText(activity, "Повторить ошибки (1)");
            });

            waitUntilProcessingStops(scenario);
            scenario.onActivity(activity -> {
                assertShownText(activity, "Повторить ошибки (1)");
                assertShownText(activity, "Создать из готовых (3)");
                PackBuildSession<?> session = buildSession(activity);
                assertEquals("Retry must not reprocess the three successful stickers", 3, session.successCount());
                assertEquals(1, session.failureCount());
            });
            assertEquals(4, sourcesRef.get().size());
        }
    }

    @Test
    public void cancelButtonMovesUiIntoStoppingState() throws Exception {
        try (ActivityScenario<LauncherActivity> scenario = ActivityScenario.launch(LauncherActivity.class)) {
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

                    View cancel = requireText(activity, "Отменить обработку");
                    assertTrue(cancel.isEnabled());
                    assertTrue(cancel.performClick());

                    View stopping = requireText(activity, "Останавливаю…");
                    assertFalse(stopping.isEnabled());
                    assertShownTextContaining(activity, "Останавливаю обработку");
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
        }
    }

    @Test
    public void reorderKeepsSelectionOrderAndCoverCanBeChangedFromUi() throws Exception {
        try (ActivityScenario<LauncherActivity> scenario = ActivityScenario.launch(LauncherActivity.class)) {
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

            scenario.onActivity(activity -> {
                View coverButton = requireContentDescription(activity, "Сделать стикер 2 обложкой");
                assertTrue(coverButton.performClick());
                try {
                    assertEquals(original.get(2), getField(activity, "coverUri"));
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
                View currentCover = requireContentDescription(activity, "Текущая обложка набора");
                assertTrue(currentCover.isShown());
            });
        }
    }

    private void waitUntilProcessingStops(ActivityScenario<LauncherActivity> scenario) {
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

    private static PackBuildSession<?> buildSession(MainActivity activity) {
        try {
            return (PackBuildSession<?>) getField(activity, "buildSession");
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
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

    private static void clickText(MainActivity activity, String text) {
        View view = requireText(activity, text);
        assertTrue("View must be enabled before click: " + text, view.isEnabled());
        assertTrue("View must accept click: " + text, view.performClick());
    }

    private static void assertShownText(MainActivity activity, String text) {
        View view = requireText(activity, text);
        assertTrue("Expected visible text: " + text, view.isShown());
    }

    private static void assertShownTextContaining(MainActivity activity, String fragment) {
        View view = findTextContaining(activity.getWindow().getDecorView(), fragment);
        assertNotNull("Expected text containing: " + fragment, view);
        assertTrue("Expected visible text containing: " + fragment, view.isShown());
    }

    private static View requireText(MainActivity activity, String text) {
        View view = findText(activity.getWindow().getDecorView(), text);
        assertNotNull("Expected view with text: " + text, view);
        return view;
    }

    private static View requireContentDescription(MainActivity activity, String description) {
        View view = findContentDescription(activity.getWindow().getDecorView(), description);
        assertNotNull("Expected view with content description: " + description, view);
        return view;
    }

    private static View findText(View view, String text) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && text.contentEquals(value)) return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = findText(group.getChildAt(i), text);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static View findTextContaining(View view, String fragment) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && value.toString().contains(fragment)) return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = findTextContaining(group.getChildAt(i), fragment);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static View findContentDescription(View view, String description) {
        CharSequence value = view.getContentDescription();
        if (value != null && description.contentEquals(value)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = findContentDescription(group.getChildAt(i), description);
                if (match != null) return match;
            }
        }
        return null;
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
