package com.atakmap.android.haztak.plugin.core;

/**
 * WGS84 geodesics (Vincenty direct and inverse) and ring outlines for
 * formats that have no circle (KML, GPX). Tested against GeographicLib.
 */
public final class Geo {

    private Geo() {
    }

    private static final double A = 6378137.0;
    private static final double F = 1 / 298.257223563;
    private static final double B = A * (1 - F);

    /** Destination {lat, lon} (degrees) from a start, azimuth (deg) and distance (m). */
    public static double[] destination(double latDeg, double lonDeg,
            double azimuthDeg, double distanceM) {
        double alpha1 = Math.toRadians(azimuthDeg);
        double sinA1 = Math.sin(alpha1), cosA1 = Math.cos(alpha1);
        double tanU1 = (1 - F) * Math.tan(Math.toRadians(latDeg));
        double cosU1 = 1 / Math.sqrt(1 + tanU1 * tanU1), sinU1 = tanU1 * cosU1;
        double sigma1 = Math.atan2(tanU1, cosA1);
        double sinAlpha = cosU1 * sinA1;
        double cos2Alpha = 1 - sinAlpha * sinAlpha;
        double u2 = cos2Alpha * (A * A - B * B) / (B * B);
        double bigA = 1 + u2 / 16384 * (4096 + u2 * (-768 + u2 * (320 - 175 * u2)));
        double bigB = u2 / 1024 * (256 + u2 * (-128 + u2 * (74 - 47 * u2)));
        double sigma = distanceM / (B * bigA), sigmaP;
        double cos2SigmaM, sinSigma, cosSigma;
        int guard = 0;
        do {
            cos2SigmaM = Math.cos(2 * sigma1 + sigma);
            sinSigma = Math.sin(sigma);
            cosSigma = Math.cos(sigma);
            double dSigma = bigB * sinSigma * (cos2SigmaM + bigB / 4
                    * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)
                            - bigB / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma)
                                    * (-3 + 4 * cos2SigmaM * cos2SigmaM)));
            sigmaP = sigma;
            sigma = distanceM / (B * bigA) + dSigma;
        } while (Math.abs(sigma - sigmaP) > 1e-12 && ++guard < 200);
        double tmp = sinU1 * sinSigma - cosU1 * cosSigma * cosA1;
        double lat2 = Math.atan2(sinU1 * cosSigma + cosU1 * sinSigma * cosA1,
                (1 - F) * Math.sqrt(sinAlpha * sinAlpha + tmp * tmp));
        double lambda = Math.atan2(sinSigma * sinA1,
                cosU1 * cosSigma - sinU1 * sinSigma * cosA1);
        double c = F / 16 * cos2Alpha * (4 + F * (4 - 3 * cos2Alpha));
        double l = lambda - (1 - c) * F * sinAlpha * (sigma + c * sinSigma
                * (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)));
        double lon2 = Math.toDegrees(Math.toRadians(lonDeg) + l);
        lon2 = ((lon2 + 540) % 360) - 180;
        return new double[] { Math.toDegrees(lat2), lon2 };
    }

    /** Mean earth radius (IUGG), for the spherical fallback. */
    static final double MEAN_RADIUS = 6371008.8;

    /**
     * Geodesic distance in metres on WGS84 (Vincenty). For nearly antipodal
     * points, where Vincenty doesn't converge, the spherical (haversine)
     * distance is returned instead (within about 0.5 percent). NaN only for
     * non-finite input.
     */
    public static double distance(double lat1, double lon1, double lat2,
            double lon2) {
        if (!finite(lat1) || !finite(lon1) || !finite(lat2) || !finite(lon2))
            return Double.NaN;
        double v = vincenty(lat1, lon1, lat2, lon2);
        return Double.isNaN(v) ? haversine(lat1, lon1, lat2, lon2) : v;
    }

    private static boolean finite(double d) {
        return !Double.isNaN(d) && !Double.isInfinite(d);
    }

    static double haversine(double lat1, double lon1, double lat2,
            double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2)
                        * Math.sin(dl / 2);
        return 2 * MEAN_RADIUS * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** Vincenty inverse; NaN if it doesn't converge. */
    static double vincenty(double lat1, double lon1, double lat2,
            double lon2) {
        double l = Math.toRadians(lon2 - lon1);
        double u1 = Math.atan((1 - F) * Math.tan(Math.toRadians(lat1)));
        double u2 = Math.atan((1 - F) * Math.tan(Math.toRadians(lat2)));
        double sinU1 = Math.sin(u1), cosU1 = Math.cos(u1);
        double sinU2 = Math.sin(u2), cosU2 = Math.cos(u2);
        double lambda = l, lambdaP;
        double sinSigma, cosSigma, sigma, cos2Alpha, cos2SigmaM;
        int guard = 0;
        do {
            double sinL = Math.sin(lambda), cosL = Math.cos(lambda);
            sinSigma = Math.sqrt((cosU2 * sinL) * (cosU2 * sinL)
                    + (cosU1 * sinU2 - sinU1 * cosU2 * cosL)
                            * (cosU1 * sinU2 - sinU1 * cosU2 * cosL));
            if (sinSigma == 0)
                return 0;
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosL;
            sigma = Math.atan2(sinSigma, cosSigma);
            double sinAlpha = cosU1 * cosU2 * sinL / sinSigma;
            cos2Alpha = 1 - sinAlpha * sinAlpha;
            cos2SigmaM = cos2Alpha == 0 ? 0
                    : cosSigma - 2 * sinU1 * sinU2 / cos2Alpha;
            double c = F / 16 * cos2Alpha * (4 + F * (4 - 3 * cos2Alpha));
            lambdaP = lambda;
            lambda = l + (1 - c) * F * sinAlpha * (sigma + c * sinSigma
                    * (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)));
        } while (Math.abs(lambda - lambdaP) > 1e-12 && ++guard < 200);
        if (guard >= 200)
            return Double.NaN;
        double u2b = cos2Alpha * (A * A - B * B) / (B * B);
        double bigA = 1 + u2b / 16384 * (4096 + u2b * (-768 + u2b * (320 - 175 * u2b)));
        double bigB = u2b / 1024 * (256 + u2b * (-128 + u2b * (74 - 47 * u2b)));
        double dSigma = bigB * sinSigma * (cos2SigmaM + bigB / 4
                * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)
                        - bigB / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma)
                                * (-3 + 4 * cos2SigmaM * cos2SigmaM)));
        return B * bigA * (sigma - dSigma);
    }

    /**
     * Initial bearing (degrees, 0-360) from point 1 to point 2 on the
     * sphere. Within the few kilometres of an incident the difference from
     * the ellipsoidal azimuth is far below a degree.
     */
    public static double bearing(double lat1, double lon1, double lat2,
            double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2)
                - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        double b = Math.toDegrees(Math.atan2(y, x));
        return (b + 360.0) % 360.0;
    }

    /** Vertices used for ring outlines in exported files. */
    public static final int RING_VERTICES = 72;

    /**
     * A closed outline ({lat, lon} pairs, first point repeated at the end)
     * that circumscribes the circle of {@code radiusM}: vertices sit at
     * r / cos(pi/n), so every edge stays outside the true circle and the
     * exported ring is never smaller than the doctrine distance.
     */
    public static double[][] ring(double latDeg, double lonDeg,
            double radiusM) {
        int n = RING_VERTICES;
        double rv = radiusM / Math.cos(Math.PI / n);
        double[][] pts = new double[n + 1][];
        for (int i = 0; i < n; i++)
            pts[i] = destination(latDeg, lonDeg, 360.0 * i / n, rv);
        pts[n] = pts[0];
        return pts;
    }
}
