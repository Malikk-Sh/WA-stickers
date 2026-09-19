package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Focused regression coverage for the final UI/UX handoff compliance pass. */
@RunWith(AndroidJUnit4.class)
public class HandoffComplianceUiTest {
    private static final String TEST_PREFIX = "handoff_compliance_";

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        AppSettings.setAppearance(context, AppSettings.APPEARANCE_SYSTEM);
        AppSettings.setCompactMode(context, false);
        AppSettings.setKeepDrafts(context, true);
        EditorInstanceStateBridge.clearPersistent(context);
        VideoTrimStore.clearPersistent(context, AppSettings.TRIM_PERSISTENT_KEY);
        VideoTrimStore.clear();
        clearTestFiles();
        clearPacks();
    }

    @After
    public void tearDown() {
        AppSettings.setAppearance(context, AppSettings.APPEARANCE_SYSTEM);
        AppSettings.setCompactMode(context, false);
        AppSettings.setKeepDrafts(context, true);
        EditorInstanceStateBridge.clearPersistent(context);
        VideoTrimStore.clearPersistent(context, AppSettings.TRIM_PERSISTENT_KEY);
        VideoTrimStore.clear();
        clearTestFiles();
        clearPacks();
    }

    @Test
    public void restoredDraftIsVisibleAndContinueIsReadyAfterFreshLaunch() throws Exception {
        List<Uri> photos = Arrays.asList(
                writeImage(context, "draft_one.png", 0xFF225544),
                writeImage(context, "draft_two.png", 0xFF337755),
                writeImage(context, "draft_three.png", 0xFF448866));

        ActivityScenario<SettingsShellActivity> first = ActivityScenario.launch(SettingsShellActivity.class);
        first.onActivity(activity -> {
            try {
                seedSelection(activity, photos, photos.get(0), "Черновик handoff");
                EditorInstanceStateBridge.savePersistent(activity);
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
        first.close();

        try (ActivityScenario<SettingsShellActivity> ignored =
                     ActivityScenario.launch(SettingsShellActivity.class)) {
            onView(withId(R.id.create_draft_chip)).check(matches(isDisplayed()));
            onView(withId(R.id.create_draft_chip)).check(matches(withText("Черновик")));
            onView(withId(R.id.create_media_counter)).check(matches(withText("3 / 30")));
            onView(withId(R.id.create_continue)).check(matches(isEnabled()));
        }
    }

    @Test
    public void packsTabUsesItsOwnSearchAndOverflowHeader() {
        try (ActivityScenario<SettingsShellActivity> scenario =
                     ActivityScenario.launch(SettingsShellActivity.class)) {
            scenario.onActivity(activity -> {
                View packs = activity.findViewById(R.id.nav_packs);
                assertNotNull(packs);
                assertTrue(packs.performClick());
                View search = activity.findViewById(R.id.packs_search);
                View overflow = activity.findViewById(R.id.packs_overflow);
                assertNotNull(search);
                assertNotNull(overflow);
                assertTrue(search.isShown());
                assertTrue(overflow.isShown());
            });

            onView(withId(R.id.packs_search)).check(matches(isDisplayed()));
            onView(withId(R.id.packs_overflow)).check(matches(isDisplayed()));
            onView(withId(R.id.app_settings)).check(matches(not(isDisplayed())));
            onView(withId(R.id.app_overflow)).check(matches(not(isDisplayed())));
        }
    }

    @Test
    public void mediaTileHasAccessible48DpActionsAndAlternativeReorder() throws Exception {
        List<Uri> photos = Arrays.asList(
                writeImage(context, "media_one.png", 0xFF115544),
                writeImage(context, "media_two.png", 0xFF227755),
                writeImage(context, "media_three.png", 0xFF338866));

        try (ActivityScenario<SettingsShellActivity> scenario =
                     ActivityScenario.launch(SettingsShellActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    seedSelection(activity, photos, photos.get(0), "Media accessibility");
                    activity.refreshShellState();
                    View media = activity.findViewById(R.id.nav_media);
                    assertNotNull(media);
                    media.performClick();
                    activity.refreshShellState();
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });

            scenario.onActivity(activity -> {
                View remove = findByDescription(activity.getWindow().getDecorView(), "Удалить файл 1");
                View cover = findByDescription(activity.getWindow().getDecorView(), "Выбрать как обложку");
                assertNotNull(remove);
                assertNotNull(cover);
                int min = Math.round(48 * activity.getResources().getDisplayMetrics().density);
                assertTrue(remove.getWidth() >= min && remove.getHeight() >= min);
                assertTrue(cover.getWidth() >= min && cover.getHeight() >= min);

                View tile = findByDescriptionPrefix(
                        activity.getWindow().getDecorView(), "handoff_compliance_media_one.png, позиция 1 из 3");
                assertNotNull(tile);
                AccessibilityNodeInfo info = tile.createAccessibilityNodeInfo();
                AccessibilityNodeInfo.AccessibilityAction moveRight = null;
                for (AccessibilityNodeInfo.AccessibilityAction action : info.getActionList()) {
                    CharSequence label = action.getLabel();
                    if (label != null && "Переместить вправо".contentEquals(label)) {
                        moveRight = action;
                        break;
                    }
                }
                assertNotNull("Alternative reorder action is missing", moveRight);
                assertTrue(tile.performAccessibilityAction(moveRight.getId(), new Bundle()));

                List<Uri> reordered = activity.runtimeSelectedUrisSnapshot();
                assertEquals(photos.get(1), reordered.get(0));
                assertEquals(photos.get(0), reordered.get(1));
            });
        }
    }

    @Test
    public void videoTrimHonorsDarkAppearance() {
        AppSettings.setAppearance(context, AppSettings.APPEARANCE_DARK);
        Uri video = Uri.parse("content://handoff.test/long-video.mp4");
        VideoTrimStore.replaceEntries(Arrays.asList(new VideoTrimStore.Entry(
                video.toString(), video, "long-video.mp4", 25_000L, 2_000L)));

        Intent intent = new Intent(context, VideoTrimActivity.class);
        intent.putStringArrayListExtra(
                VideoTrimActivity.EXTRA_KEYS,
                new ArrayList<>(Arrays.asList(video.toString())));
        try (ActivityScenario<VideoTrimActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> assertTrue(AppSettings.isDark(activity)));
            onView(withId(R.id.trim_title)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void savedPackCanBeDuplicatedAndUnavailableMetadataCanBeCleaned() throws Exception {
        String id = "handoff_source_pack";
        File dir = PackStore.getPackDir(context, id);
        assertTrue(dir.mkdirs() || dir.isDirectory());
        writeBytes(new File(dir, "1.webp"), new byte[]{1, 2, 3});
        writeBytes(new File(dir, "2.webp"), new byte[]{4, 5, 6});
        writeBytes(new File(dir, "tray.png"), new byte[]{7, 8, 9});
        PackStore.addPack(context, new PackStore.Pack(id, "Исходный набор", 2, "1", false));

        PackStore.Pack copy = PackStore.duplicatePack(context, id);
        assertNotNull(copy);
        assertTrue(PackStore.getStickerFile(context, copy.id, "1.webp").isFile());
        assertTrue(PackStore.getStickerFile(context, copy.id, "tray.png").isFile());

        assertTrue(PackStore.getStickerFile(context, id, "1.webp").delete());
        int removed = PackStore.removeUnavailablePacks(context);
        assertEquals(1, removed);
        assertTrue(PackStore.getPack(context, id) == null);
        assertNotNull(PackStore.getPack(context, copy.id));
    }

    private static void seedSelection(SettingsShellActivity activity,
                                      List<Uri> items,
                                      Uri cover,
                                      String name) {
        activity.runtimeRestoreEditorState(false, items, cover, name, null);
    }

    private static Uri writeImage(Context context, String suffix, int color) throws Exception {
        File file = new File(context.getCacheDir(), TEST_PREFIX + suffix);
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IllegalStateException("Could not create test image " + suffix);
            }
        } finally {
            bitmap.recycle();
        }
        return Uri.fromFile(file);
    }

    private static void writeBytes(File file, byte[] value) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(value);
        }
    }

    private static View findByDescription(View view, String description) {
        CharSequence value = view.getContentDescription();
        if (value != null && description.contentEquals(value)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = findByDescription(group.getChildAt(i), description);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static View findByDescriptionPrefix(View view, String prefix) {
        CharSequence value = view.getContentDescription();
        if (value != null && value.toString().startsWith(prefix)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = findByDescriptionPrefix(group.getChildAt(i), prefix);
                if (match != null) return match;
            }
        }
        return null;
    }

    private void clearPacks() {
        for (PackStore.Pack pack : PackStore.getPacks(context)) {
            PackStore.deletePack(context, pack.id);
        }
    }

    private void clearTestFiles() {
        File[] files = context.getCacheDir().listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.getName().startsWith(TEST_PREFIX)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }
}
