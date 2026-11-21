package io.github.p4grecomp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import java.util.Random;

/** Decorative Persona 4 Golden launcher scene. It is never attached to the native game window. */
final class P4gBackgroundView extends View implements Runnable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stripePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint noisePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint scanPaint = new Paint();
    private final Paint glitchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix noiseMatrix = new Matrix();
    private final Bitmap noiseBitmap;
    private final Bitmap sceneBitmap;
    private final BitmapShader noiseShader;
    private final RectF frame = new RectF();
    private final RectF screen = new RectF();
    private final int[] stripes = {
            Color.rgb(255, 255, 255), Color.rgb(0, 166, 215), Color.rgb(72, 191, 115),
            Color.rgb(255, 221, 44), Color.rgb(255, 126, 33), Color.rgb(236, 56, 76),
            Color.rgb(127, 70, 189)
    };
    private boolean animating;
    private boolean staticEnabled = true;
    private int staticIntensity = 42;
    private float transitionFrom, transitionTo, effectProgress;
    private long transitionStarted;
    private LinearGradient backdrop;

    P4gBackgroundView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        textPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        int[] pixels = new int[64 * 64];
        Random random = new Random(0x504447);
        for (int i = 0; i < pixels.length; i++) {
            int value = 32 + random.nextInt(224);
            pixels[i] = Color.argb(value, value, value, value);
        }
        noiseBitmap = Bitmap.createBitmap(pixels, 64, 64, Bitmap.Config.ARGB_8888);
        noiseShader = new BitmapShader(noiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        noisePaint.setShader(noiseShader);
        int sceneId = context.getResources().getIdentifier("p4gp_launcher_background", "drawable", context.getPackageName());
        Bitmap decoded = sceneId == 0 ? null : BitmapFactory.decodeResource(context.getResources(), sceneId);
        sceneBitmap = decoded;
        scanPaint.setColor(Color.BLACK);
        glitchPaint.setColor(Color.WHITE);
    }

    void setAnimating(boolean enabled) {
        if (animating == enabled) return;
        animating = enabled;
        transitionFrom = enabled ? 0f : effectProgress;
        transitionTo = enabled && staticEnabled ? 1f : 0f;
        transitionStarted = SystemClock.uptimeMillis();
        removeCallbacks(this);
        if (enabled && isAttachedToWindow()) postOnAnimation(this);
    }

    void setStaticEffect(boolean enabled, int intensity) {
        transitionFrom = effectProgress;
        staticEnabled = enabled;
        transitionTo = enabled ? 1f : 0f;
        transitionStarted = SystemClock.uptimeMillis();
        staticIntensity = Math.max(0, Math.min(100, intensity));
        invalidate();
    }

    @Override public void run() {
        if (!animating || !isAttachedToWindow()) return;
        invalidate();
        postDelayed(this, 66);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animating) postOnAnimation(this);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(this);
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        backdrop = new LinearGradient(0, 0, width, height,
                new int[] {0xFFFFE45A, 0xFFFFC928, 0xFFE99217}, null, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        final float s = Math.max(w / 1280f, h / 720f);
        final long now = SystemClock.uptimeMillis();
        final float t = now / 1000f;
        float transition = Math.min(1f, Math.max(0f, (now - transitionStarted) / 240f));
        transition = transition * transition * (3f - 2f * transition);
        effectProgress = transitionFrom + (transitionTo - transitionFrom) * transition;
        if (sceneBitmap != null) {
            float scale = Math.max(w / sceneBitmap.getWidth(), h / sceneBitmap.getHeight());
            float dw = sceneBitmap.getWidth() * scale;
            float dh = sceneBitmap.getHeight() * scale;
            frame.set((w - dw) * .5f, (h - dh) * .5f, (w + dw) * .5f, (h + dh) * .5f);
            canvas.drawBitmap(sceneBitmap, null, frame, paint);
            drawStatic(canvas, w, h, t);
            return;
        }
        paint.setShader(backdrop);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // Diagonal broadcast-color bands at the left edge, a recurring P4G motif.
        canvas.save();
        canvas.rotate(-37, 0, h * .55f);
        float bandW = Math.max(12f, 25f * s);
        float left = -w * .48f;
        float bandTop = -h * .2f;
        for (int i = 0; i < stripes.length; i++) {
            stripePaint.setColor(stripes[i]);
            stripePaint.setAlpha(190);
            canvas.drawRect(left + i * bandW, bandTop, left + (i + 1) * bandW,
                    h * 1.45f, stripePaint);
        }
        canvas.restore();

        // Warm warehouse glow and a sparse steel truss silhouette.
        paint.setColor(0x22FFFFFF);
        canvas.drawCircle(w * .76f, h * .20f, h * .52f, paint);
        paint.setColor(0x66342810);
        paint.setStrokeWidth(Math.max(2f, 5f * s));
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 6; i++) {
            float x = w * (.42f + i * .12f);
            canvas.drawLine(x, 0, x - w * .12f, h * .62f, paint);
            canvas.drawLine(x - w * .12f, h * .62f, x + w * .08f, h * .62f, paint);
        }
        for (int i = 0; i < 4; i++) {
            float y = h * (.18f + i * .13f);
            canvas.drawLine(w * .34f, y, w, y - h * .06f, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        // A stacked wall of CRTs; the screens pulse gently, without touching game rendering.
        float bob = animating ? (float) Math.sin(t * 1.1) * 2.2f * s : 0;
        drawTv(canvas, w * .38f, h * .25f + bob, w * .23f, h * .22f, 0xFFFFF276, s, t);
        drawTv(canvas, w * .56f, h * .18f - bob * .4f, w * .20f, h * .23f, 0xFF7BCBEB, s, t + 1);
        drawTv(canvas, w * .47f, h * .43f - bob, w * .25f, h * .22f, 0xFF6CC3E2, s, t + 2);
        drawTv(canvas, w * .68f, h * .40f + bob * .5f, w * .22f, h * .24f, 0xFFFFEF69, s, t + 3);
        drawTv(canvas, w * .33f, h * .52f - bob * .3f, w * .20f, h * .23f, 0xFFFFE757, s, t + 4);
        drawTv(canvas, w * .52f, h * .62f + bob, w * .23f, h * .21f, 0xFFFFE84E, s, t + 5);
        drawTv(canvas, w * .75f, h * .66f - bob * .5f, w * .19f, h * .20f, 0xFF78C6E5, s, t + 6);

        if (staticIntensity > 0 && effectProgress > .001f) {
            int phase = animating ? (int) (t * 29f) : 0;
            noiseMatrix.setTranslate(phase, -phase * .63f);
            noiseShader.setLocalMatrix(noiseMatrix);
            noisePaint.setAlpha((int) (staticIntensity * effectProgress
                    * (animating ? 0.40f + .06f * Math.sin(t * 4f) : .32f)));
            canvas.drawRect(0, 0, w, h, noisePaint);
            scanPaint.setAlpha((int) (staticIntensity * effectProgress * .30f));
            float spacing = Math.max(3f, 5f * s);
            for (float y = 0; y < h; y += spacing) canvas.drawLine(0, y, w, y, scanPaint);
            if (animating) {
                float bandY = (t * h * .37f) % h;
                float bandHeight = Math.max(2f, 5f * s);
                glitchPaint.setAlpha((int) (staticIntensity * effectProgress * .50f));
                canvas.drawRect(0, bandY, w, bandY + bandHeight, glitchPaint);
                glitchPaint.setColor(0xFFFF5D45);
                glitchPaint.setAlpha(Math.max(1, staticIntensity / 8));
                canvas.drawRect(w * .37f + 3f * s, bandY, w * .92f, bandY + bandHeight * .55f, glitchPaint);
                glitchPaint.setColor(0xFF41D5F2);
            }
        }

        // Comic accents and a bold lower contrast wash keep controls readable above the artwork.
        textPaint.setTextSize(Math.max(18f, 42f * s));
        textPaint.setColor(0xAAFFFFFF);
        canvas.drawText("★", w * .31f, h * .42f, textPaint);
        canvas.drawText("✦", w * .91f, h * .35f, textPaint);
        paint.setColor(0x44201608);
        canvas.drawRect(0, h * .79f, w, h, paint);
    }

    private void drawStatic(Canvas canvas, float w, float h, float t) {
        if (staticIntensity <= 0 || effectProgress <= .001f) return;
        int phase = animating ? (int) (t * 29f) : 0;
        noiseMatrix.setTranslate(phase, -phase * .63f);
        noiseShader.setLocalMatrix(noiseMatrix);
        noisePaint.setAlpha((int) (staticIntensity * effectProgress * (animating ? 0.40f + .06f * Math.sin(t * 4f) : .32f)));
        canvas.drawRect(0, 0, w, h, noisePaint);
        scanPaint.setAlpha((int) (staticIntensity * effectProgress * .30f));
        float spacing = Math.max(3f, 5f * Math.max(w / 1280f, h / 720f));
        for (float y = 0; y < h; y += spacing) canvas.drawLine(0, y, w, y, scanPaint);
        if (animating) {
            float bandY = (t * h * .37f) % h;
            float bandHeight = Math.max(2f, 5f * Math.max(w / 1280f, h / 720f));
            glitchPaint.setColor(Color.WHITE);
            glitchPaint.setAlpha((int) (staticIntensity * effectProgress * .50f));
            canvas.drawRect(0, bandY, w, bandY + bandHeight, glitchPaint);
            glitchPaint.setColor(0xFFFF5D45);
            glitchPaint.setAlpha(Math.max(1, staticIntensity / 8));
            canvas.drawRect(w * .37f + 3f, bandY, w * .92f, bandY + bandHeight * .55f, glitchPaint);
        }
    }

    private void drawTv(Canvas canvas, float x, float y, float width, float height,
                        int screenColor, float scale, float time) {
        float r = Math.max(5f, 12f * scale);
        frame.set(x, y, x + width, y + height);
        paint.setColor(0xFF221E18);
        canvas.drawRoundRect(frame, r, r, paint);
        paint.setColor(0xFF635642);
        frame.inset(3f * scale, 3f * scale);
        canvas.drawRoundRect(frame, r * .8f, r * .8f, paint);
        float side = Math.max(13f * scale, width * .19f);
        screen.set(frame.left + 8f * scale, frame.top + 8f * scale,
                frame.right - side, frame.bottom - 8f * scale);
        int flicker = animating && staticEnabled ? (int) (Math.sin(time * 10.0) * 7 * staticIntensity / 60f) : 0;
        paint.setColor(Color.rgb(clamp(Color.red(screenColor) + flicker),
                clamp(Color.green(screenColor) + flicker), clamp(Color.blue(screenColor) + flicker)));
        canvas.drawRoundRect(screen, r * .55f, r * .55f, paint);
        paint.setColor(0x26FFFFFF);
        canvas.drawRect(screen.left, screen.centerY() - 2f * scale, screen.right,
                screen.centerY() + 2f * scale, paint);
        paint.setColor(0xFF302A20);
        canvas.drawCircle(frame.right - side * .50f, frame.centerY() - 5f * scale, 2.8f * scale, paint);
        canvas.drawCircle(frame.right - side * .50f, frame.centerY() + 6f * scale, 2.8f * scale, paint);
        paint.setColor(0xFF1B1915);
        canvas.drawRoundRect(frame.left + width * .16f, frame.bottom - 2f * scale,
                frame.right - width * .12f, frame.bottom + 3f * scale, 2f * scale, 2f * scale, paint);
    }

    private static int clamp(int c) { return Math.max(0, Math.min(255, c)); }
}
