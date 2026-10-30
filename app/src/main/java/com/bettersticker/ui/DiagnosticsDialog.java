package com.bettersticker.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import com.bettersticker.core.Bootstrap;
import com.bettersticker.core.Config;
import com.bettersticker.core.Probe;
import com.bettersticker.format.SilkDecoders;
import com.bettersticker.hook.ActivityTracker;
import com.bettersticker.hook.LongPressHook;
import com.bettersticker.hook.TriggerRegistry;
import com.bettersticker.qq.QqMenuHook;
import com.bettersticker.qq.QqMenuItemHook;
import com.bettersticker.shared.Env;
import com.bettersticker.store.FavoriteStore;

/**
 * “关于 BetterSticker”诊断页：证明模块已注入，并把 hook/触发状态摊开给用户看。
 * 只展示本地状态，不读取聊天内容、不联网。
 */
public final class DiagnosticsDialog {

    private DiagnosticsDialog() {
    }

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        try {
            Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(Ui.dp(activity, 18), Ui.dp(activity, 16),
                    Ui.dp(activity, 18), Ui.dp(activity, 16));
            root.setBackground(Ui.rounded(Ui.BG_PANEL, 18, activity, 0x33FFFFFF, 1));

            TextView title = Ui.text(activity, "关于 BetterSticker", 19, Ui.TEXT_PRIMARY, true);
            root.addView(title);

            TextView sub = Ui.text(activity, "LSPosed 注入状态 / Hook / 触发诊断", 11, Ui.TEXT_SECONDARY, false);
            sub.setPadding(0, Ui.dp(activity, 4), 0, Ui.dp(activity, 10));
            root.addView(sub);

            ScrollView scroll = new ScrollView(activity);
            TextView info = Ui.text(activity, statusText(), 11, Ui.TEXT_PRIMARY, false);
            info.setTypeface(Typeface.MONOSPACE);
            info.setTextIsSelectable(true);
            info.setMovementMethod(new ScrollingMovementMethod());
            info.setLineSpacing(Ui.dp(activity, 3), 1f);
            scroll.addView(info, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 400));
            root.addView(scroll, slp);

            LinearLayout buttons = new LinearLayout(activity);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setPadding(0, Ui.dp(activity, 12), 0, 0);

            TextView open = Ui.button(activity, "打开收藏面板", true);
            open.setOnClickListener(v -> {
                dialog.dismiss();
                PanelController.show(activity, "sticker");
            });
            buttons.addView(open, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));

            TextView probe = Ui.button(activity, "导出 View 树", false);
            probe.setOnClickListener(v -> {
                File f = Probe.dumpCurrent("diag");
                Toast.makeText(activity, f == null ? "导出失败（见日志）"
                        : "已导出: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
            });
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            plp.setMargins(Ui.dp(activity, 8), 0, 0, 0);
            buttons.addView(probe, plp);

            TextView close = Ui.button(activity, "关闭", false);
            close.setOnClickListener(v -> dialog.dismiss());
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f);
            clp.setMargins(Ui.dp(activity, 8), 0, 0, 0);
            buttons.addView(close, clp);

            root.addView(buttons);
            dialog.setContentView(root);

            Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                w.setGravity(Gravity.CENTER);
                w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                w.setDimAmount(0.45f);
            }
            dialog.show();
        } catch (Throwable t) {
            Toast.makeText(activity, "打开诊断页失败: " + t, Toast.LENGTH_LONG).show();
        }
    }

    /** QQ 进程内的实时状态；App 自己的活动通过广播读取。 */
    public static String statusText() {
        StringBuilder sb = new StringBuilder();
        sb.append("版本: ").append(Config.VERSION).append('\n');
        sb.append("注入: ").append(Bootstrap.isAppReady() ? "已注入（LSPosed 已加载）" : "未完成").append('\n');
        sb.append("进程: ").append(Env.processName)
                .append("  pid=").append(android.os.Process.myPid()).append('\n');
        sb.append("SDK: ").append(Env.sdkInt).append('\n');
        sb.append("QQ: com.tencent.mobileqq\n");
        sb.append("当前 Activity: ").append(safe(ActivityTracker.currentName())).append('\n');
        String dataDir;
        try {
            dataDir = String.valueOf(Env.moduleDataDir());
        } catch (Throwable t) {
            dataDir = "未初始化";
        }
        sb.append("数据目录: ").append(dataDir).append('\n');
        try {
            FavoriteStore s = FavoriteStore.get();
            sb.append("收藏: ").append(s.count("sticker")).append(" 表情 / ")
                    .append(s.count("voice")).append(" 语音，")
                    .append(s.groups().size()).append(" 分组，")
                    .append(s.itemsInGroup(com.bettersticker.store.FavoriteGroup.FAVORITES_ID).size())
                    .append(" 收藏项\n");
        } catch (Throwable t) {
            sb.append("收藏: 读取失败 ").append(t).append('\n');
        }
        sb.append('\n');
        sb.append("Hook:\n");
        sb.append("  长按: ").append(LongPressHook.status()).append('\n');
        sb.append("  原生菜单项: ").append(QqMenuItemHook.status()).append('\n');
        sb.append("  直插菜单: ").append(com.bettersticker.qq.QqMenuItemFactory.added()).append('\n');
        sb.append("  浮动菜单兜底: ").append(QqMenuHook.status()).append('\n');
        sb.append("  触发: ").append(TriggerRegistry.status()).append('\n');
        sb.append('\n');
        sb.append("导出: MediaStore -> Download/BetterSticker\n");
        sb.append("SILK: ").append(SilkDecoders.isAvailable() ? "可用" : "未配置（不能生成 WAV）").append('\n');
        File root = Config.funboxMaterialRoot();
        sb.append("原版素材: ").append(root == null ? "未配置 funboxMaterialRoot" : root.getAbsolutePath()).append('\n');
        sb.append('\n');
        sb.append("提示：如果长按没反应，点“导出 View 树”，把文件路径/内容发回。\n");
        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "-" : s;
    }
}
