package com.bettersticker.core;

import java.lang.reflect.Method;

import com.bettersticker.shared.Env;
import com.bettersticker.shared.LogX;

/** 反射工具：QQ 的类名/方法名集中在这里查，失败只降级不崩溃。 */
public final class HookRuntime {

    private static volatile ClassLoader classLoader;

    private HookRuntime() {
    }

    public static void setClassLoader(ClassLoader cl) {
        classLoader = cl;
    }

    public static Class<?> findClass(String name) {
        ClassLoader cl = classLoader;
        if (cl != null) {
            try {
                return Class.forName(name, false, cl);
            } catch (Throwable ignored) {
            }
        }
        cl = Env.appClassLoader;
        if (cl != null) {
            try {
                return Class.forName(name, false, cl);
            } catch (Throwable ignored) {
            }
        }
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            LogX.w("找不到类: " + name + " (" + t + ")");
            return null;
        }
    }

    public static Method findMethod(Class<?> cls, String name, String... paramTypeNames) {
        if (cls == null) {
            return null;
        }
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                if (name != null && !name.equals(m.getName())) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if (p.length != paramTypeNames.length) {
                    continue;
                }
                boolean ok = true;
                for (int i = 0; i < p.length; i++) {
                    if (!p[i].getName().equals(paramTypeNames[i])) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {
                    }
                    return m;
                }
            }
            c = c.getSuperclass();
            // 只向上找腾讯自己的基类。再往上就是 Activity/Fragment 框架方法，
            // 绝不能拿来 Hook（否则所有 Activity 都会被当成设置页）。
            if (c != null && !c.getName().startsWith("com.tencent.")) {
                break;
            }
        }
        return null;
    }

    public static Method findMethodBySignature(Class<?> cls, String... paramTypeNames) {
        return findMethod(cls, null, paramTypeNames);
    }
}
