package com.bettersticker.core;

import android.content.Context;

import java.io.File;

import com.bettersticker.shared.Env;
import com.bettersticker.shared.Storage;
import com.bettersticker.shared.LogX;

/** 模块配置入口；触发器配置单独在 TriggerConfig。 */
public final class Config {

    public static final String VERSION = "1.0.0";
    /** 调试广播默认开启；正式分发可改 false。 */
    public static final boolean DEBUG_RECEIVER = true;
    public static final String ACTION_PREFIX = "com.bettersticker.action.";

    private static volatile File dataDir;

    private Config() {
    }

    public static void init(Context context) {
        dataDir = Storage.dataDir(context);
        Storage.migrateOldInternal(context, dataDir);
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            LogX.w("无法创建模块数据目录: " + dataDir);
        }
        TriggerConfig.get().load(dataDir);
        LogX.i("Config v" + VERSION + " dataDir=" + dataDir);
    }

    public static File dataDir() {
        return dataDir;
    }

    public static File funboxMaterialRoot() {
        String s = TriggerConfig.get().funboxMaterialRoot;
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        return new File(s.trim());
    }

    public static String describe() {
        return "BetterSticker v" + VERSION + " / QQ " + Env.packageName;
    }
}
