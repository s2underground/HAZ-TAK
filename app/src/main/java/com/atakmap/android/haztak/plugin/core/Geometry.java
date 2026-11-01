package com.atakmap.android.haztak.plugin.core;

/**
 * Shapes and position checks for the ERG zones.
 *
 * The protective action zone is a square (ERG2024 p. 288-289): its length and
 * width equal the protective action distance; it lies downwind of the spill
 * with its upwind edge through the spill, centred on the downwind axis, so it
 * reaches half the distance to each side.
 */
public final class Geometry {

    private Geometry() {
    }

    /** Downwind azimuth for a wind blowing FROM the given bearing. */
    public static double downwindAzimuth(double windFromDeg) {
        return Formats.norm360(windFromDeg + 180.0);
    }

    /**
     * Corners {lat, lon} of the protective action square, in order: upwind
     * left, far left, far right, upwind right (looking downwind). Each corner
     * is placed from the spill by its own geodesic, so the shape is exact
     * relative to the spill point.
     */
    public static double[][] square(double lat, double lon, double windFromDeg,
            double sideM) {
        double az = downwindAzimuth(windFromDeg);
        double half = sideM / 2.0;
        double farDist = Math.hypot(sideM, half);
        double farAngle = Math.toDegrees(Math.atan2(half, sideM));
        return new double[][] {
                Geo.destination(lat, lon, az - 90.0, half),
                Geo.destination(lat, lon, az - farAngle, farDist),
                Geo.destination(lat, lon, az + farAngle, farDist),
                Geo.destination(lat, lon, az + 90.0, half)
        };
    }

    /** End point {lat, lon} of a line from the spill straight downwind. */
    public static double[] downwindPoint(double lat, double lon,
            double windFromDeg, double lengthM) {
        return Geo.destination(lat, lon, downwindAzimuth(windFromDeg), lengthM);
    }

    /**
     * Position relative to the spill along the downwind axis:
     * {distance, along (+ downwind), cross (+ to the right looking downwind)}.
     */
    public static double[] relative(double spillLat, double spillLon,
            double windFromDeg, double lat, double lon) {
        double d = Geo.distance(spillLat, spillLon, lat, lon);
        if (Double.isNaN(d))
            return null;
        double along = Double.NaN, cross = Double.NaN;
        if (!Double.isNaN(windFromDeg)) {
            double b = Geo.bearing(spillLat, spillLon, lat, lon);
            double delta = Math.toRadians(b - downwindAzimuth(windFromDeg));
            along = d * Math.cos(delta);
            cross = d * Math.sin(delta);
        }
        return new double[] { d, along, cross };
    }

    /** True if a point (from {@link #relative}) is inside the square. */
    public static boolean inSquare(double[] rel, double sideM) {
        if (rel == null || Double.isNaN(rel[1]))
            return false;
        return rel[1] >= 0 && rel[1] <= sideM && Math.abs(rel[2]) <= sideM / 2.0;
    }

    /**
     * Distance (m) from a point to the edge of the square: negative inside,
     * positive outside. Used to warn when a position is near a boundary.
     */
    public static double squareEdgeDistance(double[] rel, double sideM) {
        if (rel == null || Double.isNaN(rel[1]))
            return Double.NaN;
        double x = rel[1], y = Math.abs(rel[2]), h = sideM / 2.0;
        double dx = Math.max(-x, x - sideM), dy = y - h;
        if (dx <= 0 && dy <= 0)
            return Math.max(dx, dy);
        return Math.hypot(Math.max(dx, 0), Math.max(dy, 0));
    }
}
