package com.malikksh.wastickers;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PackRuntimeStateTest {
    @Test
    public void setClearAndModeMatchUseSingleCurrentPack() {
        PackRuntimeState state = new PackRuntimeState();
        PackStore.Pack pack = new PackStore.Pack("pack_one", "One", 3, "1", false);

        assertNull(state.current());
        state.set(pack);
        assertSame(pack, state.current());
        assertTrue(state.matchesMode(false));
        assertFalse(state.matchesMode(true));

        state.clear();
        assertNull(state.current());
        assertFalse(state.matchesMode(false));
    }

    @Test
    public void replaceAndClearRequireMatchingId() {
        PackRuntimeState state = new PackRuntimeState();
        PackStore.Pack original = new PackStore.Pack("pack_one", "One", 3, "1", false);
        PackStore.Pack renamed = new PackStore.Pack("pack_one", "Renamed", 3, "1", false);
        state.set(original);

        assertFalse(state.replaceIfId("other", renamed));
        assertSame(original, state.current());
        assertTrue(state.replaceIfId("pack_one", renamed));
        assertSame(renamed, state.current());

        assertFalse(state.clearIfId("other"));
        assertSame(renamed, state.current());
        assertTrue(state.clearIfId("pack_one"));
        assertNull(state.current());
    }

    @Test
    public void replaceAndClearRejectMissingArgumentsWithoutChangingState() {
        PackRuntimeState state = new PackRuntimeState();
        PackStore.Pack original = new PackStore.Pack("pack_one", "One", 3, "1", false);
        PackStore.Pack renamed = new PackStore.Pack("pack_one", "Renamed", 3, "1", false);
        state.set(original);

        assertFalse(state.replaceIfId(null, renamed));
        assertFalse(state.replaceIfId("pack_one", null));
        assertFalse(state.clearIfId(null));
        assertSame(original, state.current());
    }
}
