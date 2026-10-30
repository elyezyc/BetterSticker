package com.bettersticker.qq

import android.app.Activity
import android.widget.Toast
import com.bettersticker.core.HookRuntime
import com.bettersticker.shared.LogX
import java.io.File
import java.lang.reflect.Modifier
import java.security.MessageDigest
import java.util.UUID

/**
 * 按 QTool 的做法直接构造并发送 MessageForPic。
 * 这条路径只依赖 ChatActivityFacade + SessionInfo，不依赖 AIOContext。
 */
object QqPicSender {

    @JvmStatic
    fun send(activity: Activity, file: File): Boolean {
        return try {
            val session = QqSessionStore.currentSession() ?: findSession(activity)
            if (session == null) {
                LogX.w("sendPic: SessionInfo 为空")
                return false
            }
            val facade = HookRuntime.findClass(
                "com.tencent.mobileqq.activity.ChatActivityFacade") ?: return false
            val build = facade.declaredMethods.firstOrNull {
                Modifier.isStatic(it.modifiers) && it.parameterCount == 3
                        && it.parameterTypes[0].name.contains("QQAppInterface")
                        && it.parameterTypes[1].name.endsWith("SessionInfo")
                        && it.parameterTypes[2] == String::class.java
                        && it.returnType.name.contains("ChatMessage")
            } ?: run {
                LogX.w("sendPic: 找不到 ChatActivityFacade buildPic")
                return false
            }
            val app = QqSessionStore.currentApp() ?: run {
                try {
                    val base = HookRuntime.findClass(
                        "com.tencent.common.app.BaseApplicationImpl")
                    val appObj = base?.getMethod("getApplication")?.invoke(null)
                    appObj?.javaClass?.getMethod("getRuntime")?.invoke(appObj)
                } catch (_: Throwable) {
                    null
                }
            } ?: run {
                LogX.w("sendPic: QQAppInterface 为空")
                return false
            }
            val msg = build.invoke(null, app, session, file.absolutePath)
                ?: return false
            val md5 = md5(file)
            setField(msg, "md5", md5)
            setField(msg, "uuid", "$md5.jpg")
            setField(msg, "localUUID", UUID.randomUUID().toString())
            try {
                msg.javaClass.getMethod("prewrite").invoke(msg)
            } catch (_: Throwable) {
                try {
                    msg.javaClass.getDeclaredMethod("prewrite").apply { isAccessible = true }
                        .invoke(msg)
                } catch (_: Throwable) {
                }
            }
            val send = facade.declaredMethods.firstOrNull {
                Modifier.isStatic(it.modifiers) && it.parameterCount == 4
                        && it.parameterTypes[0].name.contains("QQAppInterface")
                        && it.parameterTypes[1].name.endsWith("SessionInfo")
                        && it.parameterTypes[2].isAssignableFrom(msg.javaClass)
                        && it.parameterTypes[3] == Int::class.javaPrimitiveType
            } ?: run {
                LogX.w("sendPic: 找不到 ChatActivityFacade sendPic")
                return false
            }
            activity.runOnUiThread {
                try {
                    send.invoke(null, app, session, msg, 0)
                    Toast.makeText(activity, "已发送表情", Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    LogX.e("sendPic 发送失败", t)
                    Toast.makeText(activity, "发送失败: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
            LogX.i("sendPic 已提交: ${file.name}")
            true
        } catch (t: Throwable) {
            LogX.e("sendPic 构造失败", t)
            false
        }
    }

    @JvmStatic
    fun findSession(activity: Activity): Any? {
        val target = "com.tencent.mobileqq.activity.aio.SessionInfo"
        val visited = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<Any, Boolean>())
        val queue = ArrayDeque<Any>()
        queue.add(activity)
        try {
            queue.add(activity.window.decorView)
        } catch (_: Throwable) {
        }
        var steps = 0
        while (queue.isNotEmpty() && steps++ < 4000) {
            val obj = queue.removeFirst()
            if (!visited.add(obj)) continue
            if (obj.javaClass.name == target) return obj
            var c: Class<*>? = obj.javaClass
            var depth = 0
            while (c != null && depth++ < 8) {
                for (f in c.declaredFields) {
                    if (Modifier.isStatic(f.modifiers)) continue
                    if (f.type.isPrimitive || f.type.name == "java.lang.String") continue
                    try {
                        f.isAccessible = true
                        var v = f.get(obj) ?: continue
                        if (v is java.lang.ref.Reference<*>) v = v.get() ?: continue
                        if (v.javaClass.name == target) return v
                        if (v is List<*> || v is Array<*> || v is Map<*, *>) {
                            queue.add(v)
                        } else {
                            val n = v.javaClass.name
                            if (!n.startsWith("android.graphics")
                                    && !n.startsWith("android.content.res")
                                    && !n.startsWith("android.util")) {
                                queue.add(v)
                            }
                        }
                    } catch (_: Throwable) {
                    }
                }
                c = c.superclass
            }
        }
        return null
    }

    @JvmStatic
    fun findApp(): Any? {
        return try {
            val base = HookRuntime.findClass(
                "com.tencent.common.app.BaseApplicationImpl")
            val appObj = base?.getMethod("getApplication")?.invoke(null)
            appObj?.javaClass?.getMethod("getRuntime")?.invoke(appObj)
        } catch (_: Throwable) {
            null
        }
    }

    private fun setField(obj: Any, name: String, value: Any?) {
        try {
            val f = obj.javaClass.getField(name)
            f.isAccessible = true
            f.set(obj, value)
        } catch (_: Throwable) {
            try {
                val f = obj.javaClass.getDeclaredField(name)
                f.isAccessible = true
                f.set(obj, value)
            } catch (_: Throwable) {
            }
        }
    }

    private fun md5(file: File): String {
        val d = MessageDigest.getInstance("MD5")
        file.inputStream().use { ins ->
            val buf = ByteArray(8192)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                d.update(buf, 0, n)
            }
        }
        return d.digest().joinToString("") { "%02x".format(it) }
    }
}
