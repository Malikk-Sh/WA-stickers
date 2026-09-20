package com.malikksh.wastickers;

import java.nio.charset.StandardCharsets;

/**
 * Small content-based classifier used by the mixed-pack planner.
 *
 * It intentionally does not use file extensions. Known image containers are inspected first; a
 * video MIME type is treated as animated because video sources inherently contain a timeline.
 */
final class MediaAnimationInspector {
    enum AnimationKind {
        STATIC,
        ANIMATED,
        UNKNOWN
    }

    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private MediaAnimationInspector() {}

    static AnimationKind inspect(String mimeType, byte[] data) {
        if (isPng(data) || isJpeg(data)) return AnimationKind.STATIC;
        if (isGif(data)) return countGifFrames(data) > 1
                ? AnimationKind.ANIMATED
                : AnimationKind.STATIC;

        WebpAnimation webp = inspectWebp(data);
        if (webp.valid) {
            return webp.animationContainer && webp.frameCount > 1
                    ? AnimationKind.ANIMATED
                    : AnimationKind.STATIC;
        }

        String mime = mimeType == null ? "" : mimeType.trim().toLowerCase(java.util.Locale.US);
        if (mime.startsWith("video/")) return AnimationKind.ANIMATED;
        return AnimationKind.UNKNOWN;
    }

    static int countGifFrames(byte[] data) {
        if (!isGif(data) || data.length < 13) return 0;
        int offset = 6;
        if (offset + 7 > data.length) return 0;

        int packed = data[offset + 4] & 0xff;
        offset += 7;
        if ((packed & 0x80) != 0) {
            int tableBytes = 3 * (1 << ((packed & 0x07) + 1));
            if (offset + tableBytes > data.length) return 0;
            offset += tableBytes;
        }

        int frames = 0;
        while (offset < data.length) {
            int marker = data[offset++] & 0xff;
            if (marker == 0x3b) break; // trailer

            if (marker == 0x21) { // extension
                if (offset >= data.length) return frames;
                offset++; // extension label
                offset = skipSubBlocks(data, offset);
                if (offset < 0) return frames;
                continue;
            }

            if (marker != 0x2c) return frames; // image descriptor expected
            if (offset + 9 > data.length) return frames;
            int imagePacked = data[offset + 8] & 0xff;
            offset += 9;
            if ((imagePacked & 0x80) != 0) {
                int tableBytes = 3 * (1 << ((imagePacked & 0x07) + 1));
                if (offset + tableBytes > data.length) return frames;
                offset += tableBytes;
            }
            if (offset >= data.length) return frames;
            offset++; // LZW minimum code size
            offset = skipSubBlocks(data, offset);
            if (offset < 0) return frames;
            frames++;
        }
        return frames;
    }

    private static int skipSubBlocks(byte[] data, int offset) {
        while (offset < data.length) {
            int size = data[offset++] & 0xff;
            if (size == 0) return offset;
            if (offset + size > data.length) return -1;
            offset += size;
        }
        return -1;
    }

    private static boolean isPng(byte[] data) {
        if (data == null || data.length < PNG_SIGNATURE.length) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) return false;
        }
        return true;
    }

    private static boolean isJpeg(byte[] data) {
        return data != null
                && data.length >= 3
                && (data[0] & 0xff) == 0xff
                && (data[1] & 0xff) == 0xd8
                && (data[2] & 0xff) == 0xff;
    }

    private static boolean isGif(byte[] data) {
        if (data == null || data.length < 6) return false;
        String header = new String(data, 0, 6, StandardCharsets.US_ASCII);
        return "GIF87a".equals(header) || "GIF89a".equals(header);
    }

    private static WebpAnimation inspectWebp(byte[] data) {
        WebpAnimation result = new WebpAnimation();
        if (data == null || data.length < 20) return result;
        if (!matchesAscii(data, 0, "RIFF") || !matchesAscii(data, 8, "WEBP")) return result;

        int offset = 12;
        boolean hasVp8x = false;
        boolean hasStaticImageChunk = false;
        boolean animationFlag = false;
        boolean hasAnim = false;

        while (offset + 8 <= data.length) {
            String chunk = ascii(data, offset, 4);
            long sizeLong = uint32Le(data, offset + 4);
            if (sizeLong < 0 || sizeLong > Integer.MAX_VALUE) return new WebpAnimation();
            int size = (int) sizeLong;
            int payload = offset + 8;
            long endLong = (long) payload + size;
            if (endLong > data.length) return new WebpAnimation();

            if ("VP8X".equals(chunk) && size >= 10) {
                hasVp8x = true;
                animationFlag = (data[payload] & 0x02) != 0;
            } else if ("VP8 ".equals(chunk) || "VP8L".equals(chunk)) {
                hasStaticImageChunk = true;
            } else if ("ANIM".equals(chunk) && size >= 6) {
                hasAnim = true;
            } else if ("ANMF".equals(chunk) && size >= 16) {
                result.frameCount++;
            }

            long next = endLong + (size & 1L);
            if (next > data.length) return new WebpAnimation();
            offset = (int) next;
        }

        result.valid = hasVp8x || hasStaticImageChunk;
        result.animationContainer = hasVp8x && animationFlag && hasAnim && result.frameCount > 0;
        return result;
    }

    private static boolean matchesAscii(byte[] data, int offset, String expected) {
        if (data == null || offset < 0 || offset + expected.length() > data.length) return false;
        for (int i = 0; i < expected.length(); i++) {
            if ((byte) expected.charAt(i) != data[offset + i]) return false;
        }
        return true;
    }

    private static String ascii(byte[] data, int offset, int count) {
        return new String(data, offset, count, StandardCharsets.US_ASCII);
    }

    private static long uint32Le(byte[] data, int offset) {
        if (offset < 0 || offset + 4 > data.length) return -1L;
        return ((long) data[offset] & 0xffL)
                | (((long) data[offset + 1] & 0xffL) << 8)
                | (((long) data[offset + 2] & 0xffL) << 16)
                | (((long) data[offset + 3] & 0xffL) << 24);
    }

    private static final class WebpAnimation {
        boolean valid;
        boolean animationContainer;
        int frameCount;
    }
}
