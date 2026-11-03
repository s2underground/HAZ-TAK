package com.atakmap.android.haztak.plugin.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Telephone numbers in the guidebook's text, for tap-to-dial. A number is
 * an optional "+", digits and hyphens, optionally ending in a word spelled
 * on the keypad ("1-888-CANUTEC"), or *666 (Canada, cellular). Anything with
 * fewer than seven digits (years, extensions, "(STAR 666)") is not a number.
 */
public final class Phones {

    private Phones() {
    }

    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\w(/.])(\\*666|\\+?\\d(?:[\\d-]*\\d)?(?:-[A-Z]{7})?)");

    /** A number found in a text: where it is and what to dial. */
    public static final class Found {
        public final int start;
        public final int end;
        public final String dial;

        Found(int start, int end, String dial) {
            this.start = start;
            this.end = end;
            this.dial = dial;
        }
    }

    public static List<Found> find(CharSequence text) {
        List<Found> out = new ArrayList<>();
        Matcher m = PHONE.matcher(text);
        while (m.find()) {
            String d = dialString(m.group(1));
            if (d != null)
                out.add(new Found(m.start(1), m.end(1), d));
        }
        return out;
    }

    /** The number to dial for a printed number, or null if it is not one. */
    public static String dialString(String printed) {
        if (printed.equals("*666"))
            return "*666";
        StringBuilder sb = new StringBuilder();
        int digits = 0;
        for (char ch : printed.toCharArray()) {
            char d = ch >= 'A' && ch <= 'Z' ? keypad(ch) : ch;
            if (d == '+' && sb.length() == 0)
                sb.append(d);
            else if (d >= '0' && d <= '9') {
                sb.append(d);
                digits++;
            }
        }
        return digits >= 7 ? sb.toString() : null;
    }

    static char keypad(char ch) {
        return "22233344455566677778889999".charAt(ch - 'A');
    }
}
