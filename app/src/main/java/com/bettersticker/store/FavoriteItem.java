package com.bettersticker.store;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 一条收藏记录。这是 BetterSticker 自己的数据模型，不是 FunBox 的原版格式。 */
public final class FavoriteItem {

    public static final String TYPE_STICKER = "sticker";
    public static final String TYPE_VOICE = "voice";

    public String id;
    public String type;
    /** stickers/xxx.webp 或 voices/xxx.silk，相对模块数据目录。 */
    public String storedFile;
    public String originalName;
    public String mime;
    public long size;
    public String sha256;
    public String source;
    public long savedAt;
    public String savedAtIso;
    public int width;
    public int height;
    public int durationMs;
    public String exportName;
    /** ready / unverified；unverified 的条目不会在导出时被假装成可用格式。 */
    public String status = "ready";
    /** 所属分组；空/未知时归入 default。 */
    public String groupId = "";
    /** 是否在系统“收藏”分组里。 */
    public boolean favorite;

    public FavoriteItem() {
    }

    public boolean isSticker() {
        return TYPE_STICKER.equals(type);
    }

    public boolean isVoice() {
        return TYPE_VOICE.equals(type);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("type", type);
        o.put("storedFile", storedFile);
        o.put("originalName", originalName == null ? "" : originalName);
        o.put("mime", mime == null ? "" : mime);
        o.put("size", size);
        o.put("sha256", sha256 == null ? "" : sha256);
        o.put("source", source == null ? "" : source);
        o.put("savedAt", savedAt);
        o.put("savedAtIso", savedAtIso == null ? iso(savedAt) : savedAtIso);
        o.put("width", width);
        o.put("height", height);
        o.put("durationMs", durationMs);
        o.put("exportName", exportName == null ? "" : exportName);
        o.put("status", status == null ? "ready" : status);
        o.put("groupId", groupId == null ? "" : groupId);
        o.put("favorite", favorite);
        return o;
    }

    public static FavoriteItem fromJson(JSONObject o) {
        FavoriteItem it = new FavoriteItem();
        it.id = o.optString("id", "");
        it.type = o.optString("type", TYPE_STICKER);
        it.storedFile = o.optString("storedFile", "");
        it.originalName = o.optString("originalName", "");
        it.mime = o.optString("mime", "");
        it.size = o.optLong("size", 0L);
        it.sha256 = o.optString("sha256", "");
        it.source = o.optString("source", "");
        it.savedAt = o.optLong("savedAt", 0L);
        it.savedAtIso = o.optString("savedAtIso", iso(it.savedAt));
        it.width = o.optInt("width", 0);
        it.height = o.optInt("height", 0);
        it.durationMs = o.optInt("durationMs", 0);
        it.exportName = o.optString("exportName", "");
        it.status = o.optString("status", "ready");
        it.groupId = o.optString("groupId", "");
        it.favorite = o.optBoolean("favorite", false);
        return it;
    }

    public static String iso(long millis) {
        if (millis <= 0) {
            millis = System.currentTimeMillis();
        }
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        return f.format(new Date(millis));
    }
}
