package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AnimatedStickerProfilesTest {
    @Test
    public void startsWithHighDetailProfile() {
        AnimatedStickerProfiles.Profile first = AnimatedStickerProfiles.get(0);
        assertEquals(18, first.fps);
        assertEquals(92, first.quality);
    }

    @Test
    public void reducesFrameRateBeforeQuality() {
        int firstQualityDrop = -1;
        for (int i = 1; i < AnimatedStickerProfiles.size(); i++) {
            if (AnimatedStickerProfiles.get(i).quality < AnimatedStickerProfiles.get(i - 1).quality) {
                firstQualityDrop = i;
                break;
            }
        }

        assertTrue(firstQualityDrop > 0);
        AnimatedStickerProfiles.Profile previous = AnimatedStickerProfiles.get(firstQualityDrop - 1);
        assertEquals(1, previous.fps);
        assertEquals(92, previous.quality);
    }

    @Test
    public void everyProfileUsesValidValues() {
        for (int i = 0; i < AnimatedStickerProfiles.size(); i++) {
            AnimatedStickerProfiles.Profile profile = AnimatedStickerProfiles.get(i);
            assertTrue(profile.fps >= 1);
            assertTrue(profile.quality >= 1 && profile.quality <= 100);
        }
    }

    @Test
    public void finalProfileIsAggressiveFallback() {
        AnimatedStickerProfiles.Profile last = AnimatedStickerProfiles.get(AnimatedStickerProfiles.size() - 1);
        assertEquals(1, last.fps);
        assertEquals(36, last.quality);
    }
}
