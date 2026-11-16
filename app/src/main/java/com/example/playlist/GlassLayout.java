package com.example.playlist;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Гласморфизм
 * Кладётся поверх картинки (ImageView) в одном FrameLayout.
 * Под собой рисует размытый кусок этой картинки + полупрозрачный белый слой + светлую рамку,
 *
 * Использование: в XML — <com.example.playlist.GlassLayout ...>,
 * в коде — glass.setBlurTarget(imageView).
 */
public class GlassLayout extends FrameLayout {

    private ImageView target;
    private Drawable blurredFrom;   // для какой картинки уже посчитано размытие
    private Bitmap blurred;

    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final float radius;
    private final int[] myPos = new int[2];
    private final int[] targetPos = new int[2];

    public GlassLayout(Context context) {
        this(context, null);
    }

    public GlassLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false); // чтобы вызывался onDraw
        float dp = getResources().getDisplayMetrics().density;
        radius = 22 * dp;

        tintPaint.setColor(Color.argb(150, 255, 252, 246)); // молочно-белый, ~60% непрозрачности

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.2f * dp);
        strokePaint.setColor(Color.argb(230, 255, 255, 255)); // светлая кромка стекла
    }

    /** Картинка, которая лежит под панелью и будет размыта. */
    public void setBlurTarget(ImageView imageView) {
        target = imageView;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(0, 0, w, h);
        clipPath.reset();
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.clipPath(clipPath);

        // 1) размытый кусок картинки ровно под панелью
        prepareBlur();
        if (blurred != null) {
            Drawable d = target.getDrawable();
            target.getLocationInWindow(targetPos);
            getLocationInWindow(myPos);
            canvas.save();
            canvas.translate(targetPos[0] - myPos[0] + target.getPaddingLeft(),
                    targetPos[1] - myPos[1] + target.getPaddingTop());
            canvas.concat(target.getImageMatrix());
            canvas.drawBitmap(blurred, null,
                    new Rect(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight()), bitmapPaint);
            canvas.restore();
        }

        // 2) полупрозрачный молочный слой
        canvas.drawRect(rect, tintPaint);
        canvas.restore();

        // 3) светлая рамка
        float half = strokePaint.getStrokeWidth() / 2f;
        canvas.drawRoundRect(half, half, getWidth() - half, getHeight() - half,
                radius, radius, strokePaint);

        super.onDraw(canvas);
    }

    /** Размываем картинку один раз. */
    private void prepareBlur() {
        if (target == null) return;
        Drawable d = target.getDrawable();
        if (!(d instanceof BitmapDrawable)) return;
        if (d == blurredFrom && blurred != null) return;

        Bitmap src = ((BitmapDrawable) d).getBitmap();
        int w = Math.max(1, src.getWidth() / 8);
        int h = Math.max(1, src.getHeight() / 8);
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true);
        for (int i = 0; i < 3; i++) {
            boxBlur(small, 2);
        }
        blurred = small;
        blurredFrom = d;
    }

    /** Простое размытие «среднее по соседям» по горизонтали и вертикали. */
    private static void boxBlur(Bitmap bmp, int r) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        int[] tmp = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        blurPass(px, tmp, w, h, r, true);
        blurPass(tmp, px, w, h, r, false);
        bmp.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static void blurPass(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = 0, rr = 0, g = 0, b = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int xx = horizontal ? x + k : x;
                    int yy = horizontal ? y : y + k;
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                    int c = in[yy * w + xx];
                    a += c >>> 24;
                    rr += (c >> 16) & 0xFF;
                    g += (c >> 8) & 0xFF;
                    b += c & 0xFF;
                    n++;
                }
                out[y * w + x] = ((a / n) << 24) | ((rr / n) << 16) | ((g / n) << 8) | (b / n);
            }
        }
    }
}
