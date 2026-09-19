package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppShellStructureTest {
    @Test
    public void shellStillLayersOnLauncherBridge() {
        assertEquals(LauncherActivity.class, AppShellActivity.class.getSuperclass());
    }
}
