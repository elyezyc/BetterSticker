package com.bettersticker.store;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.bettersticker.shared.LogX;

/**
 * 收藏索引。BetterSticker 自有 schema：
 * {"schemaVersion":1,"app":"BetterSticker","updatedAt":"...","items":[...]}
 *
 * 写入采用 tmp + rename，避免进程被杀时留下半个 JSON。
 */
public final class IndexJson {

    public static final int SCHEMA_VERSION = 2;

    private IndexJson() {
    }

    public static void write(File file, List<FavoriteItem> items,
                             List<FavoriteGroup> groups) throws IOException {
        JSONObject root = new JSONObject();
        try {
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("app", "BetterSticker");
            root.put("updatedAt", FavoriteItem.iso(System.currentTimeMillis()));
            JSONArray ga = new JSONArray();
            if (groups != null) {
                for (FavoriteGroup g : groups) {
                    ga.put(g.toJson());
                }
            }
            root.put("groups", ga);
            JSONArray arr = new JSONArray();
            for (FavoriteItem it : items) {
                arr.put(it.toJson());
            }
            root.put("items", arr);
        } catch (Exception e) {
            throw new IOException("生成索引 JSON 失败", e);
        }
        byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        File dir = file.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建目录: " + dir);
        }
        File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(bytes);
            out.flush();
            out.getFD().sync();
        }
        if (file.exists() && !file.delete()) {
            throw new IOException("无法替换旧索引: " + file);
        }
        if (!tmp.renameTo(file)) {
            throw new IOException("无法重命名索引: " + tmp + " -> " + file);
        }
    }

    public static List<FavoriteGroup> readGroups(File file) {
        List<FavoriteGroup> out = new ArrayList<>();
        if (file == null || !file.isFile()) {
            return out;
        }
        try {
            byte[] bytes = readAll(file);
            JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            JSONArray arr = root.optJSONArray("groups");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) {
                        out.add(FavoriteGroup.fromJson(o));
                    }
                }
            }
        } catch (Throwable t) {
            LogX.e("读取分组失败: " + file, t);
        }
        return out;
    }

    public static List<FavoriteItem> read(File file) {
        if (file == null || !file.isFile()) {
            return new ArrayList<>();
        }
        try {
            byte[] bytes = readAll(file);
            JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            JSONArray arr = root.optJSONArray("items");
            List<FavoriteItem> out = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) {
                        out.add(FavoriteItem.fromJson(o));
                    }
                }
            }
            return out;
        } catch (Throwable t) {
            LogX.e("读取 index.json 失败: " + file, t);
            // 保留坏文件供排查，不要静默覆盖。
            File bad = new File(file.getPath() + ".bad." + System.currentTimeMillis());
            //noinspection ResultOfMethodCallIgnored
            file.renameTo(bad);
            return new ArrayList<>();
        }
    }

    public static byte[] exportIndexBytes(List<FavoriteItem> items) throws IOException {
        return exportIndexBytes(items, null);
    }

    public static byte[] exportIndexBytes(List<FavoriteItem> items,
                                          List<FavoriteGroup> groups) throws IOException {
        JSONObject root = new JSONObject();
        try {
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("app", "BetterSticker");
            root.put("exportedAt", FavoriteItem.iso(System.currentTimeMillis()));
            root.put("note", "BetterSticker 自有索引，不是 FunBox 原版格式");
            JSONArray ga = new JSONArray();
            if (groups != null) {
                for (FavoriteGroup g : groups) {
                    ga.put(g.toJson());
                }
            }
            root.put("groups", ga);
            JSONArray arr = new JSONArray();
            for (FavoriteItem it : items) {
                JSONObject o = new JSONObject();
                o.put("originalName", it.originalName == null ? "" : it.originalName);
                o.put("type", it.type);
                o.put("size", it.size);
                o.put("sourceConversation", it.source == null ? "" : it.source);
                o.put("savedAt", it.savedAtIso);
                o.put("exportFile", it.exportName == null ? "" : it.exportName);
                o.put("mime", it.mime == null ? "" : it.mime);
                o.put("sha256", it.sha256 == null ? "" : it.sha256);
                o.put("status", it.status == null ? "" : it.status);
                arr.put(o);
            }
            root.put("items", arr);
        } catch (Exception e) {
            throw new IOException("生成导出索引失败", e);
        }
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readAll(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(64, f.length()));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
