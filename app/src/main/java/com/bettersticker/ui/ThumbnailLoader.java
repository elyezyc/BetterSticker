package com.bettersticker.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.bettersticker.shared.LogX;

/**
 * 网格缩略图加载：后台解码 + LruCache + 简单的 View tag 防错位。
 * 动图预览单独走 ImageDecoder（decodePreview）。
 */
public final class ThumbnailLoader {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "BetterSticker-thumb");
        t.setDaemon(true);
        return t;
    });
    private static final android.util.LruCache<String, Bitmap> CACHE =
            new android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private ThumbnailLoader() {
    }

    public static void load(File file, int targetPx, ImageView view) {
        if (file == null || !file.isFile()) {
            return;
        }
        String key = file.getAbsolutePath() + "#" + file.lastModified() + "#" + targetPx;
        view.setTag(key);
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(null);
        POOL.execute(() -> {
            Bitmap bmp = decodeSampled(file, targetPx);
            if (bmp != null) {
                CACHE.put(key, bmp);
            }
            MAIN.post(() -> {
                if (key.equals(view.getTag()) && bmp != null) {
                    view.setImageBitmap(bmp);
                }
            });
        });
    }

    private static Bitmap decodeSampled(File file, int target) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            int w = opts.outWidth;
            int h = opts.outHeight;
            int sample = 1;
            while (w / sample > target * 2 || h / sample > target * 2) {
                sample *= 2;
            }
            BitmapFactory.Options real = new BitmapFactory.Options();
            real.inSampleSize = sample;
            real.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), real);
        } catch (Throwable t) {
            LogX.e("缩略图解码失败: " + file, t);
            return null;
        }
    }

    /** 预览：GIF/WebP 动图交给 ImageDecoder，失败再退回位图。 */
    public static Drawable decodePreview(File file, int maxPx) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                android.graphics.ImageDecoder.Source src =
                        android.graphics.ImageDecoder.createSource(file);
                Drawable d = android.graphics.ImageDecoder.decodeDrawable(src);
                if (d instanceof android.graphics.drawable.AnimatedImageDrawable) {
                    ((android.graphics.drawable.AnimatedImageDrawable) d).setRepeatCount(
                            android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE);
                    ((android.graphics.drawable.AnimatedImageDrawable) d).start();
                }
                return d;
            }
        } catch (Throwable t) {
            LogX.w("ImageDecoder 预览失败，退回静态图: " + t);
        }
        Bitmap b = decodeSampled(file, maxPx);
        return b == null ? null : new android.graphics.drawable.BitmapDrawable(
                com.bettersticker.shared.Env.appContext.getResources(), b);
    }

    public static void clear() {
        CACHE.evictAll();
    }
}
