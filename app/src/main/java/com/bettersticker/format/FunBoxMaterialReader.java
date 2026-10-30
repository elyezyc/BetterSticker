package com.bettersticker.format;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 原版 FunBox 素材读取器。
 *
 * 现状：数据格式未确认，所以默认拒绝读取，不做以下事情：
 * - 不写死 /data/adb/funbox/xxx 素材路径；
 * - 不猜文件名/索引 schema；
 * - 不猜图片加密方式。
 *
 * 如果真机验证后确认了某个目录，可把目录填进配置（funboxMaterialRoot），
 * 本类才会只读扫描该目录，并按签名识别标准 PNG/GIF/WebP/JPEG/SILK。
 * 这只是“最小可用读取”，不代表已经还原原版索引。
 */
public final class FunBoxMaterialReader implements StoredAssetSource {

    public static final String STATUS_UNVERIFIED =
            "原版素材目录/命名/索引未验证：默认不读取。真机确认后可在 trigger.json 配置 funboxMaterialRoot";

    private final File root;

    public FunBoxMaterialReader(File root) {
        this.root = root;
    }

    @Override
    public String status() {
        if (root == null) {
            return STATUS_UNVERIFIED;
        }
        return "受限读取: " + root.getAbsolutePath() + "（仅签名识别，不解析原版索引）";
    }

    @Override
    public List<StoredAsset> list() throws IOException {
        if (root == null) {
            throw new UnverifiedFormatException(STATUS_UNVERIFIED);
        }
        if (!root.isDirectory()) {
            throw new IOException("目录不存在: " + root);
        }
        List<StoredAsset> out = new ArrayList<>();
        walk(root, out, 0, "");
        return out;
    }

    private void walk(File dir, List<StoredAsset> out, int depth, String prefix) {
        if (depth > 6) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            String rel = prefix.isEmpty() ? f.getName() : prefix + "/" + f.getName();
            if (f.isDirectory()) {
                walk(f, out, depth + 1, rel);
                continue;
            }
            if (!f.isFile() || f.length() <= 0 || f.length() > 64L * 1024 * 1024) {
                continue;
            }
            try {
                byte[] head = readHead(f, 64);
                ImageSniffer.Format fmt = ImageSniffer.detect(head);
                if (fmt == ImageSniffer.Format.UNKNOWN) {
                    continue;
                }
                StoredAsset a = new StoredAsset();
                a.name = f.getName();
                a.path = f.getAbsolutePath();
                a.relativePath = rel;
                a.size = f.length();
                a.format = fmt;
                a.type = fmt == ImageSniffer.Format.SILK
                        ? com.bettersticker.store.FavoriteItem.TYPE_VOICE
                        : com.bettersticker.store.FavoriteItem.TYPE_STICKER;
                a.source = "FunBox(受限读取)";
                a.id = a.path;
                out.add(a);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public byte[] read(StoredAsset asset) throws IOException {
        if (asset == null || asset.path == null) {
            throw new IOException("空 asset");
        }
        File f = new File(asset.path);
        if (!f.isFile()) {
            throw new IOException("文件不存在: " + asset.path);
        }
        if (f.length() > 64L * 1024 * 1024) {
            throw new IOException("文件过大: " + f.length());
        }
        byte[] data = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) {
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) {
                    break;
                }
                off += n;
            }
        }
        return data;
    }

    private static byte[] readHead(File f, int n) throws IOException {
        byte[] buf = new byte[(int) Math.min(n, f.length())];
        try (FileInputStream in = new FileInputStream(f)) {
            int off = 0;
            while (off < buf.length) {
                int r = in.read(buf, off, buf.length - off);
                if (r < 0) {
                    break;
                }
                off += r;
            }
        }
        return buf;
    }
}
