package com.bettersticker.qq

import com.bettersticker.core.HookRuntime
import com.bettersticker.shared.LogX
import com.bettersticker.shared.hook.XHooker
import java.lang.reflect.Modifier

/** 临时探针：当用户用 QQ 原版发表情时，打印真实 session / Emoticon / 参数。 */
object SendTrace {
    @JvmStatic
    fun install() {
        hookPicEmoticonInfo()
        hookAioEmoApi()
        try {
            val sender = HookRuntime.findClass(
                "com.tencent.mobileqq.emoticonview.sender.PicEmoticonInfoSender")
            if (sender != null) {
                for (m in sender.declaredMethods) {
                    if (!m.name.startsWith("send")) continue
                    XHooker.hookBefore(m) { p ->
                        val sb = StringBuilder("SendTrace PicEmoticonInfoSender." + m.name
                                + "(")
                        p.args?.forEach { a ->
                            if (a == null) sb.append("null,")
                            else {
                                sb.append(a.javaClass.simpleName).append(",")
                                if (a.javaClass.name.endsWith("Emoticon")) {
                                    sb.append(dumpEmoticon(a)).append(",")
                                }
                                if (a.javaClass.name.endsWith("aio.n")
                                        || a.javaClass.name.endsWith("SessionInfo")) {
                                    sb.append(dumpSession(a)).append(",")
                                }
                            }
                        }
                        sb.append(")")
                        LogX.i(sb.toString())
                    }
                }
                LogX.i("SendTrace 已 Hook PicEmoticonInfoSender")
            }
        } catch (t: Throwable) {
            LogX.e("SendTrace PicEmoticonInfoSender fail", t)
        }
        try {
            val facade = HookRuntime.findClass(
                "com.tencent.mobileqq.activity.ChatActivityFacade")
            if (facade != null) {
                for (m in facade.declaredMethods) {
                    if (!m.name.startsWith("C") && !m.name.startsWith("send")) continue
                    if (m.parameterCount < 2) continue
                    XHooker.hookBefore(m) { p ->
                        val sb = StringBuilder("SendTrace ChatActivityFacade." + m.name + "(")
                        p.args?.forEach { a ->
                            sb.append(if (a == null) "null" else a.javaClass.simpleName).append(",")
                            if (a != null && a.javaClass.name.endsWith("Emoticon")) {
                                sb.append(dumpEmoticon(a)).append(",")
                            }
                            if (a != null && (a.javaClass.name.endsWith("SessionInfo")
                                    || a.javaClass.name.endsWith("aio.n"))) {
                                sb.append(dumpSession(a)).append(",")
                            }
                        }
                        sb.append(")")
                        LogX.i(sb.toString())
                    }
                }
                LogX.i("SendTrace 已 Hook ChatActivityFacade")
            }
        } catch (t: Throwable) {
            LogX.e("SendTrace ChatActivityFacade fail", t)
        }
    }

    private fun hookPicEmoticonInfo() {
        try {
            val cls = HookRuntime.findClass(
                "com.tencent.mobileqq.emoticonview.PicEmoticonInfo")
            if (cls != null) {
                for (m in cls.declaredMethods) {
                    if (!m.name.startsWith("send")) continue
                    XHooker.hookBefore(m) { p ->
                        val sb = StringBuilder("SendTrace PicEmoticonInfo." + m.name + "(")
                        p.obj?.let { sb.append(dumpEmoticonInfo(it)).append(",") }
                        p.args?.forEach { a ->
                            sb.append(if (a == null) "null" else a.javaClass.simpleName).append(",")
                            if (a != null && (a.javaClass.name.endsWith("SessionInfo")
                                    || a.javaClass.name.endsWith("aio.n"))) {
                                sb.append(dumpSession(a)).append(",")
                            }
                        }
                        sb.append(")")
                        LogX.i(sb.toString())
                    }
                }
                LogX.i("SendTrace 已 Hook PicEmoticonInfo")
            }
        } catch (t: Throwable) {
            LogX.e("SendTrace PicEmoticonInfo fail", t)
        }
    }

    private fun hookAioEmoApi() {
        try {
            val cls = HookRuntime.findClass(
                "com.tencent.qqnt.aio.api.impl.AIOEmoApiImpl")
            if (cls != null) {
                for (m in cls.declaredMethods) {
                    if (!m.name.startsWith("send")) continue
                    XHooker.hookBefore(m) { p ->
                        val sb = StringBuilder("SendTrace AIOEmoApiImpl." + m.name + "(")
                        p.args?.forEach { a ->
                            sb.append(if (a == null) "null" else a.javaClass.simpleName).append(",")
                        }
                        sb.append(")")
                        LogX.i(sb.toString())
                    }
                }
                LogX.i("SendTrace 已 Hook AIOEmoApiImpl")
            }
        } catch (t: Throwable) {
            LogX.e("SendTrace AIOEmoApiImpl fail", t)
        }
    }

    private fun dumpEmoticonInfo(info: Any): String {
        val sb = StringBuilder("PicEmoticonInfo{")
        try {
            val f = info.javaClass.getField("emoticon")
            f.isAccessible = true
            val emo = f.get(info)
            if (emo != null) sb.append(dumpEmoticon(emo))
        } catch (_: Throwable) {
        }
        sb.append("}")
        return sb.toString()
    }

    private fun dumpEmoticon(emo: Any): String {
        val sb = StringBuilder("Emoticon{")
        for (n in arrayOf("path", "epId", "eId", "jobType", "imageType", "name", "width", "height")) {
            try {
                val f = emo.javaClass.getField(n)
                f.isAccessible = true
                sb.append(n).append("=").append(f.get(emo)).append(";")
            } catch (_: Throwable) {
            }
        }
        sb.append("}")
        return sb.toString()
    }

    private fun dumpSession(s: Any): String {
        val sb = StringBuilder("Session{")
        var c: Class<*>? = s.javaClass
        while (c != null) {
            for (f in c.declaredFields) {
                if (Modifier.isStatic(f.modifiers) || f.type.isPrimitive) continue
                try {
                    f.isAccessible = true
                    val v = f.get(s)
                    if (v is String) sb.append(f.name).append("=").append(v).append(";")
                } catch (_: Throwable) {
                }
            }
            c = c.superclass
        }
        sb.append("}")
        return sb.toString()
    }
}
