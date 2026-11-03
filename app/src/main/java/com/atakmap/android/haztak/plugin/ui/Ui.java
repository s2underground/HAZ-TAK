package com.atakmap.android.haztak.plugin.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.haztak.plugin.core.Phones;

import java.util.List;
import java.util.Locale;

/** Small view helpers shared by the pane and the reference pages. */
final class Ui {

    private Ui() {
    }

    static final int BG = 0xFF1E1E1E;
    static final int CARD = 0xFF2A2A2A;
    static final int CARD2 = 0xFF323232;
    static final int TEXT = 0xFFEEEEEE;
    static final int DIM = 0xFFAAAAAA;
    static final int BLUE = 0xFF40C4FF;
    static final int AMBER = 0xFFFFC107;
    static final int GREEN = 0xFF69F0AE;
    static final int RED = 0xFFFF5252;
    static final int ORANGE = 0xFFFF9100;
    static final int TABLE_HEAD = 0xFF37474F;
    static final int GRID = 0xFF555555;

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    @SuppressWarnings("deprecation")
    static Spanned html(String s) {
        if (Build.VERSION.SDK_INT >= 24)
            return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY);
        return Html.fromHtml(s);
    }

    static TextView text(Context c, CharSequence s, float sp, int color,
            boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold)
            t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    static LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams full(int top, int bottom) {
        LinearLayout.LayoutParams lp = full();
        lp.setMargins(0, top, 0, bottom);
        return lp;
    }

    static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w);
    }

    /** Rounded box background; dashed when {@code dashed}. */
    static GradientDrawable box(Context c, int fill, int stroke, float strokeDp,
            boolean dashed) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, 4));
        if (dashed)
            g.setStroke(dp(c, strokeDp), stroke, dp(c, 5), dp(c, 3));
        else
            g.setStroke(dp(c, strokeDp), stroke);
        return g;
    }

    // ------------------------------------------------------------------
    // Tables

    /**
     * A table drawn as a grid: a header row, then rows with alternating
     * shading. Cells may be any CharSequence (spans for bold or links).
     */
    static View table(Context c, List<String> cols, List<List<CharSequence>> rows,
            float[] weights, float sp) {
        LinearLayout t = vertical(c);
        t.setBackgroundColor(GRID);
        int line = Math.max(1, dp(c, 1));
        t.setPadding(line, line, line, line);
        if (cols != null && !cols.isEmpty()) {
            LinearLayout h = horizontal(c);
            for (int i = 0; i < cols.size(); i++)
                h.addView(cell(c, cols.get(i), TABLE_HEAD, Color.WHITE, true, sp),
                        cellParams(weights, i, line));
            t.addView(h, full());
        }
        for (int r = 0; r < rows.size(); r++) {
            List<CharSequence> row = rows.get(r);
            LinearLayout h = horizontal(c);
            for (int i = 0; i < row.size(); i++)
                h.addView(cell(c, row.get(i), r % 2 == 0 ? CARD : CARD2, TEXT,
                        false, sp), cellParams(weights, i, line));
            LinearLayout.LayoutParams lp = full();
            lp.setMargins(0, line, 0, 0);
            t.addView(h, lp);
        }
        return t;
    }

    private static LinearLayout.LayoutParams cellParams(float[] w, int i,
            int line) {
        LinearLayout.LayoutParams lp = weight(w != null && i < w.length ? w[i] : 1f);
        if (i > 0)
            lp.setMargins(line, 0, 0, 0); // grid line: the grey background shows
        return lp;
    }

    private static TextView cell(Context c, CharSequence s, int bg, int fg,
            boolean bold, float sp) {
        TextView v = text(c, s, sp, fg, bold);
        v.setBackgroundColor(bg);
        int p = dp(c, 5);
        v.setPadding(p, p, p, p);
        v.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        if (s instanceof Spanned && ((Spanned) s).getSpans(0, s.length(),
                ClickableSpan.class).length > 0)
            v.setMovementMethod(LinkMovementMethod.getInstance());
        return v;
    }

    // ------------------------------------------------------------------
    // Telephone numbers: tap to open the dialler (no permission needed)

    /** Text with every telephone number made tappable. */
    static CharSequence dialable(final Context activity, CharSequence s) {
        SpannableStringBuilder sb = new SpannableStringBuilder(s);
        for (final Phones.Found f : Phones.find(s)) {
            sb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    dial(activity, f.dial);
                }

                @Override
                public void updateDrawState(TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(BLUE);
                    ds.setUnderlineText(true);
                }
            }, f.start, f.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    static boolean hasPhone(CharSequence s) {
        return !Phones.find(s).isEmpty();
    }

    static void dial(Context activity, String number) {
        try {
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"
                    + Uri.encode(number)));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, "No phone app on this device: "
                    + number, Toast.LENGTH_LONG).show();
        }
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.US);
    }
}
