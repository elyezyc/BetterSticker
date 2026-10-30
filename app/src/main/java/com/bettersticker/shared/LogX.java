package com.bettersticker.shared;

import android.util.Log;

import de.robv.android.xposed.XposedBridge;

/**
 * 统一日志。LSPosed 日志里带 [BetterSticker] 前缀，方便过滤。
 * 同时写 logcat，真机抓日志时两条通道都能看到。
 */
public final class LogX {

    private static final String TAG = "BetterSticker";
    private static volatile boolean verbose = true;

    private LogX() {
    }

    public static void setVerbose(boolean v) {
        verbose = v;
    }

    public static void i(String msg) {
        Log.i(TAG, msg);
        try {
            XposedBridge.log("[" + Env.MODULE_TAG + "] " + msg);
        } catch (Throwable ignored) {
            // 单元测试/非 Xposed 环境
        }
    }

    public static void w(String msg) {
        Log.w(TAG, msg);
        try {
            XposedBridge.log("[" + Env.MODULE_TAG + "][W] " + msg);
        } catch (Throwable ignored) {
        }
    }

    public static void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
        try {
            XposedBridge.log("[" + Env.MODULE_TAG + "][E] " + msg + "\n" + Log.getStackTraceString(t));
        } catch (Throwable ignored) {
        }
    }

    /** 探针日志可能很频繁，用开关控制。 */
    public static void probe(String msg) {
        if (!verbose) {
            return;
        }
        try {
            XposedBridge.log("[" + Env.MODULE_TAG + "][probe] " + msg);
        } catch (Throwable ignored) {
        }
    }
}
