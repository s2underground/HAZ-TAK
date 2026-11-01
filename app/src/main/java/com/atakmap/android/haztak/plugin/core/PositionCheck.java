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
        OUTSIDE("outside the drawn zones");

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
        boolean near = false;
        Where w = Where.OUTSIDE;
        double iso = z.isolation.radiusM;
        if (Math.abs(d - iso) <= margin)
            near = true;
        if (d <= iso) {
            w = Where.ISOLATION;
        } else if (z.paz != null && !Double.isNaN(windFromDeg)
                && Geometry.inSquare(rel, z.paz.downwindM)) {
            w = Where.PROTECTIVE;
        } else {
            for (Zones.Circle c : z.rings)
                if (d <= c.radiusM) {
                    w = Where.EVACUATION;
                    break;
                }
        }
        if (z.paz != null && !Double.isNaN(windFromDeg)
                && Math.abs(Geometry.squareEdgeDistance(rel, z.paz.downwindM)) <= margin)
            near = true;
        for (Zones.Circle c : z.rings)
            if (Math.abs(d - c.radiusM) <= margin)
                near = true;
        return new Result(w, d, rel[1], near);
    }

    /** Counts {isolation, protective, evacuation, outside} for team positions. */
    public static int[] count(Zones z, double spillLat, double spillLon,
            double windFromDeg, double[][] positions) {
        int[] n = new int[4];
        for (double[] p : positions) {
            Result r = check(z, spillLat, spillLon, windFromDeg, p[0], p[1],
                    Double.NaN);
            if (r != null)
                n[r.where.ordinal()]++;
        }
        return n;
    }
}
