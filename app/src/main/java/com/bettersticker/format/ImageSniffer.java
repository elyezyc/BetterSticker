package com.bettersticker.format;

/**
 * 只按文件签名识别格式，不对未知容器做任何猜测/解密。
 * 识别不了的内容一律 UNKNOWN，由上层拒绝或记录。
 */
public final class ImageSniffer {

    public enum Format {
        PNG("png", "image/png", true),
        JPEG("jpg", "image/jpeg", true),
        GIF("gif", "image/gif", true),
        WEBP("webp", "image/webp", true),
        SILK("silk", "audio/silk", false),
        UNKNOWN("bin", "application/octet-stream", false);

        public final String extension;
        public final String mime;
        public final boolean image;

        Format(String extension, String mime, boolean image) {
            this.extension = extension;
            this.mime = mime;
            this.image = image;
        }
    }

    private ImageSniffer() {
    }

    public static Format detect(byte[] data) {
        if (data == null || data.length < 4) {
            return Format.UNKNOWN;
        }
        if (isPng(data)) {
            return Format.PNG;
        }
        if (isJpeg(data)) {
            return Format.JPEG;
        }
        if (isGif(data)) {
            return Format.GIF;
        }
        if (isWebp(data)) {
            return Format.WEBP;
        }
        if (isSilk(data)) {
            return Format.SILK;
        }
        return Format.UNKNOWN;
    }

    public static boolean isPng(byte[] d) {
        return d.length >= 8
                && (d[0] & 0xFF) == 0x89 && d[1] == 0x50 && d[2] == 0x4E && d[3] == 0x47
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A;
    }

    public static boolean isJpeg(byte[] d) {
        return d.length >= 3
                && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF;
    }

    public static boolean isGif(byte[] d) {
        if (d.length < 6) {
            return false;
        }
        String s = new String(d, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
        return "GIF87a".equals(s) || "GIF89a".equals(s);
    }

    public static boolean isWebp(byte[] d) {
        if (d.length < 12) {
            return false;
        }
        return d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
    }

    /** SILK v3 的两种常见开头：直接 "#!SILK_V3"，或 0x02 + "#!SILK_V3"。 */
    public static boolean isSilk(byte[] d) {
        if (d.length >= 9 && startsWith(d, 0, "#!SILK_V3")) {
            return true;
        }
        return d.length >= 10 && (d[0] & 0xFF) == 0x02 && startsWith(d, 1, "#!SILK_V3");
    }

    private static boolean startsWith(byte[] d, int offset, String ascii) {
        byte[] b = ascii.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (d.length < offset + b.length) {
            return false;
        }
        for (int i = 0; i < b.length; i++) {
            if (d[offset + i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    public static String hexPrefix(byte[] d, int max) {
        if (d == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        int n = Math.min(max, d.length);
        for (int i = 0; i < n; i++) {
            sb.append(String.format(java.util.Locale.US, "%02X", d[i] & 0xFF));
            if (i + 1 < n) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
