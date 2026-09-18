package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BatchResultPolicyTest {
    @Test
    public void requiresAtLeastThreeSuccessfulStickers() {
        assertFalse(BatchResultPolicy.canFinalize(0));
        assertFalse(BatchResultPolicy.canFinalize(2));
        assertTrue(BatchResultPolicy.canFinalize(3));
        assertTrue(BatchResultPolicy.canFinalize(30));
    }

    @Test
    public void autoFinalizesOnlyWhenNothingRemains() {
        assertTrue(BatchResultPolicy.shouldAutoFinalize(0, false));
        assertFalse(BatchResultPolicy.shouldAutoFinalize(1, false));
        assertFalse(BatchResultPolicy.shouldAutoFinalize(0, true));
    }

    @Test
    public void remainingCountNeverGoesNegative() {
        assertEquals(7, BatchResultPolicy.safeRemainingCount(10, 3));
        assertEquals(0, BatchResultPolicy.safeRemainingCount(10, 10));
        assertEquals(0, BatchResultPolicy.safeRemainingCount(10, 12));
    }
}
