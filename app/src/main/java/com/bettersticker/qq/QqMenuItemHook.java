package com.bettersticker.qq;

import android.view.View;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.bettersticker.core.HookRuntime;
import com.bettersticker.shared.LogX;
import com.bettersticker.shared.hook.XHooker;

/**
 * 把“收藏到 BetterSticker”作为原生条目插入 QQ 消息长按菜单。
 *
 * 参考 TCQT 的做法：BaseContentComponent 有一个无参、返回 List 的抽象方法，
 * 各内容组件（图片/语音/商城表情等）实现它来返回菜单项列表。Hook 该方法后，
 * 用 QqMenuItem（编译期针对 QQ 9.3.75 的 ui.e 桩）追加一个原生菜单项。
 *
 * 不依赖 MenuWrapper 的菜单构建细节；失败则回退到 QqMenuHook 的浮动按钮。
 */
public final class QqMenuItemHook {

    private static final String BASE_COMPONENT =
            "com.tencent.mobileqq.aio.msglist.holder.component.BaseContentComponent";
    private static final String AIO_MSG_ITEM =
            "com.tencent.mobileqq.aio.msg.AIOMsgItem";
    private static final String[] TARGETS = {
            "com.tencent.mobileqq.aio.msglist.holder.component.text.AIOTextContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.mix.AIOMixContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.multipci.AIOMultiPicContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.pic.AIOPicContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.pic.AIOMultiPicContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.ptt.AIOPttContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.marketface.AIOMarketFaceComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.anisticker.AIOAniStickerContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.sysface.AIOSingleSysFaceContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.facebubble.AIOFaceBubbleContentComponent",
            "com.tencent.mobileqq.aio.msglist.holder.component.flashpic.AIOFlashPicContentComponent"
    };

    private static final int MENU_ID = 0x0B500001;
    private static final String MENU_TEXT = "收藏到 BetterSticker";
    private static final AtomicLong ADDED = new AtomicLong();
    private static volatile String STATUS = "未安装";

    private QqMenuItemHook() {
    }

    /** @return true 表示已成功挂上至少一个原生菜单组件。 */
    public static boolean install() {
        try {
            // QqMenuItem 可能在 host ClassLoader 或模块 ClassLoader；优先 host。
            // 不要在安装期强制 Class.forName(e)，否则会把 host 可见性误判成失败。
        } catch (Throwable ignored) {
        }
        try {
            Class<?> base = HookRuntime.findClass(BASE_COMPONENT);
            if (base == null) {
                STATUS = "未找到 BaseContentComponent";
                return false;
            }
            Method getMsg = null;
            String listMethodName = null;
            for (Method m : base.getDeclaredMethods()) {
                if (getMsg == null && m.getParameterCount() == 0
                        && m.getReturnType().getName().equals(AIO_MSG_ITEM)) {
                    getMsg = m;
                }
                if (listMethodName == null && Modifier.isAbstract(m.getModifiers())
                        && m.getParameterCount() == 0
                        && List.class.isAssignableFrom(m.getReturnType())) {
                    listMethodName = m.getName();
                }
            }
            if (getMsg == null || listMethodName == null) {
                STATUS = "BaseContentComponent 结构不符（getMsg/List）";
                LogX.w(STATUS);
                return false;
            }
            try {
                getMsg.setAccessible(true);
            } catch (Throwable ignored) {
            }
            final Method msgMethod = getMsg;
            final String listName = listMethodName;
            Class<?> loaded = null;
            try {
                loaded = com.bettersticker.shared.HostDex.load(
                        com.bettersticker.shared.Env.appContext,
                        "qq-menu-item.dex", "com.bettersticker.qq.QqMenuItem");
            } catch (Throwable t) {
                LogX.w("HostDex 加载 QqMenuItem 失败: " + t);
            }
            final Class<?> itemClass = loaded != null ? loaded
                    : loadModuleClass("com.bettersticker.qq.QqMenuItem");
            if (itemClass == null) {
                STATUS = "QqMenuItem 类不可见";
                return false;
            }
            final Method itemCreate;
            try {
                itemCreate = itemClass.getMethod("create", Object.class, int.class,
                        int.class, String.class, Runnable.class);
            } catch (Throwable t) {
                STATUS = "QqMenuItem.create 不可用: " + t;
                return false;
            }
            int hooked = 0;
            for (String name : TARGETS) {
                Class<?> cls = HookRuntime.findClass(name);
                if (cls == null) {
                    continue;
                }
                Method listMethod = findDeclaredNoArg(cls, listName);
                if (listMethod == null) {
                    LogX.w("菜单组件没有菜单列表方法: " + name + "#" + listName);
                    continue;
                }
                try {
                    listMethod.setAccessible(true);
                } catch (Throwable ignored) {
                }
                XHooker.hookAfter(listMethod, param -> {
                    try {
                        Object msg = Modifier.isStatic(msgMethod.getModifiers())
                                ? msgMethod.invoke(null) : msgMethod.invoke(param.obj);
                        if (msg == null) {
                            return;
                        }
                        if (QqMenuItemFactory.added() > 0) {
                            return;
                        }
                        Object result = param.getResult();
                        if (!(result instanceof List)) {
                            return;
                        }
                        @SuppressWarnings("unchecked")
                        List<Object> list = (List<Object>) result;
                        for (Object o : list) {
                            if (o != null && o.getClass().getName()
                                    .equals("com.bettersticker.qq.QqMenuItem")) {
                                return;
                            }
                        }
                        Object item = itemCreate.invoke(null, msg,
                                android.R.drawable.ic_menu_save, MENU_ID, MENU_TEXT,
                                (Runnable) () -> QqMenuActions.saveFromMenu(msg));
                        try {
                            list.add(item);
                        } catch (Throwable immutable) {
                            List<Object> copy = new ArrayList<>(list);
                            copy.add(item);
                            param.setResult(copy);
                        }
                        ADDED.incrementAndGet();
                        LogX.i("已插入原生长按菜单项: " + name);
                    } catch (Throwable t) {
                        LogX.e("插入原生菜单项失败: " + name, t);
                    }
                });
                hooked++;
            }
            STATUS = hooked > 0 ? ("已安装 " + hooked + " 个组件") : "未找到目标消息组件";
            LogX.i("QqMenuItemHook: " + STATUS + "，列表方法=" + listName);
            return hooked > 0;
        } catch (Throwable t) {
            STATUS = "安装失败: " + t;
            LogX.e("QqMenuItemHook 安装失败", t);
            return false;
        }
    }

    public static String status() {
        return STATUS + "，已插入=" + ADDED.get();
    }

    public static boolean hasAdded() {
        return ADDED.get() > 0;
    }

    private static Class<?> loadModuleClass(String name) {
        ClassLoader[] loaders = {
                com.bettersticker.shared.Env.appClassLoader,
                QqMenuItemHook.class.getClassLoader()
        };
        for (ClassLoader cl : loaders) {
            if (cl == null) {
                continue;
            }
            try {
                return Class.forName(name, false, cl);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Method findDeclaredNoArg(Class<?> cls, String name) {
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) {
                return m;
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static boolean isView(Object o) {
        return o instanceof View;
    }
}
