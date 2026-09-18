package com.malikksh.wastickers;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackBuildCoordinatorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void allSuccessesAutoFinalizeWithContiguousOutputs() throws Exception {
        PackBuildSession<String> session = session(temporaryFolder.newFolder("all"), "b");
        PackBuildCoordinator<String> coordinator = new PackBuildCoordinator<>(session);
        List<String> targets = new ArrayList<>();

        PackBuildCoordinator.RunResult<String> result = coordinator.run(
                Arrays.asList("a", "b", "c"),
                (item, target, progress) -> {
                    targets.add(target.getName());
                    return new PackBuildCoordinator.ItemResult(100, 0, 0);
                },
                noopListener()
        );

        assertTrue(result.autoFinalize);
        assertFalse(result.cancelled);
        assertFalse(result.isFatal());
        assertEquals(Arrays.asList("1.webp", "2.webp", "3.webp"), targets);
        assertEquals(3, session.successCount());
        assertEquals("b", session.traySource());
        assertTrue(session.failures().isEmpty());
    }

    @Test
    public void partialFailureKeepsSuccessfulOutputNumbersContiguous() throws Exception {
        PackBuildSession<String> session = session(temporaryFolder.newFolder("partial"), "a");
        PackBuildCoordinator<String> coordinator = new PackBuildCoordinator<>(session);
        List<String> targets = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        PackBuildCoordinator.RunResult<String> result = coordinator.run(
                Arrays.asList("a", "bad", "c"),
                (item, target, progress) -> {
                    targets.add(target.getName());
                    if ("bad".equals(item)) throw new IllegalArgumentException("bad item");
                    return new PackBuildCoordinator.ItemResult(120, 0, 0);
                },
                new TestListener() {
                    @Override
                    public void onItemFailed(int index, String item, Throwable error) {
                        failed.add(item);
                    }
                }
        );

        assertFalse(result.autoFinalize);
        assertFalse(result.isFatal());
        assertEquals(Arrays.asList("1.webp", "2.webp", "2.webp"), targets);
        assertEquals(Arrays.asList("bad"), failed);
        assertEquals(Arrays.asList("bad"), session.failures());
        assertEquals(2, session.successCount());
        assertEquals(1, result.lastFailureIndex);
        assertEquals("bad", result.lastFailureItem);
    }

    @Test
    public void cancelAfterSuccessQueuesRemainingItems() throws Exception {
        PackBuildSession<String> session = session(temporaryFolder.newFolder("cancel-next"), "a");
        PackBuildCoordinator<String> coordinator = new PackBuildCoordinator<>(session);
        List<Integer> cancelledFrom = new ArrayList<>();

        PackBuildCoordinator.RunResult<String> result = coordinator.run(
                Arrays.asList("a", "b", "c"),
                (item, target, progress) -> {
                    if ("a".equals(item)) session.requestCancel();
                    return new PackBuildCoordinator.ItemResult(100, 0, 0);
                },
                new TestListener() {
                    @Override
                    public void onCancelledFrom(int startIndex) {
                        cancelledFrom.add(startIndex);
                    }
                }
        );

        assertTrue(result.cancelled);
        assertFalse(result.autoFinalize);
        assertEquals(1, session.successCount());
        assertEquals(Arrays.asList("b", "c"), session.failures());
        assertEquals(Arrays.asList(1), cancelledFrom);
    }

    @Test
    public void cancelDuringProcessorFailureQueuesCurrentAndRemaining() throws Exception {
        PackBuildSession<String> session = session(temporaryFolder.newFolder("cancel-current"), "a");
        PackBuildCoordinator<String> coordinator = new PackBuildCoordinator<>(session);

        PackBuildCoordinator.RunResult<String> result = coordinator.run(
                Arrays.asList("a", "b", "c"),
                (item, target, progress) -> {
                    if ("b".equals(item)) {
                        session.requestCancel();
                        throw new IllegalStateException("cancelled conversion");
                    }
                    return new PackBuildCoordinator.ItemResult(100, 0, 0);
                },
                noopListener()
        );

        assertTrue(result.cancelled);
        assertEquals(1, session.successCount());
        assertEquals(Arrays.asList("b", "c"), session.failures());
        assertEquals(-1, result.lastFailureIndex);
    }

    @Test
    public void fatalSetupFailureMarksWholeWorkForRetry() throws Exception {
        File notDirectory = temporaryFolder.newFile("not-a-directory");
        PackBuildSession<String> session = session(notDirectory, "a");
        PackBuildCoordinator<String> coordinator = new PackBuildCoordinator<>(session);

        PackBuildCoordinator.RunResult<String> result = coordinator.run(
                Arrays.asList("a", "b", "c"),
                (item, target, progress) -> new PackBuildCoordinator.ItemResult(100, 0, 0),
                noopListener()
        );

        assertTrue(result.isFatal());
        assertFalse(result.autoFinalize);
        assertEquals(Arrays.asList("a", "b", "c"), session.failures());
        assertEquals(Arrays.asList("a", "b", "c"), result.failures);
    }

    private PackBuildSession<String> session(File dir, String cover) {
        PackBuildSession<String> session = new PackBuildSession<>();
        session.begin("pack", "Pack", dir, false, cover);
        session.beginBatch();
        return session;
    }

    private PackBuildCoordinator.Listener<String> noopListener() {
        return new TestListener();
    }

    private static class TestListener implements PackBuildCoordinator.Listener<String> {
        @Override public void onItemStarted(int index, int total, String item) {}
        @Override public void onStaticProgress(int index, int percent, String detail) {}
        @Override public void onAnimatedProgress(int index, AnimatedStickerConverter.Progress progress) {}
        @Override public void onItemSucceeded(int index, String item, PackBuildCoordinator.ItemResult result) {}
        @Override public void onItemFailed(int index, String item, Throwable error) {}
        @Override public void onItemCompleted(int completed, int total) {}
        @Override public void onCancelledFrom(int startIndex) {}
    }
}
