package com.atakmap.android.haztak.plugin.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.haztak.plugin.atak.Exporter;
import com.atakmap.android.haztak.plugin.atak.HazStore;
import com.atakmap.android.haztak.plugin.atak.Sharing;
import com.atakmap.android.haztak.plugin.core.Erg;
import com.atakmap.android.haztak.plugin.core.Formats;
import com.atakmap.android.haztak.plugin.core.Geo;
import com.atakmap.android.haztak.plugin.core.HazRecord;
import com.atakmap.android.haztak.plugin.core.Labels;
import com.atakmap.android.haztak.plugin.core.Names;
import com.atakmap.android.haztak.plugin.core.PositionCheck;
import com.atakmap.android.haztak.plugin.core.Scenario;
import com.atakmap.android.haztak.plugin.core.Search;
import com.atakmap.android.haztak.plugin.core.Sun;
import com.atakmap.android.haztak.plugin.core.Zones;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The HAZ-TAK pane, built in code. Main screen: incidents on the map, the
 * selected incident's card, and the new-incident steps (identify, situation,
 * place). Sub-screens: a guide, word for word, and the reference pages.
 * Main thread only. Every action re-checks that its incident is still on the
 * map (the Blast 1.0 rule), so a stale card can't resurrect a deleted one.
 */
public final class HazPane {

    private static final String TAG = "HazPane";
    private static final String PREF_AUTO_SEND = "haztak.autoSend";
    private static final long TICK_MS = 3000;

    private static final int BG = 0xFF1E1E1E;
    private static final int CARD = 0xFF2A2A2A;
    private static final int TEXT = 0xFFEEEEEE;
    private static final int DIM = 0xFFAAAAAA;
    private static final int RED = 0xFFFF5252;
    private static final int ORANGE = 0xFFFF9100;
    private static final int AMBER = 0xFFFFC107;
    private static final int YELLOW = 0xFFFFEB3B;
    private static final int GREEN = 0xFF69F0AE;
    private static final int GREENBG = 0xFF33691E;
    private static final int BLUE = 0xFF40C4FF;
    private static final int SEL = 0xFF455A64;
    /** Selected choice: strong fill, bright border, bold text. */
    private static final int PICK = 0xFF00796B;
    private static final int PICK_EDGE = 0xFF64FFDA;
    private static final int BTN = 0xFF3C3C3C;
    private static final int SEND = 0xFF1565C0;
    private static final int GUIDE_BAR = 0xFFE65100;

    private final Context plugin;
    private final MapView mapView;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScrollView root;
    private final LinearLayout screen;

    private HazStore store;
    private Sharing sharing;
    private Exporter exporter;
    private Erg erg;
    private String loadError;

    // main screen views
    private LinearLayout mainView;
    private TextView statusText;
    private LinearLayout incidentList;
    private TextView incidentHeader;
    private Button deleteClearedBtn;
    private LinearLayout incidentCard;
    private LinearLayout newSection;
    private final List<Button> modeButtons = new ArrayList<>();
    private LinearLayout identifyBody;
    private TextView identifiedText;
    private LinearLayout identifiedActions;
    private LinearLayout situation;
    private Button smallBtn, largeBtn, noFireBtn, fireBtn, dayBtn, nightBtn,
            landBtn, waterBtn, waterUnkBtn;
    private LinearLayout waterRow, todRow, windRow, t3Box, containerRow, speedRow;
    private TextView todNote, windNote, t3Title;
    private EditText windEdit;
    private final Map<Scenario.Wind, Button> speedButtons = new HashMap<>();
    private final List<Button> containerButtons = new ArrayList<>();
    /** ID / name result buttons: {Button, Erg.Entry} or {Button, null, id}. */
    private final List<Object[]> resultButtons = new ArrayList<>();
    private final List<Button> windButtons = new ArrayList<>();
    private String containerGas;
    private CheckBox doubleBox;
    private TextView preview;
    private Button targetBtn, pickBtn, selfBtn, centerBtn;
    private TextView debugLine;

    // state
    private String mode;
    private Scenario scenario = Scenario.empty();
    private Zones zones;
    private boolean updatingWind;
    private Marker placeTarget;
    private String selectedUid;
    private String cardSignature, listSignature;
    private TextView youText, teamText;
    private boolean picking;
    private MapEventDispatcher.MapEventDispatchListener pickListener;
    private boolean ticking;
    /** Non-null while a guide or reference page is shown. */
    private View subScreen;
    private int mainScrollY;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!ticking)
                return;
            refresh();
            main.postDelayed(this, TICK_MS);
        }
    };

    public HazPane(Context pluginContext, MapView mapView) {
        this.plugin = pluginContext;
        this.mapView = mapView;
        this.prefs = AtakPreferences.getInstance(mapView.getContext()).getSharedPrefs();
        this.root = new ScrollView(plugin);
        root.setBackgroundColor(BG);
        screen = vertical();
        root.addView(screen);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                startTicking();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                stopTicking();
                stopPick();
            }
        });
        showMain();
    }

    public View getView() {
        return root;
    }

    /** Called once the ERG data has loaded. */
    public void setData(HazStore store, Sharing sharing) {
        this.store = store;
        this.sharing = sharing;
        this.exporter = new Exporter(store);
        this.erg = store.erg();
        showMain();
        onShow();
    }

    public void setLoadError(String err) {
        loadError = err;
    }

    // ------------------------------------------------------------------
    // Entry points

    public void onShow() {
        cardSignature = null;
        listSignature = null;
        refresh();
    }

    public void openIncident(Marker m) {
        closeSub();
        placeTarget = null;
        selectedUid = m.getUID();
        updateSituation();
        onShow();
        scrollTop();
    }

    public void placeAt(Marker target) {
        closeSub();
        placeTarget = target;
        updateSituation();
        onShow();
        scrollTop();
    }

    /** Nothing pre-selected for the next incident. */
    public void resetNew() {
        scenario = Scenario.empty();
        mode = null;
        if (windEdit != null) {
            updatingWind = true;
            windEdit.setText("");
            updatingWind = false;
        }
        if (doubleBox != null)
            doubleBox.setChecked(false);
        if (identifyBody != null)
            buildIdentifyBody();
        updateSituation();
    }

    public void dispose() {
        stopTicking();
        stopPick();
        main.removeCallbacksAndMessages(null);
    }

    private void startTicking() {
        if (ticking)
            return;
        ticking = true;
        main.post(tick);
    }

    private void stopTicking() {
        ticking = false;
        main.removeCallbacks(tick);
    }

    private void scrollTop() {
        main.post(new Runnable() {
            @Override
            public void run() {
                root.smoothScrollTo(0, 0);
            }
        });
    }

    // ------------------------------------------------------------------
    // Screens

    private void showMain() {
        subScreen = null;
        screen.removeAllViews();
        mainView = buildMain();
        screen.addView(mainView);
        cardSignature = null;
        listSignature = null;
    }

    private void showSub(View v) {
        if (subScreen == null)
            mainScrollY = root.getScrollY();
        subScreen = v;
        screen.removeAllViews();
        screen.addView(v);
        scrollTop();
    }

    private void closeSub() {
        if (subScreen == null)
            return;
        subScreen = null;
        screen.removeAllViews();
        screen.addView(mainView);
        cardSignature = null;
        listSignature = null;
        refresh();
        main.post(new Runnable() {
            @Override
            public void run() {
                root.scrollTo(0, mainScrollY);
            }
        });
    }

    private LinearLayout buildMain() {
        LinearLayout col = vertical();
        int pad = dp(8);
        col.setPadding(pad, pad, pad, pad);
        col.addView(text("HAZ-TAK", 18, TEXT, true));
        statusText = text("", 12, DIM, false);
        col.addView(statusText);
        if (erg == null) {
            statusText.setText(loadError != null
                    ? "The ERG data could not be loaded: " + loadError
                    : "Loading the ERG2024 data…");
            statusText.setTextColor(loadError != null ? RED : DIM);
            return col;
        }
        statusText.setText("2024 Emergency Response Guidebook (ERG2024), "
                + "U.S. DOT PHMSA / Transport Canada / SCT / CIQUIME. "
                + "Initial response only.");

        LinearLayout listHead = horizontal();
        incidentHeader = header("INCIDENTS ON THE MAP");
        listHead.addView(incidentHeader, weight());
        deleteClearedBtn = button("Delete all cleared", BTN);
        deleteClearedBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDeleteCleared();
            }
        });
        deleteClearedBtn.setVisibility(View.GONE);
        listHead.addView(deleteClearedBtn);
        col.addView(listHead);
        incidentList = vertical();
        col.addView(incidentList);
        incidentCard = vertical();
        col.addView(incidentCard);

        newSection = vertical();
        col.addView(newSection);
        buildNewIncident(newSection);

        col.addView(header("REFERENCE (FROM ERG2024)"));
        String[] refs = { "phone_numbers", "how_to_use", "placard_intro",
                "how_to_use_table1", "green_intro", "protective_actions",
                "table1_background", "national_response_centers" };
        List<Button> refButtons = new ArrayList<>();
        for (final String key : refs) {
            final Erg.RefPage page = erg.reference.get(key);
            if (page == null)
                continue;
            Button b = button(page.title, key.equals("phone_numbers") ? 0xFF8E2424 : BTN);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showReference(page);
                }
            });
            refButtons.add(b);
        }
        Button t3 = button("Table 3: six common TIH gases, large spills", BTN);
        t3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTable3();
            }
        });
        refButtons.add(t3);
        for (int i = 0; i < refButtons.size(); i += 2) {
            LinearLayout line = horizontal();
            for (int j = i; j < Math.min(i + 2, refButtons.size()); j++)
                line.addView(refButtons.get(j), weight());
            col.addView(line);
        }
        Button fa = button("General First Aid", BTN);
        fa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFirstAid();
            }
        });
        col.addView(fa, full());

        if (com.atakmap.android.haztak.plugin.BuildConfig.DEBUG) {
            debugLine = text("", 10, DIM, false);
            col.addView(debugLine);
        }
        TextView foot = text("HAZ-TAK shows the ERG2024 guides and distances; it "
                + "does not replace training, shipping papers, the emergency "
                + "response numbers, or technical specialists. The guidebook is "
                + "for the initial phase of a transportation incident.", 11, DIM,
                false);
        foot.setPadding(0, dp(12), 0, dp(4));
        col.addView(foot);
        return col;
    }

    // ------------------------------------------------------------------
    // New incident: identify

    private void buildNewIncident(LinearLayout col) {
        col.addView(header("NEW INCIDENT: 1. IDENTIFY"));
        LinearLayout modes = horizontal();
        String[][] ms = { { "ID", "ID number" }, { "NAME", "Name" },
                { "PLACARD", "Placard" }, { "UNKNOWN", "Unknown" } };
        modeButtons.clear();
        for (final String[] m : ms) {
            Button b = button(m[1], BTN);
            b.setTag(m[0]);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setMode(m[0]);
                }
            });
            modeButtons.add(b);
            modes.addView(b, weight());
        }
        col.addView(modes);
        identifyBody = vertical();
        col.addView(identifyBody);
        identifiedText = text("", 14, TEXT, false);
        identifiedText.setPadding(0, dp(4), 0, dp(2));
        col.addView(identifiedText);
        identifiedActions = horizontal();
        Button guideBtn = button("View guide", BTN);
        guideBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (zones != null && !zones.guides.isEmpty())
                    showGuide(zones.guides.get(0), zones.candidates.size() == 1
                            ? zones.candidates.get(0) : null, zones.guides);
            }
        });
        Button clearBtn = button("Change", BTN);
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Scenario keep = scenario;
                scenario = Scenario.empty().withDoubled(keep.doubled);
                updateSituation();
            }
        });
        identifiedActions.addView(guideBtn, weight());
        identifiedActions.addView(clearBtn, weight());
        col.addView(identifiedActions);

        situation = vertical();
        col.addView(situation);
        buildSituation(situation);

        col.addView(header("NEW INCIDENT: 3. PLACE"));
        targetBtn = button("", SEND);
        targetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (placeTarget != null && placeTarget.getPoint() != null)
                    place(placeTarget.getPoint());
            }
        });
        col.addView(targetBtn, full());
        LinearLayout place = horizontal();
        pickBtn = button("Tap map", BTN);
        selfBtn = button("My position", BTN);
        centerBtn = button("Map center", BTN);
        pickBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (picking)
                    stopPick();
                else
                    startPick();
            }
        });
        selfBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GeoPoint gp = selfPoint();
                if (gp == null)
                    toast("No GPS position yet.");
                else
                    place(gp);
            }
        });
        centerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GeoPointMetaData c = mapView.getCenterPoint();
                if (c != null && c.get() != null && c.get().isValid())
                    place(c.get());
            }
        });
        place.addView(pickBtn, weight());
        place.addView(selfBtn, weight());
        place.addView(centerBtn, weight());
        col.addView(place);
        CheckBox auto = new CheckBox(plugin);
        auto.setText("Send new incidents to everyone right away");
        auto.setTextColor(TEXT);
        auto.setChecked(prefs.getBoolean(PREF_AUTO_SEND, false));
        auto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                prefs.edit().putBoolean(PREF_AUTO_SEND, on).apply();
            }
        });
        col.addView(auto);
        buildIdentifyBody();
        updateSituation();
    }

    private void setMode(String m) {
        mode = m;
        if ("UNKNOWN".equals(m)) {
            Erg.Placard p = erg.placard("unknown");
            scenario = scenario.guideOnly(p.guide, p.key);
            prefillTimeOfDay();
        }
        buildIdentifyBody();
        updateSituation();
    }

    private void buildIdentifyBody() {
        if (identifyBody == null)
            return;
        identifyBody.removeAllViews();
        for (Button b : modeButtons)
            select(b, b.getTag().equals(mode), BTN);
        if (erg == null || mode == null) {
            if (mode == null)
                identifyBody.addView(text("Use the ID number from the placard, "
                        + "orange panel or shipping paper if you can; otherwise "
                        + "the name, then the placard.", 12, DIM, false));
            return;
        }
        final LinearLayout results = vertical();
        switch (mode) {
            case "ID": {
                final EditText e = edit("4-digit ID number (UN/NA)",
                        InputType.TYPE_CLASS_NUMBER);
                e.setFilters(new android.text.InputFilter[] {
                        new android.text.InputFilter.LengthFilter(4) });
                e.addTextChangedListener(new Watcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        showIdResults(results, s.toString());
                    }
                });
                identifyBody.addView(e, full());
                identifyBody.addView(results);
                break;
            }
            case "NAME": {
                final EditText e = edit("Name of material (2+ letters)",
                        InputType.TYPE_CLASS_TEXT);
                e.addTextChangedListener(new Watcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        showNameResults(results, s.toString());
                    }
                });
                identifyBody.addView(e, full());
                identifyBody.addView(results);
                break;
            }
            case "PLACARD": {
                for (String line : erg.placardIntro.subList(0, 1))
                    identifyBody.addView(text(line, 12, AMBER, true));
                identifyBody.addView(text(erg.placardIntro.get(5), 12, DIM, false));
                List<Erg.Placard> ps = erg.placards;
                for (int i = 0; i < ps.size(); i += 3) {
                    LinearLayout line = horizontal();
                    for (int j = i; j < Math.min(i + 3, ps.size()); j++) {
                        final Erg.Placard p = ps.get(j);
                        Button b = button(p.label + "\nGuide " + p.guide, BTN);
                        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                        Drawable d = PlacardIcon.make(plugin.getResources(), p.key,
                                p.cls, dp(44));
                        b.setCompoundDrawablesRelative(null, d, null, null);
                        b.setTag(p.key);
                        select(b, p.key.equals(scenario.placardKey)
                                && scenario.byGuideOnly(), BTN);
                        b.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                scenario = scenario.guideOnly(p.guide, p.key);
                                prefillTimeOfDay();
                                buildIdentifyBody();
                                updateSituation();
                            }
                        });
                        line.addView(b, weight());
                    }
                    identifyBody.addView(line);
                }
                break;
            }
            case "UNKNOWN":
                identifyBody.addView(text(erg.placardIntro.get(4), 12, DIM, false));
                break;
            default:
                break;
        }
    }

    private void showIdResults(LinearLayout results, String typed) {
        results.removeAllViews();
        resultButtons.clear();
        final String id = Search.cleanId(typed);
        if (id == null)
            return;
        List<Erg.Entry> es = Search.byId(erg, id);
        if (es.isEmpty()) {
            results.addView(text("UN/NA " + id + " is not listed in ERG2024. Check "
                    + "the number; if you can't, use the name, the placard or "
                    + "Unknown (Guide 111).", 13, AMBER, false));
            return;
        }
        for (final Erg.Entry e : es)
            results.addView(entryButton(e), full());
        if (es.size() > 1) {
            Button any = button("Exact name not known: use all " + es.size()
                    + " entries (largest distances, ERG2024 p. 288)", BTN);
            resultButtons.add(new Object[] { any, null, id });
            any.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    scenario = scenario.idOnly(id);
                    prefillTimeOfDay();
                    updateSituation();
                }
            });
            results.addView(any, full());
        }
        restyleResults();
    }

    private void showNameResults(LinearLayout results, String typed) {
        results.removeAllViews();
        resultButtons.clear();
        List<Erg.Entry> es = Search.byName(erg, typed);
        if (typed.trim().length() >= 2 && es.isEmpty())
            results.addView(text("No match.", 13, DIM, false));
        int shown = 0;
        for (Erg.Entry e : es) {
            if (shown++ >= 30) {
                results.addView(text("More matches: type more of the name.", 12,
                        DIM, false));
                break;
            }
            results.addView(entryButton(e), full());
        }
        restyleResults();
    }

    /** Is this index entry the one chosen? */
    private boolean chosen(Erg.Entry e) {
        if (scenario.name == null || !e.name.equals(scenario.name)
                || !e.id.equals(scenario.id))
            return false;
        return !e.id.isEmpty() || e.guideNumber().equals(scenario.guide);
    }

    /** Show which result is selected (the user taps one of several). */
    private void restyleResults() {
        for (Object[] o : resultButtons) {
            Button b = (Button) o[0];
            if (o[1] instanceof Erg.Entry) {
                Erg.Entry e = (Erg.Entry) o[1];
                select(b, chosen(e), e.highlighted() ? GREENBG : BTN);
            } else {
                select(b, scenario.nameUnknown() && scenario.id.equals(o[2]), BTN);
            }
        }
    }

    private Button entryButton(final Erg.Entry e) {
        String flags = flagText(e);
        Button b = button((e.id.isEmpty() ? "" : e.id + "  ") + e.name
                + "\nGuide " + e.guide + (flags.isEmpty() ? "" : "  ·  " + flags),
                e.highlighted() ? GREENBG : BTN);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        resultButtons.add(new Object[] { b, e });
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scenario = scenario.material(e);
                prefillTimeOfDay();
                updateSituation();
            }
        });
        return b;
    }

    private static String flagText(Erg.Entry e) {
        StringBuilder sb = new StringBuilder();
        if (e.highlighted())
            sb.append("GREEN: Table 1");
        if (e.tih())
            sb.append(", TIH");
        if (e.waterReactive())
            sb.append(", water-reactive");
        if (e.polymerization())
            sb.append(", P (polymerization)");
        String s = sb.toString();
        return s.startsWith(", ") ? s.substring(2) : s;
    }

    /** Day or night from the sun at the user's position (or map centre). */
    private void prefillTimeOfDay() {
        if (scenario.tod != null && !scenario.todFromSun)
            return; // the user chose: keep it
        GeoPoint ref = referencePoint();
        if (ref == null)
            return;
        scenario = scenario.withTimeOfDay(Sun.timeOfDay(ref.getLatitude(),
                ref.getLongitude(), System.currentTimeMillis()), true);
    }

    // ------------------------------------------------------------------
    // New incident: situation

    /** One question of the situation step, in its own card. */
    private static final class Question {
        LinearLayout card;
        TextView status;
        int state = -1;
    }

    private static final int REQUIRED = 0, ANSWERED = 1, OPTIONAL = 2;

    private Question qSpill, qFire, qWater, qTod, qWind, qT3, qDouble;

    private Question question(LinearLayout parent, String title, String help) {
        Question q = new Question();
        q.card = vertical();
        q.card.setPadding(dp(8), dp(6), dp(8), dp(8));
        LinearLayout head = horizontal();
        TextView t = text(title, 14, TEXT, true);
        head.addView(t, weight());
        q.status = text("", 11, DIM, true);
        q.status.setPadding(dp(6), dp(2), 0, 0);
        head.addView(q.status);
        q.card.addView(head);
        if (help != null) {
            TextView h = text(help, 12, DIM, false);
            h.setPadding(0, dp(1), 0, dp(4));
            q.card.addView(h);
        }
        LinearLayout.LayoutParams lp = full();
        lp.setMargins(0, dp(8), 0, 0);
        parent.addView(q.card, lp);
        mark(q, REQUIRED);
        return q;
    }

    private void mark(Question q, int state) {
        if (q.state == state)
            return;
        q.state = state;
        int edge;
        switch (state) {
            case ANSWERED:
                q.status.setText("✓ ANSWERED");
                q.status.setTextColor(GREEN);
                edge = 0xFF2E7D32;
                break;
            case OPTIONAL:
                q.status.setText("OPTIONAL");
                q.status.setTextColor(DIM);
                edge = 0xFF555555;
                break;
            default:
                q.status.setText("● REQUIRED");
                q.status.setTextColor(AMBER);
                edge = AMBER;
        }
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(6));
        g.setStroke(dp(state == REQUIRED ? 2 : 1), edge);
        q.card.setBackground(g);
    }

    /** Required while the zones still need it; answered once set. */
    private void mark(Question q, Zones.Need need, boolean answered) {
        mark(q, zones.missing.contains(need) ? REQUIRED
                : answered ? ANSWERED : OPTIONAL);
    }

    private void buildSituation(LinearLayout col) {
        col.addView(header("NEW INCIDENT: 2. SITUATION (answer each question)"));

        qSpill = question(col, "How much was released?",
                "Small or large spill (ERG2024 p. 288).");
        LinearLayout spill = horizontal();
        smallBtn = button("Small spill\n208 L (55 US gal) or less", BTN);
        largeBtn = button("Large spill\nmore than 208 L (55 US gal)", BTN);
        smallBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scenario = scenario.withSpill(Scenario.Spill.SMALL);
                updateSituation();
            }
        });
        largeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scenario = scenario.withSpill(Scenario.Spill.LARGE);
                updateSituation();
            }
        });
        spill.addView(smallBtn, weight());
        spill.addView(largeBtn, weight());
        qSpill.card.addView(spill);

        qFire = question(col, "Is the material on fire?",
                "Fire changes which distances apply (ERG2024 flowchart, p. 1).");
        LinearLayout fire = horizontal();
        noFireBtn = button("No", BTN);
        fireBtn = button("Yes, on fire", BTN);
        noFireBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scenario = scenario.withFire(false);
                updateSituation();
            }
        });
        fireBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scenario = scenario.withFire(true);
                updateSituation();
            }
        });
        fire.addView(noFireBtn, weight());
        fire.addView(fireBtn, weight());
        qFire.card.addView(fire);

        qWater = question(col, "Where was it spilled?",
                "This material reacts with water: Table 1 has separate rows "
                        + "(ERG2024 p. 281).");
        waterRow = qWater.card;
        LinearLayout w = horizontal();
        landBtn = button("On land", BTN);
        waterBtn = button("In water", BTN);
        waterUnkBtn = button("Not sure /\nboth", BTN);
        for (final Object[] o : new Object[][] { { landBtn, Scenario.Water.LAND },
                { waterBtn, Scenario.Water.WATER },
                { waterUnkBtn, Scenario.Water.UNKNOWN } }) {
            ((Button) o[0]).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    scenario = scenario.withWater((Scenario.Water) o[1]);
                    updateSituation();
                }
            });
            w.addView((Button) o[0], weight());
        }
        waterRow.addView(w);

        qTod = question(col, "Day or night?", null);
        todRow = qTod.card;
        LinearLayout t = horizontal();
        dayBtn = button("Day", BTN);
        nightBtn = button("Night", BTN);
        dayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scenario = scenario.withTimeOfDay(Scenario.TimeOfDay.DAY, false);
                updateSituation();
            }
        });
        nightBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scenario = scenario.withTimeOfDay(Scenario.TimeOfDay.NIGHT, false);
                updateSituation();
            }
        });
        t.addView(dayBtn, weight());
        t.addView(nightBtn, weight());
        todRow.addView(t);
        todNote = text("", 11, DIM, false);
        todRow.addView(todNote);

        qWind = question(col, "Which way is the wind blowing FROM?", null);
        windRow = qWind.card;
        LinearLayout entry = horizontal();
        windEdit = edit("Degrees true, e.g. 270 = from the west",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        windEdit.setFilters(new android.text.InputFilter[] {
                new android.text.InputFilter.LengthFilter(5) });
        windEdit.addTextChangedListener(new Watcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (updatingWind)
                    return;
                double d = parseWind(s.toString());
                scenario = scenario.withWindFrom(d);
                updateSituation();
            }
        });
        entry.addView(windEdit, weight());
        Button clearWind = button("Clear", BTN);
        clearWind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setWindText(Double.NaN);
                scenario = scenario.withWindFrom(Double.NaN);
                updateSituation();
            }
        });
        entry.addView(clearWind);
        windRow.addView(entry, full());
        String[] names = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };
        LinearLayout c1 = horizontal(), c2 = horizontal();
        windButtons.clear();
        for (int i = 0; i < names.length; i++) {
            final int deg = i * 45;
            Button b = button(names[i], BTN);
            b.setMinWidth(0);
            b.setTag(deg);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setWindText(deg);
                    scenario = scenario.withWindFrom(deg);
                    updateSituation();
                }
            });
            windButtons.add(b);
            (i < 4 ? c1 : c2).addView(b, weight());
        }
        windRow.addView(c1);
        windRow.addView(c2);
        windNote = text("", 11, DIM, false);
        windRow.addView(windNote);

        qT3 = question(col, "Table 3: container and wind speed", null);
        t3Box = qT3.card;
        t3Title = text("", 12, DIM, false);
        t3Box.addView(t3Title);
        t3Box.addView(text("Transport container", 12, TEXT, true));
        containerRow = vertical();
        t3Box.addView(containerRow);
        TextView ws = text("Wind speed", 12, TEXT, true);
        ws.setPadding(0, dp(6), 0, 0);
        t3Box.addView(ws);
        speedRow = horizontal();
        for (final Scenario.Wind sp : Scenario.Wind.values()) {
            Button b = button(sp == Scenario.Wind.UNKNOWN ? "Not sure"
                    : sp == Scenario.Wind.LOW ? "Low\n< 6 mph"
                            : sp == Scenario.Wind.MODERATE ? "Moderate\n6-12 mph"
                                    : "High\n> 12 mph", BTN);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    scenario = scenario.withWindSpeed(sp);
                    updateSituation();
                }
            });
            speedButtons.put(sp, b);
            speedRow.addView(b, weight());
        }
        t3Box.addView(speedRow);

        qDouble = question(col, "Was the whole contents released at once?", null);
        doubleBox = new CheckBox(plugin);
        doubleBox.setText("Yes: terrorism, sabotage or catastrophic failure. "
                + "Double the distances (ERG2024 p. 289).");
        doubleBox.setTextColor(TEXT);
        doubleBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                scenario = scenario.withDoubled(on);
                updateSituation();
            }
        });
        qDouble.card.addView(doubleBox);

        TextView ph = header("WHAT WILL BE DRAWN");
        col.addView(ph);
        preview = text("", 13, TEXT, false);
        preview.setPadding(dp(8), dp(6), dp(8), dp(6));
        preview.setBackgroundColor(CARD);
        col.addView(preview, full());
    }

    private void setWindText(double deg) {
        updatingWind = true;
        windEdit.setText(Double.isNaN(deg) ? "" : Integer.toString((int) Math.round(deg)));
        updatingWind = false;
    }

    static double parseWind(String s) {
        try {
            double d = Double.parseDouble(s.trim());
            if (d < 0 || d > 360 || Double.isNaN(d))
                return Double.NaN;
            return d;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Recompute the zones and bring the situation and place views up to date. */
    private void updateSituation() {
        if (erg == null || situation == null)
            return;
        zones = Zones.compute(erg, scenario);
        restyleResults();
        boolean identified = scenario.identified() && !zones.missing.contains(
                Zones.Need.IDENTIFY);
        // identification summary
        if (!identified) {
            identifiedText.setText(scenario.identified()
                    ? "Not found in ERG2024." : "");
            identifiedActions.setVisibility(View.GONE);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(HazRecord.labelFor(erg, scenario)).append('\n')
                    .append(Labels.guideLine(zones));
            if (scenario.nameUnknown())
                sb.append("\nName not known: ").append(zones.candidates.size())
                        .append(" entries, largest distances used.");
            identifiedText.setText(sb.toString());
            identifiedActions.setVisibility(View.VISIBLE);
        }
        situation.setVisibility(identified ? View.VISIBLE : View.GONE);
        if (identified) {
            select(smallBtn, scenario.spill == Scenario.Spill.SMALL, BTN);
            select(largeBtn, scenario.spill == Scenario.Spill.LARGE, BTN);
            mark(qSpill, Zones.Need.SPILL, scenario.spill != null);
            select(noFireBtn, Boolean.FALSE.equals(scenario.fire), BTN);
            select(fireBtn, Boolean.TRUE.equals(scenario.fire), BTN);
            mark(qFire, Zones.Need.FIRE, scenario.fire != null);
            boolean waterRelevant = waterRelevant();
            waterRow.setVisibility(waterRelevant ? View.VISIBLE : View.GONE);
            select(landBtn, scenario.water == Scenario.Water.LAND, BTN);
            select(waterBtn, scenario.water == Scenario.Water.WATER, BTN);
            select(waterUnkBtn, scenario.water == Scenario.Water.UNKNOWN, BTN);
            mark(qWater, Zones.Need.WATER, scenario.water != null);
            boolean table = zones.tableApplies;
            todRow.setVisibility(table ? View.VISIBLE : View.GONE);
            select(dayBtn, scenario.tod == Scenario.TimeOfDay.DAY, BTN);
            select(nightBtn, scenario.tod == Scenario.TimeOfDay.NIGHT, BTN);
            mark(qTod, Zones.Need.TIME_OF_DAY, scenario.tod != null);
            todNote.setText(scenario.tod == null ? "Choose day or night."
                    : scenario.todFromSun ? "Filled in from the sun's position now "
                            + "(day = after sunrise and before sunset, ERG2024 "
                            + "p. 288). Tap to change."
                            : "Chosen by you.");
            // The wind can always be entered; it is required only where the
            // guidebook draws a downwind zone (Table 1).
            windRow.setVisibility(View.VISIBLE);
            mark(qWind, Zones.Need.WIND_DIRECTION, scenario.hasWind());
            for (Button b : windButtons)
                select(b, scenario.hasWind() && Math.abs(((scenario.windFromDeg
                        - (Integer) b.getTag()) % 360 + 540) % 360 - 180) < 0.5, BTN);
            windNote.setText(windNote());
            updateTable3();
            mark(qT3, zones.missing.contains(Zones.Need.CONTAINER)
                    || zones.missing.contains(Zones.Need.WIND_SPEED) ? REQUIRED
                            : ANSWERED);
            doubleBox.setChecked(scenario.doubled);
            mark(qDouble, scenario.doubled ? ANSWERED : OPTIONAL);
            preview.setText(previewText());
        }
        updatePlacement();
    }

    private String windNote() {
        boolean table = zones.tableApplies;
        StringBuilder sb = new StringBuilder();
        if (scenario.hasWind())
            sb.append("Wind from ").append(Formats.bearing(scenario.windFromDeg))
                    .append(": downwind is toward ")
                    .append(Formats.compass(scenario.windFromDeg + 180)).append(". ");
        if (table && (zones.paz != null
                || zones.missing.contains(Zones.Need.WIND_DIRECTION)))
            sb.append("Needed to draw the protective action zone (a square "
                    + "downwind of the spill, ERG2024 p. 289).");
        else if (table)
            sb.append("Table 1 material: the protective action zone is drawn "
                    + "downwind once the other questions are answered.");
        else if (zones.downwind != null)
            sb.append("Optional: draws the guide's large-spill downwind "
                    + "evacuation distance as a line.");
        else
            sb.append("Optional. For this material the guide gives distances "
                    + "in all directions (circles); the ERG draws a downwind "
                    + "zone only for green-highlighted materials. If you enter "
                    + "the wind, a blue arrow on the map shows which way is "
                    + "downwind.");
        return sb.toString();
    }

    private boolean waterRelevant() {
        for (Erg.Entry e : zones.candidates)
            if (e.highlighted())
                for (Erg.T1Row r : erg.table1For(e))
                    if (r.variant == Erg.Variant.WATER)
                        return true;
        return false;
    }

    /** Table 3 gas when it applies (large spill of one of the six gases). */
    private Erg.T3Gas table3Gas() {
        if (scenario.spill != Scenario.Spill.LARGE)
            return null;
        for (Erg.Entry e : zones.candidates)
            if (e.highlighted())
                for (Erg.T1Row r : erg.table1For(e))
                    if (r.largeTable3)
                        return erg.table3For(r.id);
        return null;
    }

    private void updateTable3() {
        Erg.T3Gas gas = table3Gas();
        t3Box.setVisibility(gas == null ? View.GONE : View.VISIBLE);
        if (gas == null)
            return;
        t3Title.setText("Table 3 (large spills, " + gas.title() + ", ERG2024 p. "
                + gas.page + "): choose the container and the wind speed.");
        if (!gas.title().equals(containerGas)) {
            containerGas = gas.title();
            containerRow.removeAllViews();
            containerButtons.clear();
            List<String> names = new ArrayList<>();
            for (Erg.T3Row r : gas.rows)
                names.add(r.container);
            names.add(Scenario.CONTAINER_UNKNOWN);
            for (int i = 0; i < names.size(); i += 2) {
                LinearLayout line = horizontal();
                for (int j = i; j < Math.min(i + 2, names.size()); j++) {
                    final String c = names.get(j);
                    Button b = button(c.equals(Scenario.CONTAINER_UNKNOWN)
                            ? "Not sure (largest)" : c, BTN);
                    b.setTag(c);
                    b.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            scenario = scenario.withContainer(c);
                            updateSituation();
                        }
                    });
                    containerButtons.add(b);
                    line.addView(b, weight());
                }
                containerRow.addView(line);
            }
        }
        for (Button b : containerButtons)
            select(b, b.getTag().equals(scenario.container), BTN);
        for (Map.Entry<Scenario.Wind, Button> e : speedButtons.entrySet())
            select(e.getValue(), e.getKey() == scenario.windSpeed, BTN);
    }

    private String previewText() {
        if (!zones.missing.isEmpty()) {
            StringBuilder sb = new StringBuilder("Still needed:");
            for (Zones.Need n : zones.missing)
                sb.append("\n• ").append(n.prompt);
            return sb.toString();
        }
        if (zones.isolation == null) {
            StringBuilder sb = new StringBuilder();
            for (String n : zones.notes)
                sb.append(n).append('\n');
            return sb.toString().trim();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("ISOLATE ").append(zones.isolation.label)
                .append(" in all directions.");
        if (zones.paz != null)
            sb.append("\nPROTECT persons downwind ").append(zones.paz.label)
                    .append(" (square downwind, ").append(scenario.tod
                            == Scenario.TimeOfDay.DAY ? "day" : "night").append(").");
        for (Zones.Circle c : zones.rings)
            sb.append("\n").append(c.title).append(": ").append(c.label).append('.');
        if (zones.downwind != null)
            sb.append("\nLarge spill, downwind evacuation: ").append(zones.downwind.label)
                    .append(scenario.hasWind() ? "." : " (enter the wind to draw it).");
        if (scenario.hasWind() && zones.paz == null && zones.downwind == null)
            sb.append("\nWind arrow (blue): from ").append(Formats.bearing(
                    scenario.windFromDeg)).append(", pointing downwind. It shows "
                    + "the wind you entered; it is not a distance.");
        sb.append("\nSource: ").append(zones.paz != null ? zones.paz.cite
                : zones.isolation.cite);
        for (String n : zones.notes)
            sb.append("\nNote: ").append(n);
        return sb.toString();
    }

    private void updatePlacement() {
        boolean ok = erg != null && zones != null && zones.usable();
        for (Button b : new Button[] { pickBtn, selfBtn, centerBtn }) {
            if (b != null) {
                b.setEnabled(ok);
                b.setAlpha(ok ? 1f : 0.4f);
            }
        }
        if (targetBtn != null) {
            if (placeTarget != null) {
                targetBtn.setVisibility(View.VISIBLE);
                targetBtn.setText("Place at " + ATAKUtilities.getDisplayName(placeTarget));
                targetBtn.setEnabled(ok);
                targetBtn.setAlpha(ok ? 1f : 0.4f);
            } else {
                targetBtn.setVisibility(View.GONE);
            }
        }
    }

    // ------------------------------------------------------------------
    // Placement

    private void place(GeoPoint at) {
        try {
            if (zones == null || !zones.usable()) {
                toast("Finish steps 1 and 2 first.");
                return;
            }
            Scenario s = scenario;
            if (s.todFromSun && s.tod != null) {
                // Re-check day/night at the incident itself.
                Scenario.TimeOfDay here = Sun.timeOfDay(at.getLatitude(),
                        at.getLongitude(), System.currentTimeMillis());
                if (here != s.tod) {
                    s = s.withTimeOfDay(here, true);
                    toast("It is " + (here == Scenario.TimeOfDay.DAY ? "day"
                            : "night") + " at the incident location: using "
                            + (here == Scenario.TimeOfDay.DAY ? "day" : "night")
                            + " distances.");
                }
            }
            HazRecord r = HazRecord.create(erg, s, callsign(), System.currentTimeMillis());
            Marker m = store.create(at, r);
            if (m == null) {
                toast("Could not place the incident.");
                return;
            }
            placeTarget = null;
            selectedUid = m.getUID();
            resetNew();
            if (prefs.getBoolean(PREF_AUTO_SEND, false)) {
                sharing.sendToAll(m);
                toast("Incident placed and sent to everyone.");
            } else {
                toast("Incident placed. Use SEND TO ALL to share it.");
            }
            onShow();
            scrollTop();
        } catch (RuntimeException e) {
            Log.e(TAG, "place failed", e);
            toast("Could not place the incident.");
        }
    }

    private void startPick() {
        if (zones == null || !zones.usable())
            return;
        final MapEventDispatcher d = mapView.getMapEventDispatcher();
        d.pushListeners();
        d.clearListeners(MapEvent.MAP_CLICK);
        d.clearListeners(MapEvent.ITEM_CLICK);
        d.clearListeners(MapEvent.MAP_LONG_PRESS);
        pickListener = new MapEventDispatcher.MapEventDispatchListener() {
            @Override
            public void onMapEvent(MapEvent event) {
                onPicked(event);
            }
        };
        d.addMapEventListener(MapEvent.MAP_CLICK, pickListener);
        d.addMapEventListener(MapEvent.ITEM_CLICK, pickListener);
        d.addMapEventListener(MapEvent.MAP_LONG_PRESS, pickListener);
        picking = true;
        pickBtn.setText("Cancel");
        TextContainer.getInstance().displayPrompt(
                "Tap the map or a marker at the spill or leak. Press Cancel to stop.");
    }

    /** MapView.inverseWithElevation: deprecated, not for removal (see Blast). */
    @SuppressWarnings("deprecation")
    private void onPicked(MapEvent event) {
        GeoPoint gp = null;
        MapItem item = event.getItem();
        if (MapEvent.ITEM_CLICK.equals(event.getType()) && item instanceof PointMapItem) {
            gp = ((PointMapItem) item).getPoint();
        } else {
            PointF p = event.getPointF();
            if (p != null) {
                GeoPointMetaData m = mapView.inverseWithElevation(p.x, p.y);
                if (m != null)
                    gp = m.get();
            }
        }
        stopPick();
        if (gp != null && gp.isValid())
            place(gp);
    }

    private void stopPick() {
        if (!picking)
            return;
        picking = false;
        MapEventDispatcher d = mapView.getMapEventDispatcher();
        if (pickListener != null) {
            d.removeMapEventListener(MapEvent.MAP_CLICK, pickListener);
            d.removeMapEventListener(MapEvent.ITEM_CLICK, pickListener);
            d.removeMapEventListener(MapEvent.MAP_LONG_PRESS, pickListener);
            pickListener = null;
        }
        d.popListeners();
        TextContainer.getInstance().closePrompt();
        if (pickBtn != null)
            pickBtn.setText("Tap map");
    }

    // ------------------------------------------------------------------
    // Refresh: incident list and card

    private void refresh() {
        if (subScreen != null || store == null)
            return;
        try {
            refreshCard();
            refreshList();
        } catch (RuntimeException e) {
            Log.e(TAG, "refresh failed", e);
        }
    }

    private Marker selectedIncident() {
        return selectedUid == null ? null : store.liveMarker(selectedUid);
    }

    private void refreshCard() {
        Marker m = selectedIncident();
        HazRecord r = m == null ? null : store.read(m);
        String sig = m == null ? "" : m.getUID() + "|"
                + m.getMetaString(HazStore.META, "") + "|"
                + ATAKUtilities.getDisplayName(m);
        if (!sig.equals(cardSignature)) {
            cardSignature = sig;
            buildCard(m, r);
        }
        if (m != null && r != null)
            updateYou(m, r);
    }

    private void buildCard(final Marker m, final HazRecord r) {
        incidentCard.removeAllViews();
        youText = null;
        teamText = null;
        if (m == null || r == null) {
            incidentCard.setVisibility(View.GONE);
            return;
        }
        final Zones z = r.zones(erg);
        incidentCard.setVisibility(View.VISIBLE);
        incidentCard.addView(header("SELECTED INCIDENT"));
        LinearLayout card = vertical();
        card.setBackgroundColor(CARD);
        int p = dp(8);
        card.setPadding(p, p, p, p);
        incidentCard.addView(card);
        card.addView(text(ATAKUtilities.getDisplayName(m), 17,
                r.isCleared() ? DIM : TEXT, true));
        card.addView(text(r.label + (z != null ? "\n" + Labels.guideLine(z) : "")
                + (r.isCleared() ? "\nCLEARED" : ""), 13,
                r.isCleared() ? DIM : AMBER, true));
        card.addView(text(Labels.situation(r.scenario), 12, TEXT, false));

        if (z != null) {
            zoneRow(card, r.isCleared() ? DIM : RED, "Isolate all directions",
                    z.isolation.label, z.isolation.cite);
            if (z.paz != null)
                zoneRow(card, r.isCleared() ? DIM : ORANGE,
                        "Protect persons downwind (square)", z.paz.label, z.paz.cite);
            for (Zones.Circle c : z.rings)
                zoneRow(card, r.isCleared() ? DIM : YELLOW, c.title, c.label, c.cite);
            if (z.downwind != null)
                zoneRow(card, r.isCleared() ? DIM : ORANGE,
                        "Large spill: downwind evacuation", z.downwind.label
                                + (r.scenario.hasWind() ? "" : " (not drawn: no wind)"),
                        z.downwind.cite);
            card.addView(text("Shapes are drawn at the larger of the printed metric "
                    + "and US values.", 11, DIM, false));
        } else {
            card.addView(text("This build doesn't have " + r.edition + ". Showing "
                    + "the distances the sender drew: isolate "
                    + Formats.measured(r.isoM) + (Double.isNaN(r.pazM) ? ""
                            : "; protect downwind " + Formats.measured(r.pazM))
                    + ".", 12, AMBER, false));
        }
        if (r.disagrees(erg))
            card.addView(text("The sender's distances differ from this build's "
                    + "ERG data. This build's distances are drawn. Check with the "
                    + "sender.", 12, RED, false));

        youText = text("", 14, TEXT, false);
        youText.setPadding(0, dp(6), 0, 0);
        card.addView(youText);
        teamText = text("", 12, DIM, false);
        card.addView(teamText);
        TextView prov = text(Labels.provenance(r), 11, DIM, false);
        prov.setPadding(0, dp(4), 0, dp(4));
        card.addView(prov);

        LinearLayout a1 = horizontal();
        Button sendAll = button("SEND TO ALL", SEND);
        sendAll.setTypeface(Typeface.DEFAULT_BOLD);
        sendAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Marker l = live(m);
                    if (l != null)
                        toast("Sent to everyone (" + sharing.sendToAll(l) + " items).");
                } catch (RuntimeException e) {
                    Log.e(TAG, "send failed", e);
                    toast("Send failed.");
                }
            }
        });
        Button sendTo = button("Send to…", BTN);
        sendTo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Marker l = live(m);
                if (l != null)
                    sharing.sendTo(l);
            }
        });
        Button fly = button("Fly to", BTN);
        fly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goTo(m);
            }
        });
        a1.addView(sendAll, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        a1.addView(sendTo, weight());
        a1.addView(fly, weight());
        card.addView(a1);

        LinearLayout a2 = horizontal();
        Button guide = button("View guide", BTN);
        guide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (z != null && !z.guides.isEmpty())
                    showGuide(z.guides.get(0), z.candidates.size() == 1
                            ? z.candidates.get(0) : null, z.guides);
            }
        });
        Button changed = button("Wind or time changed…", BTN);
        changed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptChange(m);
            }
        });
        a2.addView(guide, weight());
        a2.addView(changed, weight());
        card.addView(a2);

        LinearLayout a3 = horizontal();
        Button ren = button("Rename…", BTN);
        ren.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptRename(m);
            }
        });
        Button exp = button("Export…", BTN);
        exp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptExport(m);
            }
        });
        a3.addView(ren, weight());
        a3.addView(exp, weight());
        card.addView(a3);

        LinearLayout a4 = horizontal();
        Button clear = button(r.isCleared() ? "Reopen" : "Mark cleared", BTN);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmStatus(m);
            }
        });
        Button del = button("Delete…", BTN);
        del.setTextColor(RED);
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete(m);
            }
        });
        a4.addView(clear, weight());
        a4.addView(del, weight());
        card.addView(a4);

        if (z != null && !z.rules.isEmpty()) {
            card.addView(header("GUIDEBOOK RULES THAT APPLY"));
            for (String key : z.rules) {
                Erg.Rule rule = erg.rule(key);
                if (rule != null)
                    card.addView(text("“" + rule.text + "” (" + rule.cite(erg.edition)
                            + ")", 12, TEXT, false));
            }
        }
        if (z != null)
            for (String n : z.notes)
                card.addView(text("Note: " + n, 12, AMBER, false));
        card.addView(header("REMEMBER"));
        for (String c : CAVEATS)
            card.addView(text("• " + c, 12, DIM, false));
    }

    /** Short reminders (HAZ-TAK's wording; the guide itself is word for word). */
    static final String[] CAVEATS = {
            "Approach from upwind, uphill and/or upstream. Stay clear of spills, "
                    + "vapors, fumes and smoke.",
            "These are initial distances for the first phase of a transportation "
                    + "incident. Get the shipping papers and call the emergency "
                    + "response number on them (or see Reference).",
            "Placards can be missing or wrong, and a load can be mixed. Switch to "
                    + "the specific guide as soon as the ID number or name is known.",
            "Distances can be larger in valleys or between tall buildings, with "
                    + "inversions or snow cover, near sunset, above 30°C (86°F), or "
                    + "with more than one leaking container (ERG2024 pp. 280-281).",
    };

    private void zoneRow(LinearLayout card, int color, String title, String value,
            String cite) {
        LinearLayout line = horizontal();
        View sw = new View(plugin);
        sw.setBackgroundColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(12), dp(12));
        lp.setMargins(0, dp(4), dp(6), 0);
        line.addView(sw, lp);
        LinearLayout tx = vertical();
        tx.addView(text(title + ": " + value, 14, TEXT, true));
        tx.addView(text(cite, 10, DIM, false));
        line.addView(tx, weight());
        card.addView(line);
    }

    private void updateYou(Marker m, HazRecord r) {
        if (youText == null)
            return;
        Zones z = r.zones(erg);
        GeoPoint center = m.getPoint();
        GeoPoint sp = selfPoint();
        if (z == null || center == null) {
            youText.setText("");
            return;
        }
        double wind = r.scenario.windFromDeg;
        if (sp == null) {
            youText.setText("YOU: no GPS position yet.");
            youText.setTextColor(DIM);
        } else {
            Marker self = ATAKUtilities.findSelf(mapView);
            double ce = self != null && self.getPoint() != null ? self.getPoint().getCE()
                    : Double.NaN;
            boolean ceKnown = !Double.isNaN(ce) && ce > 0 && ce < 9999999;
            PositionCheck.Result res = PositionCheck.check(z, center.getLatitude(),
                    center.getLongitude(), wind, sp.getLatitude(), sp.getLongitude(),
                    ceKnown ? ce : Double.NaN);
            if (res == null) {
                youText.setText("YOU: position check unavailable.");
                youText.setTextColor(DIM);
            } else {
                String s = "YOU: " + res.headline();
                if (res.nearEdge)
                    s += "\nYou are near a zone edge (within the GPS error): "
                            + "treat it as inside.";
                if (r.isCleared())
                    s = "Incident marked cleared. " + s;
                youText.setText(s);
                youText.setTextColor(r.isCleared() ? DIM
                        : res.where == PositionCheck.Where.OUTSIDE ? GREEN
                                : res.where == PositionCheck.Where.ISOLATION ? RED
                                        : ORANGE);
            }
        }
        if (teamText != null) {
            List<double[]> pts = new ArrayList<>();
            try {
                Marker self = ATAKUtilities.findSelf(mapView);
                for (Contact c : Contacts.getInstance().getAllContacts()) {
                    if (!(c instanceof IndividualContact))
                        continue;
                    MapItem mi = ((IndividualContact) c).getMapItem();
                    if (!(mi instanceof PointMapItem) || mi == self)
                        continue;
                    GeoPoint gp = ((PointMapItem) mi).getPoint();
                    if (gp != null && gp.isValid())
                        pts.add(new double[] { gp.getLatitude(), gp.getLongitude() });
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "contact scan failed", e);
            }
            if (pts.isEmpty()) {
                teamText.setText("Team: no other contacts on the map.");
                teamText.setTextColor(DIM);
            } else {
                int[] n = PositionCheck.count(z, center.getLatitude(),
                        center.getLongitude(), wind, pts.toArray(new double[0][]));
                teamText.setText("Team on the map: " + n[0] + " in isolation, " + n[1]
                        + " in protective action zone, " + n[2]
                        + " in evacuation distance, " + n[3] + " outside.");
                teamText.setTextColor((n[0] + n[1] + n[2]) > 0 && !r.isCleared()
                        ? RED : DIM);
            }
        }
    }

    private void refreshList() {
        final GeoPoint ref = referencePoint();
        List<Marker> found = store.all();
        if (debugLine != null)
            debugLine.setText("Debug: incidents " + store.lastScan());
        final Map<String, HazRecord> recs = new HashMap<>();
        final Map<String, Double> dist = new HashMap<>();
        List<Marker> ms = new ArrayList<>();
        for (Marker m : found) {
            HazRecord r = store.read(m);
            if (r == null)
                continue;
            recs.put(m.getUID(), r);
            dist.put(m.getUID(), distance(ref, m.getPoint()));
            ms.add(m);
        }
        Collections.sort(ms, new Comparator<Marker>() {
            @Override
            public int compare(Marker a, Marker b) {
                boolean ca = recs.get(a.getUID()).isCleared();
                boolean cb = recs.get(b.getUID()).isCleared();
                if (ca != cb)
                    return ca ? 1 : -1;
                return Double.compare(dist.get(a.getUID()), dist.get(b.getUID()));
            }
        });
        int cleared = 0;
        StringBuilder sig = new StringBuilder();
        for (Marker m : ms) {
            boolean c = recs.get(m.getUID()).isCleared();
            if (c)
                cleared++;
            sig.append(m.getUID()).append(c).append(ATAKUtilities.getDisplayName(m))
                    .append(Math.round(dist.get(m.getUID()) / 10)).append(';');
        }
        sig.append(selectedUid);
        deleteClearedBtn.setVisibility(cleared > 0 ? View.VISIBLE : View.GONE);
        deleteClearedBtn.setText("Delete all cleared (" + cleared + ")");
        if (sig.toString().equals(listSignature))
            return;
        listSignature = sig.toString();
        incidentList.removeAllViews();
        incidentHeader.setText("INCIDENTS ON THE MAP: " + (ms.size() - cleared)
                + " active" + (cleared > 0 ? ", " + cleared + " cleared" : ""));
        if (ms.isEmpty()) {
            incidentList.addView(text("None. Identify a material below and place it.",
                    13, DIM, false));
            return;
        }
        for (final Marker m : ms) {
            HazRecord r = recs.get(m.getUID());
            boolean sel = m.getUID().equals(selectedUid);
            double d = dist.get(m.getUID());
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(sel ? SEL : CARD);
            int p = dp(6);
            row.setPadding(p, p, p, p);
            View sw = new View(plugin);
            sw.setBackgroundColor(r.isCleared() ? DIM : RED);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(6), dp(36));
            sl.setMargins(0, 0, dp(6), 0);
            row.addView(sw, sl);
            String where = Double.isNaN(d) ? "" : "\n" + Formats.measured(d);
            TextView t = text(ATAKUtilities.getDisplayName(m) + where, 14,
                    r.isCleared() ? DIM : TEXT, sel);
            row.addView(t, weight());
            Button go = button("Go to", BTN);
            go.setMinWidth(dp(56));
            go.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    goTo(m);
                }
            });
            row.addView(go);
            Button del = button("Delete", BTN);
            del.setMinWidth(dp(56));
            del.setTextColor(RED);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDelete(m);
                }
            });
            row.addView(del);
            View.OnClickListener select = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedUid = m.getUID();
                    listSignature = null;
                    cardSignature = null;
                    refresh();
                }
            };
            t.setOnClickListener(select);
            row.setOnClickListener(select);
            LinearLayout.LayoutParams rl = full();
            rl.setMargins(0, 0, 0, dp(3));
            incidentList.addView(row, rl);
        }
    }

    private void goTo(Marker card) {
        Marker m = live(card);
        if (m == null)
            return;
        selectedUid = m.getUID();
        listSignature = null;
        cardSignature = null;
        MapTouchController.goTo(store.frameItem(m), false);
        refresh();
    }

    // ------------------------------------------------------------------
    // Actions on an incident

    private Marker live(Marker m) {
        Marker l = m == null ? null : store.liveMarker(m.getUID());
        if (l == null) {
            toast("This incident is no longer on the map.");
            afterDelete();
        }
        return l;
    }

    private void promptChange(Marker card) {
        final Marker m = live(card);
        if (m == null)
            return;
        final HazRecord r = store.read(m);
        if (r == null || !r.editionKnown(erg)) {
            toast("This build can't recompute this incident.");
            return;
        }
        Context ctx = mapView.getContext();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        box.setPadding(p, p / 2, p, 0);
        final EditText wind = new EditText(ctx);
        wind.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        wind.setHint("Wind FROM, degrees (e.g. 270)");
        if (r.scenario.hasWind())
            wind.setText(Long.toString(Math.round(r.scenario.windFromDeg)));
        box.addView(wind);
        final CheckBox night = new CheckBox(ctx);
        night.setText("Night (unticked = day)");
        night.setChecked(r.scenario.tod == Scenario.TimeOfDay.NIGHT);
        box.addView(night);
        final TextView hint = new TextView(ctx);
        hint.setText("The zones are recalculated from the same material, spill "
                + "size and fire choice. Day/night: " + (r.scenario.todFromSun
                        ? "from the sun when placed." : "chosen by the user."));
        box.addView(hint);
        DialogInterface.OnClickListener apply = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                double w = parseWind(wind.getText().toString());
                Scenario s = r.scenario.withWindFrom(w);
                Scenario.TimeOfDay tod = night.isChecked() ? Scenario.TimeOfDay.NIGHT
                        : Scenario.TimeOfDay.DAY;
                if (s.tod != null && tod != s.tod)
                    s = s.withTimeOfDay(tod, false);
                applyChange(m, s, which == DialogInterface.BUTTON_POSITIVE);
            }
        };
        new AlertDialog.Builder(ctx).setTitle("Wind or time changed")
                .setView(box)
                .setPositiveButton("Update and send to all", apply)
                .setNeutralButton("This device only", apply)
                .setNegativeButton("Cancel", null).show();
    }

    private void applyChange(Marker card, Scenario s, boolean send) {
        try {
            Marker m = live(card);
            HazRecord r = m == null ? null : store.read(m);
            if (r == null)
                return;
            Zones z = Zones.compute(erg, s);
            if (!z.usable()) {
                toast("Not updated: " + (z.missing.isEmpty() ? "no zones"
                        : z.missing.get(0).prompt));
                return;
            }
            if (!store.write(m, r.updated(erg, s))) {
                live(null);
                return;
            }
            if (send)
                sharing.sendToAll(m);
            cardSignature = null;
            listSignature = null;
            refresh();
        } catch (RuntimeException e) {
            Log.e(TAG, "update failed", e);
            toast("Could not update the incident.");
        }
    }

    private void confirmStatus(Marker card) {
        final Marker m = live(card);
        if (m == null)
            return;
        HazRecord r = store.read(m);
        if (r == null)
            return;
        final boolean clearing = !r.isCleared();
        new AlertDialog.Builder(mapView.getContext())
                .setTitle(clearing ? "Mark incident cleared?" : "Reopen incident?")
                .setMessage(clearing
                        ? "Records your callsign and the time. The zones turn grey "
                                + "and stay on the map until deleted."
                        : "Sets the incident back to active.")
                .setPositiveButton(clearing ? "Clear and send to all"
                        : "Reopen and send to all", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                setStatus(m, clearing, true);
                            }
                        })
                .setNeutralButton("This device only", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        setStatus(m, clearing, false);
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void setStatus(Marker card, boolean clearing, boolean send) {
        try {
            Marker m = live(card);
            HazRecord r = m == null ? null : store.read(m);
            if (r == null)
                return;
            HazRecord next = clearing ? r.cleared(callsign(), System.currentTimeMillis())
                    : r.reopened();
            if (!store.write(m, next)) {
                live(null);
                return;
            }
            if (send)
                sharing.sendToAll(m);
            cardSignature = null;
            listSignature = null;
            refresh();
        } catch (RuntimeException e) {
            Log.e(TAG, "status change failed", e);
            toast("Could not update the incident.");
        }
    }

    private void confirmDelete(Marker card) {
        final Marker m = live(card);
        if (m == null)
            return;
        new AlertDialog.Builder(mapView.getContext())
                .setTitle("Delete " + ATAKUtilities.getDisplayName(m) + "?")
                .setMessage("Removes the marker and every zone of this incident."
                        + "\n\nDelete for everyone also sends a remove request to "
                        + "every connected TAK client. Clients that are offline keep "
                        + "their copy.")
                .setPositiveButton("Delete for everyone", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        try {
                            if (store.liveMarker(m.getUID()) != null)
                                sharing.deleteForEveryone(m);
                        } catch (RuntimeException e) {
                            Log.e(TAG, "delete failed", e);
                        }
                        afterDelete();
                    }
                })
                .setNeutralButton("This device only", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        store.delete(m);
                        afterDelete();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void confirmDeleteCleared() {
        final List<Marker> cleared = new ArrayList<>();
        for (Marker m : store.all()) {
            HazRecord r = store.read(m);
            if (r != null && r.isCleared())
                cleared.add(m);
        }
        if (cleared.isEmpty())
            return;
        StringBuilder names = new StringBuilder();
        for (Marker m : cleared)
            names.append("\n• ").append(ATAKUtilities.getDisplayName(m));
        new AlertDialog.Builder(mapView.getContext())
                .setTitle("Delete " + cleared.size() + " cleared incident"
                        + (cleared.size() == 1 ? "" : "s") + "?")
                .setMessage("Removes each marker and its zones:" + names
                        + "\n\nActive incidents are not touched.")
                .setPositiveButton("Delete for everyone", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        for (Marker m : cleared)
                            try {
                                if (store.liveMarker(m.getUID()) != null)
                                    sharing.deleteForEveryone(m);
                            } catch (RuntimeException e) {
                                Log.e(TAG, "delete failed", e);
                            }
                        afterDelete();
                    }
                })
                .setNeutralButton("This device only", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        for (Marker m : cleared)
                            store.delete(m);
                        afterDelete();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void promptRename(Marker card) {
        final Marker m = live(card);
        if (m == null)
            return;
        final EditText input = new EditText(mapView.getContext());
        input.setSingleLine(true);
        input.setText(Names.base(HazStore.nameOf(m)));
        input.setSelectAllOnFocus(true);
        input.setFilters(new android.text.InputFilter[] {
                new android.text.InputFilter.LengthFilter(Names.MAX_LENGTH) });
        DialogInterface.OnClickListener ok = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                doRename(m, input.getText().toString(),
                        which == DialogInterface.BUTTON_POSITIVE);
            }
        };
        new AlertDialog.Builder(mapView.getContext()).setTitle("Rename incident")
                .setMessage("The name is the marker's callsign, so every TAK client "
                        + "shows it. CLEARED is added automatically while the "
                        + "incident is cleared.")
                .setView(input)
                .setPositiveButton("Rename and send to all", ok)
                .setNeutralButton("This device only", ok)
                .setNegativeButton("Cancel", null).show();
    }

    private void doRename(Marker card, String typed, boolean send) {
        try {
            Marker m = live(card);
            if (m == null)
                return;
            if (Names.sanitize(typed) == null) {
                toast("Enter a name.");
                return;
            }
            if (!store.rename(m, typed)) {
                toast("Could not rename the incident.");
                return;
            }
            if (send)
                sharing.sendToAll(m);
            cardSignature = null;
            listSignature = null;
            refresh();
        } catch (RuntimeException e) {
            Log.e(TAG, "rename failed", e);
            toast("Could not rename the incident.");
        }
    }

    private void promptExport(Marker card) {
        final Marker m = live(card);
        if (m == null)
            return;
        final Exporter.Format[] formats = Exporter.Format.values();
        final boolean[] checked = new boolean[formats.length];
        String[] labels = new String[formats.length];
        for (int i = 0; i < formats.length; i++) {
            labels[i] = formats[i].label;
            checked[i] = true;
        }
        new AlertDialog.Builder(mapView.getContext())
                .setTitle("Export " + HazStore.nameOf(m))
                .setMultiChoiceItems(labels, checked,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int i, boolean on) {
                                checked[i] = on;
                            }
                        })
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        doExport(m, formats, checked);
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void doExport(Marker card, Exporter.Format[] formats, boolean[] checked) {
        Marker m = live(card);
        if (m == null)
            return;
        StringBuilder done = new StringBuilder(), failed = new StringBuilder();
        for (int i = 0; i < formats.length; i++) {
            if (!checked[i])
                continue;
            try {
                done.append("\n• ").append(exporter.export(m, formats[i]).getName());
            } catch (Exception e) {
                Log.e(TAG, "export failed: " + formats[i], e);
                failed.append("\n• ").append(formats[i].label);
            }
        }
        if (done.length() == 0 && failed.length() == 0)
            return;
        String msg = "";
        if (done.length() > 0)
            msg += "Saved in atak/export/HAZ-TAK:" + done + "\n\nImport a data "
                    + "package with ATAK's Import Manager or send it as a file; copy "
                    + "the KMZ to Google Earth and the GPX to a Garmin's GPX folder.";
        if (failed.length() > 0)
            msg += (msg.isEmpty() ? "" : "\n\n") + "Could not save:" + failed;
        new AlertDialog.Builder(mapView.getContext()).setTitle("Export")
                .setMessage(msg).setPositiveButton("OK", null).show();
    }

    private void afterDelete() {
        if (selectedIncident() == null)
            selectedUid = null;
        cardSignature = null;
        listSignature = null;
        refresh();
    }

    // ------------------------------------------------------------------
    // Guide and reference screens

    private LinearLayout subHeader(String title) {
        LinearLayout col = vertical();
        int pad = dp(8);
        col.setPadding(pad, pad, pad, pad);
        Button back = button("◀ Back", BTN);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSub();
            }
        });
        col.addView(back, full());
        TextView t = text(title, 16, TEXT, true);
        t.setPadding(0, dp(6), 0, dp(4));
        col.addView(t);
        return col;
    }

    private void showGuide(String number, Erg.Entry entry,
            final List<String> guides) {
        final Erg.Guide g = erg.guide(number);
        if (g == null)
            return;
        LinearLayout col = subHeader("GUIDE " + g.number + "  " + g.title);
        col.addView(text(erg.edition + " pp. " + g.page + "-" + (g.page + 1)
                + ". Text exactly as printed.", 11, DIM, false));
        if (guides != null && guides.size() > 1) {
            LinearLayout others = horizontal();
            for (final String other : guides) {
                Button b = button("Guide " + other, BTN);
                select(b, other.equals(g.number), BTN);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showGuide(other, null, guides);
                    }
                });
                others.addView(b, weight());
            }
            col.addView(others);
        }
        if (entry != null) {
            col.addView(text(entry.display() + (entry.flags.isEmpty() ? ""
                    : "\n" + flagText(entry)), 14, entry.highlighted() ? GREEN : AMBER,
                    true));
            if (entry.highlighted())
                addTables(col, entry);
        }
        for (Erg.Block b : g.blocks)
            col.addView(block(b));
        Button fa = button("General First Aid (p. " + erg.generalFirstAidPage + ")", BTN);
        fa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFirstAid();
            }
        });
        col.addView(fa, full());
        showSub(col);
    }

    /** The material's green-table rows, as printed, drawn as tables. */
    private void addTables(LinearLayout col, Erg.Entry e) {
        col.addView(header("TABLE 1: INITIAL ISOLATION AND PROTECTIVE ACTION DISTANCES"));
        for (Erg.T1Row r : erg.table1For(e)) {
            col.addView(text(r.name + "  (p. " + r.page + ")", 13, TEXT, true));
            List<List<CharSequence>> rows = new ArrayList<>();
            rows.add(t1Row("Small spill", r.small, false));
            rows.add(t1Row("Large spill", r.large, r.largeTable3));
            col.addView(Ui.table(plugin, java.util.Arrays.asList("",
                    "First ISOLATE in all directions", "Then PROTECT downwind: DAY",
                    "Then PROTECT downwind: NIGHT"), rows,
                    new float[] { 0.9f, 1.2f, 1.1f, 1.1f }, 12), lp(2, 8));
        }
        Erg.T2Row t2 = erg.table2For(e);
        if (t2 != null) {
            col.addView(header("TABLE 2: WATER-REACTIVE MATERIALS (p. " + t2.page + ")"));
            List<List<CharSequence>> rows = new ArrayList<>();
            rows.add(java.util.Arrays.<CharSequence>asList(t2.name,
                    android.text.TextUtils.join("\n", t2.gases)));
            col.addView(Ui.table(plugin, java.util.Arrays.asList("Material",
                    "TIH gas(es) produced"), rows, new float[] { 1.4f, 1f }, 12),
                    lp(2, 2));
            col.addView(text(erg.rule("table2_info_only").text, 12, DIM, false));
        }
        Erg.T3Gas t3 = erg.table3For(e.id);
        if (t3 != null) {
            col.addView(header("TABLE 3: LARGE SPILLS"));
            addTable3(col, t3);
        }
    }

    private List<CharSequence> t1Row(String spill, Erg.T1Dist d, boolean t3) {
        List<CharSequence> row = new ArrayList<>();
        row.add(spill);
        if (t3 || d == null) {
            row.add("Refer to Table 3");
            row.add("");
            row.add("");
        } else {
            row.add(d.isolate.label());
            row.add(d.day.label());
            row.add(d.night.label());
        }
        return row;
    }

    private LinearLayout.LayoutParams lp(int top, int bottom) {
        LinearLayout.LayoutParams p = full();
        p.setMargins(0, dp(top), 0, dp(bottom));
        return p;
    }

    private View block(Erg.Block b) {
        TextView t;
        switch (b.type) {
            case "section":
                t = text(b.html, 14, Color.WHITE, true);
                t.setBackgroundColor(GUIDE_BAR);
                t.setGravity(Gravity.CENTER);
                t.setPadding(dp(4), dp(3), dp(4), dp(3));
                LinearLayout.LayoutParams lp = full();
                lp.setMargins(0, dp(10), 0, dp(4));
                t.setLayoutParams(lp);
                return t;
            case "head":
                t = text(b.html, 14, ORANGE, true);
                t.setPadding(0, dp(6), 0, dp(2));
                return t;
            case "sub":
                t = new TextView(plugin);
                t.setText(html("<b>" + b.html + "</b>"));
                t.setTextColor(TEXT);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                t.setPadding(0, dp(3), 0, 0);
                return t;
            case "bullet":
            case "bullet2":
                t = new TextView(plugin);
                t.setText(html((b.type.equals("bullet") ? "• " : "– ") + b.html));
                t.setTextColor(TEXT);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                t.setPadding(dp(b.type.equals("bullet") ? 6 : 20), dp(1), 0, dp(1));
                return t;
            case "canada":
            case "footnote":
            case "titlenote":
                t = new TextView(plugin);
                t.setText(html(b.html));
                t.setTextColor(DIM);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                t.setPadding(0, dp(6), 0, 0);
                return t;
            default:
                t = new TextView(plugin);
                t.setText(html(b.html));
                t.setTextColor(TEXT);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                return t;
        }
    }

    @SuppressWarnings("deprecation")
    private static Spanned html(String s) {
        if (Build.VERSION.SDK_INT >= 24)
            return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY);
        return Html.fromHtml(s);
    }

    private void showFirstAid() {
        LinearLayout col = subHeader("GENERAL FIRST AID");
        col.addView(text(erg.edition + " p. " + erg.generalFirstAidPage
                + ". Text exactly as printed.", 11, DIM, false));
        for (Erg.Block b : erg.generalFirstAid)
            col.addView(block(b));
        showSub(col);
    }

    private void showReference(Erg.RefPage page) {
        LinearLayout col = subHeader(page.title);
        col.addView(text(erg.edition + " " + page.pagesLabel() + ". Words exactly "
                + "as printed; layout redrawn for the screen. Buttons marked "
                + "\"In HAZ-TAK\" are the plugin's.", 11, DIM, false));
        boolean phones = page.key.equals("phone_numbers")
                || page.key.equals("national_response_centers");
        if (phones)
            col.addView(text("Tap a number to open it in the phone app.", 12,
                    BLUE, false));
        new RefRenderer(plugin, mapView.getContext(), new RefRenderer.Actions() {
            @Override
            public void run(String action) {
                referenceAction(action);
            }
        }, phones).render(col, page);
        showSub(col);
    }

    /** A flowchart button: go to the matching way of identifying. */
    private void referenceAction(String action) {
        if ("TABLE3".equals(action)) {
            showTable3();
            return;
        }
        String m;
        switch (action) {
            case "ID":
                m = "ID";
                break;
            case "NAME":
                m = "NAME";
                break;
            case "PLACARD":
            case "EXPLOSIVES":
                m = "PLACARD";
                break;
            case "UNKNOWN":
                m = "UNKNOWN";
                break;
            default:
                return;
        }
        closeSub();
        setMode(m);
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (newSection != null)
                    root.smoothScrollTo(0, Math.max(0, newSection.getTop() - dp(8)));
            }
        }, 50);
    }

    /** Table 3 for all six gases, as grids. */
    private void showTable3() {
        LinearLayout col = subHeader("TABLE 3: LARGE SPILLS OF SIX COMMON TIH GASES");
        col.addView(text(erg.edition + " Table 3, pp. " + erg.table3.get(0).page + "-"
                + erg.table3.get(erg.table3.size() - 1).page + ". Values exactly as "
                + "printed. Large spill = more than 208 L (55 US gal).", 11, DIM,
                false));
        for (Erg.T3Gas g : erg.table3)
            addTable3(col, g);
        showSub(col);
    }

    private void addTable3(LinearLayout col, Erg.T3Gas g) {
        TextView h = text(g.title() + " (p. " + g.page + ")", 14, AMBER, true);
        h.setPadding(0, dp(12), 0, dp(4));
        col.addView(h);
        List<List<CharSequence>> rows = new ArrayList<>();
        for (Erg.T3Row r : g.rows) {
            List<CharSequence> row = new ArrayList<>();
            row.add(r.container);
            row.add(r.isolate.label());
            row.add(winds(r.day));
            row.add(winds(r.night));
            rows.add(row);
        }
        col.addView(Ui.table(plugin, java.util.Arrays.asList("Transport container",
                "Initial isolation", "Protect downwind: DAY", "Protect downwind: NIGHT"),
                rows, new float[] { 1.3f, 1f, 1.25f, 1.25f }, 11), full());
    }

    private static CharSequence winds(Map<Erg.WindSpeed, Erg.Downwind> m) {
        return "Low wind: " + m.get(Erg.WindSpeed.LOW).label()
                + "\nModerate: " + m.get(Erg.WindSpeed.MODERATE).label()
                + "\nHigh wind: " + m.get(Erg.WindSpeed.HIGH).label();
    }

    // ------------------------------------------------------------------
    // Helpers

    private GeoPoint referencePoint() {
        GeoPoint gp = selfPoint();
        if (gp != null)
            return gp;
        GeoPointMetaData c = mapView.getCenterPoint();
        return c != null ? c.get() : null;
    }

    private static double distance(GeoPoint a, GeoPoint b) {
        if (a == null || b == null || !a.isValid() || !b.isValid())
            return Double.NaN;
        return Geo.distance(a.getLatitude(), a.getLongitude(), b.getLatitude(),
                b.getLongitude());
    }

    private GeoPoint selfPoint() {
        try {
            Marker self = ATAKUtilities.findSelf(mapView);
            GeoPoint gp = self != null ? self.getPoint() : null;
            if (gp != null && gp.isValid()
                    && !(gp.getLatitude() == 0 && gp.getLongitude() == 0))
                return gp;
        } catch (RuntimeException e) {
            Log.w(TAG, "no self position", e);
        }
        return null;
    }

    private String callsign() {
        try {
            String cs = mapView.getDeviceCallsign();
            return cs == null ? "" : cs;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private void toast(String s) {
        Toast.makeText(mapView.getContext(), s, Toast.LENGTH_LONG).show();
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                plugin.getResources().getDisplayMetrics()));
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(plugin);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(plugin);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private static LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(plugin);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold)
            t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView header(String s) {
        TextView t = text(s, 12, BLUE, true);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private EditText edit(String hint, int type) {
        EditText e = new EditText(plugin);
        e.setInputType(type);
        e.setHint(hint);
        e.setTextColor(TEXT);
        e.setHintTextColor(DIM);
        e.setSingleLine(true);
        return e;
    }

    private Button button(String label, int color) {
        Button b = new Button(plugin);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setMinHeight(dp(44));
        tint(b, color);
        return b;
    }

    /**
     * Selected or not: a selected choice gets a strong teal fill, a bright
     * border and bold text so it is obvious which answer is in use.
     */
    private void select(Button b, boolean on, int base) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(on ? PICK : base);
        g.setCornerRadius(dp(4));
        g.setStroke(on ? dp(3) : dp(1), on ? PICK_EDGE : 0xFF000000);
        b.setBackground(g);
        b.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private void tint(Button b, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(4));
        g.setStroke(dp(1), 0xFF000000);
        b.setBackground(g);
    }

    private abstract static class Watcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {
        }

        @Override
        public void onTextChanged(CharSequence s, int a, int b, int c) {
        }
    }
}
