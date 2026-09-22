package com.malikksh.wastickers;

import java.util.HashMap;
import java.util.Map;

/** Presentation deadlines only. Never blocks a worker, success, failure or cancellation. */
final class BuildMotionPolicy<T> {
    private final Map<T, Long> deadlines = new HashMap<>();
    private long batchStarted = -1;
    void reset() { deadlines.clear(); batchStarted = -1; }
    void started(T item, long now, boolean motion) {
        if (!motion) return;
        if (batchStarted < 0) batchStarted = now;
        deadlines.put(item, Math.min(batchStarted + 1500, Math.max(batchStarted + 1200, now + 1000)));
    }
    long nextDeadline(long now) {
        long next = Long.MAX_VALUE;
        for (long deadline : deadlines.values()) if (deadline > now) next = Math.min(next, deadline);
        return next;
    }
    boolean holdSuccess(T item, long now, boolean motion, boolean interrupted) {
        return motion && !interrupted && now < deadlines.getOrDefault(item, 0L);
    }
}
