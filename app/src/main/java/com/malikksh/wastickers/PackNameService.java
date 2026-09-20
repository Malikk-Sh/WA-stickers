package com.malikksh.wastickers;

import android.content.Context;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Centralizes user-facing pack-name normalization and uniqueness rules. */
final class PackNameService {
    static final String DEFAULT_NAME = "Мои стикеры";
    static final String DUPLICATE_ERROR = "Набор с таким названием уже существует";

    private PackNameService() {}

    static String normalizeDisplayName(String raw) {
        if (raw == null) return "";
        String unicode = Normalizer.normalize(raw, Normalizer.Form.NFC);
        StringBuilder result = new StringBuilder(unicode.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < unicode.length();) {
            int codePoint = unicode.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                if (result.length() > 0) pendingSpace = true;
                continue;
            }
            if (pendingSpace) {
                result.append(' ');
                pendingSpace = false;
            }
            result.appendCodePoint(codePoint);
        }
        return result.toString();
    }

    static String canonicalKey(String raw) {
        String display = normalizeDisplayName(raw);
        if (display.isEmpty()) return "";
        return Normalizer.normalize(display, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    static boolean isAvailable(List<PackStore.Pack> packs, String candidate, String excludedPackId) {
        String key = canonicalKey(candidate);
        if (key.isEmpty()) return false;
        if (packs == null) return true;
        for (PackStore.Pack pack : packs) {
            if (pack == null) continue;
            if (excludedPackId != null && excludedPackId.equals(pack.id)) continue;
            if (key.equals(canonicalKey(pack.name))) return false;
        }
        return true;
    }

    static String nextAvailableName(List<PackStore.Pack> packs, String preferredBase) {
        String base = normalizeDisplayName(preferredBase);
        if (base.isEmpty()) base = DEFAULT_NAME;
        if (isAvailable(packs, base, null)) return base;
        for (int suffix = 2; suffix < Integer.MAX_VALUE; suffix++) {
            String candidate = base + " " + suffix;
            if (isAvailable(packs, candidate, null)) return candidate;
        }
        throw new IllegalStateException("Could not allocate unique pack name");
    }

    static String nextAvailableDefault(Context context) {
        return nextAvailableName(PackStore.getPacks(context), DEFAULT_NAME);
    }

    static String validationError(Context context, String candidate, String excludedPackId) {
        String normalized = normalizeDisplayName(candidate);
        if (normalized.isEmpty()) return null;
        return isAvailable(PackStore.getPacks(context), normalized, excludedPackId)
                ? null
                : DUPLICATE_ERROR;
    }

    /**
     * Finds the one saved pack represented by an existing dialog value. Returning null when the
     * name is ambiguous deliberately avoids exempting an arbitrary duplicate left by an old build.
     */
    static String uniquePackIdForExistingName(Context context, String existingName) {
        String key = canonicalKey(existingName);
        if (key.isEmpty()) return null;
        String matchedId = null;
        for (PackStore.Pack pack : PackStore.getPacks(context)) {
            if (pack == null || !key.equals(canonicalKey(pack.name))) continue;
            if (matchedId != null) return null;
            matchedId = pack.id;
        }
        return matchedId;
    }
}
