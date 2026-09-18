package com.malikksh.wastickers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ConversionCacheKeyTest {
    @Test
    public void identicalInputsProduceStableKey() {
        String first = ConversionCacheKey.build(
                "content://item/1", true, 1200L, 12345L,
                "video/mp4", "clip.mp4", "abc", 1);
        String second = ConversionCacheKey.build(
                "content://item/1", true, 1200L, 12345L,
                "video/mp4", "clip.mp4", "abc", 1);
        assertEquals(first, second);
    }

    @Test
    public void trimOffsetChangesKey() {
        String first = ConversionCacheKey.build(
                "content://item/1", true, 0L, 12345L,
                "video/mp4", "clip.mp4", "abc", 1);
        String second = ConversionCacheKey.build(
                "content://item/1", true, 5000L, 12345L,
                "video/mp4", "clip.mp4", "abc", 1);
        assertNotEquals(first, second);
    }

    @Test
    public void modeChangesKey() {
        String animated = ConversionCacheKey.build(
                "content://item/1", true, 0L, 12345L,
                "image/webp", "sticker.webp", "abc", 1);
        String statik = ConversionCacheKey.build(
                "content://item/1", false, 0L, 12345L,
                "image/webp", "sticker.webp", "abc", 1);
        assertNotEquals(animated, statik);
    }

    @Test
    public void sourceSampleChangesKey() {
        String first = ConversionCacheKey.build(
                "content://item/1", false, 0L, 12345L,
                "image/png", "photo.png", "abc", 1);
        String second = ConversionCacheKey.build(
                "content://item/1", false, 0L, 12345L,
                "image/png", "photo.png", "xyz", 1);
        assertNotEquals(first, second);
    }

    @Test
    public void cacheVersionChangesKey() {
        String first = ConversionCacheKey.build(
                "content://item/1", true, 0L, 12345L,
                "video/mp4", "clip.mp4", "abc", 1);
        String second = ConversionCacheKey.build(
                "content://item/1", true, 0L, 12345L,
                "video/mp4", "clip.mp4", "abc", 2);
        assertNotEquals(first, second);
    }
}
