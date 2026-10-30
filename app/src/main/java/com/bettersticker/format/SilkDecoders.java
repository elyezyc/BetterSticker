package com.bettersticker.format;

/**
 * SILK 解码器注册表。
 *
 * v1 没有可用的 SILK 解码实现：
 * - 不复用 FunBox 的 libfun.so（闭源版权物，且 Java 侧类不存在）；
 * - 开源 SILK SDK 的许可证/专利条款需要单独评估，确认后再加实现；
 * - 在此之前只返回 Unavailable。
 */
public final class SilkDecoders {

    private static final SilkDecoder UNAVAILABLE = new SilkDecoder() {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String name() {
            return "unavailable";
        }

        @Override
        public PcmAudio decode(byte[] silk, int targetSampleRate, int targetChannels) {
            throw new IllegalStateException(
                    "SILK 解码器尚未配置：请在 README 的“SILK 解码”一节选定实现后再导出 WAV");
        }
    };

    private SilkDecoders() {
    }

    public static SilkDecoder get() {
        return UNAVAILABLE;
    }

    public static boolean isAvailable() {
        return UNAVAILABLE.isAvailable();
    }
}
