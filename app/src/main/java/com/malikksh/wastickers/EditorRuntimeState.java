package com.malikksh.wastickers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns the active editor selection, mode and cover plus the independent mode drafts.
 *
 * This class is deliberately UI-free so MainActivity can remain a presentation/controller host while
 * mutable editor state moves into one typed owner. Build-session and processed-pack state stay
 * separate and can be extracted independently.
 */
final class EditorRuntimeState<T> {
    private final List<T> items = new ArrayList<>();
    private final EditorStateController<T> drafts = new EditorStateController<>();
    private boolean animated;
    private T cover;

    List<T> items() {
        return Collections.unmodifiableList(items);
    }

    List<T> snapshotItems() {
        return new ArrayList<>(items);
    }

    boolean isAnimated() {
        return animated;
    }

    T cover() {
        return cover;
    }

    EditorStateController<T> drafts() {
        return drafts;
    }

    boolean addUnique(T item, int maxItems) {
        if (item == null || maxItems <= 0 || items.size() >= maxItems || items.contains(item)) {
            return false;
        }
        items.add(item);
        if (cover == null) cover = item;
        return true;
    }

    boolean selectCover(T candidate) {
        if (candidate == null || !items.contains(candidate)) return false;
        cover = candidate;
        return true;
    }

    boolean removeAt(int index) {
        if (index < 0 || index >= items.size()) return false;
        T removed = items.remove(index);
        if (removed != null && removed.equals(cover)) {
            cover = items.isEmpty() ? null : items.get(0);
        }
        ensureCover();
        return true;
    }

    boolean move(int fromIndex, int toIndex) {
        return StickerOrderPolicy.move(items, fromIndex, toIndex);
    }

    boolean clearSelection() {
        if (items.isEmpty() && cover == null) return false;
        items.clear();
        cover = null;
        return true;
    }

    void ensureCover() {
        if (items.isEmpty()) {
            cover = null;
        } else if (cover == null || !items.contains(cover)) {
            cover = items.get(0);
        }
    }

    EditorStateController.Snapshot<T> switchMode(boolean toAnimated, String currentName) {
        EditorStateController.Snapshot<T> next = drafts.switchMode(
                animated,
                toAnimated,
                items,
                cover,
                currentName
        );
        animated = toAnimated;
        replaceActive(next.items(), next.cover());
        return next;
    }

    void restoreActive(boolean animated, List<T> restoredItems, T restoredCover) {
        this.animated = animated;
        replaceActive(restoredItems, restoredCover);
    }

    private void replaceActive(List<T> sourceItems, T sourceCover) {
        items.clear();
        if (sourceItems != null) items.addAll(sourceItems);
        cover = sourceCover;
        ensureCover();
    }
}
