package com.bettersticker.core;

import android.app.Activity;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Set;

import com.bettersticker.shared.LogX;

public final class SessionProbe {
    private SessionProbe() {
    }

    public static String dump(Activity activity) {
        try {
            Class<?> pie = HookRuntime.findClass(
                    "com.tencent.mobileqq.activity.aio.core.BaseChatPie");
            LogX.i("SessionProbe BaseChatPie class=" + (pie == null ? "null" : pie.getName()));
            if (pie != null) {
                for (Method m : pie.getDeclaredMethods()) {
                    if (m.getParameterCount() == 0) {
                        LogX.i("SessionProbe BaseChatPie method: " + m.getName() + " ret=" + m.getReturnType().getName());
                    }
                }
                for (Field f : pie.getDeclaredFields()) {
                    LogX.i("SessionProbe BaseChatPie field: " + f.getName() + " " + f.getType().getName());
                }
            }
        } catch (Throwable t) {
            LogX.e("SessionProbe BaseChatPie dump fail", t);
        }
        graph(activity);
        appDump();
        dumpEmoticonClass(activity);
        return "done";
    }

    private static void facadeDump() {
        try {
            Class<?> f = HookRuntime.findClass(
                "com.tencent.mobileqq.activity.ChatActivityFacade");
            if (f == null) { LogX.i("SessionProbe ChatActivityFacade=null"); return; }
            for (Method m : f.getDeclaredMethods()) {
                String ps = "";
                for (Class<?> p : m.getParameterTypes()) ps += p.getName() + ",";
                String r = m.getReturnType().getName();
                if (ps.contains("MessageForPic") || ps.contains("ChatMessage")
                        || r.contains("MessageForPic") || r.contains("ChatMessage")
                        || (ps.contains("SessionInfo") && ps.contains("String"))) {
                    LogX.i("SessionProbe Facade: " + m.getName() + "(" + ps + ") -> " + r);
                }
            }
        } catch (Throwable t) {
            LogX.e("SessionProbe facadeDump fail", t);
        }
    }

    private static void factoryDump() {
        try {
            Class<?> b = HookRuntime.findClass("com.tencent.common.app.b");
            LogX.i("SessionProbe factory class=" + (b == null ? "null" : b.getName()));
            if (b != null) {
                for (Field f : b.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(null);
                        LogX.i("SessionProbe factory field: " + f.getName()
                                + " " + f.getType().getSimpleName() + " = " + v);
                    } catch (Throwable ignored) {}
                }
                for (Method m : b.getDeclaredMethods()) {
                    LogX.i("SessionProbe factory method: " + m.getName()
                            + "(" + m.getParameterCount() + ") -> " + m.getReturnType().getSimpleName());
                    if (m.getParameterCount() == 1 && m.getReturnType().getName().endsWith("AppRuntime")) {
                        Class<?> bi = HookRuntime.findClass("com.tencent.common.app.BaseApplicationImpl");
                        Object fac = null;
                        try {
                            Field f = bi.getDeclaredField("a"); f.setAccessible(true); fac = f.get(null);
                        } catch (Throwable ignored) {}
                        for (int arg : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18}) {
                            try {
                                Object rt = m.invoke(fac, arg);
                                LogX.i("SessionProbe factory.a(" + arg + ") -> " + (rt == null ? "null" : rt.getClass().getName()));
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LogX.e("SessionProbe factoryDump fail", t);
        }
    }

    private static void appDump() {
        facadeDump();
        factoryDump();
        try {
            Class<?> mq = HookRuntime.findClass("mqq.app.MobileQQ");
            LogX.i("SessionProbe MobileQQ=" + (mq == null ? "null" : mq.getName()));
            if (mq != null) {
                for (Field f : mq.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        try {
                            f.setAccessible(true);
                            Object v = f.get(null);
                            LogX.i("SessionProbe MobileQQ static: " + f.getName()
                                    + " " + f.getType().getSimpleName() + " = " + v);
                        } catch (Throwable ignored) {}
                    }
                }
                for (Method m : mq.getMethods()) {
                    if (m.getParameterCount() == 0 && (m.getName().equals("getRuntime")
                            || m.getName().equals("getQQAppInterface"))) {
                        LogX.i("SessionProbe MobileQQ method: " + m.getName()
                                + " ret=" + m.getReturnType().getSimpleName());
                    }
                }
            }
        } catch (Throwable t) {
            LogX.e("SessionProbe MobileQQ dump fail", t);
        }
        try {
            Class<?> b = HookRuntime.findClass("com.tencent.common.app.BaseApplicationImpl");
            LogX.i("SessionProbe BaseApplicationImpl=" + (b == null ? "null" : b.getName()));
            if (b != null) {
                for (Field f : b.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        try {
                            f.setAccessible(true);
                            Object v = f.get(null);
                            LogX.i("SessionProbe BaseApplicationImpl static: " + f.getName()
                                    + " " + f.getType().getSimpleName() + " = " + v);
                        } catch (Throwable ignored) {}
                    }
                }
                for (Method m : b.getDeclaredMethods()) {
                    if (m.getParameterCount() == 0) {
                        LogX.i("SessionProbe BaseApplicationImpl method: " + m.getName()
                                + " ret=" + m.getReturnType().getSimpleName());
                    }
                }
            }
        } catch (Throwable t) {
            LogX.e("SessionProbe appDump fail", t);
        }
    }

    private static void dumpEmoticonClass(Activity activity) {
        try {
            Object app = com.bettersticker.qq.QqRuntimeFinder.app(activity);
            if (app == null) { LogX.i("SessionProbe Emoticon: app null"); return; }
            ClassLoader l = app.getClass().getClassLoader();
            Class<?> emo = Class.forName("com.tencent.mobileqq.data.Emoticon", false, l);
            LogX.i("SessionProbe Emoticon class=" + emo.getName());
            for (Field f : emo.getDeclaredFields()) {
                LogX.i("SessionProbe Emoticon field: " + f.getName() + " " + f.getType().getSimpleName());
            }
            Class<?> sup = emo.getSuperclass();
            LogX.i("SessionProbe Emoticon superclass=" + (sup == null ? "null" : sup.getName()));
            while (sup != null && sup != Object.class) {
                for (Field f : sup.getDeclaredFields()) {
                    LogX.i("SessionProbe Emoticon super field: " + sup.getSimpleName() + "." + f.getName() + " " + f.getType().getSimpleName());
                }
                sup = sup.getSuperclass();
            }
            for (Method m : emo.getMethods()) {
                if (m.getName().equals("getPath") || m.getName().equals("getImageType")
                        || m.getName().equals("setPath") || m.getName().equals("getBigImagePath")) {
                    LogX.i("SessionProbe Emoticon method: " + m.getName() + "(" + m.getParameterCount() + ") -> " + m.getReturnType().getSimpleName());
                }
            }
            Class<?> pic = Class.forName("com.tencent.mobileqq.emoticonview.PicEmoticonInfo", false, l);
            LogX.i("SessionProbe PicEmoticonInfo class=" + pic.getName());
            for (Field f : pic.getDeclaredFields()) {
                LogX.i("SessionProbe PicEmoticonInfo field: " + f.getName() + " " + f.getType().getSimpleName());
            }
        } catch (Throwable t) {
            LogX.e("SessionProbe dumpEmoticonClass fail", t);
        }
    }

    private static void graph(Activity activity) {
        try {
            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            ArrayDeque<Object> q = new ArrayDeque<>();
            q.add(activity);
            try { q.add(activity.getWindow().getDecorView()); } catch (Throwable ignored) {}
            int steps = 0;
            while (!q.isEmpty() && steps++ < 12000) {
                Object o = q.removeFirst();
                if (o == null || !visited.add(o)) continue;
                String cn = o.getClass().getName();
                if (cn.contains("BaseChatPie") || cn.contains("ChatPie")
                        || cn.endsWith("SessionInfo")) {
                    LogX.i("SessionProbe found: " + cn);
                    dumpFields(o, 1);
                }
                if (cn.contains("QQAppInterface") || cn.contains("AppInterface")
                        || cn.endsWith("AppRuntime") || cn.contains("AIOContextImpl")) {
                    LogX.i("SessionProbe found2type: " + cn);
                    dumpFields(o, 1);
                }
                Class<?> c = o.getClass();
                int depth = 0;
                while (c != null && depth++ < 8) {
                    for (Field f : c.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers())) continue;
                        if (f.getType().isPrimitive() || f.getType().getName().equals("java.lang.String")) continue;
                        try {
                            f.setAccessible(true);
                            Object v = f.get(o);
                            if (v == null) continue;
                            if (v instanceof java.lang.ref.Reference) {
                                v = ((java.lang.ref.Reference<?>) v).get();
                                if (v == null) continue;
                            }
                            String vn = v.getClass().getName();
                            if (vn.contains("BaseChatPie") || vn.contains("ChatPie") || vn.endsWith("SessionInfo")) {
                                LogX.i("SessionProbe found2: " + vn + " via " + c.getName() + "." + f.getName());
                                dumpFields(v, 1);
                            }
                            if (v instanceof java.util.List || v instanceof java.util.Map
                                    || v.getClass().isArray() || vn.startsWith("com.tencent.")) {
                                q.add(v);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                    c = c.getSuperclass();
                }
            }
        } catch (Throwable t) {
            LogX.e("SessionProbe graph fail", t);
        }
    }

    private static void dumpFields(Object o, int level) {
        Class<?> c = o.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(o);
                    String vs = v == null ? "null" : (v instanceof String ? (String) v : String.valueOf(v));
                    LogX.i("SessionProbe field: " + c.getSimpleName() + "." + f.getName()
                            + " type=" + f.getType().getSimpleName() + " = " + vs);
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }
    }
}
