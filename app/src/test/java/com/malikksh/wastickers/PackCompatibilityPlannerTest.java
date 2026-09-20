package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PackCompatibilityPlannerTest {
    @Test
    public void emptyProjectIsInvalid() {
        PackCompatibilityPlanner.ExportPlan<String> plan =
                PackCompatibilityPlanner.plan(Collections.emptyList());
        assertFalse(plan.isValid());
        assertEquals(PackCompatibilityPlanner.TargetPackType.STATIC_PACK, plan.targetPackType);
        assertTrue(plan.jobs.isEmpty());
    }

    @Test
    public void twoStaticItemsAreInvalidBeforeExport() {
        PackCompatibilityPlanner.ExportPlan<String> plan = PackCompatibilityPlanner.plan(Arrays.asList(
                source("a", PackCompatibilityPlanner.SourceKind.STATIC),
                source("b", PackCompatibilityPlanner.SourceKind.STATIC)
        ));
        assertFalse(plan.isValid());
        assertEquals(PackCompatibilityPlanner.TargetPackType.STATIC_PACK, plan.targetPackType);
    }

    @Test
    public void threeStaticItemsUseStaticPackAndStaticWebp() {
        PackCompatibilityPlanner.ExportPlan<String> plan = PackCompatibilityPlanner.plan(Arrays.asList(
                source("a", PackCompatibilityPlanner.SourceKind.STATIC),
                source("b", PackCompatibilityPlanner.SourceKind.STATIC),
                source("c", PackCompatibilityPlanner.SourceKind.STATIC)
        ));
        assertTrue(plan.isValid());
        assertEquals(PackCompatibilityPlanner.TargetPackType.STATIC_PACK, plan.targetPackType);
        assertFalse(plan.requiresStaticWrapper());
        for (PackCompatibilityPlanner.Job<String> job : plan.jobs) {
            assertEquals(PackCompatibilityPlanner.JobStrategy.STATIC_WEBP, job.strategy);
        }
    }

    @Test
    public void mixedProjectUsesAnimatedPackAndWrapsStaticItems() {
        PackCompatibilityPlanner.ExportPlan<String> plan = PackCompatibilityPlanner.plan(Arrays.asList(
                source("photo-1", PackCompatibilityPlanner.SourceKind.STATIC),
                source("photo-2", PackCompatibilityPlanner.SourceKind.STATIC),
                source("video", PackCompatibilityPlanner.SourceKind.ANIMATED)
        ));
        assertTrue(plan.isValid());
        assertEquals(PackCompatibilityPlanner.TargetPackType.ANIMATED_PACK, plan.targetPackType);
        assertTrue(plan.requiresStaticWrapper());
        assertEquals(PackCompatibilityPlanner.JobStrategy.STATIC_TO_ANIMATED_WRAPPER, plan.jobs.get(0).strategy);
        assertEquals(PackCompatibilityPlanner.JobStrategy.STATIC_TO_ANIMATED_WRAPPER, plan.jobs.get(1).strategy);
        assertEquals(PackCompatibilityPlanner.JobStrategy.ANIMATED_TRANSCODE, plan.jobs.get(2).strategy);
    }

    @Test
    public void animatedProjectUsesAnimatedTranscode() {
        List<PackCompatibilityPlanner.Source<String>> items = Arrays.asList(
                source("a", PackCompatibilityPlanner.SourceKind.ANIMATED),
                source("b", PackCompatibilityPlanner.SourceKind.ANIMATED),
                source("c", PackCompatibilityPlanner.SourceKind.ANIMATED)
        );
        PackCompatibilityPlanner.ExportPlan<String> plan = PackCompatibilityPlanner.plan(items);
        assertTrue(plan.isValid());
        assertEquals(PackCompatibilityPlanner.TargetPackType.ANIMATED_PACK, plan.targetPackType);
        for (PackCompatibilityPlanner.Job<String> job : plan.jobs) {
            assertEquals(PackCompatibilityPlanner.JobStrategy.ANIMATED_TRANSCODE, job.strategy);
        }
    }

    @Test
    public void unknownSourceBlocksPlan() {
        PackCompatibilityPlanner.ExportPlan<String> plan = PackCompatibilityPlanner.plan(Arrays.asList(
                source("a", PackCompatibilityPlanner.SourceKind.STATIC),
                source("b", PackCompatibilityPlanner.SourceKind.STATIC),
                source("c", PackCompatibilityPlanner.SourceKind.UNKNOWN)
        ));
        assertFalse(plan.isValid());
        assertNull(plan.targetPackType);
        assertTrue(plan.jobs.isEmpty());
    }

    private PackCompatibilityPlanner.Source<String> source(
            String item,
            PackCompatibilityPlanner.SourceKind kind
    ) {
        return new PackCompatibilityPlanner.Source<>(item, kind);
    }
}
