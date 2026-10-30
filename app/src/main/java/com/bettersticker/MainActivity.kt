package com.bettersticker

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.bettersticker.core.Config
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView

/**
 * BetterSticker 自己的活动：模块状态、QQ 进程注入情况、数据目录和快捷操作。
 * 不再向 QQ 设置页注入任何“关于 BetterSticker”条目/浮卡。
 */
class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private var registered = false
    private var gotResult = false
    private val main = Handler(Looper.getMainLooper())

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_STATUS_RESULT) return
            gotResult = true
            val status = intent.getStringExtra("status") ?: "QQ 进程未返回状态"
            statusView.text = status
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(true)
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        val toolbar = MaterialToolbar(this).apply {
            title = "BetterSticker"
            subtitle = "QQ 9.3.75 · LSPosed 模块 · v${Config.VERSION}"
            setTitleTextColor(TEXT)
            setSubtitleTextColor(MUTED)
            setPadding(0, dp(2), 0, dp(8))
        }
        root.addView(toolbar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        root.addView(section("注入状态"))
        statusView = text("正在查询 QQ 进程…", 11f, TEXT, false).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        val statusCard = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            setCardBackgroundColor(CARD)
            strokeWidth = dp(1)
            strokeColor = 0x33FFFFFF
            addView(statusView, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        root.addView(statusCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        val refreshRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        refreshRow.addView(button("刷新状态", true) { requestStatus() }, weight(1f))
        refreshRow.addView(button("复制", false) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("BetterSticker status", statusView.text))
            toast("已复制")
        }, weight(1f).apply { leftMargin = dp(8) })
        root.addView(refreshRow)

        root.addView(section("快捷操作"))
        val openRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        openRow.addView(button("打开收藏面板", true) {
            sendToQq("com.bettersticker.action.OPEN")
        }, weight(1f))
        openRow.addView(button("打开 QQ", false) {
            val i = packageManager.getLaunchIntentForPackage(QQ_PKG)
            if (i == null) toast("未安装 QQ") else startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, weight(1f).apply { leftMargin = dp(8) })
        root.addView(openRow)

        val exportRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        exportRow.addView(button("导出到 Download", false) {
            sendToQq("com.bettersticker.action.EXPORT")
        }, weight(1f))
        exportRow.addView(button("刷新状态", false) { requestStatus() }, weight(1f).apply {
            leftMargin = dp(8)
        })
        root.addView(exportRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        root.addView(section("数据目录"))
        val dataText = text(QQ_DATA_PATH, 12f, TEXT, false).apply {
            typeface = Typeface.MONOSPACE
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setTextIsSelectable(true)
        }
        val dataCard = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            setCardBackgroundColor(CARD)
            strokeWidth = dp(1)
            strokeColor = 0x33FFFFFF
            addView(dataText, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        root.addView(dataCard)
        root.addView(text("配置、收藏素材和 probe 日志都在这里。导出文件在 Download/BetterSticker。",
            11f, MUTED, false).apply { setPadding(0, dp(8), 0, 0) })

        root.addView(text("此页面运行在 BetterSticker 自己的进程中；上面的注入状态由 QQ 进程通过广播实时返回，不再在 QQ 设置里放任何入口。",
            11f, MUTED, false).apply { setPadding(0, dp(22), 0, 0) })

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    override fun onStart() {
        super.onStart()
        if (!registered) {
            val filter = IntentFilter(ACTION_STATUS_RESULT)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(statusReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(statusReceiver, filter)
            }
            registered = true
        }
        requestStatus()
    }

    override fun onStop() {
        if (registered) {
            try {
                unregisterReceiver(statusReceiver)
            } catch (_: Throwable) {
            }
            registered = false
        }
        super.onStop()
    }

    private fun requestStatus() {
        gotResult = false
        statusView.text = "正在查询 QQ 进程…"
        val request = Intent(ACTION_REQUEST_STATUS).setPackage(QQ_PKG)
        sendBroadcast(request)
        main.postDelayed({
            if (!gotResult && !isFinishing) {
                statusView.text = "未收到 QQ 进程响应。\n\n" +
                    "请确认：QQ 正在运行、LSPosed 已启用 BetterSticker、作用域包含 QQ。\n" +
                    "然后点“刷新状态”。"
            }
        }, 1800)
    }

    private fun sendToQq(action: String) {
        sendBroadcast(Intent(action).setPackage(QQ_PKG))
        toast("已发送: $action")
    }

    private fun section(title: String): TextView = text(title, 13f, ACCENT, true).apply {
        setPadding(0, dp(16), 0, dp(8))
    }

    private fun text(s: CharSequence, sp: Float, color: Int, bold: Boolean): TextView =
        MaterialTextView(this).apply {
            text = s
            textSize = sp
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun button(label: String, primary: Boolean, click: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            cornerRadius = dp(20)
            insetTop = 0
            insetBottom = 0
            minHeight = dp(46)
            val primaryColor = MaterialColors.getColor(this@MainActivity,
                com.google.android.material.R.attr.colorPrimary, ACCENT)
            val onPrimary = MaterialColors.getColor(this@MainActivity,
                com.google.android.material.R.attr.colorOnPrimary, 0xFF101012.toInt())
            setTextColor(if (primary) onPrimary else TEXT)
            backgroundTintList = ColorStateList.valueOf(if (primary) primaryColor else CARD)
            setOnClickListener { click() }
        }

    private fun card(): GradientDrawable = GradientDrawable().apply {
        setColor(CARD)
        cornerRadius = dp(12).toFloat()
        setStroke(dp(1), 0x33FFFFFF)
    }

    private fun weight(w: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w)

    private fun dp(v: Int): Int = Math.round(v * resources.displayMetrics.density)

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        private const val QQ_PKG = "com.tencent.mobileqq"
        private const val ACTION_REQUEST_STATUS = "com.bettersticker.action.REQUEST_STATUS"
        private const val ACTION_STATUS_RESULT = "com.bettersticker.action.STATUS_RESULT"
        private const val QQ_DATA_PATH = "/sdcard/Android/media/com.tencent.mobileqq/.bs"
        private val BG = 0xFF121214.toInt()
        private val CARD = 0xFF1F1F21.toInt()
        private val TEXT = 0xFFF2F2F5.toInt()
        private val MUTED = 0xFF9A9AA6.toInt()
        private val ACCENT = 0xFF4FD1C5.toInt()
    }
}
