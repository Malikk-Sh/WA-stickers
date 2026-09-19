package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/** Lifecycle coverage for the redesigned launcher's editor-state bridge. */
@RunWith(AndroidJUnit4.class)
public class LauncherActivityStateUiTest {
    private static final String TEST_FILE_PREFIX = "launcher_state_";
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        EditorInstanceStateBridge.clearPersistent(context);
        VideoTrimStore.clearPersistent(context, AppSettings.TRIM_PERSISTENT_KEY);
        clearTestFiles();
        VideoTrimStore.clear();
    }

    @After
    public void tearDown() {
        EditorInstanceStateBridge.clearPersistent(context);
        VideoTrimStore.clearPersistent(context, AppSettings.TRIM_PERSISTENT_KEY);
        clearTestFiles();
        VideoTrimStore.clear();
    }

    @Test
    public void modeDraftNamesSurviveRecreate() {
        try (ActivityScenario<LauncherTestHostActivity> scenario =
                     ActivityScenario.launch(LauncherTestHostActivity.class)) {
            scenario.onActivity(activity -> {
                activity.runtimePackName().setText("Фото-черновик");
                activity.runtimeSetAnimatedMode(true);
                activity.runtimePackName().setText("Анимация-черновик");
            });

            scenario.recreate();
            scenario.onActivity(activity -> {
                assertTrue(activity.runtimeIsAnimatedMode());
                assertEquals("Анимация-черновик", packName(activity));
                activity.runtimeSetAnimatedMode(false);
                assertFalse(activity.runtimeIsAnimatedMode());
                assertEquals("Фото-черновик", packName(activity));
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

        try (ActivityScenario<LauncherTestHostActivity> scenario =
                     ActivityScenario.launch(LauncherTestHostActivity.class)) {
            scenario.onActivity(activity -> {
                seedCurrentEditor(activity, photos, photos.get(1), "Фото-порядок");
                activity.runtimeSetAnimatedMode(true);
                seedCurrentEditor(activity, animated, animated.get(2), "Анимация-порядок");
            });

            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(animated, selectionSnapshot(activity));
                assertEquals(animated.get(2), activity.runtimeCoverUri());
                assertEquals("Анимация-порядок", packName(activity));
                activity.runtimeSetAnimatedMode(false);
                assertEquals(photos, selectionSnapshot(activity));
                assertEquals(photos.get(1), activity.runtimeCoverUri());
                assertEquals("Фото-порядок", packName(activity));
            });
        }
    }

    @Test
    public void videoTrimOffsetSurvivesRecreate() {
        Uri video = Uri.parse("content://launcher.test/long-video.mp4");
        VideoTrimStore.Entry entry = new VideoTrimStore.Entry(
                video.toString(), video, "long-video.mp4", 30_000L, 7_000L);

        try (ActivityScenario<LauncherTestHostActivity> scenario =
                     ActivityScenario.launch(LauncherTestHostActivity.class)) {
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

        ActivityScenario<LauncherTestHostActivity> first =
                ActivityScenario.launch(LauncherTestHostActivity.class);
        first.onActivity(activity -> {
            seedCurrentEditor(activity, photos, photos.get(1), "Фото после рестарта");
            activity.runtimeSetAnimatedMode(true);
            seedCurrentEditor(activity, animated, video, "Анимация после рестарта");
            VideoTrimStore.replaceEntries(Arrays.asList(new VideoTrimStore.Entry(
                    video.toString(), video, "long-video.mp4", 30_000L, 7_000L)));
        });
        first.close();

        try (ActivityScenario<LauncherTestHostActivity> second =
                     ActivityScenario.launch(LauncherTestHostActivity.class)) {
            second.onActivity(activity -> {
                assertTrue(activity.runtimeIsAnimatedMode());
                assertEquals(animated, selectionSnapshot(activity));
                assertEquals(video, activity.runtimeCoverUri());
                assertEquals("Анимация после рестарта", packName(activity));
                assertEquals(7_000L, VideoTrimStore.getStartOffsetMs(video));
                activity.runtimeSetAnimatedMode(false);
                assertEquals(photos, selectionSnapshot(activity));
                assertEquals(photos.get(1), activity.runtimeCoverUri());
                assertEquals("Фото после рестарта", packName(activity));
            });
        }
    }

    private static void seedCurrentEditor(
            MainActivity activity,
            List<Uri> items,
            Uri cover,
            String name
    ) {
        activity.runtimeRestoreEditorState(
                activity.runtimeIsAnimatedMode(),
                items,
                cover,
                name,
                activity.runtimeCurrentPack()
        );
    }

    private static List<Uri> selectionSnapshot(MainActivity activity) {
        return activity.runtimeSelectedUrisSnapshot();
    }

    private static String packName(MainActivity activity) {
        return activity.runtimePackName().getText().toString();
    }

    private Uri testFileUri(String suffix) throws Exception {
        File file = new File(context.getCacheDir(), TEST_FILE_PREFIX + suffix);
        if (!file.exists() && !file.createNewFile()) {
            throw new IllegalStateException("Could not create " + file);
        }
        return Uri.fromFile(file);
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
