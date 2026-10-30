package com.bettersticker.format;

import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WavWriterTest {

    @Test
    public void writesStandardPcm16MonoHeader() throws Exception {
        int rate = 48000;
        int frames = rate; // 1 秒
        byte[] pcm = WavWriter.sinePcm16(rate, frames, 440.0, 0.5);
        assertEquals(frames * 2, pcm.length);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WavWriter.write(out, pcm, rate, 1);
        byte[] wav = out.toByteArray();
        assertEquals(44 + pcm.length, wav.length);

        assertEquals('R', wav[0]);
        assertEquals('I', wav[1]);
        assertEquals('F', wav[2]);
        assertEquals('F', wav[3]);
        assertEquals('W', wav[8]);
        assertEquals('A', wav[9]);
        assertEquals('V', wav[10]);
        assertEquals('E', wav[11]);

        // fmt chunk
        assertEquals(16, le32(wav, 16));
        assertEquals(1, le16(wav, 20));       // PCM
        assertEquals(1, le16(wav, 22));       // mono
        assertEquals(rate, le32(wav, 24));
        assertEquals(rate * 2, le32(wav, 28)); // byte rate
        assertEquals(2, le16(wav, 32));       // block align
        assertEquals(16, le16(wav, 34));      // bits

        assertEquals(pcm.length, le32(wav, 40));
        assertEquals(36 + pcm.length, le32(wav, 4));
        assertTrue(wav.length > 44);
    }

    @Test
    public void rejectsInvalidChannels() {
        try {
            WavWriter.write(new ByteArrayOutputStream(), new byte[0], 48000, 3);
        } catch (IllegalArgumentException expected) {
            return;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("应当拒绝 3 声道");
    }

    private static int le16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static int le32(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }
}
