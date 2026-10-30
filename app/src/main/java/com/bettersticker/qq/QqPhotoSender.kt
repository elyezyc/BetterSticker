package com.bettersticker.qq

import android.app.Activity
import android.content.Intent
import com.bettersticker.shared.LogX
import java.io.File
import java.util.ArrayList

/**
 * 走 QQ 原版“发图片”流程：启动 SendPhotoActivity 并自动点发送。
 * 这是最可靠的图片消息发送路径（与用户从相册选图发送完全一致）。
 */
object QqPhotoSender {
    const val EXTRA_AUTO_SEND = "bettersticker_auto_send"

    @JvmStatic
    fun send(activity: Activity, file: File): Boolean {
        return try {
            val paths = ArrayList<String>()
            paths.add(file.absolutePath)
            val intent = Intent().setClassName(
                "com.tencent.mobileqq",
                "com.tencent.mobileqq.activity.photo.SendPhotoActivity"
            )
            intent.putStringArrayListExtra("PhotoConst.PHOTO_PATHS", paths)
            intent.putExtra("PhotoConst.SINGLE_PHOTO_PATH", file.absolutePath)
            intent.putExtra("PhotoConst.PHOTO_COUNT", 1)
            intent.putExtra(EXTRA_AUTO_SEND, true)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
            LogX.i("已启动 SendPhotoActivity 自动发送: ${file.name}")
            true
        } catch (t: Throwable) {
            LogX.e("启动 SendPhotoActivity 失败", t)
            false
        }
    }
}
