package com.bettersticker.core;

import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.bettersticker.hook.ActivityTracker;
import com.bettersticker.shared.LogX;

/**
 * View 树探针：真机适配的主要信息来源。
 * 只写模块自己的数据目录，不采集文本内容之外的信息，不发网络。
 */
public final class Probe {

    private static final int MAX_TREE_LINES = 500;
    private static volatile long lastWrite;

    private Probe() {
    }

    public static void onLongClick(View v) {
        if (v == null || "bs_own".equals(v.getTag())) {
            return;
        }
        String chain = chainOf(v, 4);
        LogX.probe("长按链: " + chain);
        long now = System.currentTimeMillis();
        if (now - lastWrite < 500) {
            return;
        }
        lastWrite = now;
        append("LONGCLICK " + time() + "\n" + chain + "\n\n");
    }

    public static File dumpCurrent(String tag) {
        android.app.Activity a = ActivityTracker.current();
        if (a == null) {
            LogX.w("探针: 没有前台 Activity");
            return null;
        }
        String tree = treeOf(a.getWindow().getDecorView(), 0);
        File dir = Config.dataDir();
        if (dir == null) {
            return null;
        }
        File f = new File(dir, "probe-" + tag + "-" + System.currentTimeMillis() + ".txt");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(("activity=" + a.getClass().getName() + "\n" + tree).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            LogX.e("写探针文件失败", t);
            return null;
        }
        LogX.i("探针已写出: " + f.getAbsolutePath());
        return f;
    }


    /** 专门导出输入框附近的 View 结构，便于修长按触发。 */
    public static File dumpInputs(android.app.Activity activity) {
        if (activity == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("activity=").append(activity.getClass().getName()).append('\n');
        collectInputs(activity.getWindow().getDecorView(), sb, 0);
        File dir = Config.dataDir();
        if (dir == null) {
            return null;
        }
        File f = new File(dir, "inputs-" + System.currentTimeMillis() + ".txt");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            LogX.e("写输入区探针失败", t);
            return null;
        }
        LogX.i("输入区探针已写出: " + f.getAbsolutePath());
        return f;
    }

    private static void collectInputs(View v, StringBuilder sb, int depth) {
        if (v == null || depth > 30 || sb.length() > 200000) {
            return;
        }
        if (v instanceof android.widget.EditText) {
            sb.append("\n=== EditText ===\n").append(oneLine(v)).append('\n');
            View cur = v;
            for (int level = 0; level < 6 && cur.getParent() instanceof View; level++) {
                View parent = (View) cur.getParent();
                sb.append("  parent[").append(level).append("] ").append(oneLine(parent)).append('\n');
                if (parent instanceof android.view.ViewGroup) {
                    android.view.ViewGroup g = (android.view.ViewGroup) parent;
                    int n = Math.min(g.getChildCount(), 80);
                    for (int i = 0; i < n; i++) {
                        View c = g.getChildAt(i);
                        sb.append("    child[").append(i).append("] ").append(oneLine(c));
                        sb.append(" clickable=").append(c.isClickable())
                                .append(" longClickable=").append(c.isLongClickable())
                                .append(" shown=").append(c.isShown()).append('\n');
                    }
                }
                cur = parent;
            }
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            int n = Math.min(g.getChildCount(), 200);
            for (int i = 0; i < n; i++) {
                collectInputs(g.getChildAt(i), sb, depth + 1);
            }
        }
    }
    private static void append(String text) {
        File dir = Config.dataDir();
        if (dir == null) {
            return;
        }
        File f = new File(dir, "probe.log");
        try {
            if (f.length() > 512 * 1024) {
                File old = new File(dir, "probe.log.old");
                //noinspection ResultOfMethodCallIgnored
                old.delete();
                //noinspection ResultOfMethodCallIgnored
                f.renameTo(old);
            }
            try (FileOutputStream out = new FileOutputStream(f, true)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable t) {
            LogX.e("追加探针失败", t);
        }
    }

    private static String chainOf(View v, int depth) {
        StringBuilder sb = new StringBuilder();
        View cur = v;
        for (int i = 0; i < depth && cur != null; i++) {
            if (sb.length() > 0) {
                sb.append(" <- ");
            }
            sb.append(oneLine(cur));
            cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }
        return sb.toString();
    }

    private static String treeOf(View v, int depth) {
        StringBuilder sb = new StringBuilder();
        build(v, depth, sb, new int[]{0});
        return sb.toString();
    }

    private static void build(View v, int depth, StringBuilder sb, int[] lines) {
        if (v == null || depth > 40 || lines[0] >= MAX_TREE_LINES) {
            return;
        }
        lines[0]++;
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        sb.append(oneLine(v)).append('\n');
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            int n = Math.min(g.getChildCount(), 80);
            for (int i = 0; i < n; i++) {
                build(g.getChildAt(i), depth + 1, sb, lines);
            }
        }
    }

    private static String oneLine(View v) {
        StringBuilder sb = new StringBuilder();
        sb.append(v.getClass().getSimpleName());
        int id = v.getId();
        if (id != View.NO_ID) {
            sb.append("#0x").append(Integer.toHexString(id));
            try {
                sb.append("(").append(v.getResources().getResourceEntryName(id)).append(")");
            } catch (Throwable ignored) {
            }
        }
        CharSequence d = v.getContentDescription();
        if (d != null && d.length() > 0) {
            sb.append(" desc=").append(d);
        }
        if (v instanceof android.widget.TextView) {
            CharSequence t = ((android.widget.TextView) v).getText();
            if (t != null && t.length() > 0 && t.length() <= 40) {
                sb.append(" text=").append(t);
            }
        }
        sb.append(" ").append(v.getWidth()).append('x').append(v.getHeight());
        sb.append(" vis=").append(v.getVisibility());
        if (v.isClickable()) {
            sb.append(" clickable");
        }
        if (v.isLongClickable()) {
            sb.append(" longClickable");
        }
        return sb.toString();
    }

    private static String time() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
