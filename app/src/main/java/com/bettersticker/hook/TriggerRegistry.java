package com.bettersticker.hook;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.bettersticker.core.HookRuntime;
import com.bettersticker.core.TriggerConfig;
import com.bettersticker.shared.LogX;
import com.bettersticker.shared.hook.XHooker;

/**
 * 通过资源名识别表情/语音图标，不依赖 QQ 混淆类名。
 *
 * QQ 用 android:src="@drawable/qui_emoticon" 充气表情图标时，ImageView.setImageResource
 * 会拿到 drawable id；我们把该 View 记下来，长按时即可判定。资源名变化时还有
 * TriggerConfig 的 contentDescription 兜底。
 */
public final class TriggerRegistry {

    private static final Map<View, String> TABS =
            Collections.synchronizedMap(new WeakHashMap<View, String>());
    /** 资源 id -> tab。只对少量候选 id 做匹配，避免 setImageResource 高频调用时做资源名查找。 */
    private static final Map<Integer, String> ID_TABS = new HashMap<>();
    private static final Map<View, Boolean> OBSERVED = new WeakHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile long lastScan;
    private static volatile boolean installed;
    private static volatile String lastError = "";
    private static final java.util.concurrent.atomic.AtomicLong TOUCH_FIRED =
            new java.util.concurrent.atomic.AtomicLong();

    private TriggerRegistry() {
    }

    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        try {
            // 先解析候选 drawable 名，之后只做整数比较。
            addCandidate("qui_emoticon", "sticker");
            // QQ 9.3.75 聊天输入条（res/layout/ezn.xml）用的是 oversized 选择器
            addCandidate("qui_emoticon_aio_oversized_light_selector", "sticker");
            addCandidate("qui_emoticon_aio_oversized_selector", "sticker");
            addCandidate("qui_emoticon_selector", "sticker");

            addCandidate("skin_aio_voice_nor", "voice");
            addCandidate("skin_aio_voice_button_normal", "voice");
            addCandidate("qui_voice_filled_new", "voice");
            addCandidate("qui_voice_filled_white", "voice");
            addCandidate("qui_voice_filled_white_selector", "voice");
            addCandidate("qui_microphone_on_aio_oversized_light_selector", "voice");
            addCandidate("qui_microphone_on_aio_oversized_selector", "voice");
            addCandidate("qui_microphone_on_selector", "voice");
            addCandidate("qui_ptt_aio_icon_aio_toolbar_normal", "voice");
            Method setImage = HookRuntime.findMethod(ImageView.class, "setImageResource", "int");
            if (setImage != null) {
                XHooker.hookAfter(setImage, param -> {
                    if (!(param.obj instanceof ImageView) || param.args.length < 1
                            || !(param.args[0] instanceof Integer)) {
                        return;
                    }
                    int id = (Integer) param.args[0];
                    String tab = tabForId((View) param.obj, id, false);
                    if (tab != null) {
                        mark((View) param.obj, tab);
                    }
                });
            }
            Method setBg = HookRuntime.findMethod(View.class, "setBackgroundResource", "int");
            if (setBg != null) {
                XHooker.hookAfter(setBg, param -> {
                    if (!(param.obj instanceof View) || param.args.length < 1
                            || !(param.args[0] instanceof Integer)) {
                        return;
                    }
                    int id = (Integer) param.args[0];
                    String tab = tabForId((View) param.obj, id, true);
                    if (tab != null) {
                        mark((View) param.obj, tab);
                    }
                });
            }
            LogX.i("TriggerRegistry 已安装（资源名识别 + 输入区兜底扫描）");
        } catch (Throwable t) {
            lastError = String.valueOf(t);
            LogX.e("TriggerRegistry 安装失败", t);
        }
    }

    private static void addCandidate(String drawableName, String tab) {
        int id = TriggerConfig.get().drawableId(drawableName);
        if (id != 0) {
            ID_TABS.put(id, tab);
            LogX.i("候选图标 " + drawableName + " = 0x" + Integer.toHexString(id) + " -> " + tab);
        } else {
            LogX.w("资源名未找到: " + drawableName + "（用 contentDescription 兜底）");
        }
    }

    private static String tabForId(View v, int id, boolean background) {
        if (id == 0 || v == null) {
            return null;
        }
        String tab = ID_TABS.get(id);
        if (tab != null) {
            LogX.probe("资源命中 " + (background ? "background" : "src")
                    + " id=0x" + Integer.toHexString(id) + " -> " + tab
                    + " view=" + v.getClass().getSimpleName());
        }
        return tab;
    }

    /** 供输入区兜底扫描使用：同一 View 只标记一次。 */
    public static void markExternal(View v, String tab) {
        if (v == null || tab == null) {
            return;
        }
        // 重新挂监听：QQ 从后台回来/重建 View 后可能清掉 listener；
        // 不能因为 TABS 里已有这个 View 就跳过。
        LogX.i("输入区兜底标记: " + v.getClass().getSimpleName()
                + " tab=" + tab + " desc=" + v.getContentDescription());
        mark(v, tab);
    }

    private static void mark(View v, String tab) {
        if (v == null) {
            return;
        }
        TABS.put(v, tab);
        // 普通 ImageView 默认不是 longClickable，performLongClick 不会被调度；
        // 给命中的图标显式挂一个长按监听，才能稳定弹出面板。
        v.setOnLongClickListener(v2 -> {
            String t = TABS.get(v2);
            if (t == null) {
                return false;
            }
            com.bettersticker.ui.PanelController.showFor(v2, t);
            return true;
        });
        // 再挂一层触摸长按：有些 QQ 自定义 View 不会走 performLongClick / OnLongClickListener。
        // 普通点击返回 false，不吞 QQ 的点击；按住 500ms 才打开面板。
        final ViewConfiguration vc = ViewConfiguration.get(v.getContext());
        final int slop = vc.getScaledTouchSlop();
        final float[] down = new float[2];
        final boolean[] moved = new boolean[1];
        final boolean[] fired = new boolean[1];
        final Runnable[] longPress = new Runnable[1];
        v.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getX();
                    down[1] = event.getY();
                    moved[0] = false;
                    fired[0] = false;
                    if (longPress[0] != null) {
                        view.removeCallbacks(longPress[0]);
                    }
                    longPress[0] = () -> {
                        if (moved[0]) {
                            return;
                        }
                        String t = TABS.get(view);
                        if (t != null) {
                            fired[0] = true;
                            TOUCH_FIRED.incrementAndGet();
                            LogX.i("触摸长按命中: " + view.getClass().getSimpleName() + " -> " + t);
                            com.bettersticker.ui.PanelController.showFor(view, t);
                        }
                    };
                    view.postDelayed(longPress[0], 500);
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getX() - down[0]) > slop
                            || Math.abs(event.getY() - down[1]) > slop) {
                        moved[0] = true;
                        if (longPress[0] != null) {
                            view.removeCallbacks(longPress[0]);
                        }
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    moved[0] = true;
                    if (longPress[0] != null) {
                        view.removeCallbacks(longPress[0]);
                    }
                    // 已经弹出面板就吞掉这次 UP，避免 QQ 再触发一次点击/表情面板。
                    return fired[0];
                default:
                    return false;
            }
        });
    }

    /** 长按的可能是图标内部子 View，向上找 4 层。 */
    public static String tabOf(View v) {
        View cur = v;
        for (int i = 0; i < 4 && cur != null; i++) {
            String tab = TABS.get(cur);
            if (tab != null) {
                return tab;
            }
            if (!(cur.getParent() instanceof View)) {
                break;
            }
            cur = (View) cur.getParent();
        }
        return null;
    }

    public static int size() {
        return TABS.size();
    }


    /**
     * Activity resume 后的兜底扫描：即使 QQ 用 setImageDrawable（拿不到资源 id），
     * 也能按 id/emo_btn 找到表情按钮并挂长按监听。
     */
    public static void scanActivity(Activity activity) {
        if (activity == null) {
            return;
        }
        MAIN.postDelayed(() -> {
            try {
                View root = activity.getWindow().getDecorView();
                if (root == null) {
                    return;
                }
                if (OBSERVED.containsKey(root)) {
                    // 之前扫过但输入条可能还没 inflate；每次 resume 再扫一遍。
                    scan(root);
                    return;
                }
                OBSERVED.put(root, Boolean.TRUE);
                final ViewTreeObserver vto = root.getViewTreeObserver();
                final int[] passes = {0};
                ViewTreeObserver.OnGlobalLayoutListener listener =
                        new ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                long now = SystemClock.uptimeMillis();
                                if (now - lastScan < 800) {
                                    return;
                                }
                                lastScan = now;
                                passes[0]++;
                                boolean found = scan(root);
                                // 多扫两轮，等输入条图标全部 inflate 完；避免只标记到一个图标就停。
                                if ((found && passes[0] >= 3) || passes[0] > 8) {
                                    if (vto.isAlive()) {
                                        vto.removeOnGlobalLayoutListener(this);
                                    }
                                }
                            }
                        };
                vto.addOnGlobalLayoutListener(listener);
                scan(root);
            } catch (Throwable t) {
                LogX.e("扫描 Activity 失败", t);
            }
        }, 400);
    }

    /** @return 是否找到了可标记的触发 View（找到后就不再持续扫描整棵树）。 */
    private static boolean scan(View root) {
        boolean found = scanTree(root);
        // 即使 emo_btn 没命中，也尝试从 AIOEditText 周围的小图标兜底。
        found |= markInputIcons(root);
        found |= markDescriptionIcons(root);
        if (hasChatRoot(root)) {
            found |= markBottomIcons(root, root.getResources().getDisplayMetrics().heightPixels);
        }
        return found;
    }

    private static boolean hasChatRoot(View v) {
        if (v == null || v.getClass().getName().contains("ChatFragmentRootView")
                || v.getClass().getName().contains("AIOGestureLayout")) {
            return true;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            int n = Math.min(g.getChildCount(), 200);
            for (int i = 0; i < n; i++) {
                if (hasChatRoot(g.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * QTool 的做法：输入条图标可能带 contentDescription（如“拉起表情面板”），
     * 不依赖 id/类名，直接按描述挂长按。
     */
    private static boolean markDescriptionIcons(View v) {
        if (v == null || !v.isShown()) {
            return false;
        }
        boolean found = false;
        if (v instanceof ImageView || v instanceof TextView || v.isClickable()) {
            int w = v.getWidth();
            int h = v.getHeight();
            float density = v.getResources().getDisplayMetrics().density;
            if (w > 0 && h > 0 && w <= Math.round(90 * density)
                    && h <= Math.round(90 * density)) {
                String desc = v.getContentDescription() == null ? ""
                        : v.getContentDescription().toString().toLowerCase(java.util.Locale.US);
                if (desc.contains("表情") || desc.contains("emoji")
                        || desc.contains("emoticon")) {
                    if (!desc.contains("发送") && tabOf(v) == null) {
                        markExternal(v, "sticker");
                        found = true;
                    }
                } else if (desc.contains("语音") || desc.contains("voice")
                        || desc.contains("mic") || desc.contains("按住说话")) {
                    if (!desc.contains("发送") && tabOf(v) == null) {
                        markExternal(v, "voice");
                        found = true;
                    }
                }
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            int n = Math.min(g.getChildCount(), 200);
            for (int i = 0; i < n; i++) {
                found |= markDescriptionIcons(g.getChildAt(i));
            }
        }
        return found;
    }

    /** 聊天页底部输入条兜底：不依赖 AIOEditText 是否能找到。 */
    private static boolean markBottomIcons(View v, int screenH) {
        if (v == null || !v.isShown()) {
            return false;
        }
        boolean found = false;
        int w = v.getWidth();
        int h = v.getHeight();
        float density = v.getResources().getDisplayMetrics().density;
        if (v instanceof ImageView || v instanceof TextView || v.isClickable()) {
            if (w > 0 && h > 0 && w <= Math.round(80 * density) && h <= Math.round(80 * density)) {
                int[] loc = new int[2];
                v.getLocationOnScreen(loc);
                if (loc[1] > screenH * 0.68f) {
                    String desc = v.getContentDescription() == null ? ""
                            : v.getContentDescription().toString();
                    String text = v instanceof TextView && ((TextView) v).getText() != null
                            ? ((TextView) v).getText().toString() : "";
                    String idName = "";
                    try {
                        if (v.getId() != View.NO_ID) {
                            idName = v.getResources().getResourceEntryName(v.getId());
                        }
                    } catch (Throwable ignored) {
                    }
                    String all = (desc + " " + text + " " + idName)
                            .toLowerCase(java.util.Locale.US);
                    if (!all.contains("发送") && !all.contains("send")
                            && !idName.contains("send") && !idName.contains("plus")
                            && !idName.contains("add") && tabOf(v) == null) {
                        String tab = all.contains("语音") || all.contains("voice")
                                || all.contains("mic") ? "voice" : "sticker";
                        markExternal(v, tab);
                        found = true;
                    }
                }
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            int n = Math.min(g.getChildCount(), 200);
            for (int i = 0; i < n; i++) {
                found |= markBottomIcons(g.getChildAt(i), screenH);
            }
        }
        return found;
    }

    private static boolean scanTree(View v) {
        if (v == null) {
            return false;
        }
        boolean found = false;
        String tab = TriggerConfig.get().idTab(v);
        if (tab == null) {
            // QQ 9.3.75.42350 聊天输入条（res/layout/ezn.xml）的固定资源 id 兜底。
            // id 在 QQ 升级后可能变化；只是最后一道保险，不替代 trigger.json。
            int id = v.getId();
            if (id == 0x7f0a1ecb) {
                tab = "sticker";
            } else if (id == 0x7f0a9457) {
                tab = "voice";
            }
        }
        if (tab != null) {
            mark(v, tab);
            found = true;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            int n = Math.min(g.getChildCount(), 200);
            for (int i = 0; i < n; i++) {
                found |= scanTree(g.getChildAt(i));
            }
        }
        return found;
    }

    /**
     * 兜底：找到聊天输入框 AIOEditText，把它周围 4 层内的小图标都挂上长按。
     * 这样即使 QQ 用 setImageDrawable / 自定义 View / 改了 id，也能长按表情/语音。
     * 排除发送/加号，避免误触。
     */
    private static boolean markInputIcons(View v) {
        if (v == null) {
            return false;
        }
        boolean found = false;
        if (v instanceof EditText && isChatInput((EditText) v)) {
            EditText edit = (EditText) v;
            View cur = edit;
            for (int level = 0; level < 6 && cur.getParent() instanceof View; level++) {
                View parent = (View) cur.getParent();
                if (parent instanceof ViewGroup) {
                    ViewGroup g = (ViewGroup) parent;
                    int n = Math.min(g.getChildCount(), 40);
                    for (int i = 0; i < n; i++) {
                        found |= maybeMarkIcon(g.getChildAt(i), edit);
                    }
                }
                cur = parent;
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            int n = Math.min(g.getChildCount(), 200);
            for (int i = 0; i < n; i++) {
                found |= markInputIcons(g.getChildAt(i));
            }
        }
        return found;
    }

    private static boolean isChatInput(EditText edit) {
        try {
            String cn = edit.getClass().getName();
            if (cn.contains("AIOEditText") || cn.contains("InputEditText")) {
                return true;
            }
            if (edit.getId() != View.NO_ID) {
                String id = edit.getResources().getResourceEntryName(edit.getId());
                if ("input".equals(id)) {
                    return true;
                }
            }
            if (edit.isShown()) {
                int[] loc = new int[2];
                edit.getLocationOnScreen(loc);
                int screenH = edit.getResources().getDisplayMetrics().heightPixels;
                return loc[1] > screenH * 0.45f;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean maybeMarkIcon(View child, EditText edit) {
        if (child == null || child == edit || child instanceof EditText) {
            return false;
        }
        if ("bs_own".equals(child.getTag()) || !child.isShown()) {
            return false;
        }
        if (child instanceof ViewGroup) {
            return false;
        }
        // 不限定 ImageView/TextView：QQ 可能用自定义 View 承载图标。
        if (!(child instanceof ImageView || child instanceof TextView)
                && !child.isClickable() && !child.isLongClickable()) {
            return false;
        }
        int w = child.getWidth();
        int h = child.getHeight();
        float density = child.getResources().getDisplayMetrics().density;
        int max = Math.round(80 * density);
        if (w <= 0 || h <= 0 || w > max || h > max) {
            return false;
        }
        String desc = child.getContentDescription() == null ? ""
                : child.getContentDescription().toString();
        String text = child instanceof TextView && ((TextView) child).getText() != null
                ? ((TextView) child).getText().toString() : "";
        String idName = "";
        try {
            if (child.getId() != View.NO_ID) {
                idName = child.getResources().getResourceEntryName(child.getId());
            }
        } catch (Throwable ignored) {
        }
        String all = (desc + " " + text + " " + idName).toLowerCase(java.util.Locale.US);
        if (all.contains("发送") || all.contains("send")
                || idName.toLowerCase(java.util.Locale.US).contains("send")
                || idName.toLowerCase(java.util.Locale.US).contains("plus")
                || idName.toLowerCase(java.util.Locale.US).contains("add")) {
            return false;
        }
        if (tabOf(child) != null) {
            return false;
        }
        String tab;
        if (all.contains("语音") || all.contains("voice") || all.contains("mic")) {
            tab = "voice";
        } else if (all.contains("表情") || all.contains("emoji") || all.contains("emoticon")) {
            tab = "sticker";
        } else {
            // 布局常识：语音图标在输入框左侧，表情/更多在右侧。
            try {
                int[] eLoc = new int[2];
                int[] cLoc = new int[2];
                edit.getLocationOnScreen(eLoc);
                child.getLocationOnScreen(cLoc);
                tab = cLoc[0] < eLoc[0] ? "voice" : "sticker";
            } catch (Throwable t) {
                tab = "sticker";
            }
        }
        LogX.i("输入区图标命中: " + child.getClass().getSimpleName()
                + " id=" + idName + " desc=" + desc + " -> " + tab);
        markExternal(child, tab);
        return true;
    }


    /**
     * 长按发生时的最后兜底：不依赖之前的扫描，直接看这个 View 是否在 AIOEditText 附近。
     */
    public static String matchOnTheFly(View v) {
        if (v == null || "bs_own".equals(v.getTag()) || !v.isShown()) {
            return null;
        }
        if (v instanceof EditText) {
            return null;
        }
        if (v instanceof ViewGroup) {
            return null;
        }
        if (!(v instanceof ImageView || v instanceof TextView)
                && !v.isClickable() && !v.isLongClickable()) {
            return null;
        }
        int w = v.getWidth();
        int h = v.getHeight();
        float density = v.getResources().getDisplayMetrics().density;
        int max = Math.round(90 * density);
        if (w <= 0 || h <= 0 || w > max || h > max) {
            return null;
        }
        EditText edit = findChatEdit(v.getRootView(), 0);
        if (edit == null || !nearInput(v, edit, 6)) {
            return null;
        }
        String desc = v.getContentDescription() == null ? ""
                : v.getContentDescription().toString();
        String text = v instanceof TextView && ((TextView) v).getText() != null
                ? ((TextView) v).getText().toString() : "";
        String idName = "";
        try {
            if (v.getId() != View.NO_ID) {
                idName = v.getResources().getResourceEntryName(v.getId());
            }
        } catch (Throwable ignored) {
        }
        String all = (desc + " " + text + " " + idName).toLowerCase(java.util.Locale.US);
        if (all.contains("发送") || all.contains("send")
                || idName.contains("send") || idName.contains("plus") || idName.contains("add")) {
            return null;
        }
        String tab;
        if (all.contains("语音") || all.contains("voice") || all.contains("mic")) {
            tab = "voice";
        } else if (all.contains("表情") || all.contains("emoji") || all.contains("emoticon")) {
            tab = "sticker";
        } else {
            try {
                int[] eLoc = new int[2];
                int[] cLoc = new int[2];
                edit.getLocationOnScreen(eLoc);
                v.getLocationOnScreen(cLoc);
                tab = cLoc[0] < eLoc[0] ? "voice" : "sticker";
            } catch (Throwable t) {
                tab = "sticker";
            }
        }
        LogX.i("长按即时兜底命中: " + v.getClass().getSimpleName()
                + " id=" + idName + " desc=" + desc + " -> " + tab);
        markExternal(v, tab);
        return tab;
    }

    private static EditText findChatEdit(View v, int depth) {
        if (v == null || depth > 30) {
            return null;
        }
        if (v instanceof EditText && isChatInput((EditText) v)) {
            return (EditText) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            int n = Math.min(g.getChildCount(), 200);
            for (int i = 0; i < n; i++) {
                EditText e = findChatEdit(g.getChildAt(i), depth + 1);
                if (e != null) {
                    return e;
                }
            }
        }
        return null;
    }

    /** v 与输入框在 4 层内共享祖先，说明它在输入条上而不是消息列表里。 */
    private static boolean nearInput(View v, View edit, int levels) {
        View cur = v;
        for (int i = 0; i < levels && cur != null; i++) {
            if (isAncestor(cur, edit)) {
                return true;
            }
            cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }
        return false;
    }

    private static boolean isAncestor(View ancestor, View child) {
        View cur = child;
        while (cur != null) {
            if (cur == ancestor) {
                return true;
            }
            cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }
        return false;
    }
    /** 调试广播用：立即扫描当前 Activity，返回是否命中。 */
    public static String scanNow(Activity activity) {
        if (activity == null) {
            return "no-activity";
        }
        try {
            View root = activity.getWindow().getDecorView();
            boolean found = scan(root);
            return "found=" + found + " " + status();
        } catch (Throwable t) {
            return "error=" + t + " " + status();
        }
    }

    public static String status() {
        String emo = "?";
        try {
            emo = "0x" + Integer.toHexString(TriggerConfig.get().idOf("emo_btn"));
        } catch (Throwable ignored) {
        }
        return "候选drawable=" + ID_TABS.size()
                + " 已标记View=" + TABS.size()
                + " emo_btn=" + emo
                + " 扫描=" + (lastScan > 0 ? "已运行" : "未运行")
                + " 触摸长按=" + TOUCH_FIRED.get()
                + (lastError.isEmpty() ? "" : " 错误=" + lastError);
    }
}
