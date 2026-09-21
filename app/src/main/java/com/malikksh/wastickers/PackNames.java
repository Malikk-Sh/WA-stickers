package com.malikksh.wastickers;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;

/** Display names are labels, never pack identities. */
final class PackNames {
    private PackNames() {}
    static String display(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}]+", " ").trim();
    }
    static String key(String value) { return display(value).toLowerCase(Locale.ROOT); }
    static boolean valid(String value) {
        String name = display(value);
        return !name.isEmpty() && name.length() <= 60
                && name.codePoints().noneMatch(Character::isISOControl);
    }
    static String next(String base, Collection<String> names) {
        String clean = display(base);
        if (clean.isEmpty()) clean = "Мои стикеры";
        for (int number = 1; ; number++) {
            String suffix = number == 1 ? "" : " " + number;
            String candidate = clean.substring(0, Math.min(clean.length(), 60 - suffix.length())) + suffix;
            boolean used = false;
            for (String name : names) if (key(candidate).equals(key(name))) { used = true; break; }
            if (!used) return candidate;
        }
    }
    static String nextVersion(String current) {
        return new java.math.BigInteger(current == null ? "0" : current).add(java.math.BigInteger.ONE).toString();
    }
}
