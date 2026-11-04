package com.atakmap.android.haztak.plugin.core;

/**
 * Where a position is relative to an incident's zones. Pure geometry; the
 * caller supplies positions. Boundaries count as inside (conservative).
 */
public final class PositionCheck {

    private PositionCheck() {
    }

    public enum Where {
        ISOLATION("inside the initial isolation zone"),
        PROTECTIVE("inside the protective action zone (downwind)"),
        EVACUATION("inside an evacuation distance"),
        DOWNWIND("inside the large-spill downwind evacuation distance"),
        POSSIBLE("possibly inside a downwind zone: the wind isn't entered"),
        OUTSIDE("outside the drawn distances (they are minimums for the initial phase)");

        public final String text;

        Where(String text) {
            this.text = text;
        }
    }

    public static final class Result {
        public final Where where;
        public final double distanceM;
        /** Along the downwind axis (+ downwind), NaN without wind. */
        public final double alongM;
        /** Within the GPS error of a zone edge. */
        public final boolean nearEdge;

        Result(Where where, double distanceM, double alongM, boolean nearEdge) {
            this.where = where;
            this.distanceM = distanceM;
            this.alongM = alongM;
            this.nearEdge = nearEdge;
        }

        public String headline() {
            String s = Formats.measured(distanceM) + " from the spill";
            if (!Double.isNaN(alongM))
                s += alongM >= 0 ? ", downwind side" : ", upwind side";
            return s + ": " + where.text + ".";
        }
    }

    /** Edge margin when the GPS error is unknown, metres. */
    static final double DEFAULT_MARGIN = 15;

    /**
     * Check a position. {@code ceM} is the GPS circular error (NaN if
     * unknown). {@code z} must be usable; wind may be NaN (no square).
     */
    public static Result check(Zones z, double spillLat, double spillLon,
            double windFromDeg, double lat, double lon, double ceM) {
        double[] rel = Geometry.relative(spillLat, spillLon, windFromDeg, lat, lon);
        if (rel == null)
            return null;
        double margin = Double.isNaN(ceM) ? DEFAULT_MARGIN : Math.max(ceM, 5);
        double d = rel[0];
        boolean wind = !Double.isNaN(windFromDeg);
        boolean near = false;
        Where w = Where.OUTSIDE;
        double iso = z.isolation.radiusM;
        if (Math.abs(d - iso) <= margin)
            near = true;
        if (d <= iso) {
            w = Where.ISOLATION;
        } else if (z.paz != null && wind && Geometry.inSquare(rel, z.paz.downwindM)) {
            w = Where.PROTECTIVE;
        } else {
            for (Zones.Circle c : z.rings)
                if (d <= c.radiusM) {
                    w = Where.EVACUATION;
                    break;
                }
            // The guide's large-spill downwind evacuation: the ERG gives a
            // distance, not a width; HAZ-TAK counts anyone on the downwind
            // side within that distance (conservative).
            if (w == Where.OUTSIDE && z.downwind != null && wind && rel[1] >= -margin
                    && d <= z.downwind.lengthM)
                w = Where.DOWNWIND;
            // No wind: a downwind zone could point anywhere.
            if (w == Where.OUTSIDE && !wind
                    && ((z.paz != null && d <= z.paz.downwindM * FAR_CORNER)
                            || (z.downwind != null && d <= z.downwind.lengthM)))
                w = Where.POSSIBLE;
        }
        if (z.paz != null && wind
                && Math.abs(Geometry.squareEdgeDistance(rel, z.paz.downwindM)) <= margin)
            near = true;
        if (z.downwind != null && wind && rel[1] >= -margin
                && Math.abs(d - z.downwind.lengthM) <= margin)
            near = true;
        for (Zones.Circle c : z.rings)
            if (Math.abs(d - c.radiusM) <= margin)
                near = true;
        return new Result(w, d, rel[1], near);
    }

    /** Distance from the spill to the square's far corners, per unit side. */
    static final double FAR_CORNER = Math.hypot(1.0, 0.5);

    /** True if this result means "inside something" (never shown as safe). */
    public static boolean inside(Where w) {
        return w != Where.OUTSIDE;
    }

    /** Counts per {@link Where} (indexed by ordinal) for team positions. */
    public static int[] count(Zones z, double spillLat, double spillLon,
            double windFromDeg, double[][] positions) {
        int[] n = new int[Where.values().length];
        for (double[] p : positions) {
            Result r = check(z, spillLat, spillLon, windFromDeg, p[0], p[1],
                    Double.NaN);
            if (r != null)
                n[r.where.ordinal()]++;
        }
        return n;
    }
}
