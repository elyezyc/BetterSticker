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
        // 优先走 QQ 原版 PicEmoticonInfoSender；需要当前 AIO session。
        if (sendViaPicEmoticon(activity, file)) {
            return true
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

    /** 用 QQ 原版 PicEmoticonInfoSender 发送本地 PicEmoticonInfo。 */
    private fun sendViaPicEmoticon(activity: Activity, file: File): Boolean {
        return try {
            val session = findAioSession(activity) ?: return false
            val appClass = com.bettersticker.core.HookRuntime.findClass(
                "com.tencent.common.app.BaseApplicationImpl") ?: return false
            val app = appClass.getMethod("getApplication").invoke(null) ?: return false
            val runtime = app.javaClass.getMethod("getRuntime").invoke(app) ?: return false
            val infoClass = com.bettersticker.core.HookRuntime.findClass(
                "com.tencent.mobileqq.emoticonview.PicEmoticonInfo") ?: return false
            val emoClass = com.bettersticker.core.HookRuntime.findClass(
                "com.tencent.mobileqq.data.Emoticon") ?: return false
            val info = infoClass.getConstructor(String::class.java)
                .newInstance("")
            val emoticon = emoClass.getDeclaredConstructor().newInstance()
            setField(emoClass, emoticon, "path", file.absolutePath)
            setField(emoClass, emoticon, "epId", "")
            setField(emoClass, emoticon, "eId", "")
            setField(emoClass, emoticon, "jobType", 0)
            infoClass.getField("emoticon").set(info, emoticon)
            val sender = com.bettersticker.core.HookRuntime.findClass(
                "com.tencent.mobileqq.emoticonview.sender.PicEmoticonInfoSender")
                ?: return false
            val edit = findChatInput(activity) ?: return false
            val m = sender.methods.firstOrNull {
                it.name == "send" && it.parameterCount == 5
            } ?: return false
            activity.runOnUiThread {
                try {
                    m.invoke(null, info, runtime, activity, edit, session)
                    Toast.makeText(activity, "已发送表情", Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    LogX.e("PicEmoticonInfo 发送失败", t)
                    Toast.makeText(activity, "发送失败: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
            true
        } catch (t: Throwable) {
            LogX.w("PicEmoticonInfo 发送入口不可用: $t")
            false
        }
    }

    private fun setField(c: Class<*>, obj: Any, name: String, value: Any?) {
        try {
            val f = c.getField(name)
            f.isAccessible = true
            f.set(obj, value)
        } catch (_: Throwable) {
        }
    }

    /** 从当前 Activity 的对象图里找 com.tencent.mobileqq.activity.aio.n（AIO session）。 */
    private fun findAioSession(activity: Activity): Any? {
        QqSessionStore.current()?.let { return it }
        val target = "com.tencent.mobileqq.activity.aio.n"
        val visited = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<Any, Boolean>()
        )
        val queue = ArrayDeque<Any>()
        queue.add(activity)
        // 明确加入 FragmentManager 里的 ChatFragment。
        for (getter in arrayOf("getSupportFragmentManager", "getFragmentManager")) {
            try {
                val fm = activity.javaClass.getMethod(getter).invoke(activity) ?: continue
                val fs = fm.javaClass.getMethod("getFragments").invoke(fm) as? List<*> ?: continue
                fs.filterNotNull().forEach { queue.add(it) }
            } catch (_: Throwable) {
            }
        }
        var steps = 0
        while (queue.isNotEmpty() && steps++ < 3000) {
            val obj = queue.removeFirst()
            if (obj == null || !visited.add(obj)) continue
            if (obj.javaClass.name == target) return obj
            var c: Class<*>? = obj.javaClass
            var depth = 0
            while (c != null && depth++ < 4) {
                for (f in c.declaredFields) {
                    if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
                    val tn = f.type.name
                    if (tn.startsWith("java.") && !tn.startsWith("java.util.")
                        && !tn.startsWith("java.lang.ref")) continue
                    if (tn.startsWith("android.") || tn.startsWith("kotlin.")) continue
                    try {
                        f.isAccessible = true
                        var v = f.get(obj) ?: continue
                        if (v is java.lang.ref.Reference<*>) {
                            v = v.get() ?: continue
                        }
                        if (v.javaClass.name == target) return v
                        val n = v.javaClass.name
                        if (n.startsWith("com.tencent.") || v is List<*> || v is Array<*>) {
                            queue.add(v)
                        }
                    } catch (_: Throwable) {
                    }
                }
                c = c.superclass
            }
        }
        return sessionFromAio(activity)
    }

    /** 从 ChatFragment.currentContext -> AIOParam -> AIOSession -> AIOContact 构造旧版 session。 */
    private fun sessionFromAio(activity: Activity): Any? {
        try {
            val aio = findAioContext(activity) ?: return null
            val param = aio.javaClass.getMethod("g").invoke(aio) ?: return null
            val session = param.javaClass.getMethod("o").invoke(param) ?: return null
            val contact = session.javaClass.getMethod("b").invoke(session) ?: return null
            val nClass = com.bettersticker.core.HookRuntime.findClass(
                "com.tencent.mobileqq.activity.aio.n") ?: return null
            val n = nClass.getDeclaredConstructor().newInstance()
            setField(nClass, n, "d", contact.javaClass.getField("d").get(contact))
            setField(nClass, n, "e", contact.javaClass.getField("e").get(contact))
            setField(nClass, n, "f", contact.javaClass.getField("e").get(contact))
            setField(nClass, n, "h", contact.javaClass.getField("e").get(contact))
            return n
        } catch (t: Throwable) {
            LogX.w("从 AIOContext 构造 session 失败: $t")
            return null
        }
    }

    private fun findAioContext(activity: Activity): Any? {
        for (getter in arrayOf("getSupportFragmentManager", "getFragmentManager")) {
            try {
                val fm = activity.javaClass.getMethod(getter).invoke(activity) ?: continue
                val fragments = fm.javaClass.getMethod("getFragments").invoke(fm) as? List<*>
                    ?: continue
                for (f in fragments) {
                    if (f == null || !f.javaClass.name.contains("ChatFragment")) continue
                    var c: Class<*>? = f.javaClass
                    while (c != null) {
                        try {
                            val field = c.getDeclaredField("currentContext")
                            field.isAccessible = true
                            val value = field.get(f)
                            if (value is java.lang.ref.WeakReference<*>) {
                                value.get()?.let { return it }
                            } else if (value != null) {
                                return value
                            }
                        } catch (_: Throwable) {
                        }
                        c = c.superclass
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return null
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
