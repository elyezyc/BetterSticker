package com.bettersticker.qq;

/** 从 QQ 消息里提取出来的一项待收藏媒体（尚未落盘）。 */
public final class QqMedia {

    public String type;
    public byte[] data;
    public String originalName;
    public String source;
    public int width;
    public int height;
    /** PttElement.duration 的原始值；单位未在真机验证，不当作毫秒使用。 */
    public int durationRaw;
    public String debug;

    public boolean isVoice() {
        return "voice".equals(type);
    }
}
