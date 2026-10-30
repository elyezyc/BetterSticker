package com.bettersticker.core;

import android.app.Activity;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.bettersticker.shared.Env;
import com.bettersticker.shared.LogX;

/**
 * 长按触发规则。默认只按“内容描述/文本”匹配，不写死 QQ 混淆类名。
 *
 * 已知（QQ 9.3.75.42350 资源表）：
 * - 表情图标 contentDescription = "添加表情"，src drawable = qui_emoticon
 * - 语音图标未完全确认，额外用资源名 skin_aio_voice_* / qui_voice_* 兜底
 *
 * 用户可在 files/bettersticker/trigger.json 里改，不用重新编译。
 */
public final class TriggerConfig {

    private static final TriggerConfig INSTANCE = new TriggerConfig();

    public boolean requireEditText = true;
    public final List<String> contentDescriptions = new ArrayList<>();
    public final List<String> texts = new ArrayList<>();
    public final List<String> idNames = new ArrayList<>();
    public final List<String> classNames = new ArrayList<>();
    /** 真机确认原版素材目录后填写；默认空 = 不读取。 */
    public String funboxMaterialRoot = "";

    private volatile File file;
    private final java.util.Map<String, Integer> idCache = new java.util.HashMap<>();

    private TriggerConfig() {
        resetDefaults();
    }

    public static TriggerConfig get() {
        return INSTANCE;
    }

    private void resetDefaults() {
        idCache.clear();
        contentDescriptions.clear();
        contentDescriptions.add("添加表情");
        // 语音图标的 contentDescription 未在 9.3.75.42350 里确认；
        // 不把“按住说话”放进来，避免长按录音按钮误触。
        contentDescriptions.add("切换到语音");
        contentDescriptions.add("语音输入");
        texts.clear();
        idNames.clear();
        // QQ 9.3.75 聊天输入条的表情按钮资源名（res/layout/ezn.xml: id/emo_btn）
        idNames.add("emo_btn");
        classNames.clear();
        funboxMaterialRoot = "";
    }

    public synchronized void load(File dir) {
        file = new File(dir, "trigger.json");
        if (!file.isFile()) {
            writeDefault();
            return;
        }
        try {
            String json = new String(readAll(file), StandardCharsets.UTF_8);
            JSONObject o = new JSONObject(json);
            resetDefaults();
            JSONArray desc = o.optJSONArray("contentDescriptions");
            if (desc != null) {
                contentDescriptions.clear();
                for (int i = 0; i < desc.length(); i++) {
                    String s = desc.optString(i, "");
                    if (!s.isEmpty()) {
                        contentDescriptions.add(s);
                    }
                }
            }
            JSONArray tx = o.optJSONArray("texts");
            if (tx != null) {
                texts.clear();
                for (int i = 0; i < tx.length(); i++) {
                    String s = tx.optString(i, "");
                    if (!s.isEmpty()) {
                        texts.add(s);
                    }
                }
            }
            JSONArray ids = o.optJSONArray("idNames");
            if (ids != null) {
                idNames.clear();
                for (int i = 0; i < ids.length(); i++) {
                    idNames.add(ids.optString(i, ""));
                }
            }
            JSONArray cls = o.optJSONArray("classNames");
            if (cls != null) {
                classNames.clear();
                for (int i = 0; i < cls.length(); i++) {
                    classNames.add(cls.optString(i, ""));
                }
            }
            requireEditText = o.optBoolean("requireEditText", true);
            funboxMaterialRoot = o.optString("funboxMaterialRoot", "");
            LogX.i("触发器配置已加载: " + contentDescriptions.size() + " 条描述, root=" + funboxMaterialRoot);
        } catch (Throwable t) {
            LogX.e("trigger.json 解析失败，使用默认值", t);
            resetDefaults();
        }
    }

    private void writeDefault() {
        try {
            JSONObject o = new JSONObject();
            o.put("_comment", "长按触发规则；改完重启 QQ 生效。requireEditText 用于只在输入区生效。");
            o.put("requireEditText", true);
            o.put("funboxMaterialRoot", "");
            JSONArray d = new JSONArray();
            d.put("添加表情");
            d.put("切换到语音");
            d.put("语音输入");
            o.put("contentDescriptions", d);
            o.put("texts", new JSONArray());
            JSONArray ids = new JSONArray();
            ids.put("emo_btn");
            o.put("idNames", ids);
            o.put("classNames", new JSONArray());
            if (file.getParentFile() != null) {
                //noinspection ResultOfMethodCallIgnored
                file.getParentFile().mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(o.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            LogX.i("已写入默认触发器配置: " + file);
        } catch (Throwable t) {
            LogX.e("写默认触发器配置失败", t);
        }
    }

    /** 只按资源 id 名匹配（用于 Activity resume 时的兜底扫描，避免误伤消息气泡）。 */
    public String idTab(View v) {
        if (v == null || v.getId() == View.NO_ID) {
            return null;
        }
        int id = v.getId();
        for (String s : idNames) {
            if (!s.isEmpty() && idOf(s) == id) {
                return tabForText(s);
            }
        }
        return null;
    }

    /** 资源 id 名 -> id，带缓存；避免在 View 树扫描时对每个 View 做资源名查找。 */
    public int idOf(String name) {
        if (name == null || name.isEmpty()) {
            return 0;
        }
        Integer cached = idCache.get(name);
        if (cached != null) {
            return cached;
        }
        int id = 0;
        try {
            id = Env.appContext.getResources().getIdentifier(name, "id", Env.TARGET_PACKAGE);
        } catch (Throwable ignored) {
        }
        idCache.put(name, id);
        return id;
    }

    public String matchTab(View v) {
        if (v == null || "bs_own".equals(v.getTag())) {
            return null;
        }
        if (v instanceof EditText) {
            return null;
        }
        if (requireEditText && !hasEditText(v.getRootView(), 0)) {
            return null;
        }
        String desc = descOf(v);
        for (String s : contentDescriptions) {
            if (!s.isEmpty() && desc.contains(s.toLowerCase(java.util.Locale.US))) {
                return tabForText(desc);
            }
        }
        if (v instanceof TextView) {
            String text = ((TextView) v).getText() == null ? "" : ((TextView) v).getText().toString();
            String t = text.toLowerCase(java.util.Locale.US);
            for (String s : texts) {
                if (!s.isEmpty() && t.contains(s.toLowerCase(java.util.Locale.US))) {
                    return tabForText(t);
                }
            }
        }
        for (String s : idNames) {
            if (!s.isEmpty() && v.getId() != View.NO_ID && idOf(s) == v.getId()) {
                return tabForText(s);
            }
        }
        String cls = v.getClass().getSimpleName().toLowerCase(java.util.Locale.US);
        for (String s : classNames) {
            if (!s.isEmpty() && cls.contains(s.toLowerCase(java.util.Locale.US))) {
                return tabForText(cls);
            }
        }
        return null;
    }

    private static String tabForText(String s) {
        String t = s == null ? "" : s.toLowerCase(java.util.Locale.US);
        if (t.contains("语音") || t.contains("voice") || t.contains("mic")) {
            return "voice";
        }
        return "sticker";
    }

    private static String descOf(View v) {
        CharSequence d = v.getContentDescription();
        return d == null ? "" : d.toString().toLowerCase(java.util.Locale.US);
    }

    private static boolean hasEditText(View v, int depth) {
        if (depth > 24 || v == null) {
            return false;
        }
        if (v instanceof EditText) {
            return true;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (hasEditText(g.getChildAt(i), depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    public int drawableId(String name) {
        try {
            Resources r = Env.appContext.getResources();
            return r.getIdentifier(name, "drawable", Env.TARGET_PACKAGE);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static Activity activityOf(View v) {
        android.content.Context c = v.getContext();
        while (c instanceof android.content.ContextWrapper) {
            if (c instanceof Activity) {
                return (Activity) c;
            }
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static byte[] readAll(File f) throws java.io.IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) {
                    break;
                }
                off += n;
            }
            return buf;
        }
    }
}
