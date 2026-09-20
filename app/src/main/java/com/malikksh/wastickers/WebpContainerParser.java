package com.malikksh.wastickers;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Minimal RIFF/WebP parser shared by actual-animation detection and wrapper validation. */
final class WebpContainerParser {
    static final int FEATURE_ALPHA = 0x10;
    static final int FEATURE_ANIMATION = 0x02;

    static final class FrameInfo {
        final int x;
        final int y;
        final int width;
        final int height;
        final int durationMs;
        final boolean hasImageData;

        FrameInfo(int x, int y, int width, int height, int durationMs, boolean hasImageData) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.durationMs = durationMs;
            this.hasImageData = hasImageData;
        }
    }

    static final class Result {
        boolean valid;
        int canvasWidth = -1;
        int canvasHeight = -1;
        boolean animationFlag;
        boolean animationContainer;
        boolean hasAlpha;
        boolean hasAnimChunk;
        int frameCount;
        int totalDurationMs;
        int minFrameDurationMs;
        boolean firstFrameComplete;
        byte[] staticFramePayload = new byte[0];
        List<FrameInfo> frames = Collections.emptyList();
    }

    private static final class Chunk {
        final String fourCc;
        final int offset;
        final int payloadOffset;
        final int payloadSize;
        final int totalSize;

        Chunk(String fourCc, int offset, int payloadOffset, int payloadSize, int totalSize) {
            this.fourCc = fourCc;
            this.offset = offset;
            this.payloadOffset = payloadOffset;
            this.payloadSize = payloadSize;
            this.totalSize = totalSize;
        }
    }

    private WebpContainerParser() {}

    static Result parse(byte[] data) {
        Result result = new Result();
        if (data == null || data.length < 12) return result;
        if (!matchesAscii(data, 0, "RIFF") || !matchesAscii(data, 8, "WEBP")) return result;

        long declared = uint32Le(data, 4);
        if (declared < 4 || declared + 8L != data.length) return result;

        List<Chunk> topChunks = readChunks(data, 12, data.length);
        if (topChunks == null) return result;

        boolean hasTopLevelImage = false;
        boolean hasVp8x = false;
        List<FrameInfo> frames = new ArrayList<>();
        ByteArrayOutputStream staticFrame = new ByteArrayOutputStream();

        for (Chunk chunk : topChunks) {
            if ("VP8X".equals(chunk.fourCc) && chunk.payloadSize >= 10) {
                hasVp8x = true;
                int flags = data[chunk.payloadOffset] & 0xff;
                result.animationFlag = (flags & FEATURE_ANIMATION) != 0;
                result.hasAlpha |= (flags & FEATURE_ALPHA) != 0;
                result.canvasWidth = uint24Le(data, chunk.payloadOffset + 4) + 1;
                result.canvasHeight = uint24Le(data, chunk.payloadOffset + 7) + 1;
            } else if ("ANIM".equals(chunk.fourCc) && chunk.payloadSize >= 6) {
                result.hasAnimChunk = true;
            } else if ("ANMF".equals(chunk.fourCc) && chunk.payloadSize >= 16) {
                FrameInfo frame = parseFrame(data, chunk);
                if (frame != null) frames.add(frame);
            } else if ("ALPH".equals(chunk.fourCc)) {
                result.hasAlpha = true;
                copyChunk(data, chunk, staticFrame);
            } else if ("VP8 ".equals(chunk.fourCc)) {
                hasTopLevelImage = true;
                copyChunk(data, chunk, staticFrame);
                if (result.canvasWidth <= 0 || result.canvasHeight <= 0) {
                    int[] dimensions = parseVp8Dimensions(data, chunk);
                    result.canvasWidth = dimensions[0];
                    result.canvasHeight = dimensions[1];
                }
            } else if ("VP8L".equals(chunk.fourCc)) {
                hasTopLevelImage = true;
                copyChunk(data, chunk, staticFrame);
                int[] dimensions = parseVp8lDimensions(data, chunk);
                if (result.canvasWidth <= 0 || result.canvasHeight <= 0) {
                    result.canvasWidth = dimensions[0];
                    result.canvasHeight = dimensions[1];
                }
                result.hasAlpha |= dimensions[2] != 0;
            }
        }

        result.frames = Collections.unmodifiableList(frames);
        result.frameCount = frames.size();
        if (!frames.isEmpty()) {
            int minDuration = Integer.MAX_VALUE;
            long total = 0L;
            for (FrameInfo frame : frames) {
                minDuration = Math.min(minDuration, frame.durationMs);
                total += Math.max(0, frame.durationMs);
            }
            result.minFrameDurationMs = minDuration == Integer.MAX_VALUE ? 0 : minDuration;
            result.totalDurationMs = total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
            FrameInfo first = frames.get(0);
            result.firstFrameComplete = first.hasImageData
                    && first.x == 0
                    && first.y == 0
                    && first.width == result.canvasWidth
                    && first.height == result.canvasHeight;
        }

        result.staticFramePayload = staticFrame.toByteArray();
        result.animationContainer = hasVp8x
                && result.animationFlag
                && result.hasAnimChunk
                && result.frameCount > 0;
        result.valid = hasTopLevelImage || result.animationContainer;
        return result;
    }

    private static FrameInfo parseFrame(byte[] data, Chunk chunk) {
        int payload = chunk.payloadOffset;
        int frameDataStart = payload + 16;
        int frameEnd = payload + chunk.payloadSize;
        if (frameDataStart > frameEnd) return null;

        int x = uint24Le(data, payload) * 2;
        int y = uint24Le(data, payload + 3) * 2;
        int width = uint24Le(data, payload + 6) + 1;
        int height = uint24Le(data, payload + 9) + 1;
        int duration = uint24Le(data, payload + 12);

        List<Chunk> nested = readChunks(data, frameDataStart, frameEnd);
        if (nested == null) return null;
        boolean hasImage = false;
        for (Chunk nestedChunk : nested) {
            if ("VP8 ".equals(nestedChunk.fourCc) || "VP8L".equals(nestedChunk.fourCc)) {
                hasImage = true;
                break;
            }
        }
        return new FrameInfo(x, y, width, height, duration, hasImage);
    }

    private static List<Chunk> readChunks(byte[] data, int start, int end) {
        if (start < 0 || end < start || end > data.length) return null;
        List<Chunk> chunks = new ArrayList<>();
        int offset = start;
        while (offset < end) {
            if (offset + 8 > end) return null;
            String fourCc = ascii(data, offset, 4);
            long sizeLong = uint32Le(data, offset + 4);
            if (sizeLong < 0 || sizeLong > Integer.MAX_VALUE) return null;
            int payloadSize = (int) sizeLong;
            int payloadOffset = offset + 8;
            long rawEnd = (long) payloadOffset + payloadSize;
            long paddedEnd = rawEnd + (payloadSize & 1L);
            if (rawEnd > end || paddedEnd > end) return null;
            int totalSize = (int) (paddedEnd - offset);
            chunks.add(new Chunk(fourCc, offset, payloadOffset, payloadSize, totalSize));
            offset = (int) paddedEnd;
        }
        return chunks;
    }

    private static int[] parseVp8Dimensions(byte[] data, Chunk chunk) {
        if (chunk.payloadSize < 10) return new int[]{-1, -1};
        int p = chunk.payloadOffset;
        if ((data[p + 3] & 0xff) != 0x9d
                || (data[p + 4] & 0xff) != 0x01
                || (data[p + 5] & 0xff) != 0x2a) {
            return new int[]{-1, -1};
        }
        int width = ((data[p + 6] & 0xff) | ((data[p + 7] & 0xff) << 8)) & 0x3fff;
        int height = ((data[p + 8] & 0xff) | ((data[p + 9] & 0xff) << 8)) & 0x3fff;
        return new int[]{width, height};
    }

    private static int[] parseVp8lDimensions(byte[] data, Chunk chunk) {
        if (chunk.payloadSize < 5) return new int[]{-1, -1, 0};
        int p = chunk.payloadOffset;
        if ((data[p] & 0xff) != 0x2f) return new int[]{-1, -1, 0};
        long bits = ((long) data[p + 1] & 0xffL)
                | (((long) data[p + 2] & 0xffL) << 8)
                | (((long) data[p + 3] & 0xffL) << 16)
                | (((long) data[p + 4] & 0xffL) << 24);
        int width = (int) (bits & 0x3fffL) + 1;
        int height = (int) ((bits >>> 14) & 0x3fffL) + 1;
        int alpha = (int) ((bits >>> 28) & 0x1L);
        return new int[]{width, height, alpha};
    }

    private static void copyChunk(byte[] data, Chunk chunk, ByteArrayOutputStream output) {
        output.write(data, chunk.offset, chunk.totalSize);
    }

    static boolean matchesAscii(byte[] data, int offset, String expected) {
        if (data == null || offset < 0 || offset + expected.length() > data.length) return false;
        for (int i = 0; i < expected.length(); i++) {
            if ((byte) expected.charAt(i) != data[offset + i]) return false;
        }
        return true;
    }

    static String ascii(byte[] data, int offset, int count) {
        return new String(data, offset, count, StandardCharsets.US_ASCII);
    }

    static long uint32Le(byte[] data, int offset) {
        if (data == null || offset < 0 || offset + 4 > data.length) return -1L;
        return ((long) data[offset] & 0xffL)
                | (((long) data[offset + 1] & 0xffL) << 8)
                | (((long) data[offset + 2] & 0xffL) << 16)
                | (((long) data[offset + 3] & 0xffL) << 24);
    }

    static int uint24Le(byte[] data, int offset) {
        if (data == null || offset < 0 || offset + 3 > data.length) return -1;
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16);
    }
}
