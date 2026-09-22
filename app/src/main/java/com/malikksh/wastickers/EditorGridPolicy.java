package com.malikksh.wastickers;

/** Uses the actual content width, including split-screen and display scaling. */
final class EditorGridPolicy {
    static int columns(float contentWidthDp, float fontScale) {
        if (fontScale > 1.3f || contentWidthDp < 300) {
            return Math.max(2, Math.min(5, (int) (contentWidthDp / (76 * Math.max(1, fontScale / 1.3f)))));
        }
        return Math.max(5, Math.min(10, (int) (contentWidthDp / 64)));
    }
}
