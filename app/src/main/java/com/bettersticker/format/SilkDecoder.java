package com.bettersticker.format;

/**
 * SILK 解码接口。
 *
 * 数据格式未确认前只留接口；当前实现 SilkDecoders.get() 返回“不可用”，
 * 导出时会明确报错，不会伪造音频、不会把 SILK 直接改名成 WAV。
 */
public interface SilkDecoder {

    boolean isAvailable();

    String name();

    /**
     * @param silk            原始 SILK v3 字节
     * @param targetSampleRate 目标采样率（BetterSticker 用 48000）
     * @param targetChannels   目标声道（BetterSticker 用 1）
     */
    PcmAudio decode(byte[] silk, int targetSampleRate, int targetChannels) throws Exception;

    final class PcmAudio {
        public final byte[] pcm16le;
        public final int sampleRate;
        public final int channels;

        public PcmAudio(byte[] pcm16le, int sampleRate, int channels) {
            this.pcm16le = pcm16le;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }

        public int frames() {
            int align = Math.max(1, channels * 2);
            return pcm16le.length / align;
        }
    }
}
