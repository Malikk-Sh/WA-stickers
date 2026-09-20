package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PackCompatibilityPlannerTest {
    private final PackCompatibilityPlanner<String> planner = new PackCompatibilityPlanner<>();

    @Test
    public void emptyProjectIsInvalid() {
        PackCompatibilityPlanner.ExportPlan<String> plan =
                planner.plan(Collections.emptyList(), null);

        assertFalse(plan.isValid());
        assertEquals(PackCompatibilityPlanner.TargetPackType.STATIC_PACK, plan.targetPackType);
        assertTrue(plan.jobs.isEmpty());
        assertTrue(plan.trayJob == null);
        assertFalse(plan.validationWarnings.isEmpty());
    }

    @Test
    public void twoStaticItemsAreInvalid() {
        List<PackCompatibilityPlanner.Item<String>> items = Arrays.asList(
                stat("one"), stat("two"));

        PackCompatibilityPlanner.ExportPlan<String> plan = planner.plan(items, "one");

        assertFalse(plan.isValid());
        assertEquals(PackCompatibilityPlanner.TargetPackType.STATIC_PACK, plan.targetPackType);
    }

    @Test
    public void threeStaticItemsProduceStaticPackAndStaticWebpJobs() {
        List<PackCompatibilityPlanner.Item<String>> items = Arrays.asList(
                stat("one"), stat("two"), stat("three"));

        PackCompatibilityPlanner.ExportPlan<String> plan = planner.plan(items, "two");

        assertTrue(plan.isValid());
        assertEquals(PackCompatibilityPlanner.TargetPackType.STATIC_PACK, plan.targetPackType);
        assertEquals(3, plan.jobs.size());
        for (PackCompatibilityPlanner.Job<String> job : plan.jobs) {
            assertEquals(PackCompatibilityPlanner.OutputStrategy.STATIC_WEBP, job.strategy);
        }
        assertNotNull(plan.trayJob);
        assertEquals("two", plan.trayJob.source);
    }

    @Test
    public void mixedProjectProducesAnimatedPackWrappersAndAnimatedTranscode() {
        List<PackCompatibilityPlanner.Item<String>> items = Arrays.asList(
                stat("photo-one"), stat("photo-two"), animated("video"));

        PackCompatibilityPlanner.ExportPlan<String> plan = planner.plan(items, "photo-one");

        assertTrue(plan.isValid());
        assertEquals(PackCompatibilityPlanner.TargetPackType.ANIMATED_PACK, plan.targetPackType);
        assertEquals(PackCompatibilityPlanner.OutputStrategy.STATIC_TO_ANIMATED_WRAPPER,
                plan.jobs.get(0).strategy);
        assertEquals(PackCompatibilityPlanner.OutputStrategy.STATIC_TO_ANIMATED_WRAPPER,
                plan.jobs.get(1).strategy);
        assertEquals(PackCompatibilityPlanner.OutputStrategy.ANIMATED_TRANSCODE,
                plan.jobs.get(2).strategy);
    }

    @Test
    public void allAnimatedItemsProduceAnimatedTranscodeJobs() {
        List<PackCompatibilityPlanner.Item<String>> items = Arrays.asList(
                animated("one"), animated("two"), animated("three"));

        PackCompatibilityPlanner.ExportPlan<String> plan = planner.plan(items, "three");

        assertTrue(plan.isValid());
        assertEquals(PackCompatibilityPlanner.TargetPackType.ANIMATED_PACK, plan.targetPackType);
        for (PackCompatibilityPlanner.Job<String> job : plan.jobs) {
            assertEquals(PackCompatibilityPlanner.OutputStrategy.ANIMATED_TRANSCODE, job.strategy);
        }
    }

    @Test
    public void jobsPreserveProjectOrder() {
        List<PackCompatibilityPlanner.Item<String>> items = Arrays.asList(
                stat("first"), animated("second"), stat("third"));

        PackCompatibilityPlanner.ExportPlan<String> plan = planner.plan(items, "first");

        assertEquals("first", plan.jobs.get(0).source);
        assertEquals("second", plan.jobs.get(1).source);
        assertEquals("third", plan.jobs.get(2).source);
    }

    @Test
    public void missingCoverIsInvalid() {
        List<PackCompatibilityPlanner.Item<String>> items = Arrays.asList(
                stat("one"), stat("two"), stat("three"));

        PackCompatibilityPlanner.ExportPlan<String> plan = planner.plan(items, "outside");

        assertFalse(plan.isValid());
        assertTrue(plan.trayJob == null);
    }

    @Test
    public void unknownMediaKindBlocksPlan() {
        List<PackCompatibilityPlanner.Item<String>> items = Arrays.asList(
                stat("one"), stat("two"),
                new PackCompatibilityPlanner.Item<>(
                        "unknown", MediaAnimationInspector.AnimationKind.UNKNOWN));

        PackCompatibilityPlanner.ExportPlan<String> plan = planner.plan(items, "one");

        assertFalse(plan.isValid());
        assertEquals(2, plan.jobs.size());
    }

    private static PackCompatibilityPlanner.Item<String> stat(String value) {
        return new PackCompatibilityPlanner.Item<>(
                value, MediaAnimationInspector.AnimationKind.STATIC);
    }

    private static PackCompatibilityPlanner.Item<String> animated(String value) {
        return new PackCompatibilityPlanner.Item<>(
                value, MediaAnimationInspector.AnimationKind.ANIMATED);
    }
}
