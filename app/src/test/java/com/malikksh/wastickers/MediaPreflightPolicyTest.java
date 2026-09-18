package com.malikksh.wastickers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MediaPreflightPolicyTest {
    @Test
    public void unreadableFileIsError() {
        MediaPreflightPolicy.Assessment result = MediaPreflightPolicy.assess(
                false, false, 1000L, -1, -1, -1L);
        assertEquals(MediaPreflightPolicy.Severity.ERROR, result.severity);
    }

    @Test
    public void longVideoGetsTrimInfo() {
        MediaPreflightPolicy.Assessment result = MediaPreflightPolicy.assess(
                true, true, 2_000_000L, 1920, 1080, 23_400L);
        assertEquals(MediaPreflightPolicy.Severity.INFO, result.severity);
        assertEquals("Фрагмент 12.3–22.3 сек",
                MediaPreflightPolicy.formatTrimWindow(12_300L, 23_400L));
    }

    @Test
    public void hugeSourceGetsWarning() {
        MediaPreflightPolicy.Assessment result = MediaPreflightPolicy.assess(
                true, false, MediaPreflightPolicy.LARGE_IMAGE_BYTES + 1,
                3000, 2000, -1L);
        assertEquals(MediaPreflightPolicy.Severity.WARNING, result.severity);
    }

    @Test
    public void normalImageIsReady() {
        MediaPreflightPolicy.Assessment result = MediaPreflightPolicy.assess(
                true, false, 2_000_000L, 2048, 2048, -1L);
        assertEquals(MediaPreflightPolicy.Severity.OK, result.severity);
    }

    @Test
    public void trimWindowIsClampedToEnd() {
        assertEquals("Фрагмент 13.4–23.4 сек",
                MediaPreflightPolicy.formatTrimWindow(20_000L, 23_400L));
    }
}
