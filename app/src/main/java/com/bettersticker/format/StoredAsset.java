package com.bettersticker.format;

/** 从“原版素材源”读出来的一项，尚未进入 BetterSticker 收藏库。 */
public final class StoredAsset {

    public String id;
    public String name;
    public String path;
    /** 相对于 FunBox 根目录的路径，例如 packA/001.png；用于按原目录分组。 */
    public String relativePath;
    public String type;
    public long size;
    public ImageSniffer.Format format = ImageSniffer.Format.UNKNOWN;
    public String source;

    @Override
    public String toString() {
        return "StoredAsset{" + name + ", " + type + ", " + format + ", " + size + "}";
    }
}
