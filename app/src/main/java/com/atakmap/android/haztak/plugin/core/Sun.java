package com.atakmap.android.haztak.plugin.core;

/**
 * Sun elevation from the NOAA solar position equations (Meeus, accurate to
 * about 0.01 degree for current dates), used to fill in DAY or NIGHT.
 *
 * The guidebook defines day as "any time after sunrise and before sunset"
 * (ERG2024 p. 288). Sunrise and sunset are taken, as usual, when the upper
 * edge of the sun is on the horizon with standard refraction: an elevation
 * of -0.833 degrees.
 */
public final class Sun {

    private Sun() {
    }

    public static final double SUNRISE_ELEVATION = -0.833;

    /** Apparent elevation of the sun's centre in degrees (no refraction). */
    public static double elevation(double latDeg, double lonDeg, long utcMillis) {
        double jd = utcMillis / 86400000.0 + 2440587.5;
        double t = (jd - 2451545.0) / 36525.0;
        double l0 = norm(280.46646 + t * (36000.76983 + t * 0.0003032));
        double m = 357.52911 + t * (35999.05029 - 0.0001537 * t);
        double e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);
        double mr = Math.toRadians(m);
        double c = Math.sin(mr) * (1.914602 - t * (0.004817 + 0.000014 * t))
                + Math.sin(2 * mr) * (0.019993 - 0.000101 * t)
                + Math.sin(3 * mr) * 0.000289;
        double trueLong = l0 + c;
        double omega = 125.04 - 1934.136 * t;
        double lambda = trueLong - 0.00569 - 0.00478 * Math.sin(Math.toRadians(omega));
        double eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059
                - t * 0.001813))) / 60.0) / 60.0;
        double eps = eps0 + 0.00256 * Math.cos(Math.toRadians(omega));
        double decl = Math.asin(Math.sin(Math.toRadians(eps))
                * Math.sin(Math.toRadians(lambda)));
        double y = Math.tan(Math.toRadians(eps / 2));
        y *= y;
        double l0r = Math.toRadians(l0);
        double eqTime = 4 * Math.toDegrees(y * Math.sin(2 * l0r)
                - 2 * e * Math.sin(mr) + 4 * e * y * Math.sin(mr) * Math.cos(2 * l0r)
                - 0.5 * y * y * Math.sin(4 * l0r) - 1.25 * e * e * Math.sin(2 * mr));
        double minutes = ((utcMillis % 86400000L) + 86400000L) % 86400000L / 60000.0;
        double trueSolarTime = minutes + eqTime + 4 * lonDeg;
        double ha = trueSolarTime / 4 - 180;
        double cosZen = Math.sin(Math.toRadians(latDeg)) * Math.sin(decl)
                + Math.cos(Math.toRadians(latDeg)) * Math.cos(decl)
                        * Math.cos(Math.toRadians(ha));
        cosZen = Math.max(-1, Math.min(1, cosZen));
        return 90 - Math.toDegrees(Math.acos(cosZen));
    }

    private static double norm(double d) {
        d %= 360;
        return d < 0 ? d + 360 : d;
    }

    /** DAY if the sun is above the sunrise/sunset elevation. */
    public static Scenario.TimeOfDay timeOfDay(double latDeg, double lonDeg,
            long utcMillis) {
        return elevation(latDeg, lonDeg, utcMillis) > SUNRISE_ELEVATION
                ? Scenario.TimeOfDay.DAY
                : Scenario.TimeOfDay.NIGHT;
    }
}
