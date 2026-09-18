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

    private AnimatedStickerProfiles() {}

    static int size() {
        return PROFILES.length;
    }

    static Profile get(int index) {
        if (index < 0 || index >= PROFILES.length) {
            throw new IndexOutOfBoundsException("Profile index: " + index);
        }
        return PROFILES[index];
    }
}
