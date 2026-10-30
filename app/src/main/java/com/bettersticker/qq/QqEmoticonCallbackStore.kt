package com.bettersticker.qq

import com.bettersticker.core.HookRuntime
import com.bettersticker.shared.LogX
import com.bettersticker.shared.hook.XHooker
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/** 保存当前 QQ 表情面板的 EmoticonCallback，发送本地表情时直接调用它。 */
object QqEmoticonCallbackStore {
    private val installed = AtomicBoolean()
    @Volatile
    private var ref: WeakReference<Any>? = null

    @JvmStatic
    fun install() {
        if (!installed.compareAndSet(false, true)) return
        try {
            val cls = HookRuntime.findClass(
                "com.tencent.mobileqq.emoticonview.EmoticonPanelController") ?: return
            for (m in cls.declaredMethods) {
                if (m.name != "init") continue
                try {
                    XHooker.hookAfter(m) { p ->
                        capture(p.obj)
                    }
                } catch (_: Throwable) {
                }
            }
            LogX.i("QqEmoticonCallbackStore 已安装")
        } catch (t: Throwable) {
            installed.set(false)
            LogX.e("QqEmoticonCallbackStore 安装失败", t)
        }
    }

    @JvmStatic
    fun capture(controller: Any?) {
        if (controller == null) return
        try {
            val f = controller.javaClass.getDeclaredField("callback")
            f.isAccessible = true
            val cb = f.get(controller) ?: return
            ref = WeakReference(cb)
            LogX.i("已捕获 EmoticonCallback: ${cb.javaClass.name}")
        } catch (t: Throwable) {
            LogX.w("读取 EmoticonPanelController.callback 失败: $t")
        }
    }

    @JvmStatic
    fun current(): Any? = ref?.get()
}
