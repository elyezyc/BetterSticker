package com.bettersticker.store;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.bettersticker.format.ImageSniffer;
import com.bettersticker.shared.Env;
import com.bettersticker.shared.LogX;
import com.bettersticker.shared.Storage;

/**
 * 收藏存储。落在 QQ 私有目录 files/bettersticker/：
 *   index.json
 *   stickers/...
 *   voices/...
 *
 * 注意：模块运行在 QQ 进程里，没有权限写 /data/data/com.bettersticker，
 * 所以这里用的是 QQ 的 filesDir；卸载/清除 QQ 数据会一起清掉。
 */
public final class FavoriteStore {

    private static volatile FavoriteStore instance;

    private final File root;
    private final File stickers;
    private final File voices;
    private final File indexFile;
    private final List<FavoriteItem> items = new ArrayList<>();
    private final List<FavoriteGroup> groups = new ArrayList<>();
    private final Object lock = new Object();

    private FavoriteStore(Context context) {
        root = Storage.dataDir(context);
        Storage.migrateOldInternal(context, root);
        stickers = new File(root, "stickers");
        voices = new File(root, "voices");
        indexFile = new File(root, "index.json");
    }

    public static void init(Context context) {
        if (instance == null) {
            synchronized (FavoriteStore.class) {
                if (instance == null) {
                    FavoriteStore s = new FavoriteStore(context);
                    s.load();
                    instance = s;
                }
            }
        }
    }

    public static FavoriteStore get() {
        FavoriteStore s = instance;
        if (s == null) {
            throw new IllegalStateException("FavoriteStore 尚未初始化");
        }
        return s;
    }

    public File root() {
        return root;
    }

    public File indexFile() {
        return indexFile;
    }

    private void load() {
        synchronized (lock) {
            ensureDirs();
            items.clear();
            items.addAll(IndexJson.read(indexFile));
            groups.clear();
            groups.addAll(IndexJson.readGroups(indexFile));
            reconcileFiles();
            normalizeGroups();
            sortNewestFirst();
            persistQuietly();
            LogX.i("收藏索引加载完成: " + items.size() + " 条, root=" + root);
        }
    }

    public void reload() {
        synchronized (lock) {
            items.clear();
            items.addAll(IndexJson.read(indexFile));
            groups.clear();
            groups.addAll(IndexJson.readGroups(indexFile));
            reconcileFiles();
            normalizeGroups();
            sortNewestFirst();
        }
    }

    private void ensureDirs() {
        if (!root.exists() && !root.mkdirs()) {
            LogX.w("无法创建收藏目录: " + root);
        }
        if (!stickers.exists() && !stickers.mkdirs()) {
            LogX.w("无法创建表情目录: " + stickers);
        }
        if (!voices.exists() && !voices.mkdirs()) {
            LogX.w("无法创建语音目录: " + voices);
        }
    }

    /** 文件已落盘但索引没写成功时，尽量把孤儿文件补回索引。 */
    private void reconcileFiles() {
        List<FavoriteItem> known = new ArrayList<>(items);
        for (FavoriteItem it : known) {
            if (it.storedFile == null || it.storedFile.isEmpty()) {
                continue;
            }
            File f = new File(root, it.storedFile);
            if (!f.isFile()) {
                items.remove(it);
            }
        }
        scanDir(stickers, FavoriteItem.TYPE_STICKER);
        scanDir(voices, FavoriteItem.TYPE_VOICE);
    }

    private void scanDir(File dir, String type) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (!f.isFile() || f.getName().endsWith(".tmp")) {
                continue;
            }
            if (f.length() > 64L * 1024 * 1024) {
                LogX.w("跳过超大文件: " + f);
                continue;
            }
            String rel = type.equals(FavoriteItem.TYPE_STICKER)
                    ? "stickers/" + f.getName()
                    : "voices/" + f.getName();
            boolean exists = false;
            for (FavoriteItem it : items) {
                if (rel.equals(it.storedFile)) {
                    exists = true;
                    break;
                }
            }
            if (exists) {
                continue;
            }
            try {
                byte[] data = readAll(f);
                ImageSniffer.Format fmt = ImageSniffer.detect(data);
                FavoriteItem it = new FavoriteItem();
                it.id = UUID.randomUUID().toString();
                it.type = type;
                it.storedFile = rel;
                it.originalName = f.getName();
                it.mime = fmt.mime;
                it.size = data.length;
                it.sha256 = sha256(data);
                it.source = "";
                it.savedAt = f.lastModified() > 0 ? f.lastModified() : System.currentTimeMillis();
                it.savedAtIso = FavoriteItem.iso(it.savedAt);
                it.status = "ready";
                items.add(it);
                LogX.w("发现孤儿文件并补入索引: " + rel);
            } catch (Throwable t) {
                LogX.e("补索引失败: " + f, t);
            }
        }
    }

    public List<FavoriteItem> items() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(items));
        }
    }

    public List<FavoriteGroup> groups() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(groups));
        }
    }

    public FavoriteGroup groupById(String id) {
        synchronized (lock) {
            for (FavoriteGroup g : groups) {
                if (g.id.equals(id)) {
                    return g;
                }
            }
            return null;
        }
    }

    public FavoriteGroup ensureGroup(String name) {
        synchronized (lock) {
            String clean = FavoriteStore.safeName(name, "未命名分组");
            for (FavoriteGroup g : groups) {
                if (!g.favorite && g.name.equalsIgnoreCase(clean)) {
                    return g;
                }
            }
            String id = "g_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            FavoriteGroup g = new FavoriteGroup(id, clean, false, false);
            groups.add(g);
            persistQuietly();
            return g;
        }
    }

    public FavoriteGroup createGroup(String name) {
        return ensureGroup(name);
    }

    public boolean renameGroup(String id, String name) {
        synchronized (lock) {
            FavoriteGroup g = groupById(id);
            if (g == null || g.builtin) {
                return false;
            }
            g.name = FavoriteStore.safeName(name, g.name);
            persistQuietly();
            return true;
        }
    }

    public boolean deleteGroup(String id) {
        synchronized (lock) {
            FavoriteGroup g = groupById(id);
            if (g == null || g.builtin) {
                return false;
            }
            for (FavoriteItem it : items) {
                if (id.equals(it.groupId)) {
                    it.groupId = FavoriteGroup.DEFAULT_ID;
                }
            }
            groups.remove(g);
            persistQuietly();
            return true;
        }
    }

    public List<FavoriteItem> itemsInGroup(String groupId) {
        synchronized (lock) {
            List<FavoriteItem> out = new ArrayList<>();
            for (FavoriteItem it : items) {
                if (groupId == null) {
                    out.add(it);
                } else if (FavoriteGroup.FAVORITES_ID.equals(groupId)) {
                    if (it.favorite) {
                        out.add(it);
                    }
                } else if (groupId.equals(it.groupId)) {
                    out.add(it);
                }
            }
            return out;
        }
    }

    public boolean setFavorite(String id, boolean favorite) {
        synchronized (lock) {
            FavoriteItem it = findById(id);
            if (it == null) {
                return false;
            }
            it.favorite = favorite;
            persistQuietly();
            return true;
        }
    }

    public boolean toggleFavorite(String id) {
        synchronized (lock) {
            FavoriteItem it = findById(id);
            if (it == null) {
                return false;
            }
            it.favorite = !it.favorite;
            persistQuietly();
            return it.favorite;
        }
    }

    public int markGroupFavorite(String groupId) {
        synchronized (lock) {
            int n = 0;
            for (FavoriteItem it : items) {
                if (groupId == null || groupId.equals(it.groupId)) {
                    if (!it.favorite) {
                        it.favorite = true;
                        n++;
                    }
                }
            }
            if (n > 0) {
                persistQuietly();
            }
            return n;
        }
    }

    public boolean moveToGroup(String itemId, String groupId) {
        synchronized (lock) {
            FavoriteItem it = findById(itemId);
            if (it == null) {
                return false;
            }
            FavoriteGroup g = groupById(groupId);
            if (g == null || g.favorite) {
                return false;
            }
            it.groupId = groupId;
            persistQuietly();
            return true;
        }
    }

    public int count(String type) {
        synchronized (lock) {
            int n = 0;
            for (FavoriteItem it : items) {
                if (type == null || type.equals(it.type)) {
                    n++;
                }
            }
            return n;
        }
    }

    public FavoriteItem findById(String id) {
        synchronized (lock) {
            for (FavoriteItem it : items) {
                if (it.id != null && it.id.equals(id)) {
                    return it;
                }
            }
            return null;
        }
    }

    public FavoriteItem addSticker(byte[] data, String originalName, String source, int width, int height)
            throws IOException {
        ImageSniffer.Format fmt = ImageSniffer.detect(data);
        if (!fmt.image) {
            throw new IOException("不是可识别的标准图片（PNG/GIF/WebP/JPEG），"
                    + "头部=" + ImageSniffer.hexPrefix(data, 16)
                    + "；不做猜测/解密");
        }
        return add(FavoriteItem.TYPE_STICKER, data, originalName, source, fmt, width, height, 0, null);
    }

    public FavoriteItem addSticker(byte[] data, String originalName, String source,
                                   int width, int height, String groupId)
            throws IOException {
        ImageSniffer.Format fmt = ImageSniffer.detect(data);
        if (!fmt.image) {
            throw new IOException("不是可识别的标准图片（PNG/GIF/WebP/JPEG），"
                    + "头部=" + ImageSniffer.hexPrefix(data, 16) + "；不做猜测/解密");
        }
        return add(FavoriteItem.TYPE_STICKER, data, originalName, source, fmt, width, height, 0, groupId);
    }

    public FavoriteItem addVoice(byte[] data, String originalName, String source, int durationMs)
            throws IOException {
        ImageSniffer.Format fmt = ImageSniffer.detect(data);
        if (fmt != ImageSniffer.Format.SILK) {
            throw new IOException("不是 SILK v3（头部=" + ImageSniffer.hexPrefix(data, 16) + "）；"
                    + "不做猜测/解密");
        }
        return add(FavoriteItem.TYPE_VOICE, data, originalName, source, fmt, 0, 0, durationMs, null);
    }

    private FavoriteItem add(String type, byte[] data, String originalName, String source,
                             ImageSniffer.Format fmt, int width, int height, int durationMs,
                             String groupId)
            throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("空数据");
        }
        String hash = sha256(data);
        synchronized (lock) {
            for (FavoriteItem existing : items) {
                if (hash.equalsIgnoreCase(existing.sha256)) {
                    // 重复导入时允许更新分组，避免之前导进“FunBox 迁移”的旧条目
                    // 在重新导入后仍停留在旧分组。
                    if (groupId != null && !groupId.isEmpty()
                            && !FavoriteGroup.FAVORITES_ID.equals(groupId)
                            && !groupId.equals(existing.groupId)) {
                        existing.groupId = groupId;
                        persistQuietly();
                    }
                    LogX.i("重复收藏，复用已有条目: " + existing.storedFile);
                    return existing;
                }
            }
            ensureDirs();
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String file = type + "_" + ts + "_" + hash.substring(0, 8) + "." + fmt.extension;
            File dir = FavoriteItem.TYPE_STICKER.equals(type) ? stickers : voices;
            File out = new File(dir, file);
            writeFile(out, data);

            FavoriteItem it = new FavoriteItem();
            it.id = UUID.randomUUID().toString();
            it.type = type;
            it.storedFile = (FavoriteItem.TYPE_STICKER.equals(type) ? "stickers/" : "voices/") + file;
            it.originalName = safeName(originalName, type + "_" + ts + "." + fmt.extension);
            it.mime = fmt.mime;
            it.size = data.length;
            it.sha256 = hash;
            it.source = source == null ? "" : source;
            it.savedAt = System.currentTimeMillis();
            it.savedAtIso = FavoriteItem.iso(it.savedAt);
            it.width = width;
            it.height = height;
            it.durationMs = durationMs;
            it.status = "ready";
            it.groupId = groupId == null || groupId.isEmpty()
                    ? FavoriteGroup.DEFAULT_ID : groupId;
            items.add(it);
            sortNewestFirst();
            try {
                IndexJson.write(indexFile, items, groups);
            } catch (IOException e) {
                LogX.e("索引写入失败（文件已保存）: " + it.storedFile, e);
                throw e;
            }
            LogX.i("已收藏 " + it.type + " -> " + it.storedFile + " (" + data.length + "B)");
            return it;
        }
    }

    public boolean delete(String id) {
        synchronized (lock) {
            FavoriteItem target = null;
            for (FavoriteItem it : items) {
                if (it.id != null && it.id.equals(id)) {
                    target = it;
                    break;
                }
            }
            if (target == null) {
                return false;
            }
            File f = resolveStored(target.storedFile);
            if (f != null && f.isFile() && !f.delete()) {
                LogX.w("删除文件失败: " + f);
            }
            items.remove(target);
            try {
                IndexJson.write(indexFile, items, groups);
            } catch (IOException e) {
                LogX.e("删除后索引写入失败", e);
            }
            return true;
        }
    }

    public void clearAll() {
        synchronized (lock) {
            for (FavoriteItem it : new ArrayList<>(items)) {
                File f = resolveStored(it.storedFile);
                if (f != null && f.isFile()) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
            items.clear();
            try {
                IndexJson.write(indexFile, items, groups);
            } catch (IOException e) {
                LogX.e("清空索引失败", e);
            }
        }
    }

    public byte[] read(FavoriteItem item) throws IOException {
        File f = resolveStored(item.storedFile);
        if (f == null || !f.isFile()) {
            throw new IOException("文件不存在: " + item.storedFile);
        }
        return readAll(f);
    }

    public File firstStickerFile() {
        try {
            for (FavoriteItem it : items()) {
                if (FavoriteItem.TYPE_STICKER.equals(it.type)) {
                    File f = fileOf(it);
                    if (f != null && f.isFile()) return f;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public File fileOf(FavoriteItem item) {
        return resolveStored(item.storedFile);
    }

    private File resolveStored(String rel) {
        if (rel == null || rel.isEmpty()) {
            return null;
        }
        try {
            File f = new File(root, rel);
            String canonicalRoot = root.getCanonicalPath() + File.separator;
            String canonicalFile = f.getCanonicalPath();
            if (!canonicalFile.startsWith(canonicalRoot)) {
                LogX.w("拒绝越界路径: " + rel);
                return null;
            }
            return f;
        } catch (IOException e) {
            return null;
        }
    }

    public void updateExportName(String id, String exportName) {
        synchronized (lock) {
            FavoriteItem it = findById(id);
            if (it != null) {
                it.exportName = exportName;
                try {
                    IndexJson.write(indexFile, items, groups);
                } catch (IOException e) {
                    LogX.e("更新导出名失败", e);
                }
            }
        }
    }

    /** 批量修改导出名之后统一落索引，避免每条都写一次。 */
    public void flushIndex() {
        synchronized (lock) {
            try {
                IndexJson.write(indexFile, items, groups);
            } catch (IOException e) {
                LogX.e("索引写入失败", e);
            }
        }
    }

    private void normalizeGroups() {
        List<FavoriteGroup> defaults = FavoriteGroup.defaults();
        for (FavoriteGroup d : defaults) {
            FavoriteGroup found = null;
            for (FavoriteGroup g : groups) {
                if (d.id.equals(g.id)) {
                    found = g;
                    break;
                }
            }
            if (found == null) {
                groups.add(d);
            } else {
                found.favorite = d.favorite;
                found.builtin = true;
                if (found.name == null || found.name.isEmpty()) {
                    found.name = d.name;
                }
            }
        }
        for (FavoriteItem it : items) {
            if (it.groupId == null || it.groupId.isEmpty() || groupById(it.groupId) == null) {
                it.groupId = FavoriteGroup.DEFAULT_ID;
            }
        }
    }

    private void sortNewestFirst() {
        Collections.sort(items, (a, b) -> Long.compare(b.savedAt, a.savedAt));
    }

    private void persistQuietly() {
        try {
            IndexJson.write(indexFile, items, groups);
        } catch (IOException e) {
            LogX.e("初始化索引写入失败", e);
        }
    }

    private static void writeFile(File file, byte[] data) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建目录: " + parent);
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
            out.flush();
            out.getFD().sync();
        }
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

    public static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(String.format(Locale.US, "%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public static String safeName(String name, String fallback) {
        String s = name == null ? "" : name.trim();
        if (s.isEmpty()) {
            s = fallback;
        }
        s = s.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_");
        if (s.length() > 80) {
            s = s.substring(0, 80);
        }
        return s.isEmpty() ? "item" : s;
    }
}
