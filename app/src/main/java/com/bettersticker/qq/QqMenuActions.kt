package com.bettersticker.qq

import android.app.Activity
import android.widget.Toast
import com.bettersticker.hook.ActivityTracker
import com.bettersticker.shared.LogX
import com.bettersticker.store.FavoriteStore
import com.bettersticker.ui.PanelController
import java.util.concurrent.atomic.AtomicLong

/** 消息长按菜单项的点击动作：抽取本地媒体并保存。 */
object QqMenuActions {
    @JvmStatic
    val CALLS = AtomicLong()

    @JvmStatic
    fun saveFromMenu(msgItem: Any) {
        val activity: Activity? = ActivityTracker.current()
        Thread({
            try {
                val medias = QqMsgRecordAdapter().extract(msgItem)
                var ok = 0
                var err: String? = null
                val store = FavoriteStore.get()
                for (m in medias) {
                    try {
                        if (m.isVoice) {
                            store.addVoice(m.data, m.originalName, m.source, 0)
                        } else {
                            store.addSticker(m.data, m.originalName, m.source, m.width, m.height)
                        }
                        ok++
                    } catch (t: Throwable) {
                        if (err == null) err = t.message
                        LogX.e("菜单收藏失败: ${m.originalName}", t)
                    }
                }
                activity?.runOnUiThread {
                    if (ok > 0) {
                        PanelController.refreshIfOpen()
                        Toast.makeText(activity, "已收藏 $ok 项到 BetterSticker",
                            Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(activity,
                            err?.let { "收藏失败: $it" } ?: "未找到本地媒体（可能需要先点开/下载）",
                            Toast.LENGTH_LONG).show()
                    }
                }
            } catch (t: Throwable) {
                LogX.e("菜单收藏异常", t)
                activity?.runOnUiThread {
                    Toast.makeText(activity, "收藏失败: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        }, "BetterSticker-menu-save").start()
    }
}
