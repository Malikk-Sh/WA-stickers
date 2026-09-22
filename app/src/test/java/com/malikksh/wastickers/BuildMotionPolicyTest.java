package com.malikksh.wastickers;
import org.junit.Test;
import static org.junit.Assert.*;

public class BuildMotionPolicyTest {
    @Test public void thirtyFastFilesShareBoundedDeadline() {
        BuildMotionPolicy<Integer> policy = new BuildMotionPolicy<>();
        for (int i = 0; i < 30; i++) policy.started(i, 100 + i * 10, true);
        assertTrue(policy.holdSuccess(0, 1299, true, false));
        assertFalse(policy.holdSuccess(0, 1300, true, false));
        for (int i = 0; i < 30; i++) assertFalse(policy.holdSuccess(i, 1600, true, false));
    }
    @Test public void noDelayForErrorCancellationReducedMotionOrUnstartedItems() {
        BuildMotionPolicy<String> policy = new BuildMotionPolicy<>();
        policy.started("a", 100, true);
        assertFalse(policy.holdSuccess("a", 101, true, true));
        assertFalse(policy.holdSuccess("a", 101, false, false));
        assertFalse(policy.holdSuccess("b", 101, true, false));
        policy.reset();
        assertFalse(policy.holdSuccess("a", 101, true, false));
        policy.started("b", 200, false);
        assertFalse(policy.holdSuccess("b", 201, true, false));
    }
    @Test public void slowWorkNeverGetsAdditionalPerFileDelay() {
        BuildMotionPolicy<String> policy = new BuildMotionPolicy<>();
        policy.started("a", 0, true);
        policy.started("b", 8000, true);
        assertFalse(policy.holdSuccess("b", 8001, true, false));
    }
}
