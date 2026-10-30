package com.bettersticker.qq

import android.app.Activity
import android.content.Intent
import com.bettersticker.shared.LogX
import java.io.File
import java.util.ArrayList

/** 走 QQ 原版 SendPhotoActivity 发图片：传 PHOTO_PATHS + session_info，自动点发送。 */
object QqPhotoSender {
    const val EXTRA_AUTO_SEND = "bettersticker_auto_send"

    @JvmStatic
    fun send(activity: Activity, file: File): Boolean {
        return try {
            val app = QqSessionStore.currentApp() ?: QqRuntimeFinder.app(activity)
            val loader = app?.javaClass?.classLoader ?: activity.classLoader
            val aioparam = QqRuntimeFinder.aioparam(activity)
            val session = QqSessionStore.currentSession()
                ?: (aioparam?.let { QqRuntimeFinder.buildSessionInfo(it, loader) })
            val paths = ArrayList<String>()
            paths.add(file.absolutePath)
            val intent = Intent().setClassName(
                "com.tencent.mobileqq",
                "com.tencent.mobileqq.activity.photo.SendPhotoActivity"
            )
            intent.putStringArrayListExtra("PhotoConst.PHOTO_PATHS", paths)
            intent.putExtra("PhotoConst.INIT_ACTIVITY_PACKAGE_NAME", "com.tencent.mobileqq")
            intent.putExtra("PhotoConst.INIT_ACTIVITY_CLASS_NAME", "com.tencent.mobileqq.activity.SplashActivity")
            intent.putExtra("PhotoConst.SEND_PHOTO_ACTIVITY_FROM", "PhotoConst.SEND_PHOTO_ACTIVITY_FROM")
            intent.putExtra("PhotoConst.SINGLE_PHOTO_PATH", file.absolutePath)
            intent.putExtra("PhotoConst.PHOTO_COUNT", 1)
            if (session != null) {
                intent.putExtra("session_info", session as android.os.Parcelable)
            }
            intent.putExtra(EXTRA_AUTO_SEND, true)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
            LogX.i("已启动 SendPhotoActivity 自动发送: ${file.name} session=${session != null}")
            true
        } catch (t: Throwable) {
            LogX.e("启动 SendPhotoActivity 失败", t)
            false
        }
    }
}
