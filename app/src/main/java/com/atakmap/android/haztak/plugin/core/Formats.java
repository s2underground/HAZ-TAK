package com.atakmap.android.haztak.plugin.core;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Text formatting. Strings are built by concatenation; nothing here
 * formats resource text.
 */
public final class Formats {

    private Formats() {
    }

    private static final String[] MONTHS = {
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP",
            "OCT", "NOV", "DEC"
    };

    private static final String[] COMPASS16 = {
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW",
            "WSW", "W", "WNW", "NW", "NNW"
    };

    /** 1200 → "1,200". */
    public static String thousands(long v) {
        boolean neg = v < 0;
        String s = Long.toString(Math.abs(v));
        StringBuilder sb = new StringBuilder();
        int n = s.length();
        for (int i = 0; i < n; i++) {
            sb.append(s.charAt(i));
            int left = n - 1 - i;
            if (left > 0 && left % 3 == 0)
                sb.append(',');
        }
        return (neg ? "-" : "") + sb;
    }

    /** Above this many feet, measured distances switch to mi/km. */
    public static final double MILES_ABOVE_FT = 10000;

    /** Measured distance: "450 ft (137 m)" or "2.4 mi (3.9 km)". */
    public static String measured(double metres) {
        if (Double.isNaN(metres))
            return "?";
        double feet = metres / Units.M_PER_FT;
        if (feet >= MILES_ABOVE_FT)
            return decimal(metres / Units.M_PER_MI) + " mi ("
                    + decimal(metres / 1000.0) + " km)";
        return thousands(Math.round(feet)) + " ft ("
                + thousands(Math.round(metres)) + " m)";
    }

    /** One decimal below 100, whole numbers with separators above. */
    static String decimal(double v) {
        if (v >= 100)
            return thousands(Math.round(v));
        long tenths = Math.round(v * 10.0);
        return thousands(tenths / 10) + "." + (tenths % 10);
    }

    /** Whole metres, rounded up (drawn radii are never shown smaller). */
    public static String metresUp(double m) {
        return thousands((long) Math.ceil(m - 1e-9)) + " m";
    }

    /** 270 → "270° (W)". */
    public static String bearing(double deg) {
        if (Double.isNaN(deg))
            return "?";
        long d = Math.round(norm360(deg)) % 360;
        return d + "° (" + compass(d) + ")";
    }

    /** 16-point compass name for a bearing. */
    public static String compass(double deg) {
        int i = (int) Math.round(norm360(deg) / 22.5) % 16;
        return COMPASS16[i];
    }

    public static double norm360(double deg) {
        double d = deg % 360.0;
        return d < 0 ? d + 360.0 : d;
    }

    /** Military date-time group in Zulu: "312145Z OCT 26". */
    public static String dtg(long millis) {
        java.util.Calendar c = java.util.Calendar.getInstance(
                TimeZone.getTimeZone("UTC"), Locale.US);
        c.setTimeInMillis(millis);
        return two(c.get(java.util.Calendar.DAY_OF_MONTH))
                + two(c.get(java.util.Calendar.HOUR_OF_DAY))
                + two(c.get(java.util.Calendar.MINUTE)) + "Z "
                + MONTHS[c.get(java.util.Calendar.MONTH)] + " "
                + two(c.get(java.util.Calendar.YEAR) % 100);
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : Integer.toString(v);
    }

    /** ISO 8601 UTC, CoT style: "2026-10-31T21:45:00.000Z". */
    public static String iso(long millis) {
        SimpleDateFormat f = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(millis));
    }

    /** Parse {@link #iso} output (milliseconds optional); -1 if invalid. */
    public static long parseIso(String s) {
        if (s == null || s.isEmpty())
            return -1;
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };
        for (String p : patterns) {
            SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            f.setLenient(false);
            try {
                Date d = f.parse(s);
                if (d != null)
                    return d.getTime();
            } catch (ParseException ignored) {
            }
        }
        return -1;
    }
}
