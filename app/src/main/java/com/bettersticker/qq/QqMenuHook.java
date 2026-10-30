package com.bettersticker.qq;

import android.app.Activity;
import android.view.View;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.bettersticker.core.HookRuntime;
import com.bettersticker.shared.LogX;
import com.bettersticker.shared.hook.XHooker;
import com.bettersticker.hook.ActivityTracker;

/**
 * 捕获 QQ 长按消息的时机。
 *
 * MenuWrapper.e(ui.c, AIOMsgItem, View) 是 QQ 9.3.75.42350 里构建消息长按菜单的方法
 * （参数类型已核对）。Hook 它拿到 AIOMsgItem，再从 MsgRecord 里读本地媒体路径。
 * 找不到方法时只记日志，模块其余功能不受影响。
 */
public final class QqMenuHook {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicLong LAST_MS = new AtomicLong();
    private static volatile String STATUS = "未安装";
    private static final AtomicLong CALLS = new AtomicLong();
    private static final QqMsgRecordAdapter ADAPTER = new QqMsgRecordAdapter();

    private QqMenuHook() {
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            Class<?> cls = HookRuntime.findClass(QqNames.MENU_WRAPPER);
            if (cls == null) {
                STATUS = "未找到 MenuWrapper";
                LogX.w("MenuWrapper 不存在，长按消息收藏不可用（面板仍可用）");
                return;
            }
            Method target = null;
            int itemIndex = -1;
            int viewIndex = -1;
            for (Method m : cls.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3
                        && p[0].getName().equals(QqNames.MENU_UI_MODEL)
                        && p[1].getName().equals(QqNames.AIO_MSG_ITEM)
                        && p[2].getName().equals("android.view.View")) {
                    target = m;
                    itemIndex = 1;
                    viewIndex = 2;
                    break;
                }
            }
            // 回退：不写死方法名/第一个参数，只要求参数里有 AIOMsgItem + View。
            if (target == null) {
                for (Method m : cls.getDeclaredMethods()) {
                    Class<?>[] p = m.getParameterTypes();
                    int ii = -1;
                    int vi = -1;
                    for (int i = 0; i < p.length; i++) {
                        if (p[i].getName().equals(QqNames.AIO_MSG_ITEM)) {
                            ii = i;
                        } else if (p[i].getName().equals("android.view.View")) {
                            vi = i;
                        }
                    }
                    if (ii >= 0 && vi >= 0) {
                        target = m;
                        itemIndex = ii;
                        viewIndex = vi;
                        LogX.w("MenuWrapper 精确签名未命中，回退到 #" + m.getName());
                        break;
                    }
                }
            }
            if (target == null) {
                STATUS = "未找到 e(ui.c, AIOMsgItem, View)";
                LogX.w("未找到 MenuWrapper(ui.c, AIOMsgItem, View) 方法，长按消息收藏不可用");
                return;
            }
            final int itemPos = itemIndex;
            final int viewPos = viewIndex;
            final Method hooked = target;
            try {
                hooked.setAccessible(true);
            } catch (Throwable ignored) {
            }
            XHooker.hookAfter(hooked, param -> {
                Object item = param.args.length > itemPos ? param.args[itemPos] : null;
                View view = param.args.length > viewPos && param.args[viewPos] instanceof View
                        ? (View) param.args[viewPos] : null;
                if (item == null) {
                    return;
                }
                if (param.args.length > 0) {
                    QqMenuItemFactory.addToMenu(param.args[0], item);
                }
                long now = System.currentTimeMillis();
                long last = LAST_MS.get();
                if (now - last < 800) {
                    return;
                }
                LAST_MS.set(now);
                CALLS.incrementAndGet();
                LogX.i("长按消息: " + QqMsgRecordAdapter.describe(item));
                new Thread(() -> extractAndShow(item, view), "BetterSticker-qq-menu").start();
            });
            STATUS = "已安装 #" + hooked.getName();
            LogX.i("已 Hook MenuWrapper#" + hooked.getName()
                    + " 参数=" + java.util.Arrays.toString(hooked.getParameterTypes()));
        } catch (Throwable t) {
            STATUS = "安装失败: " + t;
            LogX.e("安装 QqMenuHook 失败", t);
        }
    }

    private static void extractAndShow(Object item, View view) {
        try {
            // 原生菜单项与顶部浮动按钮同时保留：原生项若因菜单刷新时机没显示，
            // 用户仍能看到“收藏到 BetterSticker”。
            List<QqMedia> medias = ADAPTER.extract(item);
            if (medias.isEmpty()) {
                LogX.probe("长按消息未发现可收藏的本地媒体");
                return;
            }
            Activity activity = view != null ? QqSaveChip.activityOf(view) : ActivityTracker.current();
            if (activity == null) {
                LogX.w("找不到 Activity，无法显示收藏按钮");
                return;
            }
            QqSaveChip.show(activity, medias, view);
        } catch (Throwable t) {
            LogX.e("提取 QQ 媒体失败", t);
        }
    }

    public static String status() {
        return STATUS + "，调用=" + CALLS.get();
    }
}
