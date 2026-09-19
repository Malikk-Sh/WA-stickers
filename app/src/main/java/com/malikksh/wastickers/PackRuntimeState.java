package com.malikksh.wastickers;

/** Owns the currently finalized pack independently from Activity UI state. */
final class PackRuntimeState {
    private PackStore.Pack current;

    PackStore.Pack current() {
        return current;
    }

    void set(PackStore.Pack pack) {
        current = pack;
    }

    void clear() {
        current = null;
    }

    boolean matchesMode(boolean animated) {
        return current != null && current.animated == animated;
    }

    boolean replaceIfId(String expectedId, PackStore.Pack replacement) {
        if (expectedId == null || replacement == null || current == null
                || !expectedId.equals(current.id)) {
            return false;
        }
        current = replacement;
        return true;
    }

    boolean clearIfId(String expectedId) {
        if (expectedId == null || current == null || !expectedId.equals(current.id)) {
            return false;
        }
        current = null;
        return true;
    }
}
