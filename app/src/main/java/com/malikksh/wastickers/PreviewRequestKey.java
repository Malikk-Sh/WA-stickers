package com.malikksh.wastickers;

final class PreviewRequestKey {
    private PreviewRequestKey() {}

    static String create(String uri, long startOffsetMs) {
        return (uri == null ? "" : uri) + "#t=" + Math.max(0L, startOffsetMs);
    }
}
