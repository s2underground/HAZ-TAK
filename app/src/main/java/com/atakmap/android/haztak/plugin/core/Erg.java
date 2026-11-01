package com.atakmap.android.haztak.plugin.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Emergency Response Guidebook data, as extracted from the published PDF
 * by tools/erg (see tools/erg/REPORT.md). Read-only after loading.
 *
 * Every page number here is the printed page number of the guidebook.
 */
public final class Erg {

    /** Highest dataset format this build reads. */
    public static final int FORMAT = 1;

    public final String edition;
    public final String sourceTitle;
    public final String sourceSha256;
    public final List<Entry> index;
    public final Map<String, Guide> guides;
    public final List<Block> generalFirstAid;
    public final int generalFirstAidPage;
    public final List<T1Row> table1;
    public final List<T2Row> table2;
    public final List<T3Gas> table3;
    public final List<Placard> placards;
    public final List<String> placardIntro;
    public final Map<String, Rule> rules;
    public final Map<String, List<RefPage>> reference;

    private final Map<String, List<Entry>> byId = new LinkedHashMap<>();
    private final Map<String, List<T1Row>> t1ById = new LinkedHashMap<>();
    private final Map<String, T3Gas> t3ById = new LinkedHashMap<>();
    private final Map<String, Placard> placardByKey = new LinkedHashMap<>();

    // ------------------------------------------------------------------
    // Model

    /** One line of the yellow/blue index. */
    public static final class Entry {
        public final String id;      // "" for the explosives rows shown as "— —"
        public final String guide;   // as printed, e.g. "116P"
        public final String name;
        public final Set<String> flags;
        public final int yellowPage;
        public final int bluePage;

        Entry(String id, String guide, String name, Set<String> flags,
                int yellowPage, int bluePage) {
            this.id = id;
            this.guide = guide;
            this.name = name;
            this.flags = Collections.unmodifiableSet(flags);
            this.yellowPage = yellowPage;
            this.bluePage = bluePage;
        }

        /** Guide number without the polymerization "P". */
        public String guideNumber() {
            return guide.endsWith("P") ? guide.substring(0, guide.length() - 1)
                    : guide;
        }

        /** Highlighted in green in the yellow and blue sections. */
        public boolean highlighted() {
            return flags.contains("G");
        }

        public boolean tih() {
            return flags.contains("TIH");
        }

        public boolean waterReactive() {
            return flags.contains("WR");
        }

        public boolean polymerization() {
            return flags.contains("P");
        }

        /** "UN1017 Chlorine", or the name alone for rows without an ID. */
        public String display() {
            return id.isEmpty() ? name : "UN" + id + " " + name;
        }

        @Override
        public String toString() {
            return id + " " + guide + " " + name;
        }
    }

    /** One block of guide text (HTML with only b, sub and sup tags). */
    public static final class Block {
        public final String type; // section, head, sub, bullet, bullet2, para, canada, titlenote, footnote
        public final String html;

        Block(String type, String html) {
            this.type = type;
            this.html = html;
        }
    }

    /** A distance sentence from a guide's EVACUATION section. */
    public static final class GuideDistance {
        public final String sub;     // "Immediate precautionary measure", "Large Spill", "Fire", ...
        public final int metres;     // as printed
        public final String other;   // the printed other unit: "330 feet", "1/2 mile"
        public final String dir;     // all, downwind, unspecified
        public final String action;  // isolate, evacuate, other
        public final String text;    // the whole sentence, verbatim

        GuideDistance(String sub, int metres, String other, String dir,
                String action, String text) {
            this.sub = sub == null ? "" : sub;
            this.metres = metres;
            this.other = other;
            this.dir = dir;
            this.action = action;
            this.text = text;
        }

        /** Radius to draw: the larger of the printed metres and the other unit. */
        public double radiusM() {
            return Math.max(metres, Units.otherToMetres(other));
        }

        /** "800 meters (1/2 mile)", as printed. */
        public String label() {
            return metres + " meters (" + other + ")";
        }

        public boolean allDirections() {
            return "all".equals(dir);
        }

        public boolean downwind() {
            return "downwind".equals(dir);
        }
    }

    public static final class Guide {
        public final String number;
        public final String title;
        public final int page;
        public final boolean blank;
        public final List<Block> blocks;
        public final List<GuideDistance> distances;

        Guide(String number, String title, int page, boolean blank,
                List<Block> blocks, List<GuideDistance> distances) {
            this.number = number;
            this.title = title;
            this.page = page;
            this.blank = blank;
            this.blocks = Collections.unmodifiableList(blocks);
            this.distances = Collections.unmodifiableList(distances);
        }

        /** Distance sentences under one EVACUATION sub-heading. */
        public List<GuideDistance> under(String sub) {
            List<GuideDistance> out = new ArrayList<>();
            for (GuideDistance d : distances)
                if (d.sub.equalsIgnoreCase(sub))
                    out.add(d);
            return out;
        }
    }

    /** A protective action distance pair as printed: "1.5 km (0.9 mi)". */
    public static final class Downwind {
        public final String km;
        public final String mi;
        public final boolean more; // printed with "+": can be larger

        Downwind(String km, String mi, boolean more) {
            this.km = km;
            this.mi = mi;
            this.more = more;
        }

        /** Distance to draw: the larger of the printed km and miles. */
        public double metres() {
            return Math.max(Units.parseDecimal(km) * 1000.0,
                    Units.parseDecimal(mi) * Units.M_PER_MI);
        }

        public String label() {
            String p = more ? "+" : "";
            return km + p + " km (" + mi + p + " mi)";
        }
    }

    /** An isolation distance as printed: "60 m (200 ft)". */
    public static final class Isolation {
        public final int m;
        public final int ft;

        Isolation(int m, int ft) {
            this.m = m;
            this.ft = ft;
        }

        public double radiusM() {
            return Math.max(m, ft * Units.M_PER_FT);
        }

        public String label() {
            return Formats.thousands(m) + " m (" + Formats.thousands(ft) + " ft)";
        }
    }

    /** Table 1 distances for one spill size. */
    public static final class T1Dist {
        public final Isolation isolate;
        public final Downwind day;
        public final Downwind night;

        T1Dist(Isolation isolate, Downwind day, Downwind night) {
            this.isolate = isolate;
            this.day = day;
            this.night = night;
        }
    }

    public enum Variant {
        NONE, LAND, WATER
    }

    public static final class T1Row {
        public final String id;
        public final String guide;
        public final String name;     // as printed, including "(when spilled ...)"
        public final String baseName; // without the land/water note
        public final Variant variant;
        public final int page;
        public final T1Dist small;
        public final T1Dist large;    // null when largeTable3
        public final boolean largeTable3;

        T1Row(String id, String guide, String name, int page, T1Dist small,
                T1Dist large, boolean largeTable3) {
            this.id = id;
            this.guide = guide;
            this.name = name;
            this.page = page;
            this.small = small;
            this.large = large;
            this.largeTable3 = largeTable3;
            if (name.endsWith("(when spilled in water)")) {
                variant = Variant.WATER;
                baseName = name.substring(0, name.length()
                        - "(when spilled in water)".length()).trim();
            } else if (name.endsWith("(when spilled on land)")) {
                variant = Variant.LAND;
                baseName = name.substring(0, name.length()
                        - "(when spilled on land)".length()).trim();
            } else {
                variant = Variant.NONE;
                baseName = name;
            }
        }
    }

    public static final class T2Row {
        public final String id;
        public final String guide;
        public final String name;
        public final List<String> gases;
        public final int page;

        T2Row(String id, String guide, String name, List<String> gases,
                int page) {
            this.id = id;
            this.guide = guide;
            this.name = name;
            this.gases = Collections.unmodifiableList(gases);
            this.page = page;
        }
    }

    public enum WindSpeed {
        LOW("Low wind (< 6 mph = < 10 km/h)"),
        MODERATE("Moderate wind (6-12 mph = 10 - 20 km/h)"),
        HIGH("High wind (> 12 mph = > 20 km/h)");

        public final String label;

        WindSpeed(String label) {
            this.label = label;
        }
    }

    public static final class T3Row {
        public final String container;
        public final Isolation isolate;
        public final Map<WindSpeed, Downwind> day;
        public final Map<WindSpeed, Downwind> night;

        T3Row(String container, Isolation isolate, Map<WindSpeed, Downwind> day,
                Map<WindSpeed, Downwind> night) {
            this.container = container;
            this.isolate = isolate;
            this.day = Collections.unmodifiableMap(day);
            this.night = Collections.unmodifiableMap(night);
        }
    }

    public static final class T3Gas {
        /** {id, name} pairs this gas block covers ("UN1005 Ammonia, anhydrous / Anhydrous ammonia"). */
        public final List<String[]> materials;
        public final List<T3Row> rows;
        public final int page;

        T3Gas(List<String[]> materials, List<T3Row> rows, int page) {
            this.materials = Collections.unmodifiableList(materials);
            this.rows = Collections.unmodifiableList(rows);
            this.page = page;
        }

        public T3Row row(String container) {
            for (T3Row r : rows)
                if (r.container.equals(container))
                    return r;
            return null;
        }

        /** "UN1017 Chlorine". */
        public String title() {
            StringBuilder sb = new StringBuilder();
            for (String[] m : materials) {
                if (sb.length() > 0)
                    sb.append(" / ");
                sb.append("UN").append(m[0]).append(' ').append(m[1]);
            }
            return sb.toString();
        }
    }

    public static final class Placard {
        public final String key;
        public final String cls;
        public final String label;
        public final String guide;
        public final int page;

        Placard(String key, String cls, String label, String guide, int page) {
            this.key = key;
            this.cls = cls;
            this.label = label;
            this.guide = guide;
            this.page = page;
        }
    }

    /** A rule quoted verbatim from the guidebook. */
    public static final class Rule {
        public final String page; // printed page number, or "back cover"
        public final String text;

        Rule(String page, String text) {
            this.page = page;
            this.text = text;
        }

        /** "ERG2024 p. 288". */
        public String cite(String edition) {
            return edition + (page.matches("\\d+") ? " p. " + page : ", " + page);
        }
    }

    public static final class RefPage {
        public final int page;
        public final List<String> paras;

        RefPage(int page, List<String> paras) {
            this.page = page;
            this.paras = Collections.unmodifiableList(paras);
        }
    }

    // ------------------------------------------------------------------
    // Loading

    private Erg(JSONObject o) throws JSONException {
        int format = o.getInt("format");
        if (format > FORMAT)
            throw new JSONException("dataset format " + format
                    + " is newer than this build reads (" + FORMAT + ")");
        edition = o.getString("edition");
        JSONObject src = o.getJSONObject("source");
        sourceTitle = src.getString("title");
        sourceSha256 = src.getString("sha256");

        List<Entry> idx = new ArrayList<>();
        JSONArray a = o.getJSONArray("index");
        for (int i = 0; i < a.length(); i++) {
            JSONObject e = a.getJSONObject(i);
            Set<String> flags = new LinkedHashSet<>();
            JSONArray f = e.getJSONArray("flags");
            for (int k = 0; k < f.length(); k++)
                flags.add(f.getString(k));
            Entry en = new Entry(e.getString("id"), e.getString("guide"),
                    e.getString("name"), flags, e.optInt("yp", 0),
                    e.optInt("bp", 0));
            idx.add(en);
            put(byId, en.id, en);
        }
        index = Collections.unmodifiableList(idx);

        Map<String, Guide> gs = new LinkedHashMap<>();
        a = o.getJSONArray("guides");
        for (int i = 0; i < a.length(); i++) {
            JSONObject g = a.getJSONObject(i);
            List<GuideDistance> ds = new ArrayList<>();
            JSONArray d = g.optJSONArray("distances");
            if (d != null)
                for (int k = 0; k < d.length(); k++) {
                    JSONObject x = d.getJSONObject(k);
                    ds.add(new GuideDistance(x.isNull("sub") ? ""
                            : x.getString("sub"), x.getInt("m"),
                            x.getString("other"), x.getString("dir"),
                            x.getString("action"), x.getString("text")));
                }
            gs.put(g.getString("guide"), new Guide(g.getString("guide"),
                    g.getString("title"), g.getInt("page"),
                    g.optBoolean("blank", false), blocks(g.getJSONArray("blocks")),
                    ds));
        }
        guides = Collections.unmodifiableMap(gs);
        JSONObject fa = o.getJSONObject("generalFirstAid");
        generalFirstAid = Collections.unmodifiableList(blocks(fa.getJSONArray("blocks")));
        generalFirstAidPage = fa.getInt("page");

        List<T1Row> t1 = new ArrayList<>();
        a = o.getJSONArray("table1");
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.getJSONObject(i);
            Object large = r.opt("large");
            boolean t3 = "TABLE3".equals(large);
            T1Row row = new T1Row(r.getString("id"), r.getString("guide"),
                    r.getString("name"), r.getInt("page"),
                    t1dist(r.optJSONObject("small")),
                    t3 ? null : t1dist(r.optJSONObject("large")), t3);
            t1.add(row);
            put(t1ById, row.id, row);
        }
        table1 = Collections.unmodifiableList(t1);

        List<T2Row> t2 = new ArrayList<>();
        a = o.getJSONArray("table2");
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.getJSONObject(i);
            List<String> gases = new ArrayList<>();
            JSONArray g = r.getJSONArray("gases");
            for (int k = 0; k < g.length(); k++)
                gases.add(g.getString(k));
            t2.add(new T2Row(r.getString("id"), r.getString("guide"),
                    r.getString("name"), gases, r.getInt("page")));
        }
        table2 = Collections.unmodifiableList(t2);

        List<T3Gas> t3 = new ArrayList<>();
        a = o.getJSONArray("table3");
        for (int i = 0; i < a.length(); i++) {
            JSONObject g = a.getJSONObject(i);
            List<String[]> mats = new ArrayList<>();
            JSONArray m = g.getJSONArray("materials");
            for (int k = 0; k < m.length(); k++) {
                JSONObject x = m.getJSONObject(k);
                JSONArray names = x.getJSONArray("names");
                StringBuilder nm = new StringBuilder();
                for (int j = 0; j < names.length(); j++) {
                    if (j > 0)
                        nm.append(" / ");
                    nm.append(names.getString(j));
                }
                mats.add(new String[] { x.getString("id"), nm.toString() });
            }
            List<T3Row> rows = new ArrayList<>();
            JSONArray rs = g.getJSONArray("rows");
            for (int k = 0; k < rs.length(); k++) {
                JSONObject r = rs.getJSONObject(k);
                rows.add(new T3Row(r.getString("container"),
                        isolation(r.getJSONObject("isolate")),
                        winds(r.getJSONObject("day")),
                        winds(r.getJSONObject("night"))));
            }
            T3Gas gas = new T3Gas(mats, rows, g.getInt("page"));
            t3.add(gas);
            for (String[] mm : mats)
                t3ById.put(mm[0], gas);
        }
        table3 = Collections.unmodifiableList(t3);

        JSONObject pl = o.getJSONObject("placards");
        List<String> intro = new ArrayList<>();
        JSONArray in = pl.getJSONArray("intro");
        for (int i = 0; i < in.length(); i++)
            intro.add(in.getString(i));
        placardIntro = Collections.unmodifiableList(intro);
        List<Placard> ps = new ArrayList<>();
        a = pl.getJSONArray("placards");
        for (int i = 0; i < a.length(); i++) {
            JSONObject p = a.getJSONObject(i);
            Placard pc = new Placard(p.getString("key"), p.getString("cls"),
                    p.getString("label"), p.getString("guide"), p.getInt("page"));
            ps.add(pc);
            placardByKey.put(pc.key, pc);
        }
        placards = Collections.unmodifiableList(ps);

        Map<String, Rule> rl = new LinkedHashMap<>();
        JSONObject r = o.getJSONObject("rules");
        for (java.util.Iterator<String> it = r.keys(); it.hasNext();) {
            String k = it.next();
            JSONObject x = r.getJSONObject(k);
            rl.put(k, new Rule(String.valueOf(x.get("page")), x.getString("text")));
        }
        rules = Collections.unmodifiableMap(rl);

        Map<String, List<RefPage>> ref = new LinkedHashMap<>();
        JSONObject rf = o.getJSONObject("reference");
        for (java.util.Iterator<String> it = rf.keys(); it.hasNext();) {
            String k = it.next();
            JSONArray pages = rf.getJSONArray(k);
            List<RefPage> list = new ArrayList<>();
            for (int i = 0; i < pages.length(); i++) {
                JSONObject p = pages.getJSONObject(i);
                JSONArray paras = p.getJSONArray("paras");
                List<String> pp = new ArrayList<>();
                for (int j = 0; j < paras.length(); j++)
                    pp.add(paras.getString(j));
                list.add(new RefPage(p.getInt("page"), pp));
            }
            ref.put(k, Collections.unmodifiableList(list));
        }
        reference = Collections.unmodifiableMap(ref);
    }

    public static Erg parse(String json) throws JSONException {
        return new Erg(new JSONObject(json));
    }

    private static <T> void put(Map<String, List<T>> m, String k, T v) {
        List<T> l = m.get(k);
        if (l == null)
            m.put(k, l = new ArrayList<>());
        l.add(v);
    }

    private static List<Block> blocks(JSONArray a) throws JSONException {
        List<Block> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject b = a.getJSONObject(i);
            out.add(new Block(b.getString("t"), b.getString("x")));
        }
        return out;
    }

    private static Isolation isolation(JSONObject o) throws JSONException {
        return new Isolation(o.getInt("m"), o.getInt("ft"));
    }

    private static Downwind downwind(JSONObject o) throws JSONException {
        return new Downwind(o.getString("km"), o.getString("mi"),
                o.getBoolean("more"));
    }

    private static T1Dist t1dist(JSONObject o) throws JSONException {
        if (o == null)
            return null;
        return new T1Dist(isolation(o.getJSONObject("isolate")),
                downwind(o.getJSONObject("day")), downwind(o.getJSONObject("night")));
    }

    private static Map<WindSpeed, Downwind> winds(JSONObject o) throws JSONException {
        Map<WindSpeed, Downwind> m = new LinkedHashMap<>();
        m.put(WindSpeed.LOW, downwind(o.getJSONObject("low")));
        m.put(WindSpeed.MODERATE, downwind(o.getJSONObject("moderate")));
        m.put(WindSpeed.HIGH, downwind(o.getJSONObject("high")));
        return m;
    }

    // ------------------------------------------------------------------
    // Lookups

    /** Index entries with this ID number (empty list if none). */
    public List<Entry> byId(String id) {
        List<Entry> l = byId.get(id);
        return l == null ? Collections.<Entry> emptyList()
                : Collections.unmodifiableList(l);
    }

    /** The index entry with exactly this ID and name, or null. */
    public Entry entry(String id, String name) {
        for (Entry e : byId(id))
            if (e.name.equals(name))
                return e;
        return null;
    }

    public Guide guide(String number) {
        if (number == null)
            return null;
        String n = number.endsWith("P") ? number.substring(0, number.length() - 1)
                : number;
        return guides.get(n);
    }

    /** Table 1 rows for this index entry (several for land/water pairs). */
    public List<T1Row> table1For(Entry e) {
        List<T1Row> out = new ArrayList<>();
        List<T1Row> l = t1ById.get(e.id);
        if (l != null)
            for (T1Row r : l)
                if (r.baseName.equals(e.name))
                    out.add(r);
        return out;
    }

    public T3Gas table3For(String id) {
        return t3ById.get(id);
    }

    public T2Row table2For(Entry e) {
        for (T2Row r : table2)
            if (r.id.equals(e.id) && r.name.equals(e.name))
                return r;
        return null;
    }

    public Placard placard(String key) {
        return placardByKey.get(key);
    }

    public Rule rule(String key) {
        return rules.get(key);
    }

    /** Distinct ID numbers, for camera validation later. */
    public Set<String> ids() {
        return Collections.unmodifiableSet(byId.keySet());
    }

    /** "ERG2024". */
    public String cite(int page) {
        return edition + " p. " + page;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%s: %d entries, %d guides", edition,
                index.size(), guides.size());
    }
}
