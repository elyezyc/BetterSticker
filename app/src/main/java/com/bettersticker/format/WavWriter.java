package com.bettersticker.format;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * 标准 RIFF/WAVE 写入器（PCM16）。
 *
 * 这是通用文件格式，与 FunBox 无关；SILK 解码后拿到 PCM 即可用它落盘。
 * 目标格式按需求：单声道、48kHz、16bit。
 */
public final class WavWriter {

    public static final int DEFAULT_SAMPLE_RATE = 48000;
    public static final int DEFAULT_CHANNELS = 1;

    private WavWriter() {
    }

    public static void writeFile(File file, byte[] pcm16le, int sampleRate, int channels) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建目录: " + parent);
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            write(out, pcm16le, sampleRate, channels);
        }
    }

    public static void write(OutputStream out, byte[] pcm16le, int sampleRate, int channels) throws IOException {
        if (pcm16le == null) {
            pcm16le = new byte[0];
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate 必须 > 0");
        }
        if (channels != 1 && channels != 2) {
            throw new IllegalArgumentException("只支持 1/2 声道: " + channels);
        }
        int blockAlign = channels * 2;
        int byteRate = sampleRate * blockAlign;
        int dataSize = pcm16le.length;
        int riffSize = 36 + dataSize;

        ByteArrayOutputStream header = new ByteArrayOutputStream(44);
        writeAscii(header, "RIFF");
        writeIntLE(header, riffSize);
        writeAscii(header, "WAVE");
        writeAscii(header, "fmt ");
        writeIntLE(header, 16);          // PCM fmt chunk size
        writeShortLE(header, 1);         // PCM
        writeShortLE(header, channels);
        writeIntLE(header, sampleRate);
        writeIntLE(header, byteRate);
        writeShortLE(header, blockAlign);
        writeShortLE(header, 16);        // bits per sample
        writeAscii(header, "data");
        writeIntLE(header, dataSize);

        out.write(header.toByteArray());
        out.write(pcm16le);
        out.flush();
    }

    /** 生成一段正弦波 PCM16LE，用于自测导出链路（不是业务数据）。 */
    public static byte[] sinePcm16(int sampleRate, int frames, double frequencyHz, double amplitude) {
        if (sampleRate <= 0 || frames < 0) {
            throw new IllegalArgumentException("非法参数");
        }
        double amp = Math.max(0.0, Math.min(1.0, amplitude)) * 32767.0;
        byte[] out = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            short v = (short) Math.round(amp * Math.sin(2.0 * Math.PI * frequencyHz * i / sampleRate));
            out[i * 2] = (byte) (v & 0xFF);
            out[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return out;
    }

    private static void writeAscii(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static void writeIntLE(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }

    private static void writeShortLE(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }
}
