package com.bettersticker.qq

import android.app.Activity
import android.widget.Toast
import com.bettersticker.shared.LogX
import java.io.File
import java.lang.reflect.Modifier

/**
 * QQ 9.x 的 ChatActivityFacade.C(...)：
 * (QQAppInterface, Context, SessionInfo, Emoticon, StickerInfo)V —— sendEmosmMsg。
 *
 * 关键点：所有反射类都用“真实 QQAppInterface 的 ClassLoader”加载，避免
 * classloader 不一致导致的 "argument has type X, got X"。
 */
object QqEmoticonSender {

    @JvmStatic
    fun send(activity: Activity, file: File): Boolean {
        return try {
            val app = QqSessionStore.currentApp()
                ?: QqRuntimeFinder.app(activity)
            if (app == null) {
                LogX.w("sendEmosmMsg: QQAppInterface 为空")
                return false
            }
            val loader = app.javaClass.classLoader
            val facade = Class.forName(
                "com.tencent.mobileqq.activity.ChatActivityFacade", false, loader)
            val method = facade.declaredMethods.firstOrNull {
                Modifier.isStatic(it.modifiers) && it.parameterCount == 5
                        && it.parameterTypes[0].name.contains("QQAppInterface")
                        && it.parameterTypes[1].name == "android.content.Context"
                        && it.parameterTypes[2].name.endsWith("SessionInfo")
                        && it.parameterTypes[3].name.endsWith("Emoticon")
                        && it.parameterTypes[4].name.endsWith("StickerInfo")
                        && it.returnType == Void.TYPE
            }
            if (method == null) {
                LogX.w("sendEmosmMsg: 找不到 ChatActivityFacade.C")
                return false
            }
            val aioparam = QqRuntimeFinder.aioparam(activity)
            val session = QqSessionStore.currentSession()
                ?: (aioparam?.let { QqRuntimeFinder.buildSessionInfo(it, loader) })
            if (session == null) {
                LogX.w("sendEmosmMsg: SessionInfo 为空")
                return false
            }
            val emoClass = Class.forName(
                "com.tencent.mobileqq.data.Emoticon", false, loader)
            val emo = emoClass.getDeclaredConstructor().newInstance()
            setField(emo, "path", file.absolutePath)
            setField(emo, "epId", "")
            setField(emo, "eId", "")
            setField(emo, "jobType", 0)
            var sticker: Any? = null
            try {
                val stickerClass = Class.forName(
                    "com.tencent.mobileqq.emoticon.StickerInfo", false, loader)
                sticker = stickerClass.getDeclaredConstructor().newInstance()
            } catch (_: Throwable) {
            }
            val finalSticker = sticker
            activity.runOnUiThread {
                try {
                    method.invoke(null, app, activity, session, emo, finalSticker)
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
