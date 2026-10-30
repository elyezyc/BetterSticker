package com.bettersticker.qq

import com.bettersticker.core.HookRuntime
import com.bettersticker.shared.LogX
import com.bettersticker.shared.hook.XHooker
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/** 捕获当前聊天 AIOContext（每次进入聊天创建 AIOContextImpl），用于发送本地表情。 */
object AioContextStore {
    private val installed = AtomicBoolean()
    @Volatile
    private var ref: WeakReference<Any>? = null

    @JvmStatic
    fun install() {
        if (!installed.compareAndSet(false, true)) return
        try {
            val cls = HookRuntime.findClass("com.tencent.aio.runtime.AIOContextImpl")
            if (cls != null) {
                for (ctor in cls.declaredConstructors) {
                    try {
                        XHooker.hookAfter(ctor) { param ->
                            val obj = param.obj ?: return@hookAfter
                            ref = WeakReference(obj)
                            LogX.i("已捕获 AIOContext: ${obj.javaClass.name}")
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
            // 有些版本由 AIOContextHolder.a(AIOContext) 记录当前上下文。
            val holder = HookRuntime.findClass("com.tencent.qqnt.qwallet.aio.AIOContextHolder")
            if (holder != null) {
                val setter = HookRuntime.findMethod(holder, "a",
                    "com.tencent.aio.api.runtime.a")
                if (setter != null) {
                    XHooker.hookAfter(setter) { param ->
                        val obj = param.args.getOrNull(0) ?: return@hookAfter
                        ref = WeakReference(obj)
                        LogX.i("已从 AIOContextHolder 捕获 AIOContext")
                    }
                }
            }
            LogX.i("AioContextStore 已安装")
        } catch (t: Throwable) {
            installed.set(false)
            LogX.e("AioContextStore 安装失败", t)
        }
    }

    @JvmStatic
    fun current(): Any? = ref?.get()
}
