package com.bettersticker.qq

import com.bettersticker.core.HookRuntime
import com.bettersticker.shared.LogX
import com.bettersticker.shared.hook.XHooker
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/** 记录当前 AIO session（com.tencent.mobileqq.activity.aio.n），供原版表情发送使用。 */
object QqSessionStore {
    private val installed = AtomicBoolean()
    @Volatile
    private var ref: WeakReference<Any>? = null
    @Volatile
    private var aioRef: WeakReference<Any>? = null
    @Volatile
    private var sessionRef: WeakReference<Any>? = null
    @Volatile
    private var appRef: WeakReference<Any>? = null

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

    @JvmStatic
    fun currentAio(): Any? = aioRef?.get()

    @JvmStatic
    fun currentSession(): Any? = sessionRef?.get()

    @JvmStatic
    fun currentApp(): Any? = appRef?.get()

    /** BaseChatPie.s() 之后读取 SessionInfo / QQAppInterface，和 QTool 的做法一致。 */
    @JvmStatic
    fun captureChatPie(chatPie: Any?) {
        if (chatPie == null) return
        try {
            var c: Class<*>? = chatPie.javaClass
            while (c != null) {
                for (f in c.declaredFields) {
                    if (Modifier.isStatic(f.modifiers)) continue
                    val tn = f.type.name
                    if (!tn.endsWith("SessionInfo")
                            && !tn.contains("QQAppInterface")) continue
                    try {
                        f.isAccessible = true
                        val v = f.get(chatPie) ?: continue
                        if (tn.endsWith("SessionInfo")) {
                            sessionRef = WeakReference(v)
                            LogX.i("已捕获 SessionInfo: ${v.javaClass.name}")
                        } else {
                            appRef = WeakReference(v)
                        }
                    } catch (_: Throwable) {
                    }
                }
                c = c.superclass
            }
        } catch (t: Throwable) {
            LogX.w("读取 BaseChatPie 字段失败: $t")
        }
    }

    /** RootAIOVB 构建视图时传入的 MviContext 就是当前 AIOContext。 */
    @JvmStatic
    fun captureAio(aio: Any?) {
        if (aio == null) return
        try {
            val cls = aio.javaClass
            if (cls.name == "com.tencent.aio.api.runtime.a"
                    || cls.interfaces.any { it.name == "com.tencent.aio.api.runtime.a" }
                    || cls.name.contains("AIOContext")) {
                aioRef = WeakReference(aio)
                LogX.i("已捕获 RootAIOVB AIOContext: ${cls.name}")
            }
        } catch (_: Throwable) {
        }
    }

    /** ChatFragment.onResume 时直接读 currentContext（WeakReference<AIOContext>）。 */
    @JvmStatic
    fun captureFromFragment(fragment: Any?) {
        if (fragment == null) return
        try {
            var c: Class<*>? = fragment.javaClass
            while (c != null) {
                try {
                    val f = c.getDeclaredField("currentContext")
                    f.isAccessible = true
                    val v = f.get(fragment)
                    val aio = if (v is java.lang.ref.WeakReference<*>) v.get() else v
                    if (aio != null) {
                        aioRef = WeakReference(aio)
                        LogX.i("已捕获 ChatFragment AIOContext: ${aio.javaClass.name}")
                        return
                    }
                } catch (_: Throwable) {
                }
                c = c.superclass
            }
        } catch (t: Throwable) {
            LogX.w("读取 ChatFragment.currentContext 失败: $t")
        }
    }
}
