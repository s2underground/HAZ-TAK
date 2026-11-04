package com.atakmap.android.haztak.plugin.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Finding materials in the index: by ID number (yellow pages) or by name
 * (blue pages). Name search ignores case, punctuation and word order:
 * "ammonia anhydrous" finds "Ammonia, anhydrous".
 */
public final class Search {

    private Search() {
    }

    /** Most results returned by a name search. */
    public static final int MAX_RESULTS = 60;

    /**
     * Entries for a typed ID: digits only, with an optional "UN"/"NA"
     * prefix. Empty if it isn't a 4-digit number or isn't listed.
     */
    public static List<Erg.Entry> byId(Erg erg, String typed) {
        String id = cleanId(typed);
        return id == null ? Collections.<Erg.Entry> emptyList() : erg.byId(id);
    }

    /** "UN 1017" → "1017"; null if not four digits. */
    public static String cleanId(String typed) {
        if (typed == null)
            return null;
        String s = typed.trim().toUpperCase(Locale.US);
        if (s.startsWith("UN") || s.startsWith("NA"))
            s = s.substring(2);
        s = s.replaceAll("[\\s-]", "");
        return s.matches("\\d{4}") ? s : null;
    }

    /** Lower case, accents and punctuation removed, single spaces. */
    static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.US);
        n = n.replaceAll("[^a-z0-9]+", " ");
        return n.trim();
    }

    /**
     * Entries whose name contains every typed word (each as the start of a
     * word in the name). Best matches first: exact name, then names that
     * start with the query, then shorter names.
     */
    public static List<Erg.Entry> byName(Erg erg, String typed) {
        final String q = normalize(typed == null ? "" : typed);
        if (q.length() < 2)
            return Collections.emptyList();
        final String[] words = q.split(" ");
        List<Erg.Entry> hits = new ArrayList<>();
        for (Erg.Entry e : erg.index) {
            String n = " " + normalize(e.name) + " ";
            boolean all = true;
            for (String w : words) {
                if (!n.contains(" " + w)) {
                    all = false;
                    break;
                }
            }
            if (all)
                hits.add(e);
        }
        Collections.sort(hits, new Comparator<Erg.Entry>() {
            @Override
            public int compare(Erg.Entry a, Erg.Entry b) {
                int ra = rank(a, q), rb = rank(b, q);
                if (ra != rb)
                    return ra - rb;
                if (a.name.length() != b.name.length())
                    return a.name.length() - b.name.length();
                return a.name.compareTo(b.name);
            }
        });
        return hits.size() > MAX_RESULTS ? hits.subList(0, MAX_RESULTS) : hits;
    }

    private static int rank(Erg.Entry e, String q) {
        String n = normalize(e.name);
        if (n.equals(q))
            return 0;
        if (n.startsWith(q))
            return 1;
        return 2;
    }

    /**
     * "Did you mean": up to {@code max} distinct names whose words are
     * within a small spelling distance of what was typed (for a search that
     * found nothing, e.g. "amonia", "gasolene"). Each typed word must match
     * some word of the name; the closest names come first.
     */
    public static List<String> suggest(Erg erg, String typed, int max) {
        String q = normalize(typed == null ? "" : typed);
        if (q.replace(" ", "").length() < 4)
            return Collections.emptyList();
        String[] words = q.split(" ");
        final java.util.Map<String, Integer> best = new java.util.LinkedHashMap<>();
        for (Erg.Entry e : erg.index) {
            String[] nw = normalize(e.name).split(" ");
            int total = 0;
            boolean all = true;
            for (String w : words) {
                if (w.isEmpty())
                    continue;
                int allowed = w.length() >= 7 ? 2 : 1;
                int bestW = Integer.MAX_VALUE;
                for (String n : nw) {
                    if (Math.abs(n.length() - w.length()) > allowed)
                        continue;
                    bestW = Math.min(bestW, distance(w, n, allowed));
                }
                if (bestW > allowed) {
                    all = false;
                    break;
                }
                total += bestW;
            }
            if (!all || total == 0)
                continue;
            Integer old = best.get(e.name);
            if (old == null || total < old)
                best.put(e.name, total);
        }
        List<String> names = new ArrayList<>(best.keySet());
        Collections.sort(names, new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                int c = best.get(a) - best.get(b);
                return c != 0 ? c : a.length() - b.length();
            }
        });
        return names.size() > max ? names.subList(0, max) : names;
    }

    /** Levenshtein distance, giving up (returns limit + 1) beyond limit. */
    static int distance(String a, String b, int limit) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++)
            prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, cur[j]);
            }
            if (rowMin > limit)
                return limit + 1;
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
