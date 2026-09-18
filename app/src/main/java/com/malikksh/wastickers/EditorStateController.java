package com.malikksh.wastickers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns the independent editor drafts for photo and animated modes.
 *
 * This controller deliberately knows nothing about Android UI or processed pack state. It only
 * preserves the user's editable source selection, order, cover and name while switching modes.
 */
final class EditorStateController<T> {
    static final class Snapshot<T> {
        private final List<T> items;
        private final T cover;
        private final String name;

        Snapshot(List<T> items, T cover, String name) {
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.cover = cover;
            this.name = name == null ? "" : name;
        }

        List<T> items() {
            return items;
        }

        T cover() {
            return cover;
        }

        String name() {
            return name;
        }
    }

    private final EditorDraftState<T> photoDraft = new EditorDraftState<>();
    private final EditorDraftState<T> animatedDraft = new EditorDraftState<>();

    Snapshot<T> switchMode(
            boolean fromAnimated,
            boolean toAnimated,
            List<T> currentItems,
            T currentCover,
            String currentName
    ) {
        capture(fromAnimated, currentItems, currentCover, currentName);
        return snapshot(toAnimated);
    }

    void capture(boolean animated, List<T> items, T cover, String name) {
        draft(animated).capture(items, cover, name);
    }

    Snapshot<T> snapshot(boolean animated) {
        EditorDraftState<T> draft = draft(animated);
        List<T> items = new ArrayList<>();
        draft.restoreItemsInto(items);
        return new Snapshot<>(items, draft.cover(), draft.name());
    }

    private EditorDraftState<T> draft(boolean animated) {
        return animated ? animatedDraft : photoDraft;
    }
}
