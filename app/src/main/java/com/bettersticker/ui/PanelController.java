package com.bettersticker.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.lang.ref.WeakReference;

import com.bettersticker.core.TriggerConfig;
import com.bettersticker.hook.ActivityTracker;
import com.bettersticker.shared.LogX;
import com.bettersticker.store.FavoriteItem;

/**
 * 面板窗口控制。用 Activity 的 Dialog（不是 QQ 的 View 层级），
 * 关闭后不残留窗口；QQ 退出/Activity pause 时主动 dismiss。
 */
public final class PanelController {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Dialog> dialogRef = new WeakReference<>(null);
    private static WeakReference<PanelView> panelRef = new WeakReference<>(null);
    private static WeakReference<Activity> ownerRef = new WeakReference<>(null);

    private PanelController() {
    }

    public static void init(Context context) {
        LogX.i("PanelController 初始化");
    }

    public static void showFor(View anchor, String tab) {
        Activity activity = anchor == null ? null : TriggerConfig.activityOf(anchor);
        if (activity == null) {
            activity = ActivityTracker.current();
        }
        show(activity, tab);
    }

    public static void showCurrent(String tab) {
        show(ActivityTracker.current(), tab);
    }

    public static void show(Activity activity, String tab) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            toast("没有可用的 QQ 界面，请先进入聊天");
            return;
        }
        if (tab == null) {
            tab = FavoriteItem.TYPE_STICKER;
        }
        final String finalTab = tab;
        MAIN.post(() -> {
            try {
                Dialog existing = dialogRef.get();
                PanelView pv = panelRef.get();
                if (existing != null && existing.isShowing() && pv != null) {
                    pv.switchTab(finalTab);
                    return;
                }
                dismiss();
                PanelView panel = new PanelView(activity, finalTab, PanelController::dismiss);
                Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setContentView(panel);
                dialog.setCanceledOnTouchOutside(true);
                dialog.setCancelable(true);
                Window w = dialog.getWindow();
                if (w != null) {
                    w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                    w.setDimAmount(0.38f);
                    w.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                    int width = activity.getResources().getDisplayMetrics().widthPixels
                            - (int) (24 * activity.getResources().getDisplayMetrics().density);
                    w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
                }
                dialog.setOnDismissListener(d -> {
                    dialogRef = new WeakReference<>(null);
                    panelRef = new WeakReference<>(null);
                    ownerRef = new WeakReference<>(null);
                });
                dialog.show();
                Window shown = dialog.getWindow();
                if (shown != null) {
                    int width = activity.getResources().getDisplayMetrics().widthPixels
                            - (int) (24 * activity.getResources().getDisplayMetrics().density);
                    shown.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
                }
                dialogRef = new WeakReference<>(dialog);
                panelRef = new WeakReference<>(panel);
                ownerRef = new WeakReference<>(activity);
                LogX.i("面板已显示 tab=" + finalTab + " activity=" + activity.getClass().getName());
            } catch (Throwable t) {
                LogX.e("显示面板失败", t);
                toast("显示面板失败: " + t.getMessage());
            }
        });
    }

    public static void refreshIfOpen() {
        MAIN.post(() -> {
            PanelView pv = panelRef.get();
            Dialog d = dialogRef.get();
            if (pv != null && d != null && d.isShowing()) {
                pv.refresh();
            }
        });
    }

    public static boolean isOpen() {
        Dialog d = dialogRef.get();
        return d != null && d.isShowing();
    }

    public static void dismiss() {
        Dialog d = dialogRef.get();
        dialogRef = new WeakReference<>(null);
        panelRef = new WeakReference<>(null);
        ownerRef = new WeakReference<>(null);
        if (d != null) {
            try {
                d.dismiss();
            } catch (Throwable ignored) {
            }
        }
    }

    public static void dismissFor(Activity activity) {
        Activity owner = ownerRef.get();
        if (owner != null && owner == activity) {
            dismiss();
        }
    }

    private static void toast(String msg) {
        Context c = com.bettersticker.shared.Env.appContext;
        if (c != null) {
            Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
        }
    }
}
