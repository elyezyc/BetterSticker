package com.bettersticker.core;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.widget.Toast;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import com.bettersticker.export.ExportService;
import com.bettersticker.shared.LogX;
import com.bettersticker.store.FavoriteItem;
import com.bettersticker.ui.PanelController;

/**
 * 调试广播（无权限保护，任何应用都能发；只影响面板/导出，不泄露数据）。
 *
 * adb shell am broadcast -a com.bettersticker.action.OPEN -p com.tencent.mobileqq --es tab sticker
 * adb shell am broadcast -a com.bettersticker.action.PROBE -p com.tencent.mobileqq
 * adb shell am broadcast -a com.bettersticker.action.DEMO -p com.tencent.mobileqq
 * adb shell am broadcast -a com.bettersticker.action.EXPORT -p com.tencent.mobileqq
 */
public final class DebugReceiver extends BroadcastReceiver {

    public static final String ACTION_OPEN = Config.ACTION_PREFIX + "OPEN";
    public static final String ACTION_PROBE = Config.ACTION_PREFIX + "PROBE";
    public static final String ACTION_DEMO = Config.ACTION_PREFIX + "DEMO";
    public static final String ACTION_EXPORT = Config.ACTION_PREFIX + "EXPORT";
    public static final String ACTION_STATUS = Config.ACTION_PREFIX + "STATUS";
    public static final String ACTION_MARK = Config.ACTION_PREFIX + "MARK";
    public static final String ACTION_REQUEST_STATUS = Config.ACTION_PREFIX + "REQUEST_STATUS";
    public static final String ACTION_SESSION = Config.ACTION_PREFIX + "SESSION";
    public static final String ACTION_SENDPHOTO = Config.ACTION_PREFIX + "SENDPHOTO";
    public static final String ACTION_STATUS_RESULT = Config.ACTION_PREFIX + "STATUS_RESULT";

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    public static void register(Context context) {
        if (!Config.DEBUG_RECEIVER || !REGISTERED.compareAndSet(false, true)) {
            return;
        }
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(ACTION_OPEN);
            f.addAction(ACTION_PROBE);
            f.addAction(ACTION_DEMO);
            f.addAction(ACTION_EXPORT);
            f.addAction(ACTION_STATUS);
            f.addAction(ACTION_MARK);
            f.addAction(ACTION_REQUEST_STATUS);
            f.addAction(ACTION_SESSION);
            f.addAction(ACTION_SENDPHOTO);
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(new DebugReceiver(), f, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(new DebugReceiver(), f);
            }
            LogX.i("调试广播已注册: " + ACTION_OPEN + " / " + ACTION_PROBE
                    + " / " + ACTION_DEMO + " / " + ACTION_EXPORT
                    + " / " + ACTION_STATUS + " / " + ACTION_MARK
                    + " / " + ACTION_REQUEST_STATUS);
        } catch (Throwable t) {
            LogX.e("注册调试广播失败", t);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        LogX.i("收到调试广播: " + action);
        try {
            switch (action) {
                case ACTION_OPEN: {
                    String tab = intent.getStringExtra("tab");
                    if (tab == null) {
                        tab = FavoriteItem.TYPE_STICKER;
                    }
                    Activity a = com.bettersticker.hook.ActivityTracker.current();
                    if (a == null) {
                        Toast.makeText(context, "当前没有前台 QQ Activity", Toast.LENGTH_SHORT).show();
                    } else {
                        PanelController.show(a, tab);
                    }
                    break;
                }
                case ACTION_PROBE: {
                    File f = Probe.dumpCurrent("manual");
                    Toast.makeText(context, f == null ? "探针失败（见日志）" : "探针: " + f.getAbsolutePath(),
                            Toast.LENGTH_LONG).show();
                    break;
                }
                case ACTION_DEMO: {
                    Toast.makeText(context, "正在生成演示数据…", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        int n = DemoData.seed(context);
                        PanelController.refreshIfOpen();
                        android.os.Handler h = new android.os.Handler(context.getMainLooper());
                        h.post(() -> Toast.makeText(context, "演示数据已生成 " + n + " 条",
                                Toast.LENGTH_SHORT).show());
                    }, "BetterSticker-demo").start();
                    break;
                }
                case ACTION_STATUS: {
                    Activity a = com.bettersticker.hook.ActivityTracker.current();
                    if (a == null) {
                        Toast.makeText(context, "当前没有前台 Activity", Toast.LENGTH_SHORT).show();
                    } else {
                        com.bettersticker.ui.DiagnosticsDialog.show(a);
                    }
                    break;
                }
                case ACTION_MARK: {
                    Activity a = com.bettersticker.hook.ActivityTracker.current();
                    String r = com.bettersticker.hook.TriggerRegistry.scanNow(a);
                    File inputs = Probe.dumpInputs(a);
                    LogX.i("MARK 结果: " + r + " inputs=" + inputs);
                    Toast.makeText(context, "MARK: " + r + "\n输入区探针: " + inputs,
                            Toast.LENGTH_LONG).show();
                    break;
                }
                case ACTION_SENDPHOTO: {
                    Activity a = com.bettersticker.hook.ActivityTracker.current();
                    if (a == null) {
                        Toast.makeText(context, "当前没有前台 Activity", Toast.LENGTH_SHORT).show();
                    } else {
                        java.io.File f = com.bettersticker.store.FavoriteStore.get()
                                .firstStickerFile();
                        if (f == null) {
                            Toast.makeText(context, "没有可用表情文件", Toast.LENGTH_SHORT).show();
                        } else {
                            com.bettersticker.qq.QqPhotoSender.send(a, f);
                        }
                    }
                    break;
                }
                case ACTION_SESSION: {
                    Activity a = com.bettersticker.hook.ActivityTracker.current();
                    if (a == null) {
                        Toast.makeText(context, "当前没有前台 Activity", Toast.LENGTH_SHORT).show();
                    } else {
                        String r = com.bettersticker.core.SessionProbe.dump(a);
                        Toast.makeText(context, "Session 探针: " + r, Toast.LENGTH_SHORT).show();
                    }
                    break;
                }
                case ACTION_REQUEST_STATUS: {
                    String status = com.bettersticker.ui.DiagnosticsDialog.statusText();
                    Intent out = new Intent(ACTION_STATUS_RESULT)
                            .setPackage("com.elyezyc.bettersticker");
                    out.putExtra("status", status);
                    out.putExtra("time", System.currentTimeMillis());
                    context.sendBroadcast(out);
                    LogX.i("已向 BetterSticker App 返回状态");
                    break;
                }
                case ACTION_EXPORT: {
                    Toast.makeText(context, "开始导出…", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        ExportService.Result r = new ExportService(context)
                                .exportAll((done, total, current) -> {
                                });
                        android.os.Handler h = new android.os.Handler(context.getMainLooper());
                        h.post(() -> Toast.makeText(context, "导出: " + r.summary(),
                                Toast.LENGTH_LONG).show());
                        PanelController.refreshIfOpen();
                    }, "BetterSticker-export-broadcast").start();
                    break;
                }
                default:
                    break;
            }
        } catch (Throwable t) {
            LogX.e("处理调试广播失败: " + action, t);
        }
    }
}
