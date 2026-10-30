package com.bettersticker.qq

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import com.bettersticker.shared.LogX

/** 在 SendPhotoActivity 里自动点“发送”按钮（仅当 Intent 带 bettersticker_auto_send 标记时）。 */
object PhotoSendClicker {
    private val handler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun onResumed(activity: Activity) {
        if (activity.javaClass.name != "com.tencent.mobileqq.activity.photo.SendPhotoActivity") {
            return
        }
        val auto = try {
            activity.intent?.getBooleanExtra(QqPhotoSender.EXTRA_AUTO_SEND, false) == true
        } catch (_: Throwable) { false }
        LogX.i("PhotoSendClicker onResumed auto=$auto")
        if (!auto) return
        click(activity, 0)
    }

    private fun click(activity: Activity, attempt: Int) {
        if (attempt >= 6) return
        handler.postDelayed({
            try {
                val btn = findSend(activity.window.decorView)
                LogX.i("PhotoSendClicker attempt=$attempt btn=" + (btn?.javaClass?.simpleName ?: "null"))
                if (btn != null && btn.isEnabled) {
                    btn.performClick()
                    LogX.i("PhotoSendClicker 已自动点击发送")
                } else {
                    click(activity, attempt + 1)
                }
            } catch (t: Throwable) {
                LogX.e("PhotoSendClicker 点击失败", t)
            }
        }, 700L)
    }

    private fun findSend(v: View?): View? {
        if (v == null) return null
        if (v is android.widget.TextView) {
            val t = v.text?.toString()
            if (t == "发送" || t == "确定" || t == "完成") return v
        }
        val idName = try {
            if (v.id != View.NO_ID) v.resources.getResourceEntryName(v.id) else null
        } catch (_: Throwable) { null }
        if (idName != null && (idName.contains("send") || idName.contains("confirm"))) return v
        if (v is android.view.ViewGroup) {
            for (i in 0 until v.childCount) {
                val r = findSend(v.getChildAt(i))
                if (r != null) return r
            }
        }
        return null
    }
}
