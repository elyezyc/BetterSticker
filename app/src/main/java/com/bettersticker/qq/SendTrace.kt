package com.bettersticker.qq

import com.bettersticker.core.HookRuntime
import com.bettersticker.shared.LogX
import com.bettersticker.shared.hook.XHooker
import java.lang.reflect.Modifier

/** 窄探针：抓图片消息发送的真实入口。 */
object SendTrace {
    @JvmStatic
    fun install() {
        hookFacade()
        hookByFile()
        hookMsgService()
    }

    private fun hookMsgService() {
        try {
            val cls = HookRuntime.findClass("com.tencent.qqnt.kernel.api.impl.MsgService")
            if (cls != null) {
                for (m in cls.declaredMethods) {
                    val ps = m.parameterTypes
                    if (ps.size != 4) continue
                    if (ps[0] != java.lang.Long.TYPE) continue
                    if (!ps[2].name.contains("ArrayList")) continue
                    if (!ps[1].name.contains("Contact")) continue
                    XHooker.hookBefore(m) { p ->
                        val sb = StringBuilder("SendTrace MsgService." + m.name + "(")
                        p.args?.forEach { a ->
                            if (a == null) sb.append("null,")
                            else if (a is Long) sb.append("id=$a,")
                            else if (a.javaClass.name.contains("Contact")) sb.append(dumpContact(a)).append(",")
                            else if (a is java.util.ArrayList<*>) {
                                sb.append("list${a.size}=[")
                                a.forEach { e -> if (e != null) sb.append(dumpElement(e)).append(";") }
                                sb.append("],")
                            } else sb.append(a.javaClass.simpleName).append(",")
                        }
                        sb.append(")")
                        LogX.i(sb.toString())
                    }
                }
                LogX.i("SendTrace 已 Hook MsgService")
            }
        } catch (t: Throwable) {
            LogX.e("SendTrace msgService fail", t)
        }
    }

    private fun dumpContact(c: Any): String {
        val sb = StringBuilder("Contact{")
        for (n in arrayOf("peerUid", "chatType", "guildId", "peerUin")) {
            try { val f = c.javaClass.getField(n); f.isAccessible = true; sb.append(n).append("=").append(f.get(c)).append(";") } catch (_: Throwable) {}
        }
        sb.append("}")
        return sb.toString()
    }

    private fun dumpElement(e: Any): String {
        val sb = StringBuilder("Elem{cls=" + e.javaClass.simpleName)
        for (n in arrayOf("elementType", "elementId", "textElement", "picElement", "imageElement")) {
            try {
                val f = e.javaClass.getField(n); f.isAccessible = true; val v = f.get(e)
                if (v != null) {
                    sb.append(";").append(n).append("=").append(v.javaClass.simpleName)
                    if (n == "picElement") {
                        for (pn in arrayOf("sourcePath", "filePath", "picSubType", "md5HexStr")) {
                            try { val pf = v.javaClass.getField(pn); pf.isAccessible = true; sb.append(":").append(pn).append("=").append(pf.get(v)) } catch (_: Throwable) {}
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
        sb.append("}")
        return sb.toString()
    }

    private fun hookFacade() {
        try {
            val cls = HookRuntime.findClass("com.tencent.imcore.message.BaseQQMessageFacade")
            if (cls != null) {
                for (m in cls.declaredMethods) {
                    if (m.name != "a" || m.parameterCount < 1 || m.parameterCount > 3) continue
                    XHooker.hookBefore(m) { p ->
                        val sb = StringBuilder("SendTrace BaseQQMessageFacade.a(")
                        p.args?.forEach { a ->
                            if (a == null) sb.append("null,")
                            else {
                                sb.append(a.javaClass.simpleName).append(",")
                                if (a.javaClass.name.contains("MessageRecord")
                                        || a.javaClass.name.contains("MessageForPic")) {
                                    sb.append(dumpRecord(a)).append(",")
                                }
                            }
                        }
                        sb.append(")")
                        LogX.i(sb.toString())
                    }
                }
                LogX.i("SendTrace 已 Hook BaseQQMessageFacade")
            }
        } catch (t: Throwable) {
            LogX.e("SendTrace facade fail", t)
        }
    }

    private fun hookByFile() {
        try {
            val cls = HookRuntime.findClass("com.tencent.mobileqq.utils.SendByFile")
            if (cls != null) {
                for (m in cls.declaredMethods) {
                    if (m.name != "d") continue
                    XHooker.hookBefore(m) { p ->
                        val sb = StringBuilder("SendTrace SendByFile.d(")
                        p.args?.forEach { a ->
                            if (a == null) sb.append("null,")
                            else if (a is List<*>) sb.append("List${a.size},")
                            else sb.append(a.javaClass.simpleName).append("=").append(a.toString()).append(",")
                        }
                        sb.append(")")
                        LogX.i(sb.toString())
                    }
                }
                LogX.i("SendTrace 已 Hook SendByFile")
            }
        } catch (t: Throwable) {
            LogX.e("SendTrace byFile fail", t)
        }
    }

    private fun dumpRecord(r: Any): String {
        val sb = StringBuilder("Record{")
        var c: Class<*>? = r.javaClass
        while (c != null && c != Object::class.java) {
            for (f in c.declaredFields) {
                if (Modifier.isStatic(f.modifiers)) continue
                try {
                    f.isAccessible = true
                    val v = f.get(r)
                    if (v is String || v is Number || v is Boolean) {
                        sb.append(f.name).append("=").append(v).append(";")
                    }
                } catch (_: Throwable) {
                }
            }
            c = c.superclass
        }
        sb.append("}")
        return sb.toString()
    }
}
