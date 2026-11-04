package com.atakmap.android.haztak.plugin.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code <__haztak .../>} CoT detail.
 *
 * <pre>
 * &lt;__haztak v="1" ed="ERG2024" id="1017" mat="Chlorine" spill="L" fire="0"
 *   tod="N" tods="sun" wf="270" cont="Rail tank car" ws="M" x2="0"
 *   iso_m="1000" paz_m="9600" by="CALLSIGN" t="..." st="ACTIVE" n="3"/&gt;
 * </pre>
 *
 * Guide-only incidents carry {@code g} (and {@code pl} for a placard) instead
 * of {@code id}/{@code mat}; "name not known" incidents carry {@code id}
 * without {@code mat}. Received data is untrusted: text is cleaned and
 * capped, numbers are range-checked, and the receiver recomputes the zones
 * from its own data (see {@link HazRecord#disagrees}). Unknown attributes are
 * kept and sent on unchanged.
 */
public final class HazCodec {

    public static final String DETAIL = "__haztak";

    static final String[] KNOWN = {
            "v", "ed", "id", "mat", "g", "pl", "spill", "fire", "tod", "tods",
            "wf", "wl", "wat", "cont", "ws", "x2", "iso_m", "paz_m", "dw_m", "ev_m",
            "lbl", "by", "t", "st", "cby", "ct", "n"
    };

    static final int MAX_TEXT = 64;
    static final int MAX_NAME = 240;
    /**
     * Largest distance accepted from a sender: 25 km, above the ERG's
     * largest (11.0+ km, 22.5 km doubled), so a typo can't draw 100 km.
     */
    static final double MAX_M = 25_000;
    static final int MAX_ATTRIBUTES = 64;
    static final int MAX_RINGS = 8;

    private HazCodec() {
    }

    public static Map<String, String> encode(HazRecord r) {
        Scenario s = r.scenario;
        Map<String, String> m = new LinkedHashMap<>();
        m.put("v", Integer.toString(HazRecord.VERSION));
        m.put("ed", r.edition);
        if (s.byGuideOnly()) {
            m.put("g", s.guide);
            put(m, "pl", s.placardKey);
            put(m, "mat", s.name);
        } else {
            m.put("id", s.id);
            put(m, "mat", s.name);
        }
        put(m, "lbl", r.label);
        if (s.spill != null)
            m.put("spill", s.spill == Scenario.Spill.SMALL ? "S" : "L");
        if (s.fire != null)
            m.put("fire", s.fire ? "1" : "0");
        if (s.tod != null) {
            m.put("tod", s.tod == Scenario.TimeOfDay.DAY ? "D" : "N");
            m.put("tods", s.todFromSun ? "sun" : "user");
        }
        if (s.hasWind())
            m.put("wf", num(s.windFromDeg));
        if (s.water != null)
            m.put("wat", waterCode(s.water));
        put(m, "cont", s.container);
        if (s.windSpeed != null)
            m.put("ws", windCode(s.windSpeed));
        m.put("x2", s.doubled ? "1" : "0");
        if (s.windLater)
            m.put("wl", "1");
        m.put("iso_m", num(r.isoM));
        if (!Double.isNaN(r.pazM))
            m.put("paz_m", num(r.pazM));
        if (!Double.isNaN(r.dwM))
            m.put("dw_m", num(r.dwM));
        if (!r.ringsM.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (double d : r.ringsM) {
                if (sb.length() > 0)
                    sb.append(',');
                sb.append(num(d));
            }
            m.put("ev_m", sb.toString());
        }
        put(m, "by", r.byCallsign);
        if (r.timeMillis > 0)
            m.put("t", Formats.iso(r.timeMillis));
        m.put("st", r.isCleared() ? "CLEARED" : "ACTIVE");
        if (r.isCleared()) {
            put(m, "cby", r.clearedBy);
            if (r.clearedMillis > 0)
                m.put("ct", Formats.iso(r.clearedMillis));
        }
        if (r.number > 0)
            m.put("n", Integer.toString(r.number));
        for (Map.Entry<String, String> e : r.extra.entrySet())
            if (!m.containsKey(e.getKey()))
                m.put(e.getKey(), e.getValue());
        return m;
    }

    private static void put(Map<String, String> m, String k, String v) {
        if (v != null && !v.isEmpty())
            m.put(k, v);
    }

    /** One decimal, no exponent: 1000.0 → "1000", 160.9344 → "160.9". */
    static String num(double d) {
        long tenths = Math.round(d * 10.0);
        if (tenths % 10 == 0)
            return Long.toString(tenths / 10);
        return String.format(Locale.US, "%.1f", tenths / 10.0);
    }

    /**
     * Decode; null if the data can't describe an incident. When {@code erg}
     * has the sender's edition the zones are recomputed and compared.
     */
    public static HazRecord decode(Map<String, String> a, Erg erg) {
        if (a == null)
            return null;
        String ed = text(a.get("ed"), MAX_TEXT);
        String id = text(a.get("id"), 8);
        String mat = text(a.get("mat"), MAX_NAME);
        String g = text(a.get("g"), 8);
        if (ed.isEmpty())
            return null;
        Scenario s = Scenario.empty();
        if (!id.isEmpty()) {
            if (!id.matches("\\d{4}"))
                return null;
            s = mat.isEmpty() ? s.idOnly(id) : new Scenario(id, mat, null, null,
                    null, null, null, false, Double.NaN, null, null, null, false);
        } else if (!g.isEmpty()) {
            if (!g.matches("1\\d\\d"))
                return null;
            String pl = text(a.get("pl"), MAX_TEXT);
            s = s.guideOnly(g, pl.isEmpty() ? null : pl);
            if (!mat.isEmpty()) // an index row without ID (explosives)
                s = new Scenario("", mat, g, s.placardKey, null, null, null,
                        false, Double.NaN, null, null, null, false);
        } else {
            return null;
        }
        String sp = a.get("spill");
        s = s.withSpill("S".equals(sp) ? Scenario.Spill.SMALL
                : "L".equals(sp) ? Scenario.Spill.LARGE : null);
        String f = a.get("fire");
        s = s.withFire("1".equals(f) ? Boolean.TRUE : "0".equals(f) ? Boolean.FALSE
                : null);
        String tod = a.get("tod");
        s = s.withTimeOfDay("D".equals(tod) ? Scenario.TimeOfDay.DAY
                : "N".equals(tod) ? Scenario.TimeOfDay.NIGHT : null,
                "sun".equals(a.get("tods")));
        double wf = parseDouble(a.get("wf"));
        s = s.withWindFrom(Double.isInfinite(wf) ? Double.NaN : wf);
        s = s.withWater(parseWater(a.get("wat")));
        String cont = text(a.get("cont"), MAX_TEXT);
        s = s.withContainer(cont.isEmpty() ? null : cont);
        s = s.withWindSpeed(parseWind(a.get("ws")));
        s = s.withDoubled("1".equals(a.get("x2")));
        s = s.withWindLater("1".equals(a.get("wl")));

        double iso = metres(a.get("iso_m"));
        double paz = metres(a.get("paz_m"));
        double dw = metres(a.get("dw_m"));
        List<Double> rings = new ArrayList<>();
        String ev = a.get("ev_m");
        if (ev != null)
            for (String p : ev.split(",")) {
                double d = metres(p);
                if (!Double.isNaN(d) && rings.size() < MAX_RINGS)
                    rings.add(d);
            }

        boolean known = erg != null && erg.edition.equals(ed);
        String label = text(a.get("lbl"), MAX_NAME);
        boolean mismatch = false;
        boolean newer = parseInt(a.get("v"), 1) > HazRecord.VERSION;
        if (known) {
            Zones z = Zones.compute(erg, s);
            if (z.usable()) {
                label = HazRecord.labelFor(erg, s);
                mismatch = newer || !HazRecord.Same.metres(iso, z.isolation.radiusM)
                        || !HazRecord.Same.metres(paz,
                                z.paz == null ? Double.NaN : z.paz.downwindM)
                        || !HazRecord.Same.metres(dw,
                                z.downwind == null ? Double.NaN : z.downwind.lengthM)
                        || !HazRecord.Same.list(rings, HazRecord.ringList(z));
                if (Double.isNaN(iso)) {
                    // nothing carried: use this build's distances
                    iso = z.isolation.radiusM;
                    paz = z.paz == null ? Double.NaN : z.paz.downwindM;
                    dw = z.downwind == null ? Double.NaN : z.downwind.lengthM;
                    rings = HazRecord.ringList(z);
                    mismatch = newer;
                }
                // Otherwise the sender's distances are kept as received, so
                // HazRecord.disagrees() keeps comparing them with this
                // build's after the record is stored and read back.
            } else if (Double.isNaN(iso) || iso <= 0) {
                return null; // nothing this device can draw
            } else {
                // e.g. a newer build's name or code this dataset lacks:
                // draw the sender's distances and flag it.
                mismatch = true;
            }
        } else if (Double.isNaN(iso) || iso <= 0) {
            return null;
        }

        Map<String, String> extra = new LinkedHashMap<>();
        outer: for (Map.Entry<String, String> e : a.entrySet()) {
            if (extra.size() >= MAX_ATTRIBUTES)
                break;
            for (String k : KNOWN)
                if (k.equals(e.getKey()))
                    continue outer;
            if (isName(e.getKey()) && e.getValue() != null
                    && e.getValue().length() <= 256)
                extra.put(e.getKey(), e.getValue());
        }
        int n = parseInt(a.get("n"), 0);
        return new HazRecord(parseInt(a.get("v"), 1), ed, s, label,
                text(a.get("by"), MAX_TEXT), Formats.parseIso(a.get("t")),
                HazRecord.Status.parse(a.get("st")), text(a.get("cby"), MAX_TEXT),
                Formats.parseIso(a.get("ct")), n, iso, paz, dw, rings, extra,
                mismatch);
    }

    private static double metres(String s) {
        double d = parseDouble(s);
        return d >= 1 && d <= MAX_M ? d : Double.NaN;
    }

    // Wire codes are explicit strings (never Enum.name()/valueOf), so code
    // shrinking can't change the format.

    static String waterCode(Scenario.Water w) {
        switch (w) {
            case LAND:
                return "LAND";
            case WATER:
                return "WATER";
            default:
                return "UNKNOWN";
        }
    }

    static Scenario.Water parseWater(String s) {
        if ("LAND".equals(s))
            return Scenario.Water.LAND;
        if ("WATER".equals(s))
            return Scenario.Water.WATER;
        if ("UNKNOWN".equals(s))
            return Scenario.Water.UNKNOWN;
        return null;
    }

    static String windCode(Scenario.Wind w) {
        switch (w) {
            case LOW:
                return "LOW";
            case MODERATE:
                return "MODERATE";
            case HIGH:
                return "HIGH";
            default:
                return "UNKNOWN";
        }
    }

    static Scenario.Wind parseWind(String s) {
        if ("LOW".equals(s))
            return Scenario.Wind.LOW;
        if ("MODERATE".equals(s))
            return Scenario.Wind.MODERATE;
        if ("HIGH".equals(s))
            return Scenario.Wind.HIGH;
        if ("UNKNOWN".equals(s))
            return Scenario.Wind.UNKNOWN;
        return null;
    }

    // ------------------------------------------------------------------
    // XML text form (marker metadata and ATAK's opaque details)

    public static String toXml(HazRecord r) {
        StringBuilder sb = new StringBuilder("<").append(DETAIL);
        for (Map.Entry<String, String> e : encode(r).entrySet())
            sb.append(' ').append(e.getKey()).append("=\"")
                    .append(Xml.escape(e.getValue())).append('"');
        return sb.append("/>").toString();
    }

    public static HazRecord fromXml(String xml, Erg erg) {
        return decode(Xml.attributes(xml, DETAIL), erg);
    }

    /** Trim, drop control characters, cap the length. */
    static String text(String s, int max) {
        if (s == null)
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < max; i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c != 0x7f)
                sb.append(c);
        }
        return sb.toString().trim();
    }

    private static boolean isName(String s) {
        if (s == null || s.isEmpty() || s.length() > 32)
            return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.'))
                return false;
        }
        return Character.isLetter(s.charAt(0)) || s.charAt(0) == '_';
    }

    static int parseInt(String s, int def) {
        if (s == null)
            return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static double parseDouble(String s) {
        if (s == null)
            return Double.NaN;
        try {
            double d = Double.parseDouble(s.trim());
            return Double.isInfinite(d) ? Double.NaN : d;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
