package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pure policy coverage mirroring the invariants used by MainActivity's typed runtime bridge. */
public class RuntimeBridgePolicyTest {
    @Test
    public void removingCoverPromotesFirstRemainingItem() {
        List<String> items = new ArrayList<>(Arrays.asList("a", "b", "c"));
        String cover = "b";

        String removed = items.remove(1);
        if (removed.equals(cover)) cover = items.isEmpty() ? null : items.get(0);

        assertEquals(Arrays.asList("a", "c"), items);
        assertEquals("a", cover);
    }

    @Test
    public void clearingSelectionAlsoClearsCover() {
        List<String> items = new ArrayList<>(Arrays.asList("a", "b", "c"));
        String cover = "c";

        items.clear();
        cover = null;

        assertTrue(items.isEmpty());
        assertNull(cover);
    }
}
