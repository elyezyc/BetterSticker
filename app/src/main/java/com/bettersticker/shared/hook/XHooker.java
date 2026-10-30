package com.bettersticker.shared.hook;

import java.lang.reflect.Member;

/**
 * Hook 抽象层。
 *
 * v1 只有 Xposed 实现（LSPosed）；simplehook 包只保留“不可用”说明，
 * 因为参考目录/原始 APK 里的 libhooker.so 是闭源二进制，不能在 BetterSticker 里复用。
 */
public abstract class XHooker {

    public interface HookCallback {
        void onInvoke(HookParam param) throws Throwable;
    }

    public static abstract class HookParam {
        public Object[] args;
        public Member method;
        public Object obj;

        public abstract Object getResult();

        public abstract void setResult(Object result);

        public abstract Throwable getThrowable();

        public abstract void setThrowable(Throwable throwable);

        public abstract void unhook();
    }

    private static volatile XHooker instance;

    protected abstract void hookBeforeImpl(Member member, HookCallback callback);

    protected abstract void hookAfterImpl(Member member, HookCallback callback);

    protected abstract Object invokeImpl(Member member, Object obj, Object[] args) throws Throwable;

    protected abstract void logImpl(String message);

    public static void setImpl(XHooker impl) {
        instance = impl;
    }

    public static XHooker get() {
        XHooker impl = instance;
        if (impl == null) {
            throw new IllegalStateException("XHooker 未初始化");
        }
        return impl;
    }

    public static void hookBefore(Member member, HookCallback callback) {
        get().hookBeforeImpl(member, callback);
    }

    public static void hookAfter(Member member, HookCallback callback) {
        get().hookAfterImpl(member, callback);
    }

    public static Object invoke(Member member, Object obj, Object[] args) throws Throwable {
        return get().invokeImpl(member, obj, args);
    }

    public static void log(String message) {
        get().logImpl(message);
    }
}
