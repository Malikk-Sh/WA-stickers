package com.malikksh.wastickers;

import java.util.List;

final class StickerOrderPolicy {
    private StickerOrderPolicy() {}

    static <T> boolean move(List<T> items, int fromIndex, int toIndex) {
        if (items == null
                || fromIndex < 0
                || toIndex < 0
                || fromIndex >= items.size()
                || toIndex >= items.size()
                || fromIndex == toIndex) {
            return false;
        }

        T item = items.remove(fromIndex);
        items.add(toIndex, item);
        return true;
    }
}
