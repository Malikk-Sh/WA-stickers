package com.malikksh.wastickers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

@RunWith(AndroidJUnit4.class)
public class VideoTrimRangeTest {
    private Context context;
    private File source;
    private Uri uri;
    private final String persistenceKey = AppSettings.TRIM_PERSISTENT_KEY + ".range-test";

    @Before public void before() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        AppSettings.setKeepDrafts(context, true);
        source = new File(context.getCacheDir(), "trim-motion-test.mp4");
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("trim-motion.mp4");
             FileOutputStream output = new FileOutputStream(source)) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
        }
        uri = Uri.fromFile(source);
        VideoTrimStore.replaceEntries(Collections.singletonList(
                new VideoTrimStore.Entry(uri.toString(), uri, "Video", 12000L, 2000L, 4000L)));
    }
    @After public void after() {
        VideoTrimStore.clear();
        VideoTrimStore.clearPersistent(context, persistenceKey);
        source.delete();
    }

    @Test public void endBoundarySurvivesRecreationAndCancellationRestoresBothBoundaries() {
        Intent intent = new Intent(context, VideoTrimActivity.class);
        intent.putExtra(VideoTrimActivity.EXTRA_PROJECT_ID, "range-test");
        intent.putStringArrayListExtra(VideoTrimActivity.EXTRA_KEYS, new ArrayList<>(Collections.singletonList(uri.toString())));
        try (ActivityScenario<VideoTrimActivity> scenario = ActivityScenario.launch(intent)) {
            // Use the accessible slider alternative to dragging a timeline handle.
            scenario.onActivity(activity -> {
                android.widget.SeekBar end = activity.findViewById(R.id.trim_end_seek);
                Bundle arguments = new Bundle();
                arguments.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 39f);
                assertTrue(end.performAccessibilityAction(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(), arguments));
            });
            assertEquals(3900L, VideoTrimStore.getEndOffsetMs(uri));
            scenario.recreate();
            assertEquals(2000L, VideoTrimStore.getStartOffsetMs(uri));
            assertEquals(3900L, VideoTrimStore.getEndOffsetMs(uri));
            onView(withId(R.id.trim_cancel)).perform(scrollTo(), click());
            assertEquals(2000L, VideoTrimStore.getStartOffsetMs(uri));
            assertEquals(4000L, VideoTrimStore.getEndOffsetMs(uri));
        }
    }

    @Test public void rangeSurvivesBundleAndDiskAndChangesActualEncodedDuration() throws Exception {
        VideoTrimStore.setRangeMs(uri.toString(), 2000L, 4000L);
        Bundle bundle = new Bundle();
        VideoTrimStore.saveToBundle(bundle, "trim.");
        VideoTrimStore.clear();
        VideoTrimStore.restoreFromBundle(bundle, "trim.");
        VideoTrimStore.savePersistent(context, persistenceKey);
        VideoTrimStore.clear();
        VideoTrimStore.restorePersistent(context, persistenceKey);
        assertEquals(2000L, VideoTrimStore.getStartOffsetMs(uri));
        assertEquals(4000L, VideoTrimStore.getEndOffsetMs(uri));

        File first = new File(context.getCacheDir(), "trim-first.webp");
        File second = new File(context.getCacheDir(), "trim-second.webp");
        try {
            AnimatedStickerConverter.convert(context, uri, first);
            long firstDuration = animationDuration(first);
            VideoTrimStore.setRangeMs(uri.toString(), 2000L, 3000L);
            AnimatedStickerConverter.convert(context, uri, second);
            long secondDuration = animationDuration(second);
            assertTrue("First duration: " + firstDuration, Math.abs(firstDuration - 2000L) < 250L);
            assertTrue("Changed end reused stale output: " + secondDuration, Math.abs(secondDuration - 1000L) < 250L);
            assertTrue(firstDuration > secondDuration + 500L);
        } finally { first.delete(); second.delete(); }
    }

    private long animationDuration(File file) throws IOException {
        byte[] data;
        try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
            data = output.toByteArray();
        }
        long duration = 0;
        for (int offset = 12; offset + 8 <= data.length;) {
            int size = (data[offset + 4] & 255) | ((data[offset + 5] & 255) << 8)
                    | ((data[offset + 6] & 255) << 16) | ((data[offset + 7] & 255) << 24);
            if (size < 0 || size > data.length - offset - 8) throw new IOException("Invalid WebP chunk");
            if (data[offset] == 'A' && data[offset + 1] == 'N' && data[offset + 2] == 'M' && data[offset + 3] == 'F' && size >= 16) {
                duration += (data[offset + 20] & 255) | ((data[offset + 21] & 255) << 8) | ((data[offset + 22] & 255) << 16);
            }
            offset += 8 + size + (size & 1);
        }
        assertTrue("Expected animated output", duration > 0);
        return duration;
    }
}
