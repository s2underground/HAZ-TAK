package com.atakmap.android.haztak.plugin.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The zones for one incident, worked out from the guidebook exactly as its
 * instructions say (ERG2024 pp. 280-289 and the yellow-section
 * introduction, p. 28):
 *
 * <ul>
 * <li><b>Highlighted materials</b> (TIH or water-reactive): the initial
 * isolation distance (a circle around the spill) and the protective action
 * distance (a square downwind of the spill whose length and width equal the
 * downwind distance) from Table 1, or from Table 3 for large spills of the
 * six common TIH gases.</li>
 * <li><b>Other materials, or identification by placard</b>: the guide's
 * EVACUATION distances: the immediate precautionary isolation, plus the
 * large-spill evacuation when the spill is large.</li>
 * <li><b>Fire</b>: the guide's fire distance is used as the isolation
 * distance, and Table 1 still protects downwind for residual release.</li>
 * <li><b>Several possible entries</b> (name unknown, land or water unknown,
 * Table 3 container or wind unknown): the largest distance is used.</li>
 * <li><b>Catastrophic release</b> (user's choice): distances doubled.</li>
 * </ul>
 *
 * Every drawn distance is the larger of the printed metric and US values.
 * Nothing is interpolated or reduced.
 */
public final class Zones {

    /** Inputs still needed before zones can be drawn. */
    public enum Need {
        IDENTIFY("Identify the material (ID number, name or placard)."),
        SPILL("Choose the spill size."),
        FIRE("Choose whether a fire is involved."),
        TIME_OF_DAY("Choose day or night."),
        WIND_DIRECTION("Enter the direction the wind is blowing FROM."),
        WATER("Choose whether it is spilled on land or in water."),
        CONTAINER("Choose the container (Table 3)."),
        WIND_SPEED("Choose the wind speed (Table 3).");

        public final String prompt;

        Need(String prompt) {
            this.prompt = prompt;
        }
    }

    public enum Basis {
        TABLE1, TABLE3, GUIDE
    }

    /** A circle drawn around the spill. */
    public static final class Circle {
        public final String title;   // "Initial isolation zone"
        public final double radiusM; // drawn radius (x2 applied)
        public final String label;   // printed distance, e.g. "60 m (200 ft)"
        public final String cite;    // "ERG2024 Table 1, p. 290"
        public final String text;    // the guide sentence, when from a guide
        public final boolean atLeast;   // the guide says "at least"
        public final boolean evacuate;  // isolate AND evacuate (same distance)
        public final boolean responders; // evacuation includes emergency responders

        Circle(String title, double radiusM, String label, String cite,
                String text) {
            this(title, radiusM, label, cite, text, false, false, false);
        }

        Circle(String title, double radiusM, String label, String cite,
                String text, boolean atLeast, boolean evacuate, boolean responders) {
            this.title = title;
            this.radiusM = radiusM;
            this.label = label;
            this.cite = cite;
            this.text = text;
            this.atLeast = atLeast;
            this.evacuate = evacuate;
            this.responders = responders;
        }

        Circle alsoEvacuate(Erg.GuideDistance fe) {
            return new Circle(title, radiusM, label, cite, text, atLeast, true,
                    responders || fe.responders());
        }
    }

    /** The protective action zone: a square downwind of the spill. */
    public static final class Square {
        public final double downwindM; // drawn length and width (x2 applied)
        public final String label;     // "1.5 km (0.9 mi)"
        public final boolean more;     // printed with "+"
        public final String cite;

        Square(double downwindM, String label, boolean more, String cite) {
            this.downwindM = downwindM;
            this.label = label;
            this.more = more;
            this.cite = cite;
        }
    }

    /** A guide's downwind evacuation distance (drawn as a line downwind). */
    public static final class Line {
        public final double lengthM;
        public final String label;
        public final String cite;
        public final String text;
        public final boolean atLeast;

        Line(double lengthM, String label, String cite, String text,
                boolean atLeast) {
            this.lengthM = lengthM;
            this.label = label;
            this.cite = cite;
            this.text = text;
            this.atLeast = atLeast;
        }
    }

    public final List<Need> missing;
    public final Basis basis;
    public final Circle isolation;
    public final Square paz;
    public final List<Circle> rings;
    public final Line downwind;
    /** Guidebook rules that applied (keys into Erg.rules), in order. */
    public final List<String> rules;
    /** Plugin notes (our wording, not the guidebook's). */
    public final List<String> notes;
    public final List<Erg.Entry> candidates;
    public final List<String> guides;
    /** True if Table 1/3 applies (highlighted material, right land/water case). */
    public final boolean tableApplies;
    /** True if the zones need a wind direction (square or downwind line). */
    public final boolean needsWind;

    private Zones(List<Need> missing, Basis basis, Circle isolation,
            Square paz, List<Circle> rings, Line downwind, List<String> rules,
            List<String> notes, List<Erg.Entry> candidates, List<String> guides,
            boolean tableApplies, boolean needsWind) {
        this.missing = Collections.unmodifiableList(missing);
        this.basis = basis;
        this.isolation = isolation;
        this.paz = paz;
        this.rings = Collections.unmodifiableList(rings);
        this.downwind = downwind;
        this.rules = Collections.unmodifiableList(rules);
        this.notes = Collections.unmodifiableList(notes);
        this.candidates = Collections.unmodifiableList(candidates);
        this.guides = Collections.unmodifiableList(guides);
        this.tableApplies = tableApplies;
        this.needsWind = needsWind;
    }

    /** Ready to draw: nothing missing and an isolation circle exists. */
    public boolean usable() {
        return missing.isEmpty() && isolation != null;
    }

    /** Largest distance from the spill that is drawn (for framing the map). */
    public double extentM() {
        double m = isolation == null ? 0 : isolation.radiusM;
        if (paz != null)
            m = Math.max(m, Math.hypot(paz.downwindM, paz.downwindM / 2));
        for (Circle c : rings)
            m = Math.max(m, c.radiusM);
        if (downwind != null)
            m = Math.max(m, downwind.lengthM);
        return m;
    }

    // ------------------------------------------------------------------

    /** Work out the zones for a scenario. Never throws for bad input. */
    public static Zones compute(Erg erg, Scenario s) {
        List<Need> missing = new ArrayList<>();
        List<String> rules = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<Erg.Entry> cands = new ArrayList<>();
        Set<String> guides = new LinkedHashSet<>();

        if (s == null || !s.identified()) {
            missing.add(Need.IDENTIFY);
            return new Zones(missing, null, null, null,
                    Collections.<Circle> emptyList(), null, rules, notes, cands,
                    new ArrayList<String>(), false, false);
        }
        if (s.byGuideOnly()) {
            if (erg.guide(s.guide) != null)
                guides.add(erg.guide(s.guide).number);
        } else if (s.name != null) {
            Erg.Entry e = erg.entry(s.id, s.name);
            if (e != null)
                cands.add(e);
        } else {
            cands.addAll(erg.byId(s.id));
            if (cands.size() > 1)
                rules.add("unknown_name");
        }
        for (Erg.Entry e : cands)
            guides.add(e.guideNumber());
        if (guides.isEmpty()) {
            missing.add(Need.IDENTIFY);
            return new Zones(missing, null, null, null,
                    Collections.<Circle> emptyList(), null, rules, notes, cands,
                    new ArrayList<>(guides), false, false);
        }
        if (s.spill == null)
            missing.add(Need.SPILL);
        if (s.fire == null)
            missing.add(Need.FIRE);

        // Which Table 1 rows apply (land/water)?
        boolean waterRelevant = false;
        for (Erg.Entry e : cands)
            if (e.highlighted())
                for (Erg.T1Row r : erg.table1For(e))
                    if (r.variant == Erg.Variant.WATER)
                        waterRelevant = true;
        if (waterRelevant && s.water == null)
            missing.add(Need.WATER);
        List<Erg.T1Row> rows = new ArrayList<>();
        boolean waterOnlyOnLand = false;
        for (Erg.Entry e : cands) {
            if (!e.highlighted())
                continue;
            List<Erg.T1Row> land = new ArrayList<>(), wet = new ArrayList<>();
            for (Erg.T1Row r : erg.table1For(e))
                (r.variant == Erg.Variant.WATER ? wet : land).add(r);
            Scenario.Water w = waterRelevant ? s.water : null;
            if (w == Scenario.Water.WATER)
                rows.addAll(wet.isEmpty() ? land : wet);
            else if (w == Scenario.Water.LAND) {
                rows.addAll(land);
                if (land.isEmpty())
                    waterOnlyOnLand = true;
            } else {
                rows.addAll(land);
                rows.addAll(wet);
            }
        }
        if (waterRelevant && s.water == Scenario.Water.UNKNOWN) {
            rules.add("land_water_larger");
            rules.add("land_water_unclear");
        }
        boolean table = !rows.isEmpty();
        if (!table && rules.remove("unknown_name"))
            notes.add("Name not known: HAZ-TAK uses the largest distances of "
                    + "the guides listed for this ID number.");
        if (waterOnlyOnLand && !table)
            rules.add("water_only_entry");

        // Table 3 for large spills of the six gases.
        boolean useT3 = false;
        if (table && s.spill == Scenario.Spill.LARGE)
            for (Erg.T1Row r : rows)
                if (r.largeTable3)
                    useT3 = true;
        if (table) {
            if (s.tod == null)
                missing.add(Need.TIME_OF_DAY);
            if (!s.hasWind() && !s.windLater)
                missing.add(Need.WIND_DIRECTION);
            if (useT3) {
                if (s.container == null)
                    missing.add(Need.CONTAINER);
                if (s.windSpeed == null)
                    missing.add(Need.WIND_SPEED);
            }
        }
        if (!missing.isEmpty())
            return new Zones(missing, null, null, null,
                    Collections.<Circle> emptyList(), null, rules, notes, cands,
                    new ArrayList<>(guides), table, table);

        double k = s.doubled ? 2.0 : 1.0;
        String x2 = s.doubled ? " x2" : "";
        Basis basis;
        Circle iso;
        Square paz = null;
        List<Circle> rings = new ArrayList<>();
        Line dwLine = null;

        if (table) {
            Erg.Isolation bestIso = null;
            String isoCite = null;
            Erg.Downwind bestDw = null;
            String dwCite = null;
            boolean anyT3 = false;
            for (Erg.T1Row r : rows) {
                if (s.spill == Scenario.Spill.LARGE && r.largeTable3) {
                    Erg.T3Gas gas = erg.table3For(r.id);
                    if (gas == null)
                        continue;
                    anyT3 = true;
                    List<Erg.T3Row> t3rows = new ArrayList<>();
                    if (Scenario.CONTAINER_UNKNOWN.equals(s.container))
                        t3rows.addAll(gas.rows);
                    else if (gas.row(s.container) != null)
                        t3rows.add(gas.row(s.container));
                    else
                        t3rows.addAll(gas.rows); // container from another gas: be conservative
                    List<Erg.WindSpeed> speeds = new ArrayList<>();
                    if (s.windSpeed == Scenario.Wind.UNKNOWN || s.windSpeed == null) {
                        Collections.addAll(speeds, Erg.WindSpeed.values());
                    } else {
                        speeds.add(s.windSpeed == Scenario.Wind.LOW ? Erg.WindSpeed.LOW
                                : s.windSpeed == Scenario.Wind.MODERATE
                                        ? Erg.WindSpeed.MODERATE : Erg.WindSpeed.HIGH);
                    }
                    for (Erg.T3Row tr : t3rows) {
                        String cite = erg.edition + " Table 3, " + gas.title()
                                + ", " + tr.container + ", p. " + gas.page;
                        if (bestIso == null || tr.isolate.radiusM() > bestIso.radiusM()) {
                            bestIso = tr.isolate;
                            isoCite = cite;
                        }
                        for (Erg.WindSpeed ws : speeds) {
                            Erg.Downwind dw = (s.tod == Scenario.TimeOfDay.DAY
                                    ? tr.day : tr.night).get(ws);
                            if (better(dw, bestDw)) {
                                bestDw = dw;
                                dwCite = cite + ", " + ws.label;
                            }
                        }
                    }
                } else {
                    Erg.T1Dist d = s.spill == Scenario.Spill.SMALL ? r.small : r.large;
                    if (d == null)
                        continue;
                    String cite = erg.edition + " Table 1, " + r.name + ", p. " + r.page;
                    if (bestIso == null || d.isolate.radiusM() > bestIso.radiusM()) {
                        bestIso = d.isolate;
                        isoCite = cite;
                    }
                    Erg.Downwind dw = s.tod == Scenario.TimeOfDay.DAY ? d.day : d.night;
                    if (better(dw, bestDw)) {
                        bestDw = dw;
                        dwCite = cite;
                    }
                }
            }
            if (bestIso == null || bestDw == null) {
                // Cannot happen with a consistent dataset; refuse to draw.
                notes.add("No Table 1/3 distances found for this choice.");
                return new Zones(Collections.singletonList(Need.IDENTIFY), null,
                        null, null, rings, null, rules, notes, cands,
                        new ArrayList<>(guides), true, true);
            }
            basis = anyT3 ? Basis.TABLE3 : Basis.TABLE1;
            if (anyT3 && (Scenario.CONTAINER_UNKNOWN.equals(s.container)
                    || s.windSpeed == Scenario.Wind.UNKNOWN))
                notes.add("Container or wind speed not known: HAZ-TAK uses the "
                        + "largest Table 3 distance for this gas (a conservative "
                        + "choice, not a guidebook rule).");
            iso = new Circle("Initial isolation zone", bestIso.radiusM() * k,
                    bestIso.label() + x2, isoCite, null);
            paz = new Square(bestDw.metres() * k, bestDw.label() + x2,
                    bestDw.more, dwCite);
            if (bestDw.more)
                rules.add("plus_distance");
            if (s.spill == Scenario.Spill.LARGE)
                rules.add("more_than_one_package");
            if (s.fire == Boolean.TRUE) {
                iso = fire(erg, guides, k, x2, iso, rings);
                fireNote(erg, guides, notes);
                rules.add("yellow_fire");
                rules.add("fire_rule");
            }
        } else {
            basis = Basis.GUIDE;
            Erg.GuideDistance im = largest(erg, guides,
                    "Immediate precautionary measure", "isolate");
            if (im == null) {
                notes.add("The guide gives no isolation distance; nothing to draw.");
                return new Zones(new ArrayList<Need>(), basis, null, null, rings,
                        null, rules, notes, cands, new ArrayList<>(guides), false,
                        false);
            }
            iso = new Circle("Isolation (immediate precautionary measure)",
                    im.radiusM() * k, im.label() + x2, guideCite(erg, guides, im),
                    im.text, im.atLeast(), false, false);
            if (s.fire == Boolean.TRUE) {
                iso = fire(erg, guides, k, x2, iso, rings);
                fireNote(erg, guides, notes);
            }
            String downwindNote = downwindNote(erg, guides);
            if (downwindNote != null)
                notes.add(downwindNote);
            if (s.spill == Scenario.Spill.LARGE) {
                Erg.GuideDistance le = null, ld = null;
                for (String g : guides) {
                    Erg.Guide gd = erg.guide(g);
                    if (gd == null)
                        continue;
                    for (Erg.GuideDistance d : gd.under("Large Spill")) {
                        if (!"evacuate".equals(d.action))
                            continue;
                        if (d.allDirections() && (le == null || d.radiusM() > le.radiusM()))
                            le = d;
                        if (d.downwind() && (ld == null || d.radiusM() > ld.radiusM()))
                            ld = d;
                    }
                }
                if (le != null && le.radiusM() * k > iso.radiusM + 0.5)
                    rings.add(new Circle("Large spill: initial evacuation",
                            le.radiusM() * k, le.label() + x2,
                            guideCite(erg, guides, le), le.text, le.atLeast(),
                            false, le.responders()));
                if (ld != null)
                    dwLine = new Line(ld.radiusM() * k, ld.label() + x2,
                            guideCite(erg, guides, ld), ld.text, ld.atLeast());
            }
        }
        if (s.doubled)
            rules.add("double_catastrophic");
        // Largest ring last so it draws under the others.
        Collections.sort(rings, new java.util.Comparator<Circle>() {
            @Override
            public int compare(Circle a, Circle b) {
                return Double.compare(a.radiusM, b.radiusM);
            }
        });
        boolean needsWind = paz != null || dwLine != null;
        if (paz != null && !s.hasWind())
            notes.add("PROTECTIVE ACTION ZONE NOT DRAWN: the wind direction "
                    + "isn't known yet. Enter it as soon as you can; the zone "
                    + "extends " + paz.label + " downwind of the spill.");
        if (dwLine != null && !s.hasWind())
            notes.add("Enter the wind direction to draw the downwind "
                    + "evacuation distance on the map.");
        return new Zones(new ArrayList<Need>(), basis, iso, paz, rings, dwLine,
                dedupe(rules), notes, cands, new ArrayList<>(guides), table,
                needsWind);
    }

    /** True if a is a longer protective action distance than b (ties: "+" wins). */
    private static boolean better(Erg.Downwind a, Erg.Downwind b) {
        if (a == null)
            return false;
        if (b == null)
            return true;
        double da = a.metres(), db = b.metres();
        if (da > db + 1e-6)
            return true;
        return Math.abs(da - db) <= 1e-6 && a.more && !b.more;
    }

    /** Largest all-directions distance under a sub-heading across the guides. */
    static Erg.GuideDistance largest(Erg erg, Set<String> guides, String sub,
            String action) {
        Erg.GuideDistance best = null;
        for (String g : guides) {
            Erg.Guide gd = erg.guide(g);
            if (gd == null)
                continue;
            for (Erg.GuideDistance d : gd.under(sub))
                if (action.equals(d.action) && d.allDirections()
                        && (best == null || d.radiusM() > best.radiusM()))
                    best = d;
        }
        return best;
    }

    private static String guideCite(Erg erg, Set<String> guides,
            Erg.GuideDistance d) {
        for (String g : guides) {
            Erg.Guide gd = erg.guide(g);
            if (gd != null && gd.distances.contains(d)) {
                boolean second = "EMERGENCY RESPONSE".equals(d.section);
                return erg.edition + " Guide " + gd.number + ", " + d.section
                        + (second ? " (cargo fire)" : " (" + d.sub + ")") + ", p. "
                        + (second ? gd.page + 1 : gd.page);
            }
        }
        return erg.edition;
    }

    /**
     * Fire: the guide's fire isolation replaces a smaller isolation; the
     * largest fire evacuation becomes a ring when larger, or is folded into
     * the isolation circle ("isolate and evacuate") when the same.
     */
    private static Circle fire(Erg erg, Set<String> guides, double k, String x2,
            Circle iso, List<Circle> rings) {
        Erg.GuideDistance fi = largest(erg, guides, "Fire", "isolate");
        if (fi != null && fi.radiusM() * k > iso.radiusM)
            iso = new Circle("Isolation (fire)", fi.radiusM() * k,
                    fi.label() + x2, guideCite(erg, guides, fi), fi.text,
                    fi.atLeast(), false, false);
        Erg.GuideDistance fe = largest(erg, guides, "Fire", "evacuate");
        if (fe != null) {
            double r = fe.radiusM() * k;
            if (r > iso.radiusM + 0.5)
                rings.add(new Circle(fe.clearArea()
                        ? "Fire reaches cargo: clear the area"
                        : "Fire: initial evacuation", r, fe.label() + x2,
                        guideCite(erg, guides, fe), fe.text, fe.atLeast(), false,
                        fe.responders()));
            else if (r >= iso.radiusM - 0.5)
                iso = iso.alsoEvacuate(fe);
        }
        return iso;
    }

    /** A note when the largest fire distance is for a specific product only. */
    private static void fireNote(Erg erg, Set<String> guides, List<String> notes) {
        Erg.GuideDistance fe = largest(erg, guides, "Fire", "evacuate");
        if (fe != null && fe.text.contains("ammonium nitrate"))
            notes.add("Fire: HAZ-TAK uses the guide's distance for ammonium "
                    + "nitrate products (the largest fire distance in the guide), "
                    + "a conservative choice for any material on this guide.");
    }

    /**
     * The guide's own sentence telling responders to increase the distance
     * downwind (22 guides), shown as a note for materials drawn as circles.
     */
    private static String downwindNote(Erg erg, Set<String> guides) {
        for (String g : guides) {
            Erg.Guide gd = erg.guide(g);
            if (gd == null)
                continue;
            for (Erg.Block b : gd.blocks) {
                String t = b.html.replaceAll("<[^>]+>", "");
                if (t.toLowerCase(java.util.Locale.US).contains(
                        "increase the immediate precautionary measure distance, in the downwind direction"))
                    return "Guide " + gd.number + ": \"" + t + "\" (" + erg.edition
                            + " p. " + gd.page + ")";
            }
        }
        return null;
    }

    private static List<String> dedupe(List<String> in) {
        return new ArrayList<>(new LinkedHashSet<>(in));
    }
}
