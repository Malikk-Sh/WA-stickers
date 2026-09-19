package com.malikksh.wastickers;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EditorRuntimeStateTest {
    @Test
    public void selectionOwnsCoverAndRejectsDuplicatesAndOverflow() {
        EditorRuntimeState<String> state = new EditorRuntimeState<>();

        assertTrue(state.addUnique("one", 2));
        assertTrue(state.addUnique("two", 2));
        assertFalse(state.addUnique("two", 2));
        assertFalse(state.addUnique("three", 2));

        assertEquals(Arrays.asList("one", "two"), state.items());
        assertEquals("one", state.cover());
        assertTrue(state.selectCover("two"));
        assertEquals("two", state.cover());
        assertFalse(state.selectCover("missing"));
    }

    @Test
    public void removingCoverFallsBackToFirstRemainingItem() {
        EditorRuntimeState<String> state = new EditorRuntimeState<>();
        state.restoreActive(false, Arrays.asList("one", "two", "three"), "two");

        assertTrue(state.removeAt(1));
        assertEquals(Arrays.asList("one", "three"), state.items());
        assertEquals("one", state.cover());

        assertTrue(state.removeAt(0));
        assertTrue(state.removeAt(0));
        assertNull(state.cover());
    }

    @Test
    public void modeSwitchKeepsIndependentDraftsAndActiveState() {
        EditorRuntimeState<String> state = new EditorRuntimeState<>();
        state.restoreActive(false, Arrays.asList("p1", "p2", "p3"), "p2");

        EditorStateController.Snapshot<String> animated = state.switchMode(true, "Photos");
        assertTrue(state.isAnimated());
        assertTrue(state.items().isEmpty());
        assertNull(state.cover());
        assertEquals("", animated.name());

        state.restoreActive(true, Arrays.asList("a1", "a2", "a3"), "a3");
        EditorStateController.Snapshot<String> photos = state.switchMode(false, "Animations");

        assertFalse(state.isAnimated());
        assertEquals(Arrays.asList("p1", "p2", "p3"), state.items());
        assertEquals("p2", state.cover());
        assertEquals("Photos", photos.name());

        EditorStateController.Snapshot<String> savedAnimations = state.drafts().snapshot(true);
        assertEquals(Arrays.asList("a1", "a2", "a3"), savedAnimations.items());
        assertEquals("a3", savedAnimations.cover());
        assertEquals("Animations", savedAnimations.name());
    }

    @Test
    public void restoreNormalizesInvalidCoverAndMoveKeepsChosenCover() {
        EditorRuntimeState<String> state = new EditorRuntimeState<>();
        state.restoreActive(false, Arrays.asList("one", "two", "three"), "missing");
        assertEquals("one", state.cover());

        assertTrue(state.selectCover("three"));
        assertTrue(state.move(2, 0));
        assertEquals(Arrays.asList("three", "one", "two"), state.items());
        assertEquals("three", state.cover());
    }
}
