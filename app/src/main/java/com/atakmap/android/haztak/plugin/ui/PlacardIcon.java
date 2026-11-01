package com.atakmap.android.haztak.plugin.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * Simple original placard pictures for the placard picker: a diamond in the
 * class colours, a short word and the class number. They help the user match
 * what they see; they are not reproductions of the regulatory designs.
 */
final class PlacardIcon {

    private PlacardIcon() {
    }

    private static final int RED = 0xFFE53935, GREEN = 0xFF2E7D32,
            YELLOW = 0xFFFDD835, ORANGE = 0xFFFB8C00, BLUE = 0xFF1E88E5,
            WHITE = 0xFFFFFFFF, BLACK = 0xFF000000, GREY = 0xFF9E9E9E;

    /** Top colour, bottom colour, text colour, word. */
    private static Object[] scheme(String key) {
        switch (key) {
            case "dangerous":
                return new Object[] { WHITE, WHITE, BLACK, "DANGEROUS", RED };
            case "unknown":
                return new Object[] { GREY, GREY, BLACK, "?", null };
            case "exp_1_1": case "exp_1_2": case "exp_1_3": case "exp_1_4":
            case "exp_1_5": case "exp_1_6":
                return new Object[] { ORANGE, ORANGE, BLACK, "EXPLOSIVES", null };
            case "gas_flam":
                return new Object[] { RED, RED, WHITE, "FLAMMABLE GAS", null };
            case "gas_nonflam":
                return new Object[] { GREEN, GREEN, WHITE, "NON-FLAMMABLE GAS", null };
            case "gas_oxygen":
                return new Object[] { YELLOW, YELLOW, BLACK, "OXYGEN", null };
            case "gas_toxic":
                return new Object[] { WHITE, WHITE, BLACK, "INHALATION HAZARD", null };
            case "gas_1005":
                return new Object[] { WHITE, WHITE, BLACK, "1005", null };
            case "flam_liq":
                return new Object[] { RED, RED, WHITE, "FLAMMABLE", null };
            case "fuel":
                return new Object[] { RED, RED, WHITE, "FUEL OIL GASOLINE", null };
            case "hot":
                return new Object[] { WHITE, WHITE, RED, "HOT", null };
            case "flam_solid":
                return new Object[] { RED, WHITE, BLACK, "FLAMMABLE SOLID", RED };
            case "spont":
                return new Object[] { WHITE, RED, BLACK, "SPONT. COMBUSTIBLE", null };
            case "wet":
                return new Object[] { BLUE, BLUE, WHITE, "DANGEROUS WHEN WET", null };
            case "oxidizer":
                return new Object[] { YELLOW, YELLOW, BLACK, "OXIDIZER", null };
            case "peroxide":
                return new Object[] { RED, YELLOW, BLACK, "ORGANIC PEROXIDE", null };
            case "toxic":
                return new Object[] { WHITE, WHITE, BLACK, "TOXIC / POISON", null };
            case "infectious":
                return new Object[] { WHITE, WHITE, BLACK, "INFECTIOUS", null };
            case "radioactive":
                return new Object[] { YELLOW, WHITE, BLACK, "RADIOACTIVE", null };
            case "corrosive":
                return new Object[] { WHITE, BLACK, BLACK, "CORROSIVE", null };
            case "li_metal": case "li_ion": case "misc":
                return new Object[] { WHITE, WHITE, BLACK, "", BLACK };
            default:
                return new Object[] { GREY, GREY, BLACK, "", null };
        }
    }

    static Drawable make(Resources res, String key, String cls, int sizePx) {
        Bitmap b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Object[] s = scheme(key);
        int top = (Integer) s[0], bottom = (Integer) s[1], text = (Integer) s[2];
        String word = (String) s[3];
        Integer accent = (Integer) s[4];
        float h = sizePx / 2f, inset = sizePx * 0.04f;
        Path diamond = new Path();
        diamond.moveTo(h, inset);
        diamond.lineTo(sizePx - inset, h);
        diamond.lineTo(h, sizePx - inset);
        diamond.lineTo(inset, h);
        diamond.close();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.save();
        c.clipPath(diamond);
        p.setColor(top);
        c.drawRect(0, 0, sizePx, h, p);
        p.setColor(bottom);
        c.drawRect(0, h, sizePx, sizePx, p);
        if (accent != null) {
            p.setColor(accent);
            if ("flam_solid".equals(key)) {
                p.setStrokeWidth(sizePx * 0.07f);
                for (float x = -sizePx; x < 2 * sizePx; x += sizePx * 0.18f)
                    c.drawLine(x, sizePx, x + sizePx, 0, p);
            } else if ("dangerous".equals(key)) {
                c.drawRect(0, 0, sizePx, sizePx * 0.28f, p);
                c.drawRect(0, sizePx * 0.72f, sizePx, sizePx, p);
            } else { // class 9: stripes in the top half
                p.setStrokeWidth(sizePx * 0.05f);
                for (float x = sizePx * 0.2f; x < sizePx * 0.8f; x += sizePx * 0.11f)
                    c.drawLine(x, 0, x, h * 0.95f, p);
            }
        }
        c.restore();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, sizePx * 0.025f));
        p.setColor(top == WHITE && bottom == WHITE ? BLACK : 0xFF333333);
        c.drawPath(diamond, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(text == WHITE || (bottom == BLACK) ? WHITE : text);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        if (word != null && !word.isEmpty()) {
            p.setColor(top == BLACK || (top == RED && text == WHITE)
                    || top == BLUE || top == GREEN ? WHITE : BLACK);
            if ("dangerous".equals(key))
                p.setColor(BLACK);
            String[] lines = wrap(word);
            float ts = sizePx * (lines.length > 1 ? 0.085f : 0.1f);
            if (word.equals("?"))
                ts = sizePx * 0.3f;
            p.setTextSize(ts);
            float y = h - (lines.length - 1) * ts * 0.55f + ts * 0.35f;
            for (String l : lines) {
                c.drawText(l, h, y, p);
                y += ts * 1.1f;
            }
        }
        if (cls != null && !cls.isEmpty()) {
            p.setTextSize(sizePx * 0.13f);
            p.setColor(bottom == BLACK || bottom == RED && !"peroxide".equals(key)
                    && !"spont".equals(key) || bottom == BLUE || bottom == GREEN
                            ? WHITE : BLACK);
            c.drawText(cls, h, sizePx * 0.86f, p);
        }
        BitmapDrawable d = new BitmapDrawable(res, b);
        d.setBounds(0, 0, sizePx, sizePx);
        return d;
    }

    private static String[] wrap(String w) {
        if (w.length() <= 10 || !w.contains(" "))
            return new String[] { w };
        int mid = w.length() / 2, best = -1;
        for (int i = 0; i < w.length(); i++)
            if (w.charAt(i) == ' ' && (best < 0 || Math.abs(i - mid) < Math.abs(best - mid)))
                best = i;
        return new String[] { w.substring(0, best), w.substring(best + 1) };
    }

    static int color(int c) {
        return Color.argb(255, Color.red(c), Color.green(c), Color.blue(c));
    }
}
