package com.atakmap.android.haztak.plugin.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Incident names. The name is the marker's ordinary callsign, so anyone
 * (with or without HAZ-TAK) can rename it. HAZ-TAK sets it only when an
 * incident is placed or renamed in HAZ-TAK; clearing just adds or removes the
 * " CLEARED" suffix. (Same rules as Blast 1.0.)
 */
public final class Names {

    private Names() {
    }

    public static final String CLEARED_SUFFIX = " CLEARED";
    public static final int MAX_LENGTH = 64;

    private static final Pattern NUMBER = Pattern.compile("^HAZ (\\d{1,6})\\b");

    /** Longest material part of a default name. */
    static final int SHORT_LABEL = 32;

    /** Default name for a new incident: "HAZ 3 UN1017 Chlorine". */
    public static String initial(int number, String label) {
        String l = label == null ? "" : label.trim();
        if (l.length() > SHORT_LABEL)
            l = l.substring(0, SHORT_LABEL).trim() + "...";
        return ("HAZ " + number + " " + l).trim();
    }

    /** The name without the CLEARED suffix. */
    public static String base(String name) {
        if (name == null)
            return "";
        String n = name.trim();
        while (n.endsWith(CLEARED_SUFFIX))
            n = n.substring(0, n.length() - CLEARED_SUFFIX.length()).trim();
        return n;
    }

    /** The name with the suffix added or removed for the given status. */
    public static String withStatus(String name, boolean cleared) {
        String b = base(name);
        return cleared ? b + CLEARED_SUFFIX : b;
    }

    /**
     * Clean a typed name: trim, collapse whitespace, drop control
     * characters, cap the length. Returns null if nothing is left.
     */
    public static String sanitize(String input) {
        if (input == null)
            return null;
        StringBuilder sb = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c)) {
                space = sb.length() > 0;
                continue;
            }
            if (c < 0x20 || c == 0x7f)
                continue;
            if (space) {
                sb.append(' ');
                space = false;
            }
            sb.append(c);
        }
        String s = sb.toString();
        if (s.length() > MAX_LENGTH)
            s = s.substring(0, MAX_LENGTH).trim();
        return s.isEmpty() ? null : s;
    }

    /** "HAZ 3 ..." → 3; 0 if the name doesn't start that way. */
    public static int numberIn(String name) {
        if (name == null)
            return 0;
        Matcher m = NUMBER.matcher(name.trim());
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    /** Safe file name stem: letters, digits, '-' and '_' only. */
    public static String fileStem(String name) {
        String b = base(name);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length() && sb.length() < 40; i++) {
            char c = b.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_')
                sb.append(c);
            else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_')
                sb.append('_');
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '_')
            sb.setLength(sb.length() - 1);
        return sb.length() == 0 ? "HAZ" : sb.toString();
    }
}
