package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EditorDraftStateTest {
    @Test
    public void preservesOrderCoverAndName() {
        EditorDraftState<String> draft = new EditorDraftState<>();
        draft.capture(Arrays.asList("a", "c", "b"), "c", "Animated memes");

        List<String> restored = new ArrayList<>();
        draft.restoreItemsInto(restored);

        assertEquals(Arrays.asList("a", "c", "b"), restored);
        assertEquals("c", draft.cover());
        assertEquals("Animated memes", draft.name());
        assertEquals(3, draft.size());
    }

    @Test
    public void fallsBackToFirstItemWhenCoverIsMissing() {
        EditorDraftState<String> draft = new EditorDraftState<>();
        draft.capture(Arrays.asList("a", "b"), "missing", "Pack");
        assertEquals("a", draft.cover());
    }

    @Test
    public void captureCopiesItemsInsteadOfAliasingSource() {
        EditorDraftState<String> draft = new EditorDraftState<>();
        List<String> source = new ArrayList<>(Arrays.asList("a", "b"));
        draft.capture(source, "b", "Pack");
        source.clear();

        List<String> restored = new ArrayList<>();
        draft.restoreItemsInto(restored);
        assertEquals(Arrays.asList("a", "b"), restored);
    }

    @Test
    public void emptyDraftHasNoCoverAndNormalizesNullName() {
        EditorDraftState<String> draft = new EditorDraftState<>();
        draft.capture(new ArrayList<>(), null, null);
        assertNull(draft.cover());
        assertEquals("", draft.name());
        assertEquals(0, draft.size());
    }
}
