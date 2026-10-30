package com.bettersticker.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.TextView;

/** 轻量 UI 工具，避免引入 AndroidX/Material 依赖。 */
public final class Ui {

    // Material Design 3 dark palette（surface / surface-container / secondary-container）
    public static final int BG_PANEL = 0xFF1C1B1F;
    public static final int BG_CARD = 0xFF2B2930;
    public static final int BG_CARD_SELECTED = 0xFF4A4458;
    public static final int TEXT_PRIMARY = 0xFFE6E1E5;
    public static final int TEXT_SECONDARY = 0xFFCAC4D0;
    public static final int ACCENT = 0xFF6EE7B7;
    public static final int DANGER = 0xFFFF6B6B;

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(int color, float radiusDp, Context c, int strokeColor, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) {
            d.setStroke(dp(c, strokeDp), strokeColor);
        }
        return d;
    }

    public static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        if (bold) {
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        }
        tv.setIncludeFontPadding(false);
        return tv;
    }

    public static TextView button(Context c, CharSequence s, boolean primary) {
        TextView tv = text(c, s, 14, primary ? 0xFF101014 : TEXT_PRIMARY, true);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(dp(c, 18), dp(c, 11), dp(c, 18), dp(c, 11));
        tv.setBackground(rounded(primary ? ACCENT : BG_CARD, 20, c, 0, 0));
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

    public static int colorWithAlpha(int color, float alpha) {
        int a = Math.round(Color.alpha(color) * alpha);
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
