package com.bettersticker.loader;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.bettersticker.core.Bootstrap;
import com.bettersticker.shared.Env;
import com.bettersticker.shared.LogX;
import com.bettersticker.shared.hook.XHooker;

/**
 * LSPosed 入口。只处理 QQ 主进程。
 *
 * v1 明确不做 Zygisk：参考目录没有 funloader.so/libhooker.so 等二进制，
 * 原始 APK 里的 so 是 FunBox 的闭源版权物，且内联 Hook ART 的实现与版本强耦合。
 */
public final class XPEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (lp == null || !Env.TARGET_PACKAGE.equals(lp.packageName)) {
            return;
        }
        // 只要主进程，避免 :MSF / :peak / :qzone 等子进程重复注入。
        if (lp.processName == null || !lp.processName.equals(lp.packageName)) {
            return;
        }
        if (Env.initialized) {
            return;
        }

        Env.init(lp);
        XHooker.setImpl(new XposedHookerImpl());
        LogX.i("注入 QQ 主进程 pid=" + android.os.Process.myPid()
                + " sdk=" + Env.sdkInt
                + " dataDir=" + Env.dataDir);

        try {
            Bootstrap.install(lp.classLoader);
        } catch (Throwable t) {
            LogX.e("Bootstrap.install 失败", t);
        }
    }
}
