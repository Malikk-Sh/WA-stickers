package com.malikksh.wastickers;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PackBuildSessionTest {
    @Test
    public void recordSuccessTracksPreferredTrayAndProfile() {
        PackBuildSession<String> session = new PackBuildSession<>();
        session.begin("id", "Pack", new File("draft"), true, "preferred");

        session.recordSuccess("first", 12, 92);
        assertEquals("first", session.traySource());
        assertEquals(1, session.successCount());

        session.recordSuccess("preferred", 8, 84);
        assertEquals("preferred", session.traySource());
        assertEquals(2, session.successCount());
        assertEquals(8, session.lastFps());
        assertEquals(84, session.lastQuality());
    }

    @Test
    public void failuresAreCopiedAndTakenForRetry() {
        PackBuildSession<String> session = new PackBuildSession<>();
        session.begin("id", "Pack", new File("draft"), false, "a");
        List<String> source = new ArrayList<>(Arrays.asList("b", "c"));
        session.setFailures(source);
        source.clear();

        assertEquals(Arrays.asList("b", "c"), session.failures());
        assertEquals(Arrays.asList("b", "c"), session.takeFailuresForRetry());
        assertFalse(session.hasFailures());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void failureSnapshotIsImmutable() {
        PackBuildSession<String> session = new PackBuildSession<>();
        session.begin("id", "Pack", new File("draft"), false, "a");
        session.setFailures(Arrays.asList("b"));
        session.failures().add("c");
    }

    @Test
    public void cancelControlsAutoFinalizeAndBeginBatchClearsCancel() {
        PackBuildSession<String> session = new PackBuildSession<>();
        session.begin("id", "Pack", new File("draft"), false, "a");
        session.recordSuccess("a", 0, 0);
        session.recordSuccess("b", 0, 0);
        session.recordSuccess("c", 0, 0);

        assertTrue(session.canFinalize());
        assertTrue(session.shouldAutoFinalize());

        session.requestCancel();
        assertTrue(session.isCancelRequested());
        assertFalse(session.shouldAutoFinalize());

        session.beginBatch();
        assertFalse(session.isCancelRequested());
        assertTrue(session.shouldAutoFinalize());
    }

    @Test
    public void resetClearsAllSessionState() {
        PackBuildSession<String> session = new PackBuildSession<>();
        session.begin("id", "Pack", new File("draft"), true, "cover");
        session.recordSuccess("cover", 6, 76);
        session.setFailures(Arrays.asList("bad"));
        session.requestCancel();

        session.reset();

        assertFalse(session.isActive());
        assertNull(session.packId());
        assertNull(session.packName());
        assertNull(session.packDir());
        assertNull(session.traySource());
        assertEquals(0, session.successCount());
        assertEquals(0, session.failureCount());
        assertFalse(session.isCancelRequested());
    }
}
