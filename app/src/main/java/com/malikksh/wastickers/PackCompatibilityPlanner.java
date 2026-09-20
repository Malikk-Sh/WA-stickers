package com.malikksh.wastickers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** UI-independent compatibility planner for static, animated and mixed sticker projects. */
final class PackCompatibilityPlanner {
    static final int MIN_STICKERS = 3;
    static final int MAX_STICKERS = 30;

    enum SourceKind {
        STATIC,
        ANIMATED,
        UNKNOWN
    }

    enum TargetPackType {
        STATIC_PACK,
        ANIMATED_PACK
    }

    enum JobStrategy {
        STATIC_WEBP,
        STATIC_TO_ANIMATED_WRAPPER,
        ANIMATED_TRANSCODE
    }

    static final class Source<T> {
        final T item;
        final SourceKind kind;

        Source(T item, SourceKind kind) {
            this.item = item;
            this.kind = kind == null ? SourceKind.UNKNOWN : kind;
        }
    }

    static final class Job<T> {
        final T item;
        final SourceKind sourceKind;
        final JobStrategy strategy;

        Job(T item, SourceKind sourceKind, JobStrategy strategy) {
            this.item = item;
            this.sourceKind = sourceKind;
            this.strategy = strategy;
        }
    }

    static final class ExportPlan<T> {
        final TargetPackType targetPackType;
        final List<Job<T>> jobs;
        final List<String> validationWarnings;

        ExportPlan(
                TargetPackType targetPackType,
                List<Job<T>> jobs,
                List<String> validationWarnings
        ) {
            this.targetPackType = targetPackType;
            this.jobs = Collections.unmodifiableList(new ArrayList<>(jobs));
            this.validationWarnings = Collections.unmodifiableList(new ArrayList<>(validationWarnings));
        }

        boolean isValid() {
            return targetPackType != null
                    && validationWarnings.isEmpty()
                    && jobs.size() >= MIN_STICKERS
                    && jobs.size() <= MAX_STICKERS;
        }

        boolean requiresStaticWrapper() {
            for (Job<T> job : jobs) {
                if (job.strategy == JobStrategy.STATIC_TO_ANIMATED_WRAPPER) return true;
            }
            return false;
        }
    }

    private PackCompatibilityPlanner() {}

    static <T> ExportPlan<T> plan(List<Source<T>> sources) {
        List<Source<T>> safeSources = sources == null
                ? Collections.emptyList()
                : new ArrayList<>(sources);
        List<String> warnings = new ArrayList<>();

        if (safeSources.size() < MIN_STICKERS) {
            warnings.add("Для набора нужно минимум 3 стикера");
        } else if (safeSources.size() > MAX_STICKERS) {
            warnings.add("В наборе может быть не больше 30 стикеров");
        }

        boolean anyAnimated = false;
        boolean hasUnknown = false;
        for (Source<T> source : safeSources) {
            SourceKind kind = source == null ? SourceKind.UNKNOWN : source.kind;
            if (kind == SourceKind.ANIMATED) anyAnimated = true;
            if (kind == SourceKind.UNKNOWN) hasUnknown = true;
        }
        if (hasUnknown) warnings.add("Не удалось определить тип одного или нескольких файлов");

        TargetPackType target = hasUnknown
                ? null
                : (anyAnimated ? TargetPackType.ANIMATED_PACK : TargetPackType.STATIC_PACK);
        List<Job<T>> jobs = new ArrayList<>();
        if (target != null) {
            for (Source<T> source : safeSources) {
                if (source == null) continue;
                jobs.add(new Job<>(source.item, source.kind, strategyFor(source.kind, target)));
            }
        }
        return new ExportPlan<>(target, jobs, warnings);
    }

    private static JobStrategy strategyFor(SourceKind source, TargetPackType target) {
        if (source == SourceKind.STATIC && target == TargetPackType.STATIC_PACK) {
            return JobStrategy.STATIC_WEBP;
        }
        if (source == SourceKind.STATIC && target == TargetPackType.ANIMATED_PACK) {
            return JobStrategy.STATIC_TO_ANIMATED_WRAPPER;
        }
        if (source == SourceKind.ANIMATED && target == TargetPackType.ANIMATED_PACK) {
            return JobStrategy.ANIMATED_TRANSCODE;
        }
        throw new IllegalArgumentException("Unsupported source/target combination: " + source + " -> " + target);
    }
}
