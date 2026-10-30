package com.bettersticker.simplehook;

/**
 * 占位说明，不是功能实现。
 *
 * 参考包的 Zygisk 通道依赖自研内联 Hook（libhooker.so / libmaphider.so / funloader.so），
 * 这些二进制既不在 参考/ 目录中，也没有源码；原始 APK 里虽然有，但属于 FunBox 的闭源版权物，
 * 且直接 patch ART 的做法对 Android/QQ 版本非常敏感。
 *
 * BetterSticker v1 因此只用 LSPosed 的 XposedBridge，不提供 simplehook 实现。
 * 保留这个包名只是为了与参考工程的 loader / shared / simplehook 结构对应。
 */
public final class SimpleHookerSupport {

    private SimpleHookerSupport() {
    }

    public static boolean isAvailable() {
        return false;
    }

    public static String reason() {
        return "未捆绑 libhooker.so：闭源二进制，不复用；v1 仅支持 LSPosed";
    }
}
