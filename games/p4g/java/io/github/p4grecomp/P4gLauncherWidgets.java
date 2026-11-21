package io.github.p4grecomp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;

final class P4gNavBackdrop extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] colors = {0xDDFFFFFF, 0xDD00A6D7, 0xDD48BF73, 0xDDFFD92C, 0xDDFF7E21, 0xDDEC384C, 0xDD7F46BD};
    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        paint.setColor(0x8C17120A);
        canvas.drawRect(b, paint);
        canvas.save();
        canvas.clipRect(b);
        canvas.rotate(-36, b.left, b.centerY());
        float band = Math.max(10f, b.width() * .075f);
        float left = b.left - b.width();
        float top = b.top - b.height();
        float bottom = b.bottom + b.height();
        for (int i = 0; i < colors.length; i++) {
            paint.setColor(colors[i]);
            canvas.drawRect(left + i * band, top, left + (i + 1) * band, bottom, paint);
        }
        canvas.restore();
        paint.setColor(0x7720170B);
        canvas.drawRect(b.right - 5, b.top, b.right, b.bottom, paint);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}

/** P4G comic-style launcher controls; these views are never attached to the game renderer. */
final class P4gMenuButton extends Button {
    private final P4gComicDrawable artwork;

    P4gMenuButton(Context context) {
        super(context);
        artwork = new P4gComicDrawable(context, P4gComicDrawable.NAV);
        setBackground(artwork);
        setAllCaps(false);
        setSingleLine(true);
        setEllipsize(android.text.TextUtils.TruncateAt.END);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        setTextSize(19);
        setTextColor(Color.WHITE);
        setShadowLayer(2.5f, 2f, 2f, Color.BLACK);
        setPadding(P4gTheme.dp(context, 16), P4gTheme.dp(context, 4), P4gTheme.dp(context, 8), P4gTheme.dp(context, 4));
        setMinHeight(P4gTheme.dp(context, 48));
        setStateListAnimator(null);
        setFocusable(true);
        setContentDescription("Menu");
    }

    void setArtSelected(boolean selected) {
        artwork.setSelected(selected);
        setTextColor(selected ? P4gTheme.INK : Color.WHITE);
        setShadowLayer(selected ? 0f : 2.5f, 2f, 2f, Color.BLACK);
        invalidate();
    }
}

final class P4gQuickButton extends Button {
    private final P4gComicDrawable artwork;

    P4gQuickButton(Context context) {
        super(context);
        artwork = new P4gComicDrawable(context, P4gComicDrawable.CHIP);
        setBackground(artwork);
        setAllCaps(false);
        setSingleLine(true);
        setEllipsize(android.text.TextUtils.TruncateAt.END);
        setGravity(Gravity.CENTER);
        setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        setTextSize(12);
        setTextColor(Color.BLACK);
        setPadding(P4gTheme.dp(context, 9), 0, P4gTheme.dp(context, 9), 0);
        setMinHeight(P4gTheme.dp(context, 38));
        setStateListAnimator(null);
    }

    void setActive(boolean active) { artwork.setSelected(active); invalidate(); }
}

final class P4gPlayButton extends Button {
    P4gPlayButton(Context context) {
        super(context);
        setBackground(new P4gComicDrawable(context, P4gComicDrawable.PLAY));
        setAllCaps(false);
        setText("¡JUGAR!");
        setGravity(Gravity.CENTER);
        setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        setTextSize(29);
        setTextColor(Color.BLACK);
        setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
        setPadding(P4gTheme.dp(context, 16), P4gTheme.dp(context, 4), P4gTheme.dp(context, 16), P4gTheme.dp(context, 4));
        setMinHeight(P4gTheme.dp(context, 66));
        setStateListAnimator(null);
        setFocusable(true);
        setContentDescription("¡Jugar!");
    }
}

final class P4gLogoView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ribbon = new Path();

    P4gLogoView(Context context) {
        super(context);
        setContentDescription("Persona 4 Golden");
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        ribbon.reset();
        ribbon.moveTo(w * .02f, h * .19f);
        ribbon.lineTo(w * .97f, h * .02f);
        ribbon.lineTo(w * .87f, h * .91f);
        ribbon.lineTo(w * .08f, h * .98f);
        ribbon.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xEE17130E);
        canvas.drawPath(ribbon, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, w * .018f));
        paint.setColor(P4gTheme.YELLOW);
        canvas.drawPath(ribbon, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
        paint.setTextSize(h * .38f);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Math.max(3f, h * .07f));
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.BLACK);
        canvas.drawText("PERSONA", w * .08f, h * .40f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawText("PERSONA", w * .08f, h * .40f, paint);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        paint.setTextSize(h * .36f);
        paint.setStrokeWidth(Math.max(3f, h * .06f));
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.BLACK);
        canvas.drawText("4 GOLDEN", w * .20f, h * .80f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(P4gTheme.YELLOW);
        canvas.drawText("4 GOLDEN", w * .20f, h * .80f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        drawStar(canvas, w * .89f, h * .22f, h * .105f, paint);
    }

    static void drawStar(Canvas canvas, float cx, float cy, float radius, Paint paint) {
        Path star = new Path();
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            float r = radius * (i % 2 == 0 ? 1f : .42f);
            float x = cx + (float) Math.cos(angle) * r;
            float y = cy + (float) Math.sin(angle) * r;
            if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
        }
        star.close();
        canvas.drawPath(star, paint);
    }
}

/** Visible, draggable scrollbar paired with the active options ScrollView. */
final class P4gScrollRail extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private final RectF thumb = new RectF();
    private ScrollView scroll;
    private float thumbHeight;
    private boolean dragging;
    private float dragOffset;

    P4gScrollRail(Context context) {
        super(context);
        setMinimumWidth(P4gTheme.dp(context, 16));
        setContentDescription("Deslizador de opciones");
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    void attach(ScrollView target) {
        scroll = target;
        target.setVerticalScrollBarEnabled(false);
        target.setOnScrollChangeListener((View v, int x, int y, int oldX, int oldY) -> invalidate());
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (scroll == null) return;
        float inset = getWidth() * .42f;
        track.set(inset, P4gTheme.dp(getContext(), 7), getWidth() - inset,
                getHeight() - P4gTheme.dp(getContext(), 7));
        paint.setColor(0x990B0905);
        canvas.drawRoundRect(track, track.width(), track.width(), paint);
        int range = contentRange();
        int extent = scroll.getHeight();
        int offset = scroll.getScrollY();
        float available = track.height();
        thumbHeight = range <= 0 ? available : Math.max(P4gTheme.dp(getContext(), 42), available * extent / range);
        thumbHeight = Math.min(available, thumbHeight);
        float y = range <= extent ? track.top : track.top + (available - thumbHeight) * offset / (range - extent);
        thumb.set(track.left - P4gTheme.dp(getContext(), 3), y,
                track.right + P4gTheme.dp(getContext(), 3), y + thumbHeight);
        paint.setColor(0xFF14110B);
        canvas.drawRoundRect(thumb, P4gTheme.dp(getContext(), 6), P4gTheme.dp(getContext(), 6), paint);
        thumb.inset(P4gTheme.dp(getContext(), 2), P4gTheme.dp(getContext(), 2));
        paint.setColor(P4gTheme.YELLOW);
        canvas.drawRoundRect(thumb, P4gTheme.dp(getContext(), 4), P4gTheme.dp(getContext(), 4), paint);
        paint.setColor(0xFF17130A);
        canvas.drawCircle(thumb.centerX(), thumb.centerY(), Math.max(P4gTheme.dp(getContext(), 2), thumb.width() * .14f), paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (scroll == null) return false;
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                if (thumb.contains(event.getX(), y)) dragOffset = y - thumb.top;
                else { dragOffset = thumbHeight * .5f; moveTo(y - dragOffset); }
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) { moveTo(y - dragOffset); return true; }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
            default: return dragging;
        }
    }

    private void moveTo(float top) {
        float inset = getWidth() * .42f;
        float railTop = P4gTheme.dp(getContext(), 7);
        float railHeight = getHeight() - 2f * railTop - thumbHeight;
        int range = contentRange();
        int extent = scroll.getHeight();
        float fraction = railHeight <= 0 ? 0f : Math.max(0f, Math.min(1f, (top - railTop) / railHeight));
        scroll.scrollTo(0, Math.round(fraction * Math.max(0, range - extent)));
        invalidate();
    }

    private int contentRange() {
        if (scroll == null || scroll.getChildCount() == 0) return 0;
        return scroll.getChildAt(0).getHeight();
    }

    @Override public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (scroll == null) return super.onKeyDown(keyCode, event);
        int step = Math.max(P4gTheme.dp(getContext(), 48), scroll.getHeight() / 8);
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) { scroll.smoothScrollBy(0, -step); return true; }
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) { scroll.smoothScrollBy(0, step); return true; }
        if (keyCode == android.view.KeyEvent.KEYCODE_PAGE_UP) { scroll.pageScroll(View.FOCUS_UP); return true; }
        if (keyCode == android.view.KeyEvent.KEYCODE_PAGE_DOWN) { scroll.pageScroll(View.FOCUS_DOWN); return true; }
        return super.onKeyDown(keyCode, event);
    }
}

final class P4gComicDrawable extends Drawable {
    static final int NAV = 0, CHIP = 1, PLAY = 2;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stripe = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect bounds = new Rect();
    private final Path shape = new Path();
    private final Path clip = new Path();
    private final int mode;
    private final float density;
    private boolean selected;
    private final int[] colors = {Color.WHITE, 0xFF00A6D7, 0xFF48BF73, 0xFFFFD92C, 0xFFFF7E21, 0xFFEC384C, 0xFF7F46BD};

    P4gComicDrawable(Context context, int mode) { this.mode = mode; density = context.getResources().getDisplayMetrics().density; }
    void setSelected(boolean selected) { this.selected = selected; invalidateSelf(); }

    @Override public void draw(Canvas canvas) {
        copyBounds(bounds);
        float left = bounds.left, top = bounds.top, right = bounds.right, bottom = bounds.bottom;
        float w = right - left, h = bottom - top, skew = Math.min(h * .28f, 13f * density);
        shape.reset();
        shape.moveTo(left + skew, top + 2f * density);
        shape.lineTo(right, top);
        shape.lineTo(right - skew, bottom - 2f * density);
        shape.lineTo(left, bottom);
        shape.close();
        if (mode == PLAY) drawPlay(canvas, left, top, right, bottom, w, h, skew);
        else if (mode == CHIP) drawChip(canvas, left, top, right, bottom, w, h, skew);
        else drawNav(canvas, left, top, right, bottom, w, h, skew);
    }

    private void drawNav(Canvas canvas, float left, float top, float right, float bottom, float w, float h, float skew) {
        if (!selected) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.6f * density);
            paint.setColor(0xBB0C0A06);
            canvas.drawPath(shape, paint);
            paint.setStyle(Paint.Style.FILL);
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xCC000000);
        canvas.save(); canvas.translate(2f * density, 2f * density); canvas.drawPath(shape, paint); canvas.restore();
        paint.setColor(P4gTheme.YELLOW);
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.MITER);
        paint.setStrokeWidth(3.4f * density);
        paint.setColor(Color.BLACK);
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.FILL);
        P4gLogoView.drawStar(canvas, right - 12f * density, top + 8f * density, 5f * density, paint);
    }

    private void drawChip(Canvas canvas, float left, float top, float right, float bottom, float w, float h, float skew) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xEE0C0A06);
        canvas.save(); canvas.translate(2f * density, 2f * density); canvas.drawPath(shape, paint); canvas.restore();
        paint.setColor(selected ? P4gTheme.YELLOW : 0xFFF7F1D8);
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.2f * density);
        paint.setColor(Color.BLACK);
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x44351E00);
        canvas.drawRect(left + skew, bottom - 5f * density, right - skew, bottom - 2f * density, paint);
    }

    private void drawPlay(Canvas canvas, float left, float top, float right, float bottom, float w, float h, float skew) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        canvas.save(); canvas.translate(4f * density, 4f * density); canvas.drawPath(shape, paint); canvas.restore();
        paint.setColor(0xFFFFE200);
        canvas.drawPath(shape, paint);
        float inset = 5f * density;
        clip.reset();
        clip.moveTo(left + skew + inset, top + inset);
        clip.lineTo(right - inset, top + inset);
        clip.lineTo(right - skew - inset, bottom - inset);
        clip.lineTo(left + inset, bottom - inset);
        clip.close();
        canvas.save(); canvas.clipPath(clip);
        paint.setColor(0xFFFFF9DA); canvas.drawRect(left, top, right, bottom, paint);
        float step = Math.max(11f * density, w * .05f);
        for (float x = left - h; x < right + h; x += step) {
            stripe.setColor(colors[(int) Math.floor((x - left) / step + 8) % colors.length]);
            Path band = new Path();
            band.moveTo(x, top); band.lineTo(x + step * .45f, top); band.lineTo(x + h * .32f, bottom); band.lineTo(x + h * .32f - step * .45f, bottom); band.close();
            canvas.drawPath(band, stripe);
        }
        paint.setColor(0x66FFFFFF);
        for (int i = 0; i < 4; i++) P4gLogoView.drawStar(canvas, left + w * (.15f + i * .23f), top + h * ((i % 2 == 0) ? .22f : .76f), 4.2f * density, paint);
        canvas.restore();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.MITER);
        paint.setStrokeWidth(4f * density);
        paint.setColor(Color.BLACK);
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
