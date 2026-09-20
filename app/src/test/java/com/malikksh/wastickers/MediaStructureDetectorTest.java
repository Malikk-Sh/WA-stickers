package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class MediaStructureDetectorTest {
    @Test
    public void pngAndJpegAreStatic() throws Exception {
        assertKind(PackCompatibilityPlanner.SourceKind.STATIC,
                bytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a));
        assertKind(PackCompatibilityPlanner.SourceKind.STATIC,
                bytes(0xff, 0xd8, 0xff, 0xe0));
    }

    @Test
    public void staticWebpIsDetectedFromContainer() throws Exception {
        ByteArrayOutputStream out = webpHeader();
        chunk(out, "VP8 ", new byte[0]);
        assertKind(PackCompatibilityPlanner.SourceKind.STATIC, out.toByteArray());
    }

    @Test
    public void animatedWebpRequiresAnimationStructure() throws Exception {
        ByteArrayOutputStream out = webpHeader();
        byte[] vp8x = new byte[10];
        vp8x[0] = 0x02;
        chunk(out, "VP8X", vp8x);
        chunk(out, "ANIM", new byte[6]);
        chunk(out, "ANMF", new byte[16]);
        assertKind(PackCompatibilityPlanner.SourceKind.ANIMATED, out.toByteArray());
    }

    @Test
    public void singleFrameGifIsEffectivelyStatic() throws Exception {
        assertKind(PackCompatibilityPlanner.SourceKind.STATIC, gif(1));
    }

    @Test
    public void multiFrameGifIsAnimated() throws Exception {
        assertKind(PackCompatibilityPlanner.SourceKind.ANIMATED, gif(2));
    }

    @Test
    public void unknownBytesStayUnknown() throws Exception {
        assertKind(PackCompatibilityPlanner.SourceKind.UNKNOWN, bytes(1, 2, 3, 4, 5, 6));
    }

    private void assertKind(PackCompatibilityPlanner.SourceKind expected, byte[] data) throws Exception {
        assertEquals(expected, MediaStructureDetector.detect(new ByteArrayInputStream(data)));
    }

    private ByteArrayOutputStream webpHeader() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.write(new byte[]{0, 0, 0, 0});
        out.write("WEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return out;
    }

    private void chunk(ByteArrayOutputStream out, String fourCc, byte[] data) throws Exception {
        out.write(fourCc.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        int size = data.length;
        out.write(size & 0xff);
        out.write((size >>> 8) & 0xff);
        out.write((size >>> 16) & 0xff);
        out.write((size >>> 24) & 0xff);
        out.write(data);
        if ((size & 1) != 0) out.write(0);
    }

    private byte[] gif(int frameCount) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("GIF89a".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.write(new byte[]{1, 0, 1, 0, 0, 0, 0});
        for (int i = 0; i < frameCount; i++) {
            out.write(0x2c);
            out.write(new byte[]{0, 0, 0, 0, 1, 0, 1, 0, 0});
            out.write(2);
            out.write(1);
            out.write(0);
            out.write(0);
        }
        out.write(0x3b);
        return out.toByteArray();
    }

    private byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }
}
