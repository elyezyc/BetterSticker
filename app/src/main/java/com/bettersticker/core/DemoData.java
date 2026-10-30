package com.bettersticker.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.bettersticker.shared.LogX;
import com.bettersticker.store.FavoriteItem;
import com.bettersticker.store.FavoriteStore;

/**
 * 生成一张自绘 PNG 作为演示收藏，用于真机验证面板/导出/相册链路。
 * 不生成假的 SILK/WAV，不伪造原版格式。
 */
public final class DemoData {

    private DemoData() {
    }

    public static int seed(Context context) {
        try {
            FavoriteStore store = FavoriteStore.get();
            int n = 0;
            for (int i = 0; i < 2; i++) {
                byte[] png = makePng(i == 0 ? 0xFF6EE7B7 : 0xFF60A5FA);
                String name = "demo_" + new SimpleDateFormat("HHmmss", Locale.US).format(new Date())
                        + "_" + i + ".png";
                FavoriteItem it = store.addSticker(png, name, "demo", 512, 512);
                if (it != null) {
                    n++;
                }
            }
            LogX.i("演示数据生成: " + n);
            return n;
        } catch (Throwable t) {
            LogX.e("生成演示数据失败", t);
            return 0;
        }
    }

    private static byte[] makePng(int accent) throws Exception {
        int size = 512;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, size, size,
                0xFF111118, accent, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, size, size, bg);

        Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
        circle.setColor(0x33FFFFFF);
        c.drawCircle(size * 0.72f, size * 0.28f, size * 0.18f, circle);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(size * 0.13f);
        text.setFakeBoldText(true);
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText("BetterSticker", size / 2f, size * 0.52f, text);
        Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        small.setColor(0xCCFFFFFF);
        small.setTextSize(size * 0.06f);
        small.setTextAlign(Paint.Align.CENTER);
        c.drawText("demo asset", size / 2f, size * 0.62f, small);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        bmp.recycle();
        return out.toByteArray();
    }
}
