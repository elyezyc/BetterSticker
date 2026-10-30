package com.bettersticker.qq

import android.app.Activity
import android.widget.Toast
import com.bettersticker.core.HookRuntime
import com.bettersticker.shared.LogX
import com.bettersticker.ui.PanelController
import java.io.File
import java.lang.reflect.Method

/** 把本地表情文件交给 QQ 的 AIO 表情发送 API。 */
object StickerSender {
    @JvmStatic
    fun send(activity: Activity, file: File?): Boolean {
        if (activity == null || file == null || !file.isFile) {
            Toast.makeText(activity, "表情文件不存在", Toast.LENGTH_SHORT).show()
            return false
        }
        val aio = AioContextStore.current()
        if (aio == null) {
            Toast.makeText(activity, "还没捕获到当前聊天，先发一条消息再试", Toast.LENGTH_LONG).show()
            return false
        }
        return try {
            val apiClass = HookRuntime.findClass("com.tencent.qqnt.aio.api.impl.AIOEmoApiImpl")
            val aioClass = HookRuntime.findClass("com.tencent.aio.api.runtime.a")
            if (apiClass == null || aioClass == null) {
                Toast.makeText(activity, "QQ 发送 API 不存在", Toast.LENGTH_LONG).show()
                return false
            }
            // 优先从当前 AIOContext 取已注册的 emo API；取不到再 new 实现。
            var api: Any? = null
            val emoApiClass = HookRuntime.findClass("com.tencent.qqnt.aio.api.IAIOEmoApi")
            if (emoApiClass != null) {
                for (m in aio.javaClass.methods) {
                    if (m.parameterCount != 1 || m.parameterTypes[0] != Class::class.java) continue
                    try {
                        val svc = m.invoke(aio, emoApiClass)
                        if (svc != null && apiClass.isInstance(svc)) {
                            api = svc
                            break
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
            if (api == null) {
                api = apiClass.getDeclaredConstructor().newInstance()
            }
            val method: Method = apiClass.getMethod(
                "sendRandomPicEmotionMsgFromAIO", String::class.java, aioClass
            )
            activity.runOnUiThread {
                try {
                    method.invoke(api, file.absolutePath, aio)
                    Toast.makeText(activity, "已发送表情", Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    LogX.e("发送表情失败", t)
                    Toast.makeText(activity, "发送失败: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
            true
        } catch (t: Throwable) {
            LogX.e("准备发送表情失败", t)
            Toast.makeText(activity, "发送失败: ${t.message}", Toast.LENGTH_LONG).show()
            false
        }
    }
}
