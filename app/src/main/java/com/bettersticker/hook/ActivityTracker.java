package com.bettersticker.hook;

import android.app.Activity;

import java.lang.ref.WeakReference;

import com.bettersticker.shared.LogX;

/** 记录当前前台 Activity；面板/探针都从它取窗口。 */
public final class ActivityTracker {

    private static volatile WeakReference<Activity> current = new WeakReference<>(null);
    private static volatile String currentName = "";

    private ActivityTracker() {
    }

    public static void onResume(Activity activity) {
        current = new WeakReference<>(activity);
        currentName = activity.getClass().getName();
        LogX.i("Activity onResume: " + currentName);
        com.bettersticker.hook.TriggerRegistry.scanActivity(activity);
    }

    public static void onPause(Activity activity) {
        Activity cur = current.get();
        if (cur == activity) {
            com.bettersticker.ui.PanelController.dismissFor(activity);
            com.bettersticker.qq.QqSaveChip.dismiss();
            current = new WeakReference<>(null);
            currentName = "";
        }
    }

    public static Activity current() {
        return current.get();
    }

    public static String currentName() {
        return currentName;
    }
}
