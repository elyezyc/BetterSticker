package com.bettersticker.format;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ImageSnifferTest {

    @Test
    public void detectsPng() {
        byte[] d = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        assertEquals(ImageSniffer.Format.PNG, ImageSniffer.detect(d));
        assertTrue(ImageSniffer.detect(d).image);
    }

    @Test
    public void detectsGif87aAnd89a() {
        assertEquals(ImageSniffer.Format.GIF,
                ImageSniffer.detect("GIF87a....".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(ImageSniffer.Format.GIF,
                ImageSniffer.detect("GIF89a....".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    public void detectsWebp() {
        byte[] d = new byte[16];
        d[0] = 'R';
        d[1] = 'I';
        d[2] = 'F';
        d[3] = 'F';
        d[8] = 'W';
        d[9] = 'E';
        d[10] = 'B';
        d[11] = 'P';
        assertEquals(ImageSniffer.Format.WEBP, ImageSniffer.detect(d));
    }

    @Test
    public void detectsJpeg() {
        byte[] d = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
        assertEquals(ImageSniffer.Format.JPEG, ImageSniffer.detect(d));
    }

    @Test
    public void detectsSilkWithAndWithoutPrefix() {
        byte[] a = "#!SILK_V3".getBytes(StandardCharsets.US_ASCII);
        assertEquals(ImageSniffer.Format.SILK, ImageSniffer.detect(a));

        byte[] b = new byte[a.length + 1];
        b[0] = 0x02;
        System.arraycopy(a, 0, b, 1, a.length);
        assertEquals(ImageSniffer.Format.SILK, ImageSniffer.detect(b));
        assertFalse(ImageSniffer.detect(b).image);
    }

    @Test
    public void unknownStaysUnknown() {
        byte[] d = "not an image".getBytes(StandardCharsets.US_ASCII);
        assertEquals(ImageSniffer.Format.UNKNOWN, ImageSniffer.detect(d));
        assertFalse(ImageSniffer.detect(d).image);
    }
}
