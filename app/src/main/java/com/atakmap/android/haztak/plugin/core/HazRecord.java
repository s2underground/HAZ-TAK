package com.atakmap.android.haztak.plugin.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One incident: the user's choices (a {@link Scenario}), who placed it and
 * when, its number and status, and the distances that were drawn (carried
 * so clients can detect a disagreement, and so a client without this
 * edition can still draw something). Immutable.
 */
public final class HazRecord {

    public static final int VERSION = 1;
    public static final int MAX_NUMBER = 999_999;

    public enum Status {
        ACTIVE, CLEARED;

        public static Status parse(String s) {
            return "CLEARED".equals(s) ? CLEARED : ACTIVE;
        }
    }

    public final int version;
    public final String edition;
    public final Scenario scenario;
    /** Material name as the sender's dataset printed it (for display). */
    public final String label;
    public final String byCallsign;
    public final long timeMillis;
    public final Status status;
    public final String clearedBy;
    public final long clearedMillis;
    public final int number;
    /** Distances drawn by the sender, metres (NaN if none). */
    public final double isoM, pazM, dwM;
    public final List<Double> ringsM;
    public final Map<String, String> extra;
    /** Received distances differed from this build's computation (not sent). */
    public final boolean receivedMismatch;

    public HazRecord(int version, String edition, Scenario scenario,
            String label, String byCallsign, long timeMillis, Status status,
            String clearedBy, long clearedMillis, int number, double isoM,
            double pazM, double dwM, List<Double> ringsM,
            Map<String, String> extra, boolean receivedMismatch) {
        this.version = version;
        this.edition = edition == null ? "" : edition;
        this.scenario = scenario;
        this.label = label == null ? "" : label;
        this.byCallsign = byCallsign == null ? "" : byCallsign;
        this.timeMillis = timeMillis;
        this.status = status == null ? Status.ACTIVE : status;
        this.clearedBy = clearedBy == null ? "" : clearedBy;
        this.clearedMillis = clearedMillis;
        this.number = number < 0 || number > MAX_NUMBER ? 0 : number;
        this.isoM = isoM;
        this.pazM = pazM;
        this.dwM = dwM;
        this.ringsM = ringsM == null ? Collections.<Double> emptyList()
                : Collections.unmodifiableList(new ArrayList<>(ringsM));
        this.extra = extra == null ? Collections.<String, String> emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(extra));
        this.receivedMismatch = receivedMismatch;
    }

    /** A new incident from a scenario whose zones are usable. */
    public static HazRecord create(Erg erg, Scenario s, String by, long t) {
        Zones z = Zones.compute(erg, s);
        if (!z.usable())
            throw new IllegalArgumentException("zones not usable: " + z.missing);
        return new HazRecord(VERSION, erg.edition, s, labelFor(erg, s), by, t,
                Status.ACTIVE, "", -1, 0, z.isolation.radiusM,
                z.paz == null ? Double.NaN : z.paz.downwindM,
                z.downwind == null ? Double.NaN : z.downwind.lengthM,
                ringList(z), null, false);
    }

    static List<Double> ringList(Zones z) {
        List<Double> out = new ArrayList<>();
        for (Zones.Circle c : z.rings)
            out.add(c.radiusM);
        return out;
    }

    /** "UN1017 Chlorine", "UN1057 (name not known)", "Guide 128 (placard: Fuel oil / Gasoline)". */
    public static String labelFor(Erg erg, Scenario s) {
        if (s.byGuideOnly() && s.name != null)
            return s.name + " (Guide " + s.guide + ")";
        if (s.byGuideOnly()) {
            Erg.Placard p = s.placardKey == null ? null : erg.placard(s.placardKey);
            return "Guide " + s.guide + (p != null ? " (placard: " + p.label + ")"
                    : " (material not identified)");
        }
        if (s.name == null)
            return "UN" + s.id + " (name not known)";
        return "UN" + s.id + " " + s.name;
    }

    private HazRecord with(Scenario s, Status st, String cby, long ct, int n,
            double iso, double paz, double dw, List<Double> rings) {
        return new HazRecord(version, edition, s, label, byCallsign, timeMillis,
                st, cby, ct, n, iso, paz, dw, rings, extra, false);
    }

    public HazRecord withNumber(int n) {
        return with(scenario, status, clearedBy, clearedMillis, n, isoM, pazM,
                dwM, ringsM);
    }

    public HazRecord cleared(String by, long when) {
        return with(scenario, Status.CLEARED, by, when, number, isoM, pazM, dwM,
                ringsM);
    }

    public HazRecord reopened() {
        return with(scenario, Status.ACTIVE, "", -1, number, isoM, pazM, dwM,
                ringsM);
    }

    /**
     * The same incident with changed inputs (the wind shifted, or an edit
     * of the material or situation). Distances and the material label are
     * recomputed; who placed it, when, its number and its status stay.
     */
    public HazRecord updated(Erg erg, Scenario s) {
        Zones z = Zones.compute(erg, s);
        if (!z.usable())
            throw new IllegalArgumentException("zones not usable: " + z.missing);
        return new HazRecord(version, edition, s, labelFor(erg, s), byCallsign,
                timeMillis, status, clearedBy, clearedMillis, number,
                z.isolation.radiusM, z.paz == null ? Double.NaN : z.paz.downwindM,
                z.downwind == null ? Double.NaN : z.downwind.lengthM,
                ringList(z), extra, false);
    }

    /**
     * Edit trail: who last changed the incident's inputs and when. Carried
     * as ordinary extra attributes ("eby", "et"), so older HAZ-TAK builds keep
     * and re-send them unchanged.
     */
    public static final String EDITED_BY = "eby";
    public static final String EDITED_AT = "et";
    static final int MAX_EDITOR = 64;

    public HazRecord edited(String by, long when) {
        Map<String, String> x = new LinkedHashMap<>(extra);
        String b = by == null ? "" : by.trim();
        if (b.length() > MAX_EDITOR)
            b = b.substring(0, MAX_EDITOR);
        x.put(EDITED_BY, b);
        x.put(EDITED_AT, Formats.iso(when));
        return new HazRecord(version, edition, scenario, label, byCallsign,
                timeMillis, status, clearedBy, clearedMillis, number, isoM, pazM,
                dwM, ringsM, x, receivedMismatch);
    }

    /** Callsign of the last editor, or "" if never edited. */
    public String editedBy() {
        String b = extra.get(EDITED_BY);
        return b == null ? "" : b;
    }

    /** Time of the last edit, or -1. */
    public long editedMillis() {
        String t = extra.get(EDITED_AT);
        return t == null ? -1 : Formats.parseIso(t);
    }

    public boolean wasEdited() {
        return extra.containsKey(EDITED_AT);
    }

    public boolean isCleared() {
        return status == Status.CLEARED;
    }

    /** True if this build has the edition the incident was made with. */
    public boolean editionKnown(Erg erg) {
        return erg != null && erg.edition.equals(edition);
    }

    /**
     * Zones to draw. With the incident's edition, recomputed from this
     * build's data (authoritative). Otherwise null: the caller draws the
     * carried distances instead.
     */
    public Zones zones(Erg erg) {
        if (!editionKnown(erg))
            return null;
        Zones z = Zones.compute(erg, scenario);
        return z.usable() ? z : null;
    }

    /** True if the carried distances differ from this build's computation. */
    public boolean disagrees(Erg erg) {
        if (receivedMismatch)
            return true;
        Zones z = zones(erg);
        if (z == null)
            return editionKnown(erg); // known edition but inputs unusable
        if (Double.isNaN(isoM))
            return false;
        return !Same.metres(isoM, z.isolation.radiusM)
                || !Same.metres(pazM, z.paz == null ? Double.NaN : z.paz.downwindM)
                || !Same.metres(dwM, z.downwind == null ? Double.NaN
                        : z.downwind.lengthM)
                || !Same.list(ringsM, ringList(z));
    }

    /** Comparisons at the precision the detail carries (0.1 m). */
    static final class Same {
        static boolean metres(double a, double b) {
            if (Double.isNaN(a) || Double.isNaN(b))
                return Double.isNaN(a) && Double.isNaN(b);
            return Math.abs(a - b) <= 0.06;
        }

        static boolean list(List<Double> a, List<Double> b) {
            if (a.size() != b.size())
                return false;
            for (int i = 0; i < a.size(); i++)
                if (!metres(a.get(i), b.get(i)))
                    return false;
            return true;
        }
    }
}
