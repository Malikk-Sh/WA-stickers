package com.malikksh.wastickers;

final class AnimatedStickerProfiles {
    static final class Profile {
        final int fps;
        final int quality;

        Profile(int fps, int quality) {
            this.fps = fps;
            this.quality = quality;
        }
    }

    private static final Profile[] PROFILES = new Profile[]{
            new Profile(18, 92),
            new Profile(14, 92),
            new Profile(10, 92),
            new Profile(8, 92),
            new Profile(6, 92),
            new Profile(5, 92),
            new Profile(4, 92),
            new Profile(3, 92),
            new Profile(2, 92),
            new Profile(1, 92),
            new Profile(3, 84),
            new Profile(2, 84),
            new Profile(1, 84),
            new Profile(2, 76),
            new Profile(1, 76),
            new Profile(1, 66),
            new Profile(1, 56),
            new Profile(1, 46),
            new Profile(1, 36)
    };

    private static final int[] BALANCE = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18
    };
    private static final int[] SMOOTHER = {
            0, 1, 2, 3, 4, 5, 6, 7, 10, 11, 13, 8, 9, 12, 14, 15, 16, 17, 18
    };
    private static final int[] SHARPER = {
            2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18
    };

    private static volatile int[] activeOrder = BALANCE;

    private AnimatedStickerProfiles() {}

    static void setPreset(String preset) {
        if (AppSettings.QUALITY_SMOOTHER.equals(preset)) {
            activeOrder = SMOOTHER;
        } else if (AppSettings.QUALITY_SHARPER.equals(preset)) {
            activeOrder = SHARPER;
        } else {
            activeOrder = BALANCE;
        }
    }

    static int size() {
        return activeOrder.length;
    }

    static Profile get(int index) {
        int[] order = activeOrder;
        if (index < 0 || index >= order.length) {
            throw new IndexOutOfBoundsException("Profile index: " + index);
        }
        return PROFILES[order[index]];
    }
}