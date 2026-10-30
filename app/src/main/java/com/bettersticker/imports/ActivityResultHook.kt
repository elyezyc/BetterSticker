package com.bettersticker.imports

import android.app.Activity
import android.content.Intent
import com.bettersticker.shared.LogX
import com.bettersticker.shared.hook.XHooker
import java.util.concurrent.atomic.AtomicBoolean

/** 捕获 QQ Activity 的文件选择结果，交给 LocalImport。 */
object ActivityResultHook {
    private val installed = AtomicBoolean()

    @JvmStatic
    fun install() {
        if (!installed.compareAndSet(false, true)) return
        try {
            val m = Activity::class.java.getDeclaredMethod(
                "onActivityResult", Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Intent::class.java
            )
            m.isAccessible = true
            XHooker.hookAfter(m) { param ->
                val a = param.args.getOrNull(0) as? Activity ?: return@hookAfter
                val req = param.args.getOrNull(1) as? Int ?: return@hookAfter
                val res = param.args.getOrNull(2) as? Int ?: return@hookAfter
                val data = param.args.getOrNull(3) as? Intent
                LocalImport.onActivityResult(a, req, res, data)
            }
            LogX.i("Activity.onActivityResult 已 Hook（文件导入）")
        } catch (t: Throwable) {
            installed.set(false)
            LogX.e("Hook Activity.onActivityResult 失败", t)
        }
    }
}
