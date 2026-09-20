package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MediaAnimationInspectorTest {
    @Test
    public void pngAndJpegAreStaticByContent() {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        byte[] jpeg = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};

        assertEquals(MediaAnimationInspector.AnimationKind.STATIC,
                MediaAnimationInspector.inspect("application/octet-stream", png));
        assertEquals(MediaAnimationInspector.AnimationKind.STATIC,
                MediaAnimationInspector.inspect("video/mp4", jpeg));
    }

    @Test
    public void gifUsesRealFrameCount() throws Exception {
        assertEquals(1, MediaAnimationInspector.countGifFrames(gif(1)));
        assertEquals(2, MediaAnimationInspector.countGifFrames(gif(2)));
        assertEquals(MediaAnimationInspector.AnimationKind.STATIC,
                MediaAnimationInspector.inspect("image/gif", gif(1)));
        assertEquals(MediaAnimationInspector.AnimationKind.ANIMATED,
                MediaAnimationInspector.inspect("image/gif", gif(2)));
    }

    @Test
    public void webpUsesContainerStructureNotExtension() throws Exception {
        assertEquals(MediaAnimationInspector.AnimationKind.STATIC,
                MediaAnimationInspector.inspect("image/webp", simpleStaticWebp()));
        assertEquals(MediaAnimationInspector.AnimationKind.STATIC,
                MediaAnimationInspector.inspect("image/webp", webp(false, 0)));
        assertEquals(MediaAnimationInspector.AnimationKind.STATIC,
                MediaAnimationInspector.inspect("image/webp", webp(true, 1)));
        assertEquals(MediaAnimationInspector.AnimationKind.ANIMATED,
                MediaAnimationInspector.inspect("image/webp", webp(true, 2)));
    }

    @Test
    public void containerContentWinsOverMisleadingImageMime() throws Exception {
        assertEquals(MediaAnimationInspector.AnimationKind.ANIMATED,
                MediaAnimationInspector.inspect("image/png", webp(true, 2)));
    }

    @Test
    public void videoMimeIsAnimatedWhenNoKnownImageContainerExists() {
        assertEquals(MediaAnimationInspector.AnimationKind.ANIMATED,
                MediaAnimationInspector.inspect("video/mp4", new byte[]{0, 1, 2, 3}));
        assertEquals(MediaAnimationInspector.AnimationKind.UNKNOWN,
                MediaAnimationInspector.inspect("application/octet-stream", new byte[]{0, 1, 2, 3}));
    }

    private static byte[] gif(int frames) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("GIF89a".getBytes(StandardCharsets.US_ASCII));
        // Logical screen descriptor: 1x1, no global color table.
        out.write(new byte[]{1, 0, 1, 0, 0, 0, 0});
        for (int i = 0; i < frames; i++) {
            out.write(0x2c);
            // left, top, width, height, packed (no local color table)
            out.write(new byte[]{0, 0, 0, 0, 1, 0, 1, 0, 0});
            out.write(2); // LZW minimum code size
            out.write(1); // one byte data sub-block
            out.write(0);
            out.write(0); // sub-block terminator
        }
        out.write(0x3b);
        return out.toByteArray();
    }

    private static byte[] simpleStaticWebp() throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeChunk(body, "VP8 ", new byte[]{1, 2, 3, 4});
        return riffWebp(body.toByteArray());
    }

    private static byte[] webp(boolean animationContainer, int frames) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeChunk(body, "VP8X", vp8x(animationContainer));
        if (animationContainer) {
            writeChunk(body, "ANIM", new byte[6]);
            for (int i = 0; i < frames; i++) writeChunk(body, "ANMF", new byte[16]);
        }
        return riffWebp(body.toByteArray());
    }

    private static byte[] riffWebp(byte[] chunks) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeUInt32Le(out, 4 + chunks.length);
        out.write("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.write(chunks);
        return out.toByteArray();
    }

    private static byte[] vp8x(boolean animated) {
        byte[] payload = new byte[10];
        payload[0] = animated ? (byte) 0x02 : 0;
        // Canvas size fields are stored as dimension - 1. Use 1x1 here.
        return payload;
    }

    private static void writeChunk(ByteArrayOutputStream out, String fourCc, byte[] payload)
            throws IOException {
        out.write(fourCc.getBytes(StandardCharsets.US_ASCII));
        writeUInt32Le(out, payload.length);
        out.write(payload);
        if ((payload.length & 1) != 0) out.write(0);
    }

    private static void writeUInt32Le(ByteArrayOutputStream out, long value) {
        out.write((int) (value & 0xff));
        out.write((int) ((value >>> 8) & 0xff));
        out.write((int) ((value >>> 16) & 0xff));
        out.write((int) ((value >>> 24) & 0xff));
    }
}
