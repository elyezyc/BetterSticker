package com.bettersticker.export;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.bettersticker.shared.LogX;

/**
 * 通过 MediaStore.Downloads 导出到 /sdcard/Download/BetterSticker/。
 *
 * 为什么不用 File API：targetSdk 34 + scoped storage，QQ 进程没有 WRITE_EXTERNAL_STORAGE，
 * 直接写 /sdcard/Download 会失败；MediaStore 插入自己的文件不需要存储权限。
 */
public final class MediaStoreExporter {

    public static final String RELATIVE_DIR = Environment.DIRECTORY_DOWNLOADS + "/BetterSticker";

    private final Context context;

    public MediaStoreExporter(Context context) {
        this.context = context.getApplicationContext();
    }

    public Uri exportBytes(String displayName, String mime, byte[] data) throws IOException {
        ContentResolver cr = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, sanitize(displayName));
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = null;
        try {
            uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("MediaStore.insert 返回 null（可能被系统/厂商限制）");
            }
            try (OutputStream out = cr.openOutputStream(uri)) {
                if (out == null) {
                    throw new IOException("openOutputStream 返回 null");
                }
                out.write(data);
                out.flush();
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, done, null, null);
            LogX.i("已导出 " + displayName + " -> " + RELATIVE_DIR + " (" + data.length + "B)");
            return uri;
        } catch (Throwable t) {
            if (uri != null) {
                try {
                    cr.delete(uri, null, null);
                } catch (Throwable ignored) {
                }
            }
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new IOException("导出失败: " + displayName, t);
        }
    }

    public Uri exportFile(String displayName, String mime, File file) throws IOException {
        byte[] data;
        try (FileInputStream in = new FileInputStream(file)) {
            data = new byte[(int) file.length()];
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) {
                    break;
                }
                off += n;
            }
        }
        return exportBytes(displayName, mime, data);
    }

    public Uri exportText(String displayName, String text) throws IOException {
        return exportBytes(displayName, "application/json",
                text.getBytes(StandardCharsets.UTF_8));
    }

    public static String sanitize(String name) {
        String s = name == null ? "" : name.trim();
        s = s.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_");
        if (s.length() > 120) {
            int dot = s.lastIndexOf('.');
            if (dot > 0 && s.length() - dot <= 8) {
                s = s.substring(0, 110) + s.substring(dot);
            } else {
                s = s.substring(0, 120);
            }
        }
        return s.isEmpty() ? "bettersticker.bin" : s;
    }
}
