package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VideoTrimPolicyTest {
    @Test
    public void shortVideoAlwaysStartsAtZero() {
        assertEquals(0L, VideoTrimPolicy.maxStartMs(8_000L));
        assertEquals(0L, VideoTrimPolicy.clampStartMs(8_000L, 4_000L));
        assertEquals(8_000L, VideoTrimPolicy.clipDurationMs(8_000L, 0L));
    }

    @Test
    public void longVideoAllowsTenSecondWindow() {
        assertEquals(20_000L, VideoTrimPolicy.maxStartMs(30_000L));
        assertEquals(12_300L, VideoTrimPolicy.clampStartMs(30_000L, 12_300L));
        assertEquals(10_000L, VideoTrimPolicy.clipDurationMs(30_000L, 12_300L));
    }

    @Test
    public void startIsClampedToLastFullWindow() {
        assertEquals(20_000L, VideoTrimPolicy.clampStartMs(30_000L, 29_000L));
        assertEquals(10_000L, VideoTrimPolicy.clipDurationMs(30_000L, 29_000L));
    }

    @Test
    public void seekBarUsesHundredMillisecondSteps() {
        assertEquals(200, VideoTrimPolicy.seekBarMax(30_000L));
        assertEquals(123, VideoTrimPolicy.seekBarProgress(30_000L, 12_300L));
        assertEquals(12_300L, VideoTrimPolicy.startFromSeekBar(30_000L, 123));
    }

    @Test
    public void independentRangeCanSelectTheTailAndNeverExceedsTenSeconds() {
        assertEquals(29_500L, VideoTrimPolicy.clampRangeStartMs(30_000L, 40_000L));
        assertEquals(30_000L, VideoTrimPolicy.clampRangeEndMs(30_000L, 29_500L, 29_000L));
        assertEquals(14_000L, VideoTrimPolicy.clampRangeEndMs(30_000L, 4_000L, 29_000L));
        assertEquals(7_000L, VideoTrimPolicy.clampRangeEndMs(30_000L, 4_000L, 7_000L));
        assertEquals(4_500L, VideoTrimPolicy.clampRangeEndMs(30_000L, 4_000L, 4_000L));
    }

    @Test
    public void formatsTimeWithTenths() {
        assertEquals("1:05.4", VideoTrimPolicy.formatTime(65_400L));
    }
}
