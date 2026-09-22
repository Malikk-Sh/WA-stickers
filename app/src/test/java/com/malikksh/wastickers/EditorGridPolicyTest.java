package com.malikksh.wastickers;
import org.junit.Test;
import static org.junit.Assert.*;
public class EditorGridPolicyTest {
    @Test public void ordinaryPortraitUsesFiveColumnsAndWideScreensGrow() {
        assertEquals(5, EditorGridPolicy.columns(320, 1));
        assertEquals(5, EditorGridPolicy.columns(352, 1));
        assertTrue(EditorGridPolicy.columns(600, 1) > 5);
    }
    @Test public void accessibilityAndSplitScreenReduceColumns() {
        assertTrue(EditorGridPolicy.columns(280, 1) < 5);
        assertTrue(EditorGridPolicy.columns(352, 1.8f) < 5);
        assertEquals(2, EditorGridPolicy.columns(180, 2));
    }
}
