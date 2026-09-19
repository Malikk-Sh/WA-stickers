package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LauncherInheritanceTest {
    @Test
    public void redesignedLauncherSkipsLegacyHomeActivityLayer() {
        assertEquals(MainActivity.class, LauncherActivity.class.getSuperclass());
    }
}
