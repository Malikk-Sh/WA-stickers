package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class VideoTrimActivityUiTest {
    private static final String TRIM_PERSISTENT_KEY = "home_video_trim";

    private Context context;
    private Uri firstUri;
    private Uri secondUri;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        VideoTrimStore.clearPersistent(context, TRIM_PERSISTENT_KEY);
        VideoTrimStore.clear();

        firstUri = Uri.parse("content://trim.test/one.mp4");
        secondUri = Uri.parse("content://trim.test/two.mp4");
        VideoTrimStore.replaceEntries(Arrays.asList(
                new VideoTrimStore.Entry(
                        firstUri.toString(), firstUri, "one.mp4", 25_000L, 2_000L),
                new VideoTrimStore.Entry(
                        secondUri.toString(), secondUri, "two.mp4", 32_000L, 3_000L)
        ));
    }

    @After
    public void tearDown() {
        VideoTrimStore.clearPersistent(context, TRIM_PERSISTENT_KEY);
        VideoTrimStore.clear();
    }

    @Test
    public void trimScreenShowsFocusedEditorAndPagesBetweenVideos() {
        Intent intent = trimIntent(firstUri.toString(), secondUri.toString());
        try (ActivityScenario<VideoTrimActivity> ignored = ActivityScenario.launch(intent)) {
            onView(withId(R.id.trim_title)).check(matches(withText("Фрагмент видео")));
            onView(withId(R.id.trim_preview)).check(matches(isDisplayed()));
            onView(withId(R.id.trim_indicator)).check(matches(withText("1 из 2")));
            onView(withId(R.id.trim_filename)).check(matches(withText("one.mp4")));
            onView(withId(R.id.trim_duration)).check(matches(withText("Длительность 0:25.0")));
            onView(withId(R.id.trim_range)).check(matches(withText("Фрагмент: 0:02.0 — 0:12.0")));
            onView(withId(R.id.trim_previous)).check(matches(not(isEnabled())));
            onView(withId(R.id.trim_next)).check(matches(isEnabled()));

            onView(withId(R.id.trim_next)).perform(scrollTo(), click());

            onView(withId(R.id.trim_indicator)).check(matches(withText("2 из 2")));
            onView(withId(R.id.trim_filename)).check(matches(withText("two.mp4")));
            onView(withId(R.id.trim_previous)).check(matches(isEnabled()));
            onView(withId(R.id.trim_next)).check(matches(not(isEnabled())));
            onView(withId(R.id.trim_cancel)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.trim_done)).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }

    @Test
    public void cancelRestoresOffsetsFromBeforeEditing() {
        Intent intent = trimIntent(firstUri.toString());
        try (ActivityScenario<VideoTrimActivity> ignored = ActivityScenario.launch(intent)) {
            VideoTrimStore.setStartOffsetMs(firstUri.toString(), 8_000L);
            assertEquals(8_000L, VideoTrimStore.getStartOffsetMs(firstUri));

            onView(withId(R.id.trim_cancel)).perform(scrollTo(), click());

            assertEquals(2_000L, VideoTrimStore.getStartOffsetMs(firstUri));
        }
    }

    private Intent trimIntent(String... keys) {
        Intent intent = new Intent(context, VideoTrimActivity.class);
        intent.putStringArrayListExtra(
                VideoTrimActivity.EXTRA_KEYS,
                new ArrayList<>(Arrays.asList(keys))
        );
        return intent;
    }
}
