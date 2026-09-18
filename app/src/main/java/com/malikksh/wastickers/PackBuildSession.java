package com.malikksh.wastickers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class PackBuildSession<T> {
    private final List<T> failures = new ArrayList<>();

    private boolean active;
    private String packId;
    private String packName;
    private File packDir;
    private boolean animated;
    private int successCount;
    private T traySource;
    private T preferredTraySource;
    private int lastFps;
    private int lastQuality;
    private volatile boolean cancelRequested;
    private boolean autoFinalizeAllowed = true;

    void begin(String packId, String packName, File packDir, boolean animated, T preferredTraySource) {
        reset();
        this.active = true;
        this.packId = packId;
        this.packName = packName;
        this.packDir = packDir;
        this.animated = animated;
        this.preferredTraySource = preferredTraySource;
    }

    void beginBatch() {
        cancelRequested = false;
    }

    void recordSuccess(T item, int fps, int quality) {
        if (!active) throw new IllegalStateException("Pack build session is not active");
        if (traySource == null || Objects.equals(item, preferredTraySource)) {
            traySource = item;
        }
        successCount++;
        lastFps = fps;
        lastQuality = quality;
    }

    void setFailures(List<T> failedItems) {
        failures.clear();
        if (failedItems != null) failures.addAll(failedItems);
    }

    List<T> takeFailuresForRetry() {
        List<T> retry = new ArrayList<>(failures);
        failures.clear();
        return retry;
    }

    List<T> failures() {
        return Collections.unmodifiableList(new ArrayList<>(failures));
    }

    int failureCount() {
        return failures.size();
    }

    boolean hasFailures() {
        return !failures.isEmpty();
    }

    void requestCancel() {
        cancelRequested = true;
    }

    boolean isCancelRequested() {
        return cancelRequested;
    }

    void setAutoFinalizeAllowed(boolean allowed) {
        autoFinalizeAllowed = allowed;
    }

    boolean isAutoFinalizeAllowed() {
        return autoFinalizeAllowed;
    }

    boolean shouldAutoFinalize() {
        return autoFinalizeAllowed
                && BatchResultPolicy.shouldAutoFinalize(failures.size(), cancelRequested);
    }

    boolean canFinalize() {
        return BatchResultPolicy.canFinalize(successCount);
    }

    boolean isActive() {
        return active;
    }

    String packId() {
        return packId;
    }

    String packName() {
        return packName;
    }

    File packDir() {
        return packDir;
    }

    boolean isAnimated() {
        return animated;
    }

    int successCount() {
        return successCount;
    }

    T traySource() {
        return traySource;
    }

    int lastFps() {
        return lastFps;
    }

    int lastQuality() {
        return lastQuality;
    }

    void reset() {
        active = false;
        packId = null;
        packName = null;
        packDir = null;
        animated = false;
        successCount = 0;
        traySource = null;
        preferredTraySource = null;
        lastFps = 0;
        lastQuality = 0;
        failures.clear();
        cancelRequested = false;
        autoFinalizeAllowed = true;
    }
}
