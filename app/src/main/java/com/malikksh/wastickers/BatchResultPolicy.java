package com.malikksh.wastickers;

final class BatchResultPolicy {
    private static final int MIN_STICKERS = 3;

    private BatchResultPolicy() {}

    static boolean canFinalize(int successfulCount) {
        return successfulCount >= MIN_STICKERS;
    }

    static boolean shouldAutoFinalize(int failedOrRemainingCount, boolean cancelled) {
        return failedOrRemainingCount == 0 && !cancelled;
    }

    static int safeRemainingCount(int total, int processed) {
        return Math.max(0, total - Math.max(0, processed));
    }
}
