package io.github.p4grecomp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.SeekBar;

/** Comic-style pause-screen controls. These views only belong to PauseActivity. */
final class P4gPauseCategoryButton extends Button {
    private final P4gPauseControlDrawable artwork;

    P4gPauseCategoryButton(Context context) {
        super(context);
        artwork = new P4gPauseControlDrawable(context, P4gPauseControlDrawable.NAV);
        setBackground(artwork);
        setAllCaps(false);
        setSingleLine(true);
        setTextColor(Color.WHITE);
        setTextSize(14);
        setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        setPadding(P4gTheme.dp(context, 12), 0, P4gTheme.dp(context, 6), 0);
        setMinHeight(P4gTheme.dp(context, 38));
        setStateListAnimator(null);
        setFocusable(true);
    }

    void setCategorySelected(boolean selected) {
        artwork.setSelected(selected);
        setTextColor(selected ? P4gTheme.INK : Color.WHITE);
        setShadowLayer(selected ? 0 : 2, 1, 1, Color.BLACK);
    }
}

final class P4gPauseActionButton extends Button {
    private final P4gPauseControlDrawable artwork;
    private final boolean primary;

    P4gPauseActionButton(Context context, boolean primary) {
        super(context);
        this.primary = primary;
        artwork = new P4gPauseControlDrawable(context, P4gPauseControlDrawable.ACTION);
        setBackground(artwork);
        setAllCaps(false);
        setSingleLine(true);
        setGravity(Gravity.CENTER);
        setTextSize(15);
        setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        setPadding(P4gTheme.dp(context, 12), 0, P4gTheme.dp(context, 12), 0);
        setMinHeight(P4gTheme.dp(context, 46));
        setStateListAnimator(null);
        setFocusable(true);
        setPrimary(primary);
    }

    void setPrimary(boolean value) {
        artwork.setSelected(value);
        setTextColor(value ? P4gTheme.INK : Color.BLACK);
        setShadowLayer(0, 0, 0, Color.TRANSPARENT);
    }

    @Override public void setSelected(boolean selected) {
        super.setSelected(selected);
        if (artwork != null) artwork.setSelected(primary || selected);
    }
}

final class P4gPauseValueButton extends Button {
    private final P4gPauseControlDrawable artwork;

    P4gPauseValueButton(Context context) {
        super(context);
        artwork = new P4gPauseControlDrawable(context, P4gPauseControlDrawable.VALUE);
        setBackground(artwork);
        setAllCaps(false);
        setSingleLine(true);
        setEllipsize(android.text.TextUtils.TruncateAt.END);
        setGravity(Gravity.CENTER);
        setTextColor(P4gTheme.YELLOW);
        setTextSize(13);
        setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        setPadding(P4gTheme.dp(context, 9), 0, P4gTheme.dp(context, 9), 0);
        setMinHeight(P4gTheme.dp(context, 38));
        setStateListAnimator(null);
        setFocusable(true);
    }

    @Override public void setSelected(boolean selected) {
        super.setSelected(selected);
        if (artwork != null) artwork.setSelected(selected);
        setTextColor(selected ? P4gTheme.INK : P4gTheme.YELLOW);
    }
}

final class P4gPauseSeekBar extends SeekBar {
    P4gPauseSeekBar(Context context) {
        super(context);
        setMax(100);
        setProgressTintList(ColorStateList.valueOf(P4gTheme.YELLOW));
        setProgressBackgroundTintList(ColorStateList.valueOf(0xAA17130B));
        setThumbTintList(ColorStateList.valueOf(P4gTheme.YELLOW));
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(P4gTheme.YELLOW);
        thumb.setStroke(P4gTheme.dp(context, 2), Color.BLACK);
        setThumb(thumb);
        setThumbOffset(P4gTheme.dp(context, 9));
        setPadding(P4gTheme.dp(context, 8), 0, P4gTheme.dp(context, 8), 0);
        setMinimumHeight(P4gTheme.dp(context, 38));
        setFocusable(true);
    }
}

final class P4gPausePanelDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path panel = new Path();
    private final Path stripe = new Path();
    private final float density;
    private int accent = P4gTheme.YELLOW;
    private final int[] rainbow = {0xFF00A6D7, 0xFF48BF73, 0xFFFFD92C, 0xFFFF7E21, 0xFFEC384C, 0xFF7F46BD};

    P4gPausePanelDrawable(Context context) { density = context.getResources().getDisplayMetrics().density; }
    void setAccent(int color) { accent = color; invalidateSelf(); }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        float w = b.width(), h = b.height(), skew = Math.min(15f * density, h * .11f);
        panel.reset();
        panel.moveTo(b.left + skew, b.top + 2f * density);
        panel.lineTo(b.right - 2f * density, b.top);
        panel.lineTo(b.right - skew, b.bottom - 2f * density);
        panel.lineTo(b.left + 2f * density, b.bottom);
        panel.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        canvas.save(); canvas.translate(4f * density, 4f * density); canvas.drawPath(panel, paint); canvas.restore();
        paint.setColor(0xFFF5D600);
        canvas.drawPath(panel, paint);
        canvas.save();
        canvas.clipPath(panel);
        paint.setColor(0x20FFFFFF);
        canvas.drawRect(b.left, b.top, b.right, b.top + Math.max(3f * density, h * .035f), paint);
        float band = Math.max(4f * density, 7f * density);
        for (int i = 0; i < rainbow.length; i++) {
            stripe.reset();
            float x = b.left + i * band;
            stripe.moveTo(x, b.top); stripe.lineTo(x + band * .55f, b.top);
            stripe.lineTo(x + h * .10f + band * .55f, b.bottom); stripe.lineTo(x + h * .10f, b.bottom);
            stripe.close();
            paint.setColor(rainbow[i]);
            paint.setAlpha(110);
            canvas.drawPath(stripe, paint);
        }
        paint.setAlpha(255);
        canvas.restore();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.MITER);
        paint.setStrokeWidth(3.2f * density);
        paint.setColor(Color.BLACK);
        canvas.drawPath(panel, paint);
        paint.setStrokeWidth(1.4f * density);
        paint.setColor(0xCCFFF9D4);
        canvas.drawLine(b.left + skew + 7 * density, b.top + 5 * density,
                b.right - 12 * density, b.top + 5 * density, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        P4gLogoView.drawStar(canvas, b.right - 19 * density, b.top + 18 * density, 7 * density, paint);
        paint.setColor(accent);
        canvas.drawCircle(b.right - 19 * density, b.top + 18 * density, 2.4f * density, paint);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}

final class P4gPauseControlDrawable extends Drawable {
    static final int NAV = 0, ACTION = 1, VALUE = 2;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final float density;
    private final int kind;
    private boolean selected;
    P4gPauseControlDrawable(Context context, int kind) {
        this.kind = kind;
        density = context.getResources().getDisplayMetrics().density;
    }
    void setSelected(boolean value) { selected = value; invalidateSelf(); }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        float skew = Math.min(10f * density, b.height() * .18f);
        shape.reset();
        shape.moveTo(b.left + skew, b.top + 1f * density);
        shape.lineTo(b.right - 1f * density, b.top);
        shape.lineTo(b.right - skew, b.bottom - 1f * density);
        shape.lineTo(b.left + 1f * density, b.bottom);
        shape.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xEE080705);
        canvas.save(); canvas.translate(2f * density, 2f * density); canvas.drawPath(shape, paint); canvas.restore();
        if (kind == NAV) {
            paint.setColor(selected ? P4gTheme.YELLOW : 0xA8110F0B);
        } else if (kind == ACTION) {
            paint.setColor(selected ? P4gTheme.YELLOW : 0xFFF6F0D9);
        } else {
            paint.setColor(selected ? P4gTheme.YELLOW : 0xF2110F0B);
        }
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.MITER);
        paint.setStrokeWidth((selected || kind == ACTION ? 2.6f : 1.8f) * density);
        paint.setColor(Color.BLACK);
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.FILL);
        if (kind == NAV && !selected) {
            paint.setColor(P4gTheme.YELLOW);
            canvas.drawRect(b.left + 2f * density, b.top + 4f * density,
                    b.left + 4f * density, b.bottom - 4f * density, paint);
        }
        if (kind == ACTION && !selected) {
            paint.setColor(0xFF343026);
            float y = b.bottom - 4f * density;
            canvas.drawRect(b.left + skew, y, b.right - skew, y + 1.4f * density, paint);
        }
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
