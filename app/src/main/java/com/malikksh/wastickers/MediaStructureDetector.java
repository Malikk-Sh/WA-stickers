package com.malikksh.wastickers;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Content-structure detector. Never infers animation from a filename. */
final class MediaStructureDetector {
    private static final int MAX_SKIP_BUFFER = 8192;

    private MediaStructureDetector() {}

    static PackCompatibilityPlanner.SourceKind detect(InputStream source) throws IOException {
        if (source == null) return PackCompatibilityPlanner.SourceKind.UNKNOWN;
        BufferedInputStream input = source instanceof BufferedInputStream
                ? (BufferedInputStream) source
                : new BufferedInputStream(source);
        input.mark(16);
        byte[] header = new byte[12];
        int read = readUpTo(input, header, 0, header.length);
        input.reset();

        if (read >= 8 && isPng(header)) return PackCompatibilityPlanner.SourceKind.STATIC;
        if (read >= 3 && isJpeg(header)) return PackCompatibilityPlanner.SourceKind.STATIC;
        if (read >= 6 && isGif(header)) return detectGif(input);
        if (read >= 12 && isWebp(header)) return detectWebp(input);
        return PackCompatibilityPlanner.SourceKind.UNKNOWN;
    }

    private static PackCompatibilityPlanner.SourceKind detectGif(InputStream input) throws IOException {
        byte[] header = new byte[13];
        if (!readFully(input, header, 0, header.length)) {
            return PackCompatibilityPlanner.SourceKind.UNKNOWN;
        }
        int packed = header[10] & 0xff;
        if ((packed & 0x80) != 0) {
            int colorTableBytes = 3 * (1 << ((packed & 0x07) + 1));
            if (!skipFully(input, colorTableBytes)) return PackCompatibilityPlanner.SourceKind.UNKNOWN;
        }

        int frames = 0;
        while (true) {
            int marker = input.read();
            if (marker < 0) break;
            if (marker == 0x3b) break; // trailer
            if (marker == 0x21) { // extension
                if (input.read() < 0 || !skipSubBlocks(input)) {
                    return PackCompatibilityPlanner.SourceKind.UNKNOWN;
                }
                continue;
            }
            if (marker != 0x2c) return PackCompatibilityPlanner.SourceKind.UNKNOWN;

            byte[] descriptor = new byte[9];
            if (!readFully(input, descriptor, 0, descriptor.length)) {
                return PackCompatibilityPlanner.SourceKind.UNKNOWN;
            }
            int imagePacked = descriptor[8] & 0xff;
            if ((imagePacked & 0x80) != 0) {
                int localColorTableBytes = 3 * (1 << ((imagePacked & 0x07) + 1));
                if (!skipFully(input, localColorTableBytes)) {
                    return PackCompatibilityPlanner.SourceKind.UNKNOWN;
                }
            }
            if (input.read() < 0 || !skipSubBlocks(input)) {
                return PackCompatibilityPlanner.SourceKind.UNKNOWN;
            }
            frames++;
            if (frames >= 2) return PackCompatibilityPlanner.SourceKind.ANIMATED;
        }
        return frames == 1
                ? PackCompatibilityPlanner.SourceKind.STATIC
                : PackCompatibilityPlanner.SourceKind.UNKNOWN;
    }

    private static PackCompatibilityPlanner.SourceKind detectWebp(InputStream input) throws IOException {
        byte[] riff = new byte[12];
        if (!readFully(input, riff, 0, riff.length) || !isWebp(riff)) {
            return PackCompatibilityPlanner.SourceKind.UNKNOWN;
        }

        boolean sawStaticPayload = false;
        boolean animationFlag = false;
        boolean animationChunk = false;
        int animationFrames = 0;
        while (true) {
            byte[] chunkHeader = new byte[8];
            int read = readUpTo(input, chunkHeader, 0, chunkHeader.length);
            if (read == 0) break;
            if (read != chunkHeader.length) return PackCompatibilityPlanner.SourceKind.UNKNOWN;
            long chunkSize = uint32le(chunkHeader, 4);
            if (chunkSize > Integer.MAX_VALUE) return PackCompatibilityPlanner.SourceKind.UNKNOWN;
            String chunk = fourCc(chunkHeader, 0);

            if ("VP8X".equals(chunk)) {
                if (chunkSize < 10) return PackCompatibilityPlanner.SourceKind.UNKNOWN;
                int flags = input.read();
                if (flags < 0) return PackCompatibilityPlanner.SourceKind.UNKNOWN;
                animationFlag = (flags & 0x02) != 0;
                if (!skipFully(input, chunkSize - 1)) return PackCompatibilityPlanner.SourceKind.UNKNOWN;
                if (!animationFlag) sawStaticPayload = true;
            } else {
                if ("ANIM".equals(chunk)) animationChunk = true;
                if ("ANMF".equals(chunk)) animationFrames++;
                if ("VP8 ".equals(chunk) || "VP8L".equals(chunk)) sawStaticPayload = true;
                if (!skipFully(input, chunkSize)) return PackCompatibilityPlanner.SourceKind.UNKNOWN;
            }
            if ((chunkSize & 1L) != 0 && input.read() < 0) {
                return PackCompatibilityPlanner.SourceKind.UNKNOWN;
            }
        }

        if (animationFlag && animationChunk && animationFrames > 0) {
            return PackCompatibilityPlanner.SourceKind.ANIMATED;
        }
        if (animationFlag) return PackCompatibilityPlanner.SourceKind.UNKNOWN;
        return sawStaticPayload
                ? PackCompatibilityPlanner.SourceKind.STATIC
                : PackCompatibilityPlanner.SourceKind.UNKNOWN;
    }

    private static boolean skipSubBlocks(InputStream input) throws IOException {
        while (true) {
            int size = input.read();
            if (size < 0) return false;
            if (size == 0) return true;
            if (!skipFully(input, size)) return false;
        }
    }

    private static boolean skipFully(InputStream input, long bytes) throws IOException {
        if (bytes < 0) return false;
        byte[] scratch = null;
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
                continue;
            }
            if (scratch == null) scratch = new byte[MAX_SKIP_BUFFER];
            int read = input.read(scratch, 0, (int) Math.min(scratch.length, remaining));
            if (read < 0) return false;
            remaining -= read;
        }
        return true;
    }

    private static int readUpTo(InputStream input, byte[] target, int offset, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int read = input.read(target, offset + total, length - total);
            if (read < 0) break;
            total += read;
        }
        return total;
    }

    private static boolean readFully(InputStream input, byte[] target, int offset, int length) throws IOException {
        return readUpTo(input, target, offset, length) == length;
    }

    private static boolean isPng(byte[] h) {
        int[] signature = {0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        for (int i = 0; i < signature.length; i++) if ((h[i] & 0xff) != signature[i]) return false;
        return true;
    }

    private static boolean isJpeg(byte[] h) {
        return (h[0] & 0xff) == 0xff && (h[1] & 0xff) == 0xd8 && (h[2] & 0xff) == 0xff;
    }

    private static boolean isGif(byte[] h) {
        String magic = new String(h, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
        return "GIF87a".equals(magic) || "GIF89a".equals(magic);
    }

    private static boolean isWebp(byte[] h) {
        return "RIFF".equals(fourCc(h, 0)) && "WEBP".equals(fourCc(h, 8));
    }

    private static String fourCc(byte[] h, int offset) {
        return new String(h, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static long uint32le(byte[] data, int offset) {
        return ((long) data[offset] & 0xff)
                | (((long) data[offset + 1] & 0xff) << 8)
                | (((long) data[offset + 2] & 0xff) << 16)
                | (((long) data[offset + 3] & 0xff) << 24);
    }
}
