package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StickerOrderPolicyTest {
    @Test
    public void movesItemForward() {
        List<String> items = new ArrayList<>(Arrays.asList("a", "b", "c", "d"));
        assertTrue(StickerOrderPolicy.move(items, 1, 3));
        assertEquals(Arrays.asList("a", "c", "d", "b"), items);
    }

    @Test
    public void movesItemBackward() {
        List<String> items = new ArrayList<>(Arrays.asList("a", "b", "c", "d"));
        assertTrue(StickerOrderPolicy.move(items, 3, 1));
        assertEquals(Arrays.asList("a", "d", "b", "c"), items);
    }

    @Test
    public void invalidMoveDoesNotChangeItems() {
        List<String> items = new ArrayList<>(Arrays.asList("a", "b"));
        assertFalse(StickerOrderPolicy.move(items, -1, 1));
        assertFalse(StickerOrderPolicy.move(items, 0, 4));
        assertFalse(StickerOrderPolicy.move(items, 1, 1));
        assertEquals(Arrays.asList("a", "b"), items);
    }

    @Test
    public void nullAndEmptySelectionsAreSafeNoOps() {
        assertFalse(StickerOrderPolicy.move(null, 0, 0));

        List<String> empty = new ArrayList<>();
        assertFalse(StickerOrderPolicy.move(empty, 0, 0));
        assertTrue(empty.isEmpty());
    }
}
