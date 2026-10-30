package com.bettersticker.loader;

import java.lang.reflect.Member;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import com.bettersticker.shared.LogX;
import com.bettersticker.shared.hook.XHooker;

/**
 * 用标准 XposedBridge 实现 XHooker。
 * 只依赖 LSPosed 提供的 API，不加载任何自有 native 库。
 */
public final class XposedHookerImpl extends XHooker {

    @Override
    protected void hookBeforeImpl(final Member member, final HookCallback callback) {
        final AtomicReference<XC_MethodHook.Unhook> unhookRef = new AtomicReference<>();
        XC_MethodHook.Unhook unhook = XposedBridge.hookMethod(member, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    callback.onInvoke(new Param(param, unhookRef));
                } catch (Throwable t) {
                    LogX.e("before 回调异常: " + member, t);
                }
            }
        });
        unhookRef.set(unhook);
    }

    @Override
    protected void hookAfterImpl(final Member member, final HookCallback callback) {
        final AtomicReference<XC_MethodHook.Unhook> unhookRef = new AtomicReference<>();
        XC_MethodHook.Unhook unhook = XposedBridge.hookMethod(member, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    callback.onInvoke(new Param(param, unhookRef));
                } catch (Throwable t) {
                    LogX.e("after 回调异常: " + member, t);
                }
            }
        });
        unhookRef.set(unhook);
    }

    @Override
    protected Object invokeImpl(Member member, Object obj, Object[] args) throws Throwable {
        return XposedBridge.invokeOriginalMethod(member, obj, args);
    }

    @Override
    protected void logImpl(String message) {
        LogX.i(message);
    }

    private static final class Param extends HookParam {
        private final XC_MethodHook.MethodHookParam delegate;
        private final AtomicReference<XC_MethodHook.Unhook> unhookRef;

        Param(XC_MethodHook.MethodHookParam delegate, AtomicReference<XC_MethodHook.Unhook> unhookRef) {
            this.delegate = delegate;
            this.unhookRef = unhookRef;
            this.method = delegate.method;
            this.obj = delegate.thisObject;
            this.args = delegate.args;
        }

        @Override
        public Object getResult() {
            return delegate.getResult();
        }

        @Override
        public void setResult(Object result) {
            delegate.setResult(result);
        }

        @Override
        public Throwable getThrowable() {
            return delegate.getThrowable();
        }

        @Override
        public void setThrowable(Throwable throwable) {
            delegate.setThrowable(throwable);
        }

        @Override
        public void unhook() {
            XC_MethodHook.Unhook u = unhookRef.get();
            if (u != null) {
                u.unhook();
            }
        }
    }
}
