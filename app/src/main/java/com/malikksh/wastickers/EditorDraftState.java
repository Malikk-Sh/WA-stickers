package com.malikksh.wastickers;

import java.util.ArrayList;
import java.util.List;

final class EditorDraftState<T> {
    private final List<T> items = new ArrayList<>();
    private T cover;
    private String name = "";

    void capture(List<T> sourceItems, T sourceCover, String sourceName) {
        items.clear();
        if (sourceItems != null) items.addAll(sourceItems);
        cover = sourceCover != null && items.contains(sourceCover)
                ? sourceCover
                : (items.isEmpty() ? null : items.get(0));
        name = sourceName == null ? "" : sourceName;
    }

    void restoreItemsInto(List<T> target) {
        if (target == null) return;
        target.clear();
        target.addAll(items);
    }

    T cover() {
        return cover;
    }

    String name() {
        return name;
    }

    int size() {
        return items.size();
    }
}
