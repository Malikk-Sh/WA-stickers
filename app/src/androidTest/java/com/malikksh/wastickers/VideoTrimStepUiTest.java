package com.malikksh.wastickers;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
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
public class VideoTrimStepUiTest {
    private Context context;
    private Uri video;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        VideoTrimStore.clear();
        video = Uri.parse("content://trim.step.test/long-video.mp4");
        VideoTrimStore.replaceEntries(Arrays.asList(new VideoTrimStore.Entry(
                video.toString(), video, "long-video.mp4", 25_000L, 2_000L)));
    }

    @After
    public void tearDown() {
        VideoTrimStore.clear();
        VideoTrimStore.clearPersistent(context, AppSettings.TRIM_PERSISTENT_KEY);
    }

    @Test
    public void stepButtonsMoveStartByOneTenthWithoutDragging() {
        Intent intent = new Intent(context, VideoTrimActivity.class);
        intent.putStringArrayListExtra(
                VideoTrimActivity.EXTRA_KEYS,
                new ArrayList<>(Arrays.asList(video.toString())));

        try (ActivityScenario<VideoTrimActivity> ignored = ActivityScenario.launch(intent)) {
            onView(withId(R.id.trim_step_back)).check(matches(isEnabled()));
            onView(withId(R.id.trim_step_forward)).check(matches(isEnabled()));
            onView(withId(R.id.trim_range)).check(matches(withText("Фрагмент: 0:02.0 — 0:12.0")));

            onView(withId(R.id.trim_step_forward)).perform(click());
            onView(withId(R.id.trim_range)).check(matches(withText("Фрагмент: 0:02.1 — 0:12.1")));
            assertEquals(2_100L, VideoTrimStore.getStartOffsetMs(video));

            onView(withId(R.id.trim_step_back)).perform(click());
            onView(withId(R.id.trim_range)).check(matches(withText("Фрагмент: 0:02.0 — 0:12.0")));
            assertEquals(2_000L, VideoTrimStore.getStartOffsetMs(video));
        }
    }
}
