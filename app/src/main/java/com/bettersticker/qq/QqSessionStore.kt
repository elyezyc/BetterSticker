package com.bettersticker.qq

import com.bettersticker.core.HookRuntime
import com.bettersticker.shared.LogX
import com.bettersticker.shared.hook.XHooker
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/** 记录当前 AIO session（com.tencent.mobileqq.activity.aio.n），供原版表情发送使用。 */
object QqSessionStore {
    private val installed = AtomicBoolean()
    @Volatile
    private var ref: WeakReference<Any>? = null

    @JvmStatic
    fun install() {
        if (!installed.compareAndSet(false, true)) return
        try {
            val cls = HookRuntime.findClass("com.tencent.mobileqq.activity.aio.n") ?: return
            for (ctor in cls.declaredConstructors) {
                try {
                    XHooker.hookAfter(ctor) { p ->
                        val obj = p.obj ?: return@hookAfter
                        ref = WeakReference(obj)
                    }
                } catch (_: Throwable) {
                }
            }
            LogX.i("QqSessionStore 已安装")
        } catch (t: Throwable) {
            installed.set(false)
            LogX.e("QqSessionStore 安装失败", t)
        }
    }

    @JvmStatic
    fun current(): Any? = ref?.get()
}
