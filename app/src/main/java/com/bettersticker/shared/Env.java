package com.bettersticker.shared;

import android.content.Context;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 进程级全局状态。
 *
 * 说明：本模块运行在 QQ 进程里（Xposed 的 classloader 注入），
 * 所以 getFilesDir() 等拿到的是 QQ 的目录，而不是模块自己的 /data/data/com.bettersticker。
 * 收藏数据因此落在 QQ 私有目录 files/bettersticker/ 下，细节见 README。
 */
public final class Env {

    public static final String MODULE_TAG = "BetterSticker";
    public static final String TARGET_PACKAGE = "com.tencent.mobileqq";

    public static volatile boolean initialized;
    public static volatile Context appContext;

    public static String packageName = TARGET_PACKAGE;
    public static String processName = TARGET_PACKAGE;
    public static String dataDir;
    public static String modulePath;
    public static ClassLoader appClassLoader;
    public static int sdkInt = 29;

    private Env() {
    }

    public static void init(XC_LoadPackage.LoadPackageParam lp) {
        packageName = lp.packageName;
        processName = lp.processName;
        appClassLoader = lp.classLoader;
        if (lp.appInfo != null) {
            dataDir = lp.appInfo.dataDir;
        }
        sdkInt = android.os.Build.VERSION.SDK_INT;
        initialized = true;
    }

    /** 模块数据目录：优先 /sdcard/Android/media/com.tencent.mobileqq/.bs。 */
    public static java.io.File moduleDataDir() {
        Context c = appContext;
        if (c == null) {
            throw new IllegalStateException("appContext 尚未初始化");
        }
        return Storage.dataDir(c);
    }
}
