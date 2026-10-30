package com.bettersticker.qq

import com.bettersticker.shared.Env
import com.bettersticker.shared.HostDex
import com.bettersticker.shared.LogX
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong

/**
 * 直接在 MenuWrapper 已经拿到的 ui.c 菜单模型上追加 QqMenuItem。
 * 这是 content component 列表 hook 之外的第二条原生菜单路径。
 */
object QqMenuItemFactory {
    private const val MENU_ID = 0x0B500002
    private const val TEXT = "收藏到 BetterSticker"
    private val added = AtomicLong()

    @JvmStatic
    fun addToMenu(menu: Any?, msg: Any?): Boolean {
        if (menu == null || msg == null) return false
        return try {
            var itemClass = runCatching {
                HostDex.load(Env.appContext, "qq-menu-item.dex",
                    "com.bettersticker.qq.QqMenuItem")
            }.onFailure { LogX.w("HostDex 菜单项加载失败: $it") }.getOrNull()
            if (itemClass == null) itemClass = load("com.bettersticker.qq.QqMenuItem")
            val eClass = load("com.tencent.qqnt.aio.menu.ui.e")
            if (itemClass == null || eClass == null) return false
            val create = itemClass.getMethod("create", Any::class.java, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, String::class.java, Runnable::class.java)
            val item = create.invoke(null, msg, android.R.drawable.ic_menu_save, MENU_ID, TEXT,
                Runnable { QqMenuActions.saveFromMenu(msg) }) ?: return false
            if (alreadyContains(menu, itemClass.name)) return true
            val add = menu.javaClass.methods.firstOrNull {
                it.parameterCount == 1 && eClass.isAssignableFrom(it.parameterTypes[0])
            } ?: run {
                LogX.w("ui.c 没有可用的 add(ui.e) 方法")
                return false
            }
            add.invoke(menu, item)
            added.incrementAndGet()
            LogX.i("已直接向 ui.c 添加原生菜单项")
            true
        } catch (t: Throwable) {
            LogX.w("直接添加 ui.c 菜单项失败: $t")
            false
        }
    }

    private fun alreadyContains(menu: Any, itemClassName: String): Boolean = try {
        val f: Method = menu.javaClass.getMethod("f")
        val arr = f.invoke(menu)
        if (arr != null && arr.javaClass.isArray) {
            (0 until java.lang.reflect.Array.getLength(arr)).any { i ->
                java.lang.reflect.Array.get(arr, i)?.javaClass?.name == itemClassName
            }
        } else false
    } catch (_: Throwable) {
        false
    }

    private fun load(name: String): Class<*>? {
        for (cl in arrayOf(Env.appClassLoader, QqMenuItemFactory::class.java.classLoader)) {
            try {
                return Class.forName(name, false, cl)
            } catch (_: Throwable) {
            }
        }
        return null
    }

    @JvmStatic
    fun added(): Long = added.get()
}
