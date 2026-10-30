package com.bettersticker.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 轻量 Material 3 风格对话框。用代码绘制，不依赖 QQ 进程里的模块 Material 主题，
 * 避免 Material 组件要求 Theme.AppCompat 导致的“显示面板失败”。
 */
public final class Md3Dialog {

    public interface OnItem {
        void onItem(int index);
    }

    public interface OnInput {
        void onInput(String value);
    }

    private Md3Dialog() {
    }

    public static void showList(Activity activity, String title, String[] items, OnItem callback) {
        Dialog dialog = base(activity);
        LinearLayout root = surface(activity);
        root.addView(title(activity, title));
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            TextView row = Ui.text(activity, items[i], 15, Ui.TEXT_PRIMARY, false);
            row.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 13),
                    Ui.dp(activity, 16), Ui.dp(activity, 13));
            row.setBackground(ripple());
            row.setClickable(true);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (callback != null) callback.onItem(index);
            });
            root.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        root.addView(buttonRow(activity, dialog, "取消", false, null));
        dialog.setContentView(root);
        show(activity, dialog);
    }

    public static void showInput(Activity activity, String title, String message,
                                 String value, String positive,
                                 OnInput callback, Runnable neutral) {
        Dialog dialog = base(activity);
        LinearLayout root = surface(activity);
        root.addView(title(activity, title));
        if (message != null && !message.isEmpty()) {
            TextView msg = Ui.text(activity, message, 13, Ui.TEXT_SECONDARY, false);
            msg.setLineSpacing(Ui.dp(activity, 2), 1f);
            msg.setPadding(0, Ui.dp(activity, 8), 0, Ui.dp(activity, 14));
            root.addView(msg);
        }
        EditText input = new EditText(activity);
        input.setText(value == null ? "" : value);
        input.setSelection(input.getText().length());
        input.setTextSize(15);
        input.setTextColor(Ui.TEXT_PRIMARY);
        input.setHintTextColor(Ui.TEXT_SECONDARY);
        input.setHint("输入内容");
        input.setSingleLine(false);
        input.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 12),
                Ui.dp(activity, 16), Ui.dp(activity, 12));
        input.setBackground(Ui.rounded(Ui.BG_CARD, 14, activity, 0x33FFFFFF, 1));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.setMargins(0, 0, 0, Ui.dp(activity, 8));
        root.addView(input, ilp);

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, Ui.dp(activity, 10), 0, 0);
        if (neutral != null) {
            TextView n = Ui.text(activity, "选择目录", 14, Ui.ACCENT, true);
            n.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 10),
                    Ui.dp(activity, 12), Ui.dp(activity, 10));
            n.setBackground(ripple());
            n.setOnClickListener(v -> {
                dialog.dismiss();
                neutral.run();
            });
            buttons.addView(n);
        }
        TextView cancel = Ui.text(activity, "取消", 14, Ui.TEXT_SECONDARY, true);
        cancel.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 10),
                Ui.dp(activity, 12), Ui.dp(activity, 10));
        cancel.setBackground(ripple());
        cancel.setOnClickListener(v -> dialog.dismiss());
        buttons.addView(cancel);

        TextView ok = Ui.button(activity, positive, true);
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        okLp.setMargins(Ui.dp(activity, 8), 0, 0, 0);
        ok.setOnClickListener(v -> {
            dialog.dismiss();
            if (callback != null) callback.onInput(input.getText().toString().trim());
        });
        buttons.addView(ok, okLp);
        root.addView(buttons);
        dialog.setContentView(root);
        show(activity, dialog);
        input.requestFocus();
    }

    public static void confirm(Activity activity, String title, String message,
                               String positive, Runnable callback) {
        Dialog dialog = base(activity);
        LinearLayout root = surface(activity);
        root.addView(title(activity, title));
        TextView msg = Ui.text(activity, message, 13, Ui.TEXT_SECONDARY, false);
        msg.setLineSpacing(Ui.dp(activity, 2), 1f);
        msg.setPadding(0, Ui.dp(activity, 10), 0, Ui.dp(activity, 8));
        root.addView(msg);
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, Ui.dp(activity, 12), 0, 0);
        TextView cancel = Ui.text(activity, "取消", 14, Ui.TEXT_SECONDARY, true);
        cancel.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 10),
                Ui.dp(activity, 12), Ui.dp(activity, 10));
        cancel.setBackground(ripple());
        cancel.setOnClickListener(v -> dialog.dismiss());
        buttons.addView(cancel);
        TextView ok = Ui.button(activity, positive, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(activity, 8), 0, 0, 0);
        ok.setOnClickListener(v -> {
            dialog.dismiss();
            if (callback != null) callback.run();
        });
        buttons.addView(ok, lp);
        root.addView(buttons);
        dialog.setContentView(root);
        show(activity, dialog);
    }

    public static void showMessage(Activity activity, String title, String message) {
        Dialog dialog = base(activity);
        LinearLayout root = surface(activity);
        root.addView(title(activity, title));
        TextView msg = Ui.text(activity, message, 13, Ui.TEXT_SECONDARY, false);
        msg.setLineSpacing(Ui.dp(activity, 2), 1f);
        msg.setPadding(0, Ui.dp(activity, 10), 0, Ui.dp(activity, 4));
        root.addView(msg);
        root.addView(buttonRow(activity, dialog, "关闭", true, null));
        dialog.setContentView(root);
        show(activity, dialog);
    }

    private static Dialog base(Activity activity) {
        Dialog d = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return d;
    }

    private static LinearLayout surface(Activity activity) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(activity, 22), Ui.dp(activity, 20),
                Ui.dp(activity, 22), Ui.dp(activity, 16));
        root.setBackground(Ui.rounded(0xFF1C1B1F, 28, activity, 0x33FFFFFF, 1));
        root.setElevation(Ui.dp(activity, 16));
        return root;
    }

    private static TextView title(Activity activity, String title) {
        return Ui.text(activity, title, 20, Ui.TEXT_PRIMARY, true);
    }

    private static LinearLayout buttonRow(Activity activity, Dialog dialog,
                                          String label, boolean primary, Runnable callback) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, Ui.dp(activity, 14), 0, 0);
        TextView b = Ui.button(activity, label, primary);
        b.setOnClickListener(v -> {
            dialog.dismiss();
            if (callback != null) callback.run();
        });
        row.addView(b);
        return row;
    }

    private static RippleDrawable ripple() {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null,
                new ColorDrawable(Color.WHITE));
    }

    private static void show(Activity activity, Dialog dialog) {
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.90f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.45f);
        }
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }
}
