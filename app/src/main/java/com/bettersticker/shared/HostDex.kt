package com.bettersticker.shared

import android.content.Context
import dalvik.system.InMemoryDexClassLoader
import java.nio.ByteBuffer
import java.util.HashMap

/**
 * 用宿主 ClassLoader 作为 parent 加载模块自带的 helper dex。
 *
 * 这样 SettingsAdapterWrapper / QqMenuItem 的宿主父类（RecyclerView.Adapter、ui.e）
 * 与 QQ 里的类来自同一个 ClassLoader，避免模块 ClassLoader 看不到宿主类。
 *
 * dex 文件放在模块 APK 的 assets/ 下，通过 createPackageContext 读取。
 */
object HostDex {
    private const val MODULE_PKG = "com.elyezyc.bettersticker"
    private val cache = HashMap<String, Class<*>>()

    @JvmStatic
    fun load(host: Context, asset: String, className: String): Class<*> {
        val key = "$asset#$className"
        synchronized(cache) {
            cache[key]?.let { return it }
        }
        val module = host.createPackageContext(MODULE_PKG, Context.CONTEXT_IGNORE_SECURITY)
        val dex = module.assets.open(asset).use { it.readBytes() }
        val loader = InMemoryDexClassLoader(ByteBuffer.wrap(dex), Env.appClassLoader)
        val cls = Class.forName(className, true, loader)
        synchronized(cache) { cache[key] = cls }
        LogX.i("HostDex 加载 $className parent=${Env.appClassLoader}")
        return cls
    }
}
