package com.malikksh.wastickers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * UI-independent compatibility planning for one user sticker project.
 *
 * This layer only decides target pack type and per-item output strategy. It deliberately does not
 * execute conversion work; the existing PackBuildCoordinator remains the single build executor.
 */
final class PackCompatibilityPlanner<T> {
    static final int MIN_STICKERS = 3;
    static final int MAX_STICKERS = 30;

    enum TargetPackType {
        STATIC_PACK,
        ANIMATED_PACK
    }

    enum OutputStrategy {
        STATIC_WEBP,
        STATIC_TO_ANIMATED_WRAPPER,
        ANIMATED_TRANSCODE
    }

    static final class Item<T> {
        final T source;
        final MediaAnimationInspector.AnimationKind animationKind;

        Item(T source, MediaAnimationInspector.AnimationKind animationKind) {
            this.source = source;
            this.animationKind = animationKind == null
                    ? MediaAnimationInspector.AnimationKind.UNKNOWN
                    : animationKind;
        }
    }

    static final class Job<T> {
        final T source;
        final OutputStrategy strategy;

        Job(T source, OutputStrategy strategy) {
            this.source = source;
            this.strategy = strategy;
        }
    }

    static final class TrayJob<T> {
        final T source;

        TrayJob(T source) {
            this.source = source;
        }
    }

    static final class ExportPlan<T> {
        final TargetPackType targetPackType;
        final List<Job<T>> jobs;
        final TrayJob<T> trayJob;
        final List<String> validationWarnings;
        private final boolean valid;

        ExportPlan(TargetPackType targetPackType,
                   List<Job<T>> jobs,
                   TrayJob<T> trayJob,
                   List<String> validationWarnings,
                   boolean valid) {
            this.targetPackType = targetPackType;
            this.jobs = Collections.unmodifiableList(new ArrayList<>(jobs));
            this.trayJob = trayJob;
            this.validationWarnings = Collections.unmodifiableList(
                    new ArrayList<>(validationWarnings));
            this.valid = valid;
        }

        boolean isValid() {
            return valid;
        }
    }

    ExportPlan<T> plan(List<Item<T>> sourceItems, T cover) {
        List<Item<T>> items = sourceItems == null
                ? Collections.emptyList()
                : new ArrayList<>(sourceItems);
        List<String> warnings = new ArrayList<>();
        boolean valid = true;

        if (items.size() < MIN_STICKERS) {
            warnings.add("Для набора нужно минимум 3 стикера");
            valid = false;
        } else if (items.size() > MAX_STICKERS) {
            warnings.add("В наборе может быть не больше 30 стикеров");
            valid = false;
        }

        boolean hasAnimated = false;
        boolean coverFound = false;
        for (Item<T> item : items) {
            if (item == null || item.source == null
                    || item.animationKind == MediaAnimationInspector.AnimationKind.UNKNOWN) {
                warnings.add("Не удалось определить тип одного из файлов");
                valid = false;
                continue;
            }
            if (item.animationKind == MediaAnimationInspector.AnimationKind.ANIMATED) {
                hasAnimated = true;
            }
            if (Objects.equals(item.source, cover)) coverFound = true;
        }

        if (cover == null || !coverFound) {
            warnings.add("Выберите обложку из файлов проекта");
            valid = false;
        }

        TargetPackType target = hasAnimated
                ? TargetPackType.ANIMATED_PACK
                : TargetPackType.STATIC_PACK;
        List<Job<T>> jobs = new ArrayList<>();
        for (Item<T> item : items) {
            if (item == null || item.source == null
                    || item.animationKind == MediaAnimationInspector.AnimationKind.UNKNOWN) {
                continue;
            }
            OutputStrategy strategy;
            if (item.animationKind == MediaAnimationInspector.AnimationKind.ANIMATED) {
                // Animated + STATIC_PACK is impossible because any animated item chooses ANIMATED_PACK.
                strategy = OutputStrategy.ANIMATED_TRANSCODE;
            } else if (target == TargetPackType.ANIMATED_PACK) {
                strategy = OutputStrategy.STATIC_TO_ANIMATED_WRAPPER;
            } else {
                strategy = OutputStrategy.STATIC_WEBP;
            }
            jobs.add(new Job<>(item.source, strategy));
        }

        return new ExportPlan<>(
                target,
                jobs,
                coverFound ? new TrayJob<>(cover) : null,
                warnings,
                valid
        );
    }
}
