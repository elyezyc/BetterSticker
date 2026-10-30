package com.bettersticker.export;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.bettersticker.format.ImageSniffer;
import com.bettersticker.format.SilkDecoder;
import com.bettersticker.format.SilkDecoders;
import com.bettersticker.format.WavWriter;
import com.bettersticker.shared.LogX;
import com.bettersticker.store.FavoriteItem;
import com.bettersticker.store.FavoriteStore;
import com.bettersticker.store.IndexJson;

/**
 * 导出服务：把收藏转成通用格式并写入 Download/BetterSticker/。
 *
 * - 图片/动态表情：已确认是 PNG/GIF/WebP/JPEG 的，按原格式原样导出；
 * - 语音：SILK v3 -> WAV(48kHz, mono)；解码器不可用时明确报错，不生成假 WAV；
 * - index.json：BetterSticker 自有字段，不是 FunBox 原版格式。
 */
public final class ExportService {

    public interface Progress {
        void onProgress(int done, int total, String current);
    }

    public static final class Result {
        public final List<String> exported = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public String indexUri;

        public boolean ok() {
            return errors.isEmpty() && !exported.isEmpty();
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("成功 ").append(exported.size()).append(" 个");
            if (!errors.isEmpty()) {
                sb.append("，失败 ").append(errors.size()).append(" 个");
            }
            return sb.toString();
        }
    }

    private final Context context;
    private final FavoriteStore store;
    private final MediaStoreExporter exporter;
    private final Object lock = new Object();

    public ExportService(Context context) {
        this.context = context.getApplicationContext();
        this.store = FavoriteStore.get();
        this.exporter = new MediaStoreExporter(this.context);
    }

    public Result exportAll(Progress progress) {
        synchronized (lock) {
            Result result = new Result();
            List<FavoriteItem> items = new ArrayList<>(store.items());
            int total = items.size();
            int done = 0;

            for (FavoriteItem item : items) {
                if (progress != null) {
                    progress.onProgress(done, total, item.originalName);
                }
                try {
                    exportOne(item);
                    result.exported.add(item.exportName);
                } catch (Throwable t) {
                    String msg = (item.originalName == null ? item.storedFile : item.originalName)
                            + ": " + t.getMessage();
                    result.errors.add(msg);
                    LogX.e("导出失败 " + item.storedFile, t);
                }
                done++;
            }

            try {
                byte[] json = IndexJson.exportIndexBytes(items, store.groups());
                android.net.Uri uri = exporter.exportBytes("index.json", "application/json", json);
                result.indexUri = uri == null ? null : uri.toString();
            } catch (Throwable t) {
                result.errors.add("index.json: " + t.getMessage());
                LogX.e("导出 index.json 失败", t);
            }

            store.flushIndex();
            if (progress != null) {
                progress.onProgress(total, total, "完成");
            }
            LogX.i("导出结束: " + result.summary());
            return result;
        }
    }

    private void exportOne(FavoriteItem item) throws Exception {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date(item.savedAt > 0 ? item.savedAt : System.currentTimeMillis()));
        String source = FavoriteStore.safeName(item.source, "unknown");
        if (source.length() > 20) {
            source = source.substring(0, 20);
        }
        String hash8 = item.sha256 == null ? "00000000"
                : item.sha256.substring(0, Math.min(8, item.sha256.length()));

        if (item.isSticker()) {
            byte[] data = store.read(item);
            ImageSniffer.Format fmt = ImageSniffer.detect(data);
            if (!fmt.image) {
                throw new IOException("存储内容不是标准图片（头部="
                        + ImageSniffer.hexPrefix(data, 16) + "）");
            }
            String name = "bs_" + stamp + "_sticker_" + source + "_" + hash8 + "." + fmt.extension;
            exporter.exportBytes(name, fmt.mime, data);
            item.exportName = name;
            item.mime = fmt.mime;
            return;
        }

        if (item.isVoice()) {
            byte[] silk = store.read(item);
            if (!ImageSniffer.isSilk(silk)) {
                throw new IOException("存储内容不是 SILK v3（头部="
                        + ImageSniffer.hexPrefix(silk, 16) + "）");
            }
            SilkDecoder decoder = SilkDecoders.get();
            if (!decoder.isAvailable()) {
                throw new IOException("SILK 解码器未配置，无法生成 WAV。"
                        + "接口与桩已就绪，详见 README「SILK 解码」");
            }
            SilkDecoder.PcmAudio pcm = decoder.decode(silk, 48000, 1);
            File tmp = File.createTempFile("bs_voice_", ".wav", context.getCacheDir());
            try {
                WavWriter.writeFile(tmp, pcm.pcm16le, pcm.sampleRate, pcm.channels);
                String name = "bs_" + stamp + "_voice_" + source + "_" + hash8 + ".wav";
                exporter.exportFile(name, "audio/wav", tmp);
                item.exportName = name;
                item.mime = "audio/wav";
            } finally {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
            return;
        }

        throw new IOException("未知收藏类型: " + item.type);
    }
}
