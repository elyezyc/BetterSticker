package com.bettersticker.ui;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.widget.TextView;

import com.bettersticker.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.textview.MaterialTextView;

/**
 * 在 QQ 进程里为面板准备真正的 Material 3 主题。
 *
 * 难点：QQ 的 Activity 主题不是 AppCompat，直接用 Activity Context 创建
 * Chip/MaterialButton 会抛 "requires Theme.AppCompat"。
 *
 * 这里优先使用模块自己的 ContextThemeWrapper；如果资源没被识别，则把模块
 * APK 的 resources 注入到 Activity 的 AssetManager，再包一层带 Material 主题的
 * Context。两条路都不通时回退到自绘 MD3，保证面板不崩。
 */
public final class Md3Theme {

    private static final String MODULE = "com.elyezyc.bettersticker";

    private Md3Theme() {
    }

    /** @return 可用的 Material3 Context，失败返回 null。 */
    public static Context wrap(Activity activity) {
        Context module = null;
        try {
            module = activity.createPackageContext(MODULE, 0);
            ContextThemeWrapper themed = new ContextThemeWrapper(
                    module, R.style.Theme_BetterSticker_Material3);
            if (isMaterial(themed)) return themed;
        } catch (Throwable ignored) {
        }
        try {
            ApplicationInfo ai = activity.getPackageManager()
                    .getApplicationInfo(MODULE, 0);
            AssetManager am = activity.getAssets();
            // AssetManager.addAssetPath 是 hidden API，按反射调用。
            java.lang.reflect.Method addAssetPath = AssetManager.class
                    .getMethod("addAssetPath", String.class);
            addAssetPath.invoke(am, ai.sourceDir);
            Resources base = activity.getResources();
            Resources res = new Resources(am, base.getDisplayMetrics(),
                    base.getConfiguration());
            final Resources finalRes = res;
            final AssetManager finalAm = am;
            ContextThemeWrapper themed = new ContextThemeWrapper(activity, 0) {
                @Override
                public Resources getResources() {
                    return finalRes;
                }

                @Override
                public AssetManager getAssets() {
                    return finalAm;
                }
            };
            themed.getTheme().applyStyle(R.style.Theme_BetterSticker_Material3, true);
            if (isMaterial(themed)) return themed;
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static boolean isMaterial(Context c) {
        if (c == null) return false;
        try {
            TypedValue tv = new TypedValue();
            boolean primary = c.getTheme().resolveAttribute(
                    com.google.android.material.R.attr.colorPrimary, tv, true);
            boolean appcompat = c.getTheme().resolveAttribute(
                    androidx.appcompat.R.attr.colorPrimary, tv, true);
            return primary && appcompat;
        } catch (Throwable t) {
            return false;
        }
    }

    public static TextView text(Context theme, Activity activity, CharSequence s,
                                float sp, int color, boolean bold) {
        if (theme != null) {
            MaterialTextView tv = new MaterialTextView(theme);
            tv.setText(s);
            tv.setTextSize(sp);
            tv.setTextColor(color);
            if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
            tv.setIncludeFontPadding(false);
            return tv;
        }
        return Ui.text(activity, s, sp, color, bold);
    }

    public static TextView button(Context theme, Activity activity,
                                  CharSequence s, boolean primary) {
        if (theme != null) {
            MaterialButton b = new MaterialButton(theme);
            b.setText(s);
            b.setTextSize(14);
            b.setAllCaps(false);
            b.setCornerRadius(Ui.dp(activity, 20));
            b.setInsetTop(0);
            b.setInsetBottom(0);
            b.setMinHeight(Ui.dp(activity, 48));
            int p = com.google.android.material.color.MaterialColors.getColor(theme,
                    com.google.android.material.R.attr.colorPrimary, Ui.ACCENT);
            int on = com.google.android.material.color.MaterialColors.getColor(theme,
                    com.google.android.material.R.attr.colorOnPrimary, 0xFF101014);
            b.setBackgroundTintList(ColorStateList.valueOf(primary ? p : Ui.BG_CARD));
            b.setTextColor(primary ? on : Ui.TEXT_PRIMARY);
            return b;
        }
        return Ui.button(activity, s, primary);
    }

    public static TextView tab(Context theme, Activity activity,
                               CharSequence s, boolean selected) {
        if (theme != null) {
            Chip chip = new Chip(theme);
            chip.setText(s);
            chip.setCheckable(true);
            chip.setChecked(selected);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setTextColor(selected ? Ui.TEXT_PRIMARY : Ui.TEXT_SECONDARY);
            chip.setChipBackgroundColor(ColorStateList.valueOf(
                    selected ? Ui.BG_CARD_SELECTED : Ui.BG_CARD));
            return chip;
        }
        TextView tv = Ui.text(activity, s, 14,
                selected ? Ui.TEXT_PRIMARY : Ui.TEXT_SECONDARY, selected);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(Ui.dp(activity, 18), Ui.dp(activity, 9),
                Ui.dp(activity, 18), Ui.dp(activity, 9));
        tv.setBackground(Ui.rounded(selected ? Ui.BG_CARD_SELECTED : Ui.BG_CARD,
                20, activity, 0, 0));
        return tv;
    }

    public static TextView chip(Context theme, Activity activity,
                                CharSequence s, boolean selected) {
        if (theme != null) {
            Chip chip = new Chip(theme);
            chip.setText(s);
            chip.setCheckable(true);
            chip.setChecked(selected);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setTextColor(selected ? Ui.TEXT_PRIMARY : Ui.TEXT_SECONDARY);
            chip.setChipBackgroundColor(ColorStateList.valueOf(
                    selected ? Ui.BG_CARD_SELECTED : Ui.BG_CARD));
            return chip;
        }
        TextView tv = Ui.text(activity, s, 12,
                selected ? Ui.TEXT_PRIMARY : Ui.TEXT_SECONDARY, selected);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 8),
                Ui.dp(activity, 14), Ui.dp(activity, 8));
        tv.setBackground(Ui.rounded(selected ? Ui.BG_CARD_SELECTED : Ui.BG_CARD,
                20, activity, 0, 0));
        return tv;
    }

    public static void select(TextView v, boolean selected, Activity activity) {
        if (v instanceof Chip) {
            Chip c = (Chip) v;
            c.setChecked(selected);
            c.setTextColor(selected ? Ui.TEXT_PRIMARY : Ui.TEXT_SECONDARY);
            c.setChipBackgroundColor(ColorStateList.valueOf(
                    selected ? Ui.BG_CARD_SELECTED : Ui.BG_CARD));
        } else {
            v.setTextColor(selected ? Ui.TEXT_PRIMARY : Ui.TEXT_SECONDARY);
            v.setTypeface(v.getTypeface(), selected
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            v.setBackground(Ui.rounded(selected ? Ui.BG_CARD_SELECTED : Ui.BG_CARD,
                    20, activity, 0, 0));
        }
    }
}
