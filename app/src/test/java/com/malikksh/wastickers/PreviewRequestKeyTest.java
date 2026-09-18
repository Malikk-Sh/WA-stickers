package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class PreviewRequestKeyTest {
    @Test
    public void sameUriAndOffsetHaveSameKey() {
        assertEquals(
                PreviewRequestKey.create("content://item/1", 2500L),
                PreviewRequestKey.create("content://item/1", 2500L)
        );
    }

    @Test
    public void trimOffsetChangesPreviewKey() {
        assertNotEquals(
                PreviewRequestKey.create("content://item/1", 0L),
                PreviewRequestKey.create("content://item/1", 2500L)
        );
    }

    @Test
    public void negativeOffsetIsNormalized() {
        assertEquals(
                PreviewRequestKey.create("content://item/1", 0L),
                PreviewRequestKey.create("content://item/1", -100L)
        );
    }
}
