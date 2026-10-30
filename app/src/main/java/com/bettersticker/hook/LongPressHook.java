package com.bettersticker.hook;

import android.view.View;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.bettersticker.core.Config;
import com.bettersticker.core.HookRuntime;
import com.bettersticker.core.Probe;
import com.bettersticker.core.TriggerConfig;
import com.bettersticker.shared.LogX;
import com.bettersticker.shared.hook.XHooker;
import com.bettersticker.ui.PanelController;

/**
 * 全局长按检测：
 * 1) 资源名命中（TriggerRegistry）→ 打开对应面板；
 * 2) 内容描述命中（TriggerConfig）→ 打开对应面板；
 * 3) 其他长按 → 只记录探针链，不改变 QQ 行为。
 */
public final class LongPressHook {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicLong LAST = new AtomicLong();
    private static final AtomicLong CALLS = new AtomicLong();
    private static final AtomicLong TRIGGERED = new AtomicLong();
    private static volatile String STATUS = "未安装";

    private LongPressHook() {
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            TriggerRegistry.install();
            Method perform = HookRuntime.findMethod(View.class, "performLongClick");
            if (perform == null) {
                LogX.w("找不到 View.performLongClick，长按面板不可用");
                return;
            }
            XHooker.hookAfter(perform, param -> {
                if (!(param.obj instanceof View)) {
                    return;
                }
                View v = (View) param.obj;
                CALLS.incrementAndGet();
                Probe.onLongClick(v);
                String tab = TriggerRegistry.tabOf(v);
                if (tab == null) {
                    tab = TriggerConfig.get().matchTab(v);
                }
                if (tab == null) {
                    tab = TriggerRegistry.matchOnTheFly(v);
                }
                if (tab == null) {
                    return;
                }
                long now = System.currentTimeMillis();
                long last = LAST.get();
                if (now - last < 500) {
                    return;
                }
                LAST.set(now);
                TRIGGERED.incrementAndGet();
                param.setResult(true);
                LogX.i("长按命中触发器 tab=" + tab + " view=" + v.getClass().getSimpleName()
                        + " desc=" + v.getContentDescription());
                PanelController.showFor(v, tab);
            });
            STATUS = "已安装";
            LogX.i("LongPressHook 已安装");
        } catch (Throwable t) {
            STATUS = "安装失败: " + t;
            LogX.e("LongPressHook 安装失败", t);
        }
    }

    public static String status() {
        return STATUS + "，长按调用=" + CALLS.get() + "，命中=" + TRIGGERED.get();
    }
}
