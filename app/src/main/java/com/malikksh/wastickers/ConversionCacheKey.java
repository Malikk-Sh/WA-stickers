package com.malikksh.wastickers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class ConversionCacheKey {
    private ConversionCacheKey() {}

    static String build(String uri, boolean animated, long trimStartMs,
                        long sourceSize, String mime, String displayName,
                        String sampleDigest, int cacheVersion) {
        String raw = cacheVersion
                + "|" + (animated ? "animated" : "static")
                + "|" + Math.max(0L, trimStartMs)
                + "|" + sourceSize
                + "|" + safe(mime)
                + "|" + safe(displayName)
                + "|" + safe(sampleDigest)
                + "|" + safe(uri);
        return sha256(raw);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) out.append(String.format(java.util.Locale.US, "%02x", item & 0xff));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
