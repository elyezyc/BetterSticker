package com.bettersticker.qq;

/**
 * 针对 QQ 9.3.75.42350 反编译核对过的类名/方法名。
 *
 * 这些不是猜的：均已在 classes*.dex 的类定义里确认存在（androguard 解析）。
 * 但仍属于版本相关常量；升级 QQ 后应先用探针日志复核。
 */
public final class QqNames {

    public static final String MENU_WRAPPER = "com.tencent.qqnt.aio.menu.MenuWrapper";
    public static final String MENU_UI_MODEL = "com.tencent.qqnt.aio.menu.ui.c";
    public static final String AIO_MSG_ITEM = "com.tencent.mobileqq.aio.msg.AIOMsgItem";
    public static final String MSG_RECORD = "com.tencent.qqnt.kernel.nativeinterface.MsgRecord";
    public static final String MSG_ELEMENT = "com.tencent.qqnt.kernel.nativeinterface.MsgElement";
    public static final String PTT_ELEMENT = "com.tencent.qqnt.kernel.nativeinterface.PttElement";
    public static final String PIC_ELEMENT = "com.tencent.qqnt.kernel.nativeinterface.PicElement";
    public static final String MARKET_FACE_ELEMENT =
            "com.tencent.qqnt.kernel.nativeinterface.MarketFaceElement";

    private QqNames() {
    }
}
