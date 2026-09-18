package com.malikksh.wastickers;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class EditorStateControllerTest {
    @Test
    public void switchingModesRestoresIndependentDrafts() {
        EditorStateController<String> controller = new EditorStateController<>();

        List<String> photos = new ArrayList<>(Arrays.asList("p2", "p1", "p3"));
        EditorStateController.Snapshot<String> animated = controller.switchMode(
                false,
                true,
                photos,
                "p2",
                "Photo pack"
        );

        assertEquals(0, animated.items().size());
        assertNull(animated.cover());
        assertEquals("", animated.name());

        List<String> animations = new ArrayList<>(Arrays.asList("a1", "a2", "a3"));
        EditorStateController.Snapshot<String> restoredPhotos = controller.switchMode(
                true,
                false,
                animations,
                "a3",
                "Animated pack"
        );

        assertEquals(Arrays.asList("p2", "p1", "p3"), restoredPhotos.items());
        assertEquals("p2", restoredPhotos.cover());
        assertEquals("Photo pack", restoredPhotos.name());

        EditorStateController.Snapshot<String> restoredAnimations = controller.snapshot(true);
        assertEquals(Arrays.asList("a1", "a2", "a3"), restoredAnimations.items());
        assertEquals("a3", restoredAnimations.cover());
        assertEquals("Animated pack", restoredAnimations.name());
    }

    @Test
    public void captureCopiesMutableSelection() {
        EditorStateController<String> controller = new EditorStateController<>();
        List<String> source = new ArrayList<>(Arrays.asList("one", "two", "three"));

        controller.capture(false, source, "two", "Draft");
        source.clear();

        EditorStateController.Snapshot<String> snapshot = controller.snapshot(false);
        assertEquals(Arrays.asList("one", "two", "three"), snapshot.items());
        assertEquals("two", snapshot.cover());
    }

    @Test
    public void invalidCoverFallsBackToFirstItem() {
        EditorStateController<String> controller = new EditorStateController<>();
        controller.capture(false, Arrays.asList("first", "second"), "missing", "Pack");

        assertEquals("first", controller.snapshot(false).cover());
    }

    @Test
    public void snapshotItemsAreImmutable() {
        EditorStateController<String> controller = new EditorStateController<>();
        controller.capture(true, Arrays.asList("a", "b"), "a", "Animations");

        EditorStateController.Snapshot<String> snapshot = controller.snapshot(true);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.items().add("c"));
    }
}
