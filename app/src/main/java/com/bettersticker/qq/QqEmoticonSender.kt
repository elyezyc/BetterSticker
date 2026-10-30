package com.bettersticker.qq

import android.app.Activity
import android.widget.Toast
import com.bettersticker.core.HookRuntime
import com.bettersticker.shared.LogX
import java.io.File
import java.lang.reflect.Modifier

/**
 * QQ 9.x 的 ChatActivityFacade.C(...)：
 * (QQAppInterface, Context, SessionInfo, Emoticon, StickerInfo)V
 *
 * 这是 QQ 自己的 sendEmosmMsg，直接接受 SessionInfo，不要求 AIOContext。
 */
object QqEmoticonSender {

    @JvmStatic
    fun send(activity: Activity, file: File): Boolean {
        return try {
            val facade = HookRuntime.findClass(
                "com.tencent.mobileqq.activity.ChatActivityFacade") ?: return false
            val method = facade.declaredMethods.firstOrNull {
                Modifier.isStatic(it.modifiers) && it.parameterCount == 5
                        && it.parameterTypes[0].name.contains("QQAppInterface")
                        && it.parameterTypes[1].name == "android.content.Context"
                        && it.parameterTypes[2].name.endsWith("SessionInfo")
                        && it.parameterTypes[3].name.endsWith("Emoticon")
                        && it.parameterTypes[4].name.endsWith("StickerInfo")
                        && it.returnType == Void.TYPE
            } ?: run {
                LogX.w("sendEmosmMsg: 找不到 ChatActivityFacade.C")
                return false
            }
            val session = QqSessionStore.currentSession()
                ?: QqPicSender.findSession(activity) ?: run {
                LogX.w("sendEmosmMsg: SessionInfo 为空")
                return false
            }
            val app = QqSessionStore.currentApp()
                ?: QqPicSender.findApp() ?: run {
                LogX.w("sendEmosmMsg: QQAppInterface 为空")
                return false
            }
            val emoClass = HookRuntime.findClass(
                "com.tencent.mobileqq.data.Emoticon") ?: return false
            val emo = emoClass.getDeclaredConstructor().newInstance()
            setField(emo, "path", file.absolutePath)
            setField(emo, "epId", "")
            setField(emo, "eId", "")
            setField(emo, "jobType", 0)
            val stickerClass = HookRuntime.findClass(
                "com.tencent.mobileqq.emoticon.StickerInfo")
            val sticker = stickerClass?.getDeclaredConstructor()?.newInstance()
            activity.runOnUiThread {
                try {
                    method.invoke(null, app, activity, session, emo, sticker)
                    Toast.makeText(activity, "已发送表情", Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    LogX.e("sendEmosmMsg 发送失败", t)
                    Toast.makeText(activity, "发送失败: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
            LogX.i("sendEmosmMsg 已提交: ${file.name}")
            true
        } catch (t: Throwable) {
            LogX.e("sendEmosmMsg 构造失败", t)
            false
        }
    }

    private fun setField(obj: Any, name: String, value: Any?) {
        try {
            val f = obj.javaClass.getField(name)
            f.isAccessible = true
            f.set(obj, value)
        } catch (_: Throwable) {
        }
    }
}
