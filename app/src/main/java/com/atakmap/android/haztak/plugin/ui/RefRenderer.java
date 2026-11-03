package com.atakmap.android.haztak.plugin.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atakmap.android.haztak.plugin.core.Erg;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the reference pages from their structured blocks: headings,
 * paragraphs, numbered steps, the flowchart as coloured decision boxes,
 * tables as grids, telephone numbers as tap-to-dial, and the protective
 * action zone figure. The words are the guidebook's (verified when the
 * dataset is built); the buttons are HAZ-TAK's.
 */
final class RefRenderer {

    /** What the plugin does when a flowchart or link button is tapped. */
    interface Actions {
        void run(String action);
    }

    // Flowchart colours, as printed
    private static final int ERG_ORANGE = 0xFFF7941D;
    private static final int ERG_YELLOW = 0xFFFFF200;
    private static final int ERG_BLUE = 0xFF6CA0DC;
    private static final int ERG_GREEN = 0xFF8DC63F;
    private static final int INK = 0xFF000000;

    private final Context c;
    private final Context activity;
    private final Actions actions;
    private final boolean phones;

    RefRenderer(Context plugin, Context activity, Actions actions,
            boolean phones) {
        this.c = plugin;
        this.activity = activity;
        this.actions = actions;
        this.phones = phones;
    }

    private int dp(float v) {
        return Ui.dp(c, v);
    }

    /** Width share with the height of the text itself (not the row's). */
    private static LinearLayout.LayoutParams wrapWeight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    void render(LinearLayout col, Erg.RefPage page) {
        for (Erg.RefBlock b : page.blocks)
            add(col, b);
    }

    private CharSequence rich(String s) {
        CharSequence h = Ui.html(s);
        return phones && Ui.hasPhone(h) ? Ui.dialable(activity, h) : h;
    }

    private TextView para(String s, float sp, int color) {
        TextView t = Ui.text(c, rich(s), sp, color, false);
        if (phones)
            t.setMovementMethod(LinkMovementMethod.getInstance());
        return t;
    }

    private void add(LinearLayout col, Erg.RefBlock b) {
        switch (b.type) {
            case "h": {
                TextView t = Ui.text(c, Ui.html(b.text), 14, Ui.AMBER, true);
                col.addView(t, Ui.full(dp(12), dp(4)));
                break;
            }
            case "h2": {
                TextView t = Ui.text(c, Ui.html(b.text), 13, Ui.TEXT, true);
                col.addView(t, Ui.full(dp(8), dp(2)));
                break;
            }
            case "p":
                col.addView(para(b.text, 13, Ui.TEXT), Ui.full(dp(2), dp(4)));
                break;
            case "b":
            case "b2": {
                boolean sub = b.type.equals("b2");
                LinearLayout row = Ui.horizontal(c);
                row.setPadding(dp(sub ? 22 : 6), 0, 0, 0);
                TextView dot = Ui.text(c, sub ? "–" : "•", 13, Ui.TEXT, false);
                row.addView(dot, new LinearLayout.LayoutParams(dp(14),
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                row.addView(para(b.text, 13, Ui.TEXT), wrapWeight());
                col.addView(row, Ui.full(dp(1), dp(1)));
                break;
            }
            case "num": {
                LinearLayout row = Ui.horizontal(c);
                TextView n = Ui.text(c, b.number, 13, Ui.AMBER, true);
                row.addView(n, new LinearLayout.LayoutParams(dp(30),
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                row.addView(para(b.text, 13, Ui.TEXT), wrapWeight());
                col.addView(row, Ui.full(dp(6), dp(2)));
                break;
            }
            case "warn": {
                TextView t = para(b.text, 13, Ui.TEXT);
                t.setBackground(Ui.box(c, 0xFF3E1414, 0xFFD32F2F, 2, false));
                t.setPadding(dp(8), dp(6), dp(8), dp(6));
                col.addView(t, Ui.full(dp(6), dp(6)));
                break;
            }
            case "note": {
                TextView t = para(b.text, 12, Ui.DIM);
                col.addView(t, Ui.full(dp(4), dp(4)));
                break;
            }
            case "box":
                if (!b.label.isEmpty())
                    col.addView(connector(b.label + " ▼"));
                col.addView(box(b.color, b.text), Ui.full(dp(2), dp(2)));
                actionButton(col, b.action);
                break;
            case "q":
                question(col, b);
                break;
            case "table":
                table(col, b);
                break;
            case "phone":
                phone(col, b);
                break;
            case "figure":
                if ("paz".equals(b.kind))
                    col.addView(new PazFigure(c, b), Ui.full(dp(8), dp(8)));
                break;
            case "link":
                if ("MATERIAL_TABLES".equals(b.action)) {
                    TextView t = Ui.text(c, "In HAZ-TAK: a material's Table 1 and "
                            + "Table 2 rows are shown with its guide (View guide).",
                            12, Ui.BLUE, false);
                    col.addView(t, Ui.full(dp(4), dp(4)));
                } else
                    actionButton(col, b.action);
                break;
            default:
                col.addView(para(b.text, 13, Ui.TEXT));
        }
    }

    // ------------------------------------------------------------------
    // Flowchart

    private TextView box(String color, String html) {
        int fill, stroke;
        boolean dashed = false;
        switch (color) {
            case "orange":
                fill = ERG_ORANGE;
                stroke = 0xFF8A4B00;
                break;
            case "yellow":
                fill = ERG_YELLOW;
                stroke = 0xFF8A8200;
                break;
            case "blue":
                fill = ERG_BLUE;
                stroke = 0xFF1F4F8A;
                break;
            case "green":
                fill = ERG_GREEN;
                stroke = 0xFF3D6B10;
                break;
            case "dotted":
                fill = 0xFFE6E6E6;
                stroke = 0xFF555555;
                dashed = true;
                break;
            case "dottedred":
                fill = Color.WHITE;
                stroke = 0xFFD32F2F;
                dashed = true;
                break;
            case "dottedgreen":
                fill = ERG_GREEN;
                stroke = 0xFF1B3A05;
                dashed = true;
                break;
            default: // white
                fill = Color.WHITE;
                stroke = 0xFF333333;
        }
        TextView t = Ui.text(c, Ui.html(html), 13, INK, false);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.box(c, fill, stroke, dashed ? 2 : 1, dashed));
        t.setPadding(dp(8), dp(6), dp(8), dp(6));
        return t;
    }

    private TextView connector(String s) {
        TextView t = Ui.text(c, s, 12, Ui.DIM, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private void question(LinearLayout col, Erg.RefBlock b) {
        TextView q = Ui.text(c, b.question, 14, b.dark ? Color.WHITE : INK, true);
        q.setGravity(Gravity.CENTER);
        q.setBackground(Ui.box(c, b.dark ? 0xFF111111 : Color.WHITE,
                b.dark ? Color.WHITE : 0xFF333333, 1, false));
        q.setPadding(dp(8), dp(8), dp(8), dp(8));
        col.addView(q, Ui.full(dp(10), dp(4)));

        if (!b.yes.isEmpty()) {
            LinearLayout row = Ui.horizontal(c);
            TextView yes = Ui.text(c, "YES ▶", 12, Ui.GREEN, true);
            yes.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(yes, new LinearLayout.LayoutParams(dp(52),
                    LinearLayout.LayoutParams.MATCH_PARENT));
            LinearLayout boxes = Ui.vertical(c);
            for (Erg.RefBox x : b.yes)
                boxes.addView(box(x.color, x.text), Ui.full(dp(1), dp(1)));
            row.addView(boxes, Ui.weight(1));
            col.addView(row, Ui.full(dp(2), dp(2)));
            actionButton(col, b.action);
        }
        if (b.no != null) {
            LinearLayout row = Ui.horizontal(c);
            TextView no = Ui.text(c, "NO ▶", 12, Ui.RED, true);
            no.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(no, new LinearLayout.LayoutParams(dp(52),
                    LinearLayout.LayoutParams.MATCH_PARENT));
            LinearLayout boxes = Ui.vertical(c);
            for (Erg.RefBox x = b.no; x != null; x = x.then)
                boxes.addView(box(x.color, x.text), Ui.full(dp(1), dp(1)));
            row.addView(boxes, Ui.weight(1));
            col.addView(row, Ui.full(dp(2), dp(2)));
        } else {
            col.addView(connector("NO ▼"));
        }
    }

    /** HAZ-TAK's own button under a flowchart step (plugin wording). */
    private void actionButton(LinearLayout col, String action) {
        if (action == null || action.isEmpty())
            return;
        String label;
        switch (action) {
            case "EXPLOSIVES":
                label = "In HAZ-TAK: pick the explosives placard ▸";
                break;
            case "ID":
                label = "In HAZ-TAK: enter the ID number ▸";
                break;
            case "NAME":
                label = "In HAZ-TAK: search by name ▸";
                break;
            case "PLACARD":
                label = "In HAZ-TAK: pick the placard ▸";
                break;
            case "UNKNOWN":
                label = "In HAZ-TAK: Unknown (Guide 111) ▸";
                break;
            case "TABLE3":
                label = "Open Table 3 (six common TIH gases) ▸";
                break;
            case "RAIL": {
                TextView t = Ui.text(c, "The railcar and road trailer charts "
                        + "(pp. 10-15) are pictures and are not in HAZ-TAK: use "
                        + "the printed guidebook, then come back with the ID "
                        + "number or name.", 12, Ui.DIM, false);
                t.setPadding(dp(52), dp(2), 0, dp(2));
                col.addView(t);
                return;
            }
            default:
                return;
        }
        final String a = action;
        Button btn = new Button(c);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(Ui.box(c, 0xFF1565C0, 0xFF000000, 1, false));
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actions.run(a);
            }
        });
        LinearLayout.LayoutParams lp = Ui.full(dp(3), dp(3));
        lp.setMargins(action.equals("TABLE3") ? 0 : dp(52), dp(3), 0, dp(3));
        col.addView(btn, lp);
    }

    // ------------------------------------------------------------------
    // Tables and telephone numbers

    private void table(LinearLayout col, Erg.RefBlock b) {
        List<List<CharSequence>> rows = new ArrayList<>();
        for (List<String> r : b.rows) {
            List<CharSequence> cells = new ArrayList<>();
            for (int i = 0; i < r.size(); i++)
                cells.add(i == b.phoneCol ? Ui.dialable(activity, r.get(i))
                        : Ui.html(r.get(i)));
            rows.add(cells);
        }
        float[] w = b.phoneCol == 1 ? new float[] { 1f, 1.6f } : null;
        col.addView(Ui.table(c, b.cols, rows, w, 12), Ui.full(dp(6), dp(6)));
    }

    private void phone(LinearLayout col, Erg.RefBlock b) {
        LinearLayout card = Ui.vertical(c);
        card.setBackground(Ui.box(c, Ui.CARD, Ui.GRID, 1, false));
        card.setPadding(dp(8), dp(6), dp(8), dp(6));
        card.addView(Ui.text(c, Ui.html(b.name), 13, Ui.TEXT, true));
        for (String line : b.lines) {
            boolean number = Ui.hasPhone(line);
            TextView t = Ui.text(c, Ui.dialable(activity, line), number ? 15 : 12,
                    number ? Ui.TEXT : Ui.DIM, number);
            t.setMovementMethod(LinkMovementMethod.getInstance());
            t.setPadding(0, dp(number ? 3 : 0), 0, 0);
            card.addView(t);
        }
        col.addView(card, Ui.full(dp(4), dp(4)));
    }
}
