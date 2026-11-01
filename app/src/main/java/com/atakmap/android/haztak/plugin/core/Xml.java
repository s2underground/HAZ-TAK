package com.atakmap.android.haztak.plugin.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal XML helpers for the detail stored as text (copied from Blast 1.0). */
public final class Xml {

    private Xml() {
    }

    /**
     * Attributes of the first element named {@code element} in {@code xml},
     * or null. Handles both quote styles and the five XML entities plus
     * numeric character references.
     */
    public static Map<String, String> attributes(String xml,
            String element) {
        if (xml == null)
            return null;
        int i = 0;
        int n = xml.length();
        while (i < n) {
            int lt = xml.indexOf('<', i);
            if (lt < 0)
                return null;
            int p = lt + 1;
            int nameStart = p;
            while (p < n && isNameChar(xml.charAt(p)))
                p++;
            String name = xml.substring(nameStart, p);
            if (!name.equals(element)) {
                i = p > lt + 1 ? p : lt + 1;
                continue;
            }
            Map<String, String> attrs = new LinkedHashMap<>();
            while (p < n) {
                while (p < n && Character.isWhitespace(xml.charAt(p)))
                    p++;
                if (p >= n)
                    return null;
                char c = xml.charAt(p);
                if (c == '/' || c == '>')
                    return attrs;
                int ks = p;
                while (p < n && isNameChar(xml.charAt(p)))
                    p++;
                if (p == ks)
                    return null;
                String key = xml.substring(ks, p);
                while (p < n && Character.isWhitespace(xml.charAt(p)))
                    p++;
                if (p >= n || xml.charAt(p) != '=')
                    return null;
                p++;
                while (p < n && Character.isWhitespace(xml.charAt(p)))
                    p++;
                if (p >= n)
                    return null;
                char q = xml.charAt(p);
                if (q != '"' && q != '\'')
                    return null;
                int vs = ++p;
                int ve = xml.indexOf(q, vs);
                if (ve < 0)
                    return null;
                attrs.put(key, unescape(xml.substring(vs, ve)));
                p = ve + 1;
            }
            return null;
        }
        return null;
    }

    public static String escape(String s) {
        if (s == null)
            return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&apos;");
                    break;
                default:
                    if (c < 0x20)
                        sb.append("&#").append((int) c).append(';');
                    else
                        sb.append(c);
            }
        }
        return sb.toString();
    }

    static String unescape(String s) {
        if (s.indexOf('&') < 0)
            return s;
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = s.indexOf(';', i);
                if (semi > i && semi - i <= 10) {
                    String ent = s.substring(i + 1, semi);
                    String rep = null;
                    if (ent.equals("amp"))
                        rep = "&";
                    else if (ent.equals("lt"))
                        rep = "<";
                    else if (ent.equals("gt"))
                        rep = ">";
                    else if (ent.equals("quot"))
                        rep = "\"";
                    else if (ent.equals("apos"))
                        rep = "'";
                    else if (ent.startsWith("#")) {
                        try {
                            int cp = ent.startsWith("#x") || ent.startsWith("#X")
                                    ? Integer.parseInt(ent.substring(2), 16)
                                    : Integer.parseInt(ent.substring(1));
                            if (Character.isValidCodePoint(cp))
                                rep = new String(Character.toChars(cp));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    if (rep != null) {
                        sb.append(rep);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.'
                || c == ':';
    }

    private static boolean isName(String s) {
        if (s == null || s.isEmpty() || s.length() > 32)
            return false;
        for (int i = 0; i < s.length(); i++)
            if (!isNameChar(s.charAt(i)))
                return false;
        return Character.isLetter(s.charAt(0)) || s.charAt(0) == '_';
    }

}
