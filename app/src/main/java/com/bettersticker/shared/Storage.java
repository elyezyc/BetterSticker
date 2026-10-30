package com.bettersticker.shared;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 模块数据目录：优先 /sdcard/Android/media/com.tencent.mobileqq/.bs。
 *
 * 这是 QQ 的 app-specific 外部媒体目录，QQ 进程读写不需要存储权限；
 * 用户用文件管理器也能访问。外部存储不可用时退回内部 files/bettersticker。
 *
 * 首次启动会把旧版内部目录迁移过来（只复制，不删旧数据）。
 */
public final class Storage {

    public static final String DIR_NAME = ".bs";

    private Storage() {
    }

    public static File dataDir(Context context) {
        File target = external(context);
        if (target != null) {
            if (target.exists() || target.mkdirs()) {
                return target;
            }
        }
        File fallback = new File(context.getFilesDir(), "bettersticker");
        //noinspection ResultOfMethodCallIgnored
        fallback.mkdirs();
        LogX.w("外部 media 目录不可用，回退内部目录: " + fallback);
        return fallback;
    }

    private static File external(Context context) {
        try {
            File[] dirs = context.getExternalMediaDirs();
            if (dirs != null) {
                for (File d : dirs) {
                    if (d == null) {
                        continue;
                    }
                    return new File(d, DIR_NAME);
                }
            }
            File root = Environment.getExternalStorageDirectory();
            if (root != null) {
                return new File(root, "Android/media/" + context.getPackageName() + "/" + DIR_NAME);
            }
        } catch (Throwable t) {
            LogX.w("获取外部 media 目录失败: " + t);
        }
        return null;
    }

    public static void migrateOldInternal(Context context, File target) {
        try {
            File old = new File(context.getFilesDir(), "bettersticker");
            if (!old.isDirectory() || old.getCanonicalPath().equals(target.getCanonicalPath())) {
                return;
            }
            int n = copyTree(old, target);
            if (n > 0) {
                LogX.i("已从旧内部目录迁移 " + n + " 个文件到 " + target);
            }
        } catch (Throwable t) {
            LogX.w("迁移旧目录失败: " + t);
        }
    }

    private static int copyTree(File srcDir, File dstDir) {
        File[] files = srcDir.listFiles();
        if (files == null) {
            return 0;
        }
        if (!dstDir.exists() && !dstDir.mkdirs()) {
            return 0;
        }
        int n = 0;
        for (File src : files) {
            File dst = new File(dstDir, src.getName());
            if (src.isDirectory()) {
                n += copyTree(src, dst);
            } else if (!dst.exists()) {
                try {
                    copyFile(src, dst);
                    n++;
                } catch (IOException e) {
                    LogX.w("迁移文件失败: " + src + " -> " + dst + " " + e);
                }
            }
        }
        return n;
    }

    private static void copyFile(File src, File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建 " + parent);
        }
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        }
    }
}
