package com.tencent.qqnt.aio.menu.ui;

import com.tencent.mobileqq.aio.msg.AIOMsgItem;

/** 编译期桩：QQ 9.3.75 的抽象菜单项。运行时由 QQ 提供真实类。 */
public abstract class e {
    public e(AIOMsgItem msg) {
    }

    public abstract int b();

    public abstract int c();

    public AIOMsgItem d() {
        return null;
    }

    public abstract String g();

    public abstract String h();

    public abstract void k();
}
