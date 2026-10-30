package com.bettersticker.core;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import com.bettersticker.hook.ActivityTracker;
import com.bettersticker.hook.LongPressHook;
import com.bettersticker.hook.TriggerRegistry;
import com.bettersticker.qq.QqMenuHook;
import com.bettersticker.imports.ActivityResultHook;
import com.bettersticker.qq.QqMenuItemHook;
import com.bettersticker.store.FavoriteStore;
import com.bettersticker.ui.PanelController;
import com.bettersticker.shared.Env;
import com.bettersticker.shared.LogX;
import com.bettersticker.shared.hook.XHooker;

/**
 * 进程内初始化。
 *
 * 用 Instrumentation 的框架回调拿 Application/Activity：
 * - callApplicationOnCreate 比 Application.onCreate 更可靠（不依赖 QQ 是否 super）
 * - callActivityOnResume 能拿到当前 Activity，用于后续弹面板
 */
public final class Bootstrap {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean APP_READY = new AtomicBoolean();

    private Bootstrap() {
    }

    public static void install(ClassLoader classLoader) throws Throwable {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        HookRuntime.setClassLoader(classLoader);

        Method onAppCreate = Instrumentation.class.getDeclaredMethod(
                "callApplicationOnCreate", Application.class);
        XHooker.hookAfter(onAppCreate, param -> {
            Application app = (Application) param.args[0];
            if (app != null) {
                onAppCreated(app);
            }
        });

        Method onResume = Instrumentation.class.getDeclaredMethod(
                "callActivityOnResume", Activity.class);
        XHooker.hookAfter(onResume, param -> {
            Activity activity = (Activity) param.args[0];
            if (activity != null) {
                ActivityTracker.onResume(activity);
                if (!APP_READY.get()) {
                    // 模块注入较晚时的兜底：Application 已经存在。
                    onAppCreated(activity.getApplication());
                }
            }
        });

        Method onPause = Instrumentation.class.getDeclaredMethod(
                "callActivityOnPause", Activity.class);
        XHooker.hookAfter(onPause, param -> {
            Activity activity = (Activity) param.args[0];
            if (activity != null) {
                ActivityTracker.onPause(activity);
            }
        });

        // 第二条 Activity resume 通道：有些 ROM/QQ 版本对 Instrumentation 的调用链
        // 不完全一样，用 Activity.onResume 兜底（只影响生命周期记录，不改变 QQ 行为）。
        try {
            Method activityResume = Activity.class.getDeclaredMethod("onResume");
            XHooker.hookAfter(activityResume, param -> {
                if (param.obj instanceof Activity) {
                    Activity a = (Activity) param.obj;
                    ActivityTracker.onResume(a);
                    if (!APP_READY.get()) {
                        onAppCreated(a.getApplication());
                    }
                }
            });
            LogX.i("Bootstrap 已安装 Activity.onResume 兜底");
        } catch (Throwable t) {
            LogX.w("Activity.onResume 兜底安装失败: " + t);
        }

        // 从后台/分屏回来时，个别 ROM 只触发 window focus，不重新走 onResume。
        try {
            Method focus = Activity.class.getDeclaredMethod(
                    "onWindowFocusChanged", boolean.class);
            XHooker.hookAfter(focus, param -> {
                if (param.obj instanceof Activity
                        && param.args.length > 0 && Boolean.TRUE.equals(param.args[0])) {
                    Activity a = (Activity) param.obj;
                    if (ActivityTracker.current() != a) {
                        ActivityTracker.onResume(a);
                    } else {
                        // 同一 Activity 重新拿焦点，也重新扫描一遍输入条。
                        TriggerRegistry.scanActivity(a);
                    }
                }
            });
            LogX.i("Bootstrap 已安装 Activity.onWindowFocusChanged 兜底");
        } catch (Throwable t) {
            LogX.w("Activity.onWindowFocusChanged 兜底安装失败: " + t);
        }

        LogX.i("Bootstrap 已安装 Instrumentation Hook");
    }

    private static void onAppCreated(Application app) {
        if (!APP_READY.compareAndSet(false, true)) {
            return;
        }
        Env.appContext = app;
        LogX.i("Application 就绪: " + app.getPackageName()
                + " filesDir=" + app.getFilesDir());

        // 各步骤独立 try/catch：某一步失败（例如磁盘/反射）不能拖垮面板与长按。
        try {
            Config.init(app);
        } catch (Throwable t) {
            LogX.e("Config.init 失败", t);
        }
        try {
            FavoriteStore.init(app);
        } catch (Throwable t) {
            LogX.e("FavoriteStore.init 失败", t);
        }
        try {
            PanelController.init(app);
        } catch (Throwable t) {
            LogX.e("PanelController.init 失败", t);
        }

        try {
            ActivityResultHook.install();
        } catch (Throwable t) {
            LogX.e("ActivityResultHook.install 失败", t);
        }
        try {
            QqMenuItemHook.install();
            // 原生菜单项与浮动按钮都装；QqMenuHook 在原生项已插入时会自动跳过，
            // 只有原生项没插进去时才显示浮动按钮。
            QqMenuHook.install();
        } catch (Throwable t) {
            LogX.e("菜单 Hook 安装失败，回退 QqMenuHook", t);
            try {
                QqMenuHook.install();
            } catch (Throwable ignored) {
            }
        }
        try {
            LongPressHook.install();
        } catch (Throwable t) {
            LogX.e("LongPressHook.install 失败", t);
        }
        try {
            DebugReceiver.register(app);
        } catch (Throwable t) {
            LogX.e("DebugReceiver.register 失败", t);
        }
        LogX.i("模块初始化完成；收藏目录=" + Env.moduleDataDir().getAbsolutePath());
    }

    /** 供调试接收器判断是否已完成初始化。 */
    public static boolean isAppReady() {
        return APP_READY.get();
    }

    /** 当前 Activity 的装饰 View（探针用）。 */
    public static View currentDecorView() {
        Activity a = ActivityTracker.current();
        return a == null ? null : a.getWindow().getDecorView();
    }
}
