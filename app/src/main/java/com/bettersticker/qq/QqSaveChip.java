package com.bettersticker.qq;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import com.bettersticker.shared.LogX;
import com.bettersticker.store.FavoriteStore;
import com.bettersticker.ui.PanelController;

/**
 * 长按消息后，在 QQ 菜单之外显示一个小的“收藏”按钮。
 *
 * 不修改 QQ 自己的菜单项（避免依赖混淆的 menu id 回调），点击后把 QQ 本地文件
 * 存进 BetterSticker 的收藏库；不会下载任何东西。
 */
public final class QqSaveChip {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static View current;

    private QqSaveChip() {
    }

    public static void show(Activity activity, List<QqMedia> medias, View anchor) {
        if (activity == null || medias == null || medias.isEmpty()) {
            return;
        }
        MAIN.post(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                dismiss();
                final TextView chip = new TextView(activity);
                chip.setTag("bs_own");
                int n = medias.size();
                String label = n == 1
                        ? ("★ 收藏到 BetterSticker（" + (medias.get(0).isVoice() ? "语音" : "表情") + "）")
                        : ("★ 收藏 " + n + " 项到 BetterSticker");
                chip.setText(label);
                chip.setTextColor(Color.WHITE);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                chip.setGravity(Gravity.CENTER);
                int px = dp(activity, 14);
                chip.setPadding(px, dp(activity, 9), px, dp(activity, 9));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xF0303038);
                bg.setCornerRadius(dp(activity, 22));
                bg.setStroke(dp(activity, 1), 0x66FFFFFF);
                chip.setBackground(bg);
                chip.setElevation(dp(activity, 6));

                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                // 放在顶部：QQ 长按菜单在底部，放底部会被菜单盖住。
                lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                lp.topMargin = dp(activity, 140);
                activity.addContentView(chip, lp);
                chip.setOnClickListener(v -> {
                    dismiss();
                    saveAsync(activity, medias);
                });
                current = chip;
                MAIN.postDelayed(() -> {
                    if (current == chip) {
                        dismiss();
                    }
                }, 6000);
                LogX.i("显示收藏按钮: " + label);
            } catch (Throwable t) {
                LogX.e("显示收藏按钮失败", t);
            }
        });
    }

    private static void saveAsync(Activity activity, List<QqMedia> medias) {
        new Thread(() -> {
            int ok = 0;
            String firstError = null;
            FavoriteStore store = FavoriteStore.get();
            for (QqMedia m : medias) {
                try {
                    if (m.isVoice()) {
                        store.addVoice(m.data, m.originalName, m.source, 0);
                    } else {
                        store.addSticker(m.data, m.originalName, m.source, m.width, m.height);
                    }
                    ok++;
                } catch (Throwable t) {
                    LogX.e("收藏失败: " + m.originalName, t);
                    if (firstError == null) {
                        firstError = t.getMessage();
                    }
                }
            }
            final int okFinal = ok;
            final String err = firstError;
            MAIN.post(() -> {
                if (activity.isFinishing()) {
                    return;
                }
                String msg;
                if (okFinal > 0) {
                    msg = "已收藏 " + okFinal + " 项到 BetterSticker";
                    PanelController.refreshIfOpen();
                } else {
                    msg = "收藏失败: " + err;
                }
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
            });
        }, "BetterSticker-save").start();
    }

    public static void dismiss() {
        View v = current;
        current = null;
        if (v != null && v.getParent() instanceof ViewGroup) {
            try {
                ((ViewGroup) v.getParent()).removeView(v);
            } catch (Throwable ignored) {
            }
        }
    }

    public static Activity activityOf(View view) {
        if (view == null) {
            return null;
        }
        Context c = view.getContext();
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) {
                return (Activity) c;
            }
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
