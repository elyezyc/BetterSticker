package com.bettersticker.format;

import java.io.IOException;
import java.util.List;

/**
 * 原版素材源接口。
 *
 * 数据格式未确认前，实现只能是桩/受限实现；上层必须处理 UnverifiedFormatException，
 * 并把状态显示在面板上，而不是假装读取成功。
 */
public interface StoredAssetSource {

    String status();

    List<StoredAsset> list() throws IOException;

    byte[] read(StoredAsset asset) throws IOException;

    final class UnverifiedFormatException extends IOException {
        public UnverifiedFormatException(String message) {
            super(message);
        }
    }
}
