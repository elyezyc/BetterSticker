package com.bettersticker.qq

import android.app.Activity
import com.bettersticker.core.HookRuntime
import com.bettersticker.shared.LogX
import java.lang.reflect.Modifier

/** 从当前 Activity 对象图里按类名/结构找 QQ 运行时对象。 */
object QqRuntimeFinder {

    @JvmStatic
    fun app(activity: Activity): Any? {
        for (name in arrayOf("com.tencent.mobileqq.app.QQAppInterface",
                "com.tencent.common.app.business.BaseQQAppInterface",
                "mqq.app.AppRuntime")) {
            find(activity, name)?.let { return it }
        }
        // 结构兜底：有 getCurrentAccountUin 或 getRuntimeService 的对象。
        return findStructural(activity) { obj ->
            obj.javaClass.methods.any {
                it.name == "getCurrentAccountUin" || it.name == "getRuntimeService"
            }
        }
    }

    @JvmStatic
    fun aioparam(activity: Activity): Any? {
        return find(activity, "com.tencent.aio.data.AIOParam")
            ?: find(activity, "com.tencent.aio.base.chat.ChatPie")?.let { pie ->
                try {
                    val f = pie.javaClass.getDeclaredField("h")
                    f.isAccessible = true
                    f.get(pie)
                } catch (_: Throwable) { null }
            }
    }

    @JvmStatic
    fun buildSessionInfo(aioparam: Any, loader: ClassLoader): Any? {
        return try {
            val aioSession = aioparam.javaClass.getMethod("o").invoke(aioparam) ?: return null
            val contact = aioSession.javaClass.getMethod("b").invoke(aioSession) ?: return null
            val cls = Class.forName(
                "com.tencent.mobileqq.activity.aio.SessionInfo", false, loader)
            val s = cls.getDeclaredConstructor().newInstance()
            val cc = contact.javaClass
            for ((to, from) in arrayOf("d" to "d", "e" to "e", "f" to "e", "h" to "e")) {
                try {
                    val f = cc.getField(from)
                    f.isAccessible = true
                    setField(s, to, f.get(contact))
                } catch (_: Throwable) {
                }
            }
            LogX.i("已从 AIOParam 构造 SessionInfo(loader)")
            s
        } catch (t: Throwable) {
            LogX.w("从 AIOParam 构造 SessionInfo 失败: $t")
            null
        }
    }

    @JvmStatic
    fun session(activity: Activity): Any? {
        find(activity, "com.tencent.mobileqq.activity.aio.SessionInfo")?.let { return it }
        // 优先从 AIOParam 构造，最稳。
        val param = aioparam(activity)
        if (param != null) {
            buildSessionFromAioparam(param)?.let { return it }
        }
        val n = find(activity, "com.tencent.mobileqq.activity.aio.n") ?: return null
        return buildSession(n)
    }

    private fun buildSessionFromAioparam(param: Any): Any? {
        return try {
            val aioSession = param.javaClass.getMethod("o").invoke(param) ?: return null
            val contact = aioSession.javaClass.getMethod("b").invoke(aioSession) ?: return null
            val cls = HookRuntime.findClass(
                "com.tencent.mobileqq.activity.aio.SessionInfo") ?: return null
            val s = cls.getDeclaredConstructor().newInstance()
            val cc = contact.javaClass
            for ((to, from) in arrayOf("d" to "d", "e" to "e", "f" to "e", "h" to "e")) {
                try {
                    val f = cc.getField(from)
                    f.isAccessible = true
                    setField(s, to, f.get(contact))
                } catch (_: Throwable) {
                }
            }
            LogX.i("已从 AIOParam 构造 SessionInfo")
            s
        } catch (t: Throwable) {
            LogX.w("从 AIOParam 构造 SessionInfo 失败: $t")
            null
        }
    }

    private fun buildSession(n: Any): Any? {
        return try {
            val cls = HookRuntime.findClass(
                "com.tencent.mobileqq.activity.aio.SessionInfo") ?: return null
            val s = cls.getDeclaredConstructor().newInstance()
            for (name in arrayOf("d", "e", "f", "h")) {
                try {
                    val f = n.javaClass.getField(name)
                    f.isAccessible = true
                    setField(s, name, f.get(n))
                } catch (_: Throwable) {
                }
            }
            LogX.i("已从 n 构造 SessionInfo: ${s.javaClass.name}")
            s
        } catch (t: Throwable) {
            LogX.w("构造 SessionInfo 失败: $t")
            null
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

    private fun find(activity: Activity, target: String): Any? {
        return findStructural(activity) { obj ->
            obj.javaClass.name == target || obj.javaClass.superclass?.name == target
        }
    }

    private fun findStructural(activity: Activity, pred: (Any) -> Boolean): Any? {
        try {
            val visited = java.util.Collections.newSetFromMap(
                java.util.IdentityHashMap<Any, Boolean>())
            val queue = ArrayDeque<Any>()
            queue.add(activity)
            try { queue.add(activity.window.decorView) } catch (_: Throwable) {}
            var steps = 0
            while (queue.isNotEmpty() && steps++ < 8000) {
                val obj = queue.removeFirst()
                if (!visited.add(obj)) continue
                if (pred(obj)) {
                    LogX.i("QqRuntimeFinder 找到: ${obj.javaClass.name}")
                    return obj
                }
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
                            if (pred(v)) {
                                LogX.i("QqRuntimeFinder 字段 ${c.name}.${f.name} 找到 ${v.javaClass.name}")
                                return v
                            }
                            if (v is List<*> || v is Map<*, *> || v is Array<*>) {
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
        } catch (t: Throwable) {
            LogX.e("QqRuntimeFinder graph fail", t)
        }
        return null
    }
}
