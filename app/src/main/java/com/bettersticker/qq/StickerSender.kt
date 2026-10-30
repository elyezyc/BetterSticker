package com.bettersticker.qq

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.bettersticker.format.ImageSniffer
import com.bettersticker.shared.LogX
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 不捕获 AIOContext，直接把图片放进 QQ 输入框，再触发 QQ 自己的发送按钮。
 * 走的是 QQ 原生的“粘贴图片 + 发送”链路。
 */
object StickerSender {
    private val main = Handler(Looper.getMainLooper())

    @JvmStatic
    fun send(activity: Activity, file: File?): Boolean {
        if (file == null || !file.isFile) {
            Toast.makeText(activity, "表情文件不存在", Toast.LENGTH_SHORT).show()
            return false
        }
        val input = findChatInput(activity)
        if (input == null) {
            Toast.makeText(activity, "没有找到当前聊天输入框", Toast.LENGTH_LONG).show()
            return false
        }
        val uri = insertToMediaStore(activity, file)
        if (uri == null) {
            Toast.makeText(activity, "无法准备发送图片", Toast.LENGTH_LONG).show()
            return false
        }
        try {
            val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newUri(activity.contentResolver, "BetterSticker", uri))
        } catch (t: Throwable) {
            LogX.e("复制图片到剪贴板失败", t)
            Toast.makeText(activity, "复制图片失败: ${t.message}", Toast.LENGTH_LONG).show()
            return false
        }
        activity.runOnUiThread {
            try {
                input.requestFocus()
                // Android 原生 TextView 粘贴入口；QQ 输入框会走自己的图片粘贴逻辑。
                input.onTextContextMenuItem(android.R.id.paste)
                clickSend(activity, 0)
            } catch (t: Throwable) {
                LogX.e("粘贴图片失败", t)
                Toast.makeText(activity, "粘贴失败: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
        return true
    }

    private fun clickSend(activity: Activity, attempt: Int) {
        main.postDelayed({
            try {
                val send = findSendButton(activity)
                if (send != null && send.isEnabled) {
                    send.performClick()
                    Toast.makeText(activity, "已交给 QQ 发送", Toast.LENGTH_SHORT).show()
                } else if (attempt < 3) {
                    clickSend(activity, attempt + 1)
                } else {
                    Toast.makeText(activity,
                        "图片已粘贴到输入框，请点 QQ 的发送按钮",
                        Toast.LENGTH_LONG).show()
                }
            } catch (t: Throwable) {
                LogX.e("点击发送失败", t)
                Toast.makeText(activity, "发送失败: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }, if (attempt == 0) 600L else 500L)
    }

    private fun insertToMediaStore(activity: Activity, file: File): Uri? {
        return try {
            val bytes = file.readBytes()
            val fmt = ImageSniffer.detect(bytes)
            val ext = if (fmt.image) fmt.extension else "png"
            val mime = if (fmt.image) fmt.mime else "image/png"
            val name = "bs_send_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "." + ext
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/BetterSticker")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val cr = activity.contentResolver
            val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            cr.openOutputStream(uri)?.use { it.write(bytes) }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            cr.update(uri, done, null, null)
            uri
        } catch (t: Throwable) {
            LogX.e("写入待发送图片失败", t)
            null
        }
    }

    private fun findChatInput(activity: Activity): EditText? {
        val list = ArrayList<EditText>()
        collectEditTexts(activity.window.decorView, list, 0)
        var best: EditText? = null
        var bestY = -1
        for (e in list) {
            if (!e.isShown || e.width <= 0 || e.height <= 0) continue
            val loc = IntArray(2)
            e.getLocationOnScreen(loc)
            val cn = e.javaClass.name
            val chat = cn.contains("AIOEditText") || cn.contains("InputEditText")
            val y = loc[1]
            if (chat && y > bestY) {
                best = e
                bestY = y
            } else if (best == null && y > activity.resources.displayMetrics.heightPixels * 0.45f) {
                best = e
                bestY = y
            }
        }
        return best
    }

    private fun findSendButton(activity: Activity): View? {
        val root = activity.window.decorView
        val list = ArrayList<View>()
        collect(root, list, 0)
        for (v in list) {
            if (!v.isShown || !v.isClickable) continue
            val cn = v.javaClass.name
            val desc = v.contentDescription?.toString() ?: ""
            val text = if (v is TextView) v.text?.toString() ?: "" else ""
            if (cn.contains("AIOInputSendBtn") || cn.contains("SendBtn")
                || desc.contains("发送") || text == "发送"
                || v.id != View.NO_ID && runCatching {
                    v.resources.getResourceEntryName(v.id).contains("send")
                }.getOrDefault(false)) {
                return v
            }
        }
        return null
    }

    private fun collect(v: View?, out: MutableList<View>, depth: Int) {
        if (v == null || depth > 30) return
        out.add(v)
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) collect(v.getChildAt(i), out, depth + 1)
        }
    }

    private fun collectEditTexts(v: View?, out: MutableList<EditText>, depth: Int) {
        if (v == null || depth > 30) return
        if (v is EditText) out.add(v)
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) collectEditTexts(v.getChildAt(i), out, depth + 1)
        }
    }
}
