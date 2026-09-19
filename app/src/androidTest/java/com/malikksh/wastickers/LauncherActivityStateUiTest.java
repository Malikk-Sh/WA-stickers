package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Lifecycle coverage for the redesigned launcher's editor-state bridge. */
@RunWith(AndroidJUnit4.class)
public class LauncherActivityStateUiTest {
    private static final String TRIM_PERSISTENT_KEY = "home_video_trim";
    private static final String TEST_FILE_PREFIX = "launcher_state_";
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        EditorInstanceStateBridge.clearPersistent(context);
        VideoTrimStore.clearPersistent(context, TRIM_PERSISTENT_KEY);
        clearTestFiles();
        VideoTrimStore.clear();
    }

    @After
    public void tearDown() {
        EditorInstanceStateBridge.clearPersistent(context);
        VideoTrimStore.clearPersistent(context, TRIM_PERSISTENT_KEY);
        clearTestFiles();
        VideoTrimStore.clear();
    }

    @Test
    public void modeDraftNamesSurviveRecreate() {
        try (ActivityScenario<LauncherActivity> scenario = ActivityScenario.launch(LauncherActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    ((EditText) getField(activity, "packName")).setText("Фото-черновик");
                    invoke(activity, "setAnimatedMode", new Class<?>[]{boolean.class}, true);
                    ((EditText) getField(activity, "packName")).setText("Анимация-черновик");
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });

            scenario.recreate();
            scenario.onActivity(activity -> {
                try {
                    assertTrue((Boolean) getField(activity, "animatedMode"));
                    assertEquals("Анимация-черновик", packName(activity));
                    invoke(activity, "setAnimatedMode", new Class<?>[]{boolean.class}, false);
                    assertFalse((Boolean) getField(activity, "animatedMode"));
                    assertEquals("Фото-черновик", packName(activity));
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
        }
    }

    @Test
    public void selectionOrderAndCoverSurviveRecreateForBothModes() {
        List<Uri> photos = Arrays.asList(
                Uri.parse("content://launcher.test/photo-1"),
                Uri.parse("content://launcher.test/photo-2"),
                Uri.parse("content://launcher.test/photo-3"));
        List<Uri> animated = Arrays.asList(
                Uri.parse("content://launcher.test/animated-1"),
                Uri.parse("content://launcher.test/animated-2"),
                Uri.parse("content://launcher.test/animated-3"));

        try (ActivityScenario<LauncherActivity> scenario = ActivityScenario.launch(LauncherActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    seedCurrentEditor(activity, photos, photos.get(1), "Фото-порядок");
                    invoke(activity, "setAnimatedMode", new Class<?>[]{boolean.class}, true);
                    seedCurrentEditor(activity, animated, animated.get(2), "Анимация-порядок");
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });

            scenario.recreate();
            scenario.onActivity(activity -> {
                try {
                    assertEquals(animated, selectionSnapshot(activity));
                    assertEquals(animated.get(2), getField(activity, "coverUri"));
                    assertEquals("Анимация-порядок", packName(activity));
                    invoke(activity, "setAnimatedMode", new Class<?>[]{boolean.class}, false);
                    assertEquals(photos, selectionSnapshot(activity));
                    assertEquals(photos.get(1), getField(activity, "coverUri"));
                    assertEquals("Фото-порядок", packName(activity));
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
        }
    }

    @Test
    public void videoTrimOffsetSurvivesRecreate() {
        Uri video = Uri.parse("content://launcher.test/long-video.mp4");
        VideoTrimStore.Entry entry = new VideoTrimStore.Entry(
                video.toString(), video, "long-video.mp4", 30_000L, 7_000L);

        try (ActivityScenario<LauncherActivity> scenario = ActivityScenario.launch(LauncherActivity.class)) {
            scenario.onActivity(activity -> VideoTrimStore.replaceEntries(Arrays.asList(entry)));
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals(7_000L, VideoTrimStore.getStartOffsetMs(video)));
        }
    }

    @Test
    public void draftAndTrimSurviveFreshActivityLaunch() throws Exception {
        List<Uri> photos = Arrays.asList(
                testFileUri("photo-1.jpg"),
                testFileUri("photo-2.jpg"),
                testFileUri("photo-3.jpg"));
        Uri video = testFileUri("long-video.mp4");
        List<Uri> animated = Arrays.asList(
                testFileUri("animated-1.webp"),
                testFileUri("animated-2.webp"),
                video);

        ActivityScenario<LauncherActivity> first = ActivityScenario.launch(LauncherActivity.class);
        first.onActivity(activity -> {
            try {
                seedCurrentEditor(activity, photos, photos.get(1), "Фото после рестарта");
                invoke(activity, "setAnimatedMode", new Class<?>[]{boolean.class}, true);
                seedCurrentEditor(activity, animated, video, "Анимация после рестарта");
                VideoTrimStore.replaceEntries(Arrays.asList(new VideoTrimStore.Entry(
                        video.toString(), video, "long-video.mp4", 30_000L, 7_000L)));
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
        first.close();

        try (ActivityScenario<LauncherActivity> second = ActivityScenario.launch(LauncherActivity.class)) {
            second.onActivity(activity -> {
                try {
                    assertTrue((Boolean) getField(activity, "animatedMode"));
                    assertEquals(animated, selectionSnapshot(activity));
                    assertEquals(video, getField(activity, "coverUri"));
                    assertEquals("Анимация после рестарта", packName(activity));
                    assertEquals(7_000L, VideoTrimStore.getStartOffsetMs(video));
                    invoke(activity, "setAnimatedMode", new Class<?>[]{boolean.class}, false);
                    assertEquals(photos, selectionSnapshot(activity));
                    assertEquals(photos.get(1), getField(activity, "coverUri"));
                    assertEquals("Фото после рестарта", packName(activity));
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static void seedCurrentEditor(
            MainActivity activity,
            List<Uri> items,
            Uri cover,
            String name
    ) throws Exception {
        List<Uri> selected = (List<Uri>) getField(activity, "selectedUris");
        selected.clear();
        selected.addAll(items);
        setField(activity, "coverUri", cover);
        ((EditText) getField(activity, "packName")).setText(name);
        invoke(activity, "renderPreviews", new Class<?>[0]);
        invoke(activity, "updateUiState", new Class<?>[0]);
    }

    @SuppressWarnings("unchecked")
    private static List<Uri> selectionSnapshot(MainActivity activity) throws Exception {
        return new ArrayList<>((List<Uri>) getField(activity, "selectedUris"));
    }

    private static String packName(MainActivity activity) throws Exception {
        return ((EditText) getField(activity, "packName")).getText().toString();
    }

    private Uri testFileUri(String suffix) throws Exception {
        File file = new File(context.getCacheDir(), TEST_FILE_PREFIX + suffix);
        if (!file.exists() && !file.createNewFile()) {
            throw new IllegalStateException("Could not create " + file);
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

    private static Object invoke(
            MainActivity activity,
            String name,
            Class<?>[] parameterTypes,
            Object... args
    ) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(activity, args);
    }

    private void clearTestFiles() {
        File[] files = context.getCacheDir().listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.getName().startsWith(TEST_FILE_PREFIX)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }
}
