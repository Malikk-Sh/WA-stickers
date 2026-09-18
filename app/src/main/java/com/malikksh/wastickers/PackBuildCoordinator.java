package com.malikksh.wastickers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PackBuildCoordinator<T> {
    interface ProgressSink {
        void onStaticProgress(int percent, String detail);
        void onAnimatedProgress(AnimatedStickerConverter.Progress progress);
    }

    interface ItemProcessor<T> {
        ItemResult convert(T item, File target, ProgressSink progress) throws Exception;
    }

    interface Listener<T> {
        void onItemStarted(int index, int total, T item);
        void onStaticProgress(int index, int percent, String detail);
        void onAnimatedProgress(int index, AnimatedStickerConverter.Progress progress);
        void onItemSucceeded(int index, T item, ItemResult result);
        void onItemFailed(int index, T item, Throwable error);
        void onItemCompleted(int completed, int total);
        void onCancelledFrom(int startIndex);
    }

    static final class ItemResult {
        final long bytes;
        final int fps;
        final int quality;

        ItemResult(long bytes, int fps, int quality) {
            this.bytes = bytes;
            this.fps = fps;
            this.quality = quality;
        }

        boolean animated() {
            return fps > 0;
        }
    }

    static final class RunResult<T> {
        final boolean autoFinalize;
        final boolean cancelled;
        final Throwable fatalError;
        final Throwable lastItemError;
        final int lastFailureIndex;
        final T lastFailureItem;
        final List<T> failures;

        RunResult(
                boolean autoFinalize,
                boolean cancelled,
                Throwable fatalError,
                Throwable lastItemError,
                int lastFailureIndex,
                T lastFailureItem,
                List<T> failures
        ) {
            this.autoFinalize = autoFinalize;
            this.cancelled = cancelled;
            this.fatalError = fatalError;
            this.lastItemError = lastItemError;
            this.lastFailureIndex = lastFailureIndex;
            this.lastFailureItem = lastFailureItem;
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        }

        boolean isFatal() {
            return fatalError != null;
        }
    }

    private final PackBuildSession<T> session;

    PackBuildCoordinator(PackBuildSession<T> session) {
        this.session = session;
    }

    RunResult<T> run(List<T> work, ItemProcessor<T> processor, Listener<T> listener) {
        List<T> failures = new ArrayList<>();
        Throwable lastItemError = null;
        int lastFailureIndex = -1;
        T lastFailureItem = null;

        try {
            File packDir = session.packDir();
            if (!session.isActive() || packDir == null) {
                throw new IOException("Черновик набора больше недоступен");
            }
            if (!packDir.mkdirs() && !packDir.isDirectory()) {
                throw new IOException("Не удалось создать папку набора");
            }

            for (int i = 0; i < work.size(); i++) {
                if (session.isCancelRequested()) {
                    addRemainingForRetry(work, i, failures);
                    if (listener != null) listener.onCancelledFrom(i);
                    break;
                }

                T item = work.get(i);
                final int index = i;
                if (listener != null) listener.onItemStarted(index, work.size(), item);

                File target = new File(packDir, (session.successCount() + 1) + ".webp");
                try {
                    ItemResult result = processor.convert(item, target, new ProgressSink() {
                        @Override
                        public void onStaticProgress(int percent, String detail) {
                            if (listener != null) listener.onStaticProgress(index, percent, detail);
                        }

                        @Override
                        public void onAnimatedProgress(AnimatedStickerConverter.Progress progress) {
                            if (listener != null) listener.onAnimatedProgress(index, progress);
                        }
                    });
                    session.recordSuccess(item, result.fps, result.quality);
                    if (listener != null) listener.onItemSucceeded(index, item, result);
                } catch (Throwable itemError) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                    if (session.isCancelRequested()) {
                        addRemainingForRetry(work, i, failures);
                        if (listener != null) listener.onCancelledFrom(i);
                        break;
                    }

                    lastItemError = itemError;
                    lastFailureIndex = i;
                    lastFailureItem = item;
                    failures.add(item);
                    if (listener != null) listener.onItemFailed(i, item, itemError);
                }

                if (listener != null) listener.onItemCompleted(i + 1, work.size());
            }

            session.setFailures(failures);
            return new RunResult<>(
                    session.shouldAutoFinalize(),
                    session.isCancelRequested(),
                    null,
                    lastItemError,
                    lastFailureIndex,
                    lastFailureItem,
                    failures
            );
        } catch (Throwable fatalError) {
            session.setFailures(new ArrayList<>(work));
            return new RunResult<>(
                    false,
                    session.isCancelRequested(),
                    fatalError,
                    lastItemError,
                    lastFailureIndex,
                    lastFailureItem,
                    new ArrayList<>(work)
            );
        }
    }

    private void addRemainingForRetry(List<T> work, int startIndex, List<T> failures) {
        for (int i = Math.max(0, startIndex); i < work.size(); i++) {
            T item = work.get(i);
            if (!failures.contains(item)) failures.add(item);
        }
    }
}
