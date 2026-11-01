package com.atakmap.android.haztak.plugin.core;

import java.math.BigDecimal;

/** Unit conversions for distances printed in the guidebook. */
public final class Units {

    private Units() {
    }

    /** International foot, exact. */
    public static final double M_PER_FT = 0.3048;
    /** International mile, exact. */
    public static final double M_PER_MI = 1609.344;

    /** "0.1" → 0.1; NaN if not a plain decimal. */
    public static double parseDecimal(String s) {
        if (s == null)
            return Double.NaN;
        try {
            return new BigDecimal(s.trim()).doubleValue();
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /**
     * The imperial half of a guide distance, in metres: "330 feet",
     * "1/2 mile", "1 mile", "1/3 mile". NaN if not recognised.
     */
    public static double otherToMetres(String other) {
        if (other == null)
            return Double.NaN;
        String s = other.trim().toLowerCase(java.util.Locale.US);
        String[] p = s.split("\\s+");
        if (p.length != 2)
            return Double.NaN;
        double v = fraction(p[0]);
        if (Double.isNaN(v))
            return Double.NaN;
        if (p[1].equals("feet") || p[1].equals("foot") || p[1].equals("ft"))
            return v * M_PER_FT;
        if (p[1].equals("mile") || p[1].equals("miles") || p[1].equals("mi"))
            return v * M_PER_MI;
        return Double.NaN;
    }

    static double fraction(String s) {
        int slash = s.indexOf('/');
        if (slash < 0)
            return parseDecimal(s.replace(",", ""));
        double a = parseDecimal(s.substring(0, slash));
        double b = parseDecimal(s.substring(slash + 1));
        return b > 0 ? a / b : Double.NaN;
    }
}
