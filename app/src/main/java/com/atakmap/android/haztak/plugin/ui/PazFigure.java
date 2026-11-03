package com.atakmap.android.haztak.plugin.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

import com.atakmap.android.haztak.plugin.core.Erg;

/**
 * The guidebook's protective action zone figure (ERG2024 p. 289), redrawn:
 * the spill at the centre of the initial isolation circle, the square's
 * upwind edge through the spill, its length and width equal to the downwind
 * distance (half on each side of the downwind line). Labels are the
 * figure's own words, verified against the page when the dataset is built.
 */
@SuppressLint("ViewConstructor") // built in code only, never inflated from XML
final class PazFigure extends View {

    private static final int SQUARE = 0xFFFF9100;
    private static final int SQUARE_FILL = 0x44FF9100;
    private static final int CIRCLE = 0xFFFF5252;
    private static final int CIRCLE_FILL = 0x66FF5252;
    private static final int INK = 0xFFEEEEEE;
    private static final int DIM = 0xFFAAAAAA;

    private final String wind, upwind, downwind, paz, iiz, dist, half, cross,
            spill;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF sq = new RectF();
    private final Path head = new Path();

    PazFigure(Context c, Erg.RefBlock b) {
        super(c);
        wind = b.figureLabel("Wind Direction");
        upwind = b.figureLabel("UPWIND");
        downwind = b.figureLabel("DOWNWIND");
        paz = b.figureLabel("Protective Action Zone");
        iiz = b.figureLabel("Initial Isolation Zone");
        dist = b.figureLabel("Downwind Distance");
        half = b.figureLabel("1/2 Downwind Distance");
        cross = b.figureLabel("Crosswind Direction(s)");
        spill = b.figureLabel("Spill");
        stroke.setStyle(Paint.Style.STROKE);
        fill.setStyle(Paint.Style.FILL);
        label.setTextSize(sp(11));
        setContentDescription("Figure: " + paz + ", " + iiz);
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }

    /** Side of the square for a given width. */
    private float side(float w) {
        return Math.max(dp(110), Math.min(w - dp(140), dp(230)));
    }

    /**
     * Below this width the figure is drawn at this width and scaled down as
     * a whole, so labels keep their places instead of overlapping.
     */
    private float minWidth() {
        return dp(350);
    }

    private float scale(int w) {
        return w < minWidth() ? w / minWidth() : 1f;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        if (w == 0)
            w = (int) minWidth();
        float k = scale(w);
        float d = side(w / k);
        setMeasuredDimension(w, (int) Math.ceil((dp(44) + d + dp(44)) * k));
    }

    @Override
    protected void onDraw(Canvas c) {
        float k = scale(getWidth());
        c.save();
        c.scale(k, k);
        drawFigure(c, getWidth() / k);
        c.restore();
    }

    private void drawFigure(Canvas c, float w) {
        float d = side(w);
        float left = dp(96);
        float total = left + d + dp(44);
        float sx = (w - total) / 2f + left; // spill x
        float top = dp(44);
        float sy = top + d / 2f;
        float r = d * 0.16f;

        // wind band
        label.setColor(DIM);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        label.setTextAlign(Paint.Align.LEFT);
        c.drawText(upwind, dp(4), dp(30), label);
        label.setTextAlign(Paint.Align.RIGHT);
        c.drawText(downwind, w - dp(4), dp(30), label);
        label.setTypeface(android.graphics.Typeface.DEFAULT);
        label.setColor(INK);
        label.setTextAlign(Paint.Align.CENTER);
        float ax0 = w * 0.30f, ax1 = w * 0.70f, ay = dp(26);
        c.drawText(wind, (ax0 + ax1) / 2f, dp(14), label);
        arrow(c, ax0, ay, ax1, ay, INK, dp(2));

        // square
        sq.set(sx, sy - d / 2f, sx + d, sy + d / 2f);
        fill.setColor(SQUARE_FILL);
        c.drawRect(sq, fill);
        stroke.setColor(SQUARE);
        stroke.setStrokeWidth(dp(2));
        c.drawRect(sq, stroke);
        label.setColor(INK);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textBlock(c, paz, sx + d * 0.66f, sy - d * 0.18f, d * 0.56f);
        label.setTypeface(android.graphics.Typeface.DEFAULT);

        // crosswind double arrow inside the square
        float cx = sx + d * 0.24f;
        arrow(c, cx, sy, cx, sq.top + dp(6), DIM, dp(1.5f));
        arrow(c, cx, sy, cx, sq.bottom - dp(6), DIM, dp(1.5f));
        label.setColor(DIM);
        rotated(c, cross, cx + dp(12), sy, d - dp(16), -90);

        // isolation circle and spill
        fill.setColor(CIRCLE_FILL);
        c.drawCircle(sx, sy, r, fill);
        stroke.setColor(CIRCLE);
        stroke.setStrokeWidth(dp(2));
        c.drawCircle(sx, sy, r, stroke);
        fill.setColor(0xFF000000);
        c.drawCircle(sx, sy, dp(4), fill);
        stroke.setColor(INK);
        stroke.setStrokeWidth(dp(1));
        c.drawCircle(sx, sy, dp(4), stroke);

        label.setColor(INK);
        label.setTextAlign(Paint.Align.RIGHT);
        c.drawText(spill, sx - r - dp(6), sy + dp(4), label);
        // isolation zone label below the circle, with a leader to it
        float lx = sx - dp(6), ly = sy + r + dp(18);
        textBlockRight(c, iiz, lx, ly, sx - dp(10));
        stroke.setColor(DIM);
        stroke.setStrokeWidth(dp(1));
        c.drawLine(lx - dp(4), ly - dp(11), sx - r * 0.6f, sy + r * 0.8f, stroke);

        // half downwind distance (right side, spill line to top)
        float hx = sq.right + dp(10);
        dimension(c, hx, sq.top, hx, sy);
        label.setColor(INK);
        rotated(c, half, hx + dp(10), (sq.top + sy) / 2f, d / 2f - dp(4), 90);

        // downwind distance (below)
        float dy = sq.bottom + dp(14);
        dimension(c, sq.left, dy, sq.right, dy);
        label.setTextAlign(Paint.Align.CENTER);
        label.setColor(INK);
        c.drawText(dist, (sq.left + sq.right) / 2f, dy + dp(18), label);
    }

    /**
     * Text turned by {@code deg} about (x, y), centred there, wrapped onto
     * more lines (side by side) when longer than {@code max}.
     */
    private void rotated(Canvas c, String s, float x, float y, float max,
            float deg) {
        label.setTextSize(sp(10));
        label.setTextAlign(Paint.Align.CENTER);
        String[] lines = split(s, max);
        float lh = label.getTextSize() * 1.15f;
        c.save();
        c.rotate(deg, x, y);
        for (int i = 0; i < lines.length; i++)
            c.drawText(lines[i], x, y + dp(4) + (deg > 0 ? -i : i) * lh, label);
        c.restore();
        label.setTextSize(sp(11));
    }

    /** Centered text, wrapped when wider than {@code max}. */
    private void textBlock(Canvas c, String s, float x, float y, float max) {
        label.setTextAlign(Paint.Align.CENTER);
        String[] lines = split(s, max);
        float lh = label.getTextSize() * 1.2f;
        for (int i = 0; i < lines.length; i++)
            c.drawText(lines[i], x, y + i * lh, label);
    }

    private void textBlockRight(Canvas c, String s, float x, float y, float max) {
        label.setTextAlign(Paint.Align.RIGHT);
        String[] lines = split(s, max);
        float lh = label.getTextSize() * 1.2f;
        for (int i = 0; i < lines.length; i++)
            c.drawText(lines[i], x, y + i * lh, label);
    }

    /** Greedy word wrap to {@code max} pixels. */
    private String[] split(String s, float max) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : s.split(" ")) {
            String trial = line.length() == 0 ? w : line + " " + w;
            if (line.length() > 0 && label.measureText(trial) > max) {
                lines.add(line.toString());
                line = new StringBuilder(w);
            } else {
                line = new StringBuilder(trial);
            }
        }
        lines.add(line.toString());
        return lines.toArray(new String[0]);
    }

    private void arrow(Canvas c, float x0, float y0, float x1, float y1,
            int color, float width) {
        stroke.setColor(color);
        stroke.setStrokeWidth(width);
        c.drawLine(x0, y0, x1, y1, stroke);
        double a = Math.atan2(y1 - y0, x1 - x0);
        float h = dp(8);
        Path p = head;
        p.reset();
        p.moveTo(x1, y1);
        p.lineTo((float) (x1 - h * Math.cos(a - 0.45)), (float) (y1 - h * Math.sin(a - 0.45)));
        p.lineTo((float) (x1 - h * Math.cos(a + 0.45)), (float) (y1 - h * Math.sin(a + 0.45)));
        p.close();
        fill.setColor(color);
        c.drawPath(p, fill);
    }

    /** A dimension line with arrow heads at both ends. */
    private void dimension(Canvas c, float x0, float y0, float x1, float y1) {
        float mx = (x0 + x1) / 2f, my = (y0 + y1) / 2f;
        arrow(c, mx, my, x0, y0, DIM, dp(1));
        arrow(c, mx, my, x1, y1, DIM, dp(1));
    }
}
