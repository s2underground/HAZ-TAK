package com.atakmap.android.haztak.plugin.core;

/**
 * Every text that leaves the plugin (marker remarks, shape titles), so
 * clients without HAZ-TAK still see what the zones are and where they come
 * from. Distances are shown exactly as printed in the guidebook.
 */
public final class Labels {

    private Labels() {
    }

    public static final String REMARKS_PREFIX = "[HAZ-TAK]";
    /** Last sentence of the generated block (marks where it ends). */
    public static final String REMARKS_END = "ERG initial response distances only.";

    /** "Large spill, no fire, night (sun), wind from 270° (W)". */
    public static String situation(Scenario s) {
        StringBuilder sb = new StringBuilder();
        sb.append(s.spill == Scenario.Spill.SMALL ? "Small spill"
                : s.spill == Scenario.Spill.LARGE ? "Large spill" : "Spill size ?");
        sb.append(", ").append(s.fire == null ? "fire ?"
                : s.fire ? "FIRE" : "no fire");
        if (s.tod != null)
            sb.append(", ").append(s.tod == Scenario.TimeOfDay.DAY ? "day" : "night");
        if (s.hasWind())
            sb.append(", wind from ").append(Formats.bearing(s.windFromDeg));
        if (s.water != null)
            sb.append(", ").append(s.water == Scenario.Water.LAND ? "on land"
                    : s.water == Scenario.Water.WATER ? "in water"
                            : "land or water not known");
        if (s.container != null)
            sb.append(", ").append(Scenario.CONTAINER_UNKNOWN.equals(s.container)
                    ? "container not known" : s.container);
        if (s.windSpeed != null)
            sb.append(", ").append(s.windSpeed == Scenario.Wind.UNKNOWN
                    ? "wind speed not known"
                    : s.windSpeed == Scenario.Wind.LOW ? "low wind"
                            : s.windSpeed == Scenario.Wind.MODERATE ? "moderate wind"
                                    : "high wind");
        if (s.doubled)
            sb.append(", DISTANCES DOUBLED (catastrophic release)");
        return sb.toString();
    }

    /** "Guide 124 (TIH, water-reactive, P)". */
    public static String guideLine(Zones z) {
        StringBuilder sb = new StringBuilder();
        sb.append(z.guides.size() == 1 ? "Guide " : "Guides ");
        for (int i = 0; i < z.guides.size(); i++)
            sb.append(i > 0 ? "/" : "").append(z.guides.get(i));
        boolean tih = false, wr = false, p = false;
        for (Erg.Entry e : z.candidates) {
            tih |= e.tih();
            wr |= e.waterReactive();
            p |= e.polymerization();
        }
        String f = (tih ? "TIH, " : "") + (wr ? "water-reactive, " : "")
                + (p ? "polymerization hazard, " : "");
        if (!f.isEmpty())
            sb.append(" (").append(f, 0, f.length() - 2).append(')');
        return sb.toString();
    }

    private static String prefix(HazRecord r) {
        return r.isCleared() ? "CLEARED " : "";
    }

    public static String isolationTitle(HazRecord r, Zones.Circle c) {
        return prefix(r) + "ISOLATE " + c.label + " ALL DIRECTIONS";
    }

    public static String ringTitle(HazRecord r, Zones.Circle c) {
        return prefix(r) + c.title.toUpperCase(java.util.Locale.US) + " " + c.label;
    }

    public static String squareTitle(HazRecord r, Zones.Square q) {
        Scenario s = r.scenario;
        return prefix(r) + "PROTECTIVE ACTION ZONE " + q.label + " DOWNWIND ("
                + (s.tod == Scenario.TimeOfDay.DAY ? "DAY" : "NIGHT") + ")";
    }

    public static String lineTitle(HazRecord r, Zones.Line l) {
        return prefix(r) + "LARGE SPILL: DOWNWIND EVACUATION " + l.label;
    }

    public static String windTitle(HazRecord r) {
        double w = r.scenario.windFromDeg;
        return prefix(r) + "WIND FROM " + Formats.bearing(w) + ", DOWNWIND "
                + Formats.compass(w + 180);
    }

    /** Provenance line for the card and the remarks. */
    public static String provenance(HazRecord r) {
        StringBuilder sb = new StringBuilder(r.edition);
        sb.append(". Placed by ")
                .append(r.byCallsign.isEmpty() ? "unknown" : r.byCallsign);
        if (r.timeMillis > 0)
            sb.append(' ').append(Formats.dtg(r.timeMillis));
        sb.append('.');
        if (r.isCleared()) {
            sb.append(" CLEARED by ")
                    .append(r.clearedBy.isEmpty() ? "unknown" : r.clearedBy);
            if (r.clearedMillis > 0)
                sb.append(' ').append(Formats.dtg(r.clearedMillis));
            sb.append('.');
        }
        return sb.toString();
    }

    /** Marker remarks for any TAK client. {@code z} may be null (unknown edition). */
    public static String remarks(HazRecord r, Zones z) {
        StringBuilder sb = new StringBuilder(REMARKS_PREFIX).append(' ');
        if (r.isCleared())
            sb.append("CLEARED. ");
        sb.append(r.label);
        if (z != null)
            sb.append(", ").append(guideLine(z));
        sb.append(". ").append(situation(r.scenario)).append(". ");
        if (z != null) {
            sb.append("Isolate ").append(z.isolation.label)
                    .append(" in all directions");
            if (z.paz != null)
                sb.append("; protect persons downwind ").append(z.paz.label);
            for (Zones.Circle c : z.rings)
                sb.append("; ").append(c.title.toLowerCase(java.util.Locale.US))
                        .append(' ').append(c.label);
            if (z.downwind != null)
                sb.append("; large spill downwind evacuation ")
                        .append(z.downwind.label);
            sb.append(" (").append(z.paz != null ? z.paz.cite : z.isolation.cite)
                    .append("). ");
        } else {
            sb.append("Isolate ").append(Formats.measured(r.isoM))
                    .append(" in all directions");
            if (!Double.isNaN(r.pazM))
                sb.append("; protect persons downwind ")
                        .append(Formats.measured(r.pazM));
            sb.append(". ");
        }
        sb.append(provenance(r)).append(' ').append(REMARKS_END);
        return sb.toString();
    }

    /**
     * New marker remarks that keep anything people wrote themselves: only
     * HAZ-TAK's own block (from {@link #REMARKS_PREFIX} to
     * {@link #REMARKS_END}) is replaced. If the block can't be found intact
     * the new block goes first and the old text is kept below it.
     */
    public static String mergeRemarks(String existing, String block) {
        if (existing == null || existing.trim().isEmpty())
            return block;
        int start = existing.indexOf(REMARKS_PREFIX);
        if (start >= 0) {
            int end = existing.indexOf(REMARKS_END, start);
            if (end >= 0)
                return existing.substring(0, start) + block
                        + existing.substring(end + REMARKS_END.length());
        }
        return block + "\n\n" + existing.trim();
    }
}
