package com.bettersticker.qq;

import com.tencent.mobileqq.aio.msg.AIOMsgItem;
import com.tencent.qqnt.aio.menu.ui.e;

/**
 * QQ 长按菜单项的原生实现（编译期针对 QQ 9.3.75 的 ui.e 桩，运行时用 QQ 的真实类）。
 * 由 QqMenuItemHook 插入到消息菜单列表。
 */
public final class QqMenuItem extends e {

    private final int iconResId;
    private final int itemId;
    private final String text;
    private final Runnable click;

    public QqMenuItem(AIOMsgItem msg, int iconResId, int itemId, String text, Runnable click) {
        super(msg);
        this.iconResId = iconResId;
        this.itemId = itemId;
        this.text = text == null ? "" : text;
        this.click = click;
    }

    public static QqMenuItem create(Object msg, int iconResId, int itemId, String text, Runnable click) {
        return new QqMenuItem((AIOMsgItem) msg, iconResId, itemId, text, click);
    }

    @Override
    public int b() {
        return iconResId;
    }

    @Override
    public int c() {
        return itemId;
    }

    @Override
    public String g() {
        return text;
    }

    @Override
    public String h() {
        return text;
    }

    @Override
    public void k() {
        if (click != null) {
            click.run();
        }
    }
}
