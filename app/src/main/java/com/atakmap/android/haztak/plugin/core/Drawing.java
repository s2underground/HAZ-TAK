package com.atakmap.android.haztak.plugin.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The shapes one incident puts on the map, used by both the ATAK layer and
 * the file exports so they always agree. Shape UIDs are the marker UID plus
 * a fixed suffix, so every client derives the same UIDs and updates merge.
 */
public final class Drawing {

    private Drawing() {
    }

    public enum Kind {
        CIRCLE, POLYGON, LINE
    }

    public enum Role {
        ISOLATION, PROTECTIVE, EVACUATION, DOWNWIND
    }

    public static final String ISO = ".iso";
    public static final String PAZ = ".paz";
    public static final String DW = ".dw";
    /** Evacuation rings: ".ev1", ".ev2", ... */
    public static final String EV = ".ev";
    public static final int MAX_RINGS = 4;

    /** Every suffix a HAZ-TAK shape can have (for cleanup). */
    public static List<String> allSuffixes() {
        List<String> out = new ArrayList<>();
        out.add(ISO);
        out.add(PAZ);
        out.add(DW);
        for (int i = 1; i <= MAX_RINGS; i++)
            out.add(EV + i);
        return Collections.unmodifiableList(out);
    }

    /** Marker UID for a shape UID, or the UID itself. */
    public static String incidentUid(String uid) {
        if (uid == null)
            return null;
        for (String s : allSuffixes())
            if (uid.endsWith(s))
                return uid.substring(0, uid.length() - s.length());
        return uid;
    }

    public static boolean isShapeUid(String uid) {
        return uid != null && !incidentUid(uid).equals(uid);
    }

    public static final class Shape {
        public final String suffix;
        public final Kind kind;
        public final Role role;
        public final String title;
        public final double radiusM;      // circles
        public final double[][] points;   // polygon (open, 4 corners) or line {lat, lon}

        Shape(String suffix, Kind kind, Role role, String title, double radiusM,
                double[][] points) {
            this.suffix = suffix;
            this.kind = kind;
            this.role = role;
            this.title = title;
            this.radiusM = radiusM;
            this.points = points;
        }
    }

    /**
     * Shapes for an incident at (lat, lon). {@code z} is the recomputed
     * zones, or null when this build lacks the incident's edition (the
     * carried distances are drawn with generic titles).
     */
    public static List<Shape> shapes(HazRecord r, Zones z, double lat,
            double lon) {
        List<Shape> out = new ArrayList<>();
        Scenario s = r.scenario;
        if (z != null) {
            int i = 1;
            // Largest first so smaller shapes are drawn on top.
            List<Zones.Circle> rings = new ArrayList<>(z.rings);
            Collections.reverse(rings);
            for (Zones.Circle c : rings) {
                if (i > MAX_RINGS)
                    break;
                out.add(new Shape(EV + i++, Kind.CIRCLE, Role.EVACUATION,
                        Labels.ringTitle(r, c), c.radiusM, null));
            }
            if (z.paz != null && s.hasWind())
                out.add(new Shape(PAZ, Kind.POLYGON, Role.PROTECTIVE,
                        Labels.squareTitle(r, z.paz), 0, Geometry.square(lat,
                                lon, s.windFromDeg, z.paz.downwindM)));
            if (z.downwind != null && s.hasWind())
                out.add(new Shape(DW, Kind.LINE, Role.DOWNWIND,
                        Labels.lineTitle(r, z.downwind), 0, new double[][] {
                                { lat, lon }, Geometry.downwindPoint(lat, lon,
                                        s.windFromDeg, z.downwind.lengthM) }));
            out.add(new Shape(ISO, Kind.CIRCLE, Role.ISOLATION,
                    Labels.isolationTitle(r, z.isolation), z.isolation.radiusM,
                    null));
            return out;
        }
        String pre = r.isCleared() ? "CLEARED " : "";
        int i = 1;
        for (int k = r.ringsM.size() - 1; k >= 0 && i <= MAX_RINGS; k--)
            out.add(new Shape(EV + i++, Kind.CIRCLE, Role.EVACUATION,
                    pre + "EVACUATION " + Formats.measured(r.ringsM.get(k))
                            + " (" + r.edition + ")",
                    r.ringsM.get(k), null));
        if (!Double.isNaN(r.pazM) && s.hasWind())
            out.add(new Shape(PAZ, Kind.POLYGON, Role.PROTECTIVE, pre
                    + "PROTECTIVE ACTION ZONE " + Formats.measured(r.pazM)
                    + " DOWNWIND (" + r.edition + ")", 0,
                    Geometry.square(lat, lon, s.windFromDeg, r.pazM)));
        if (!Double.isNaN(r.dwM) && s.hasWind())
            out.add(new Shape(DW, Kind.LINE, Role.DOWNWIND, pre
                    + "DOWNWIND EVACUATION " + Formats.measured(r.dwM) + " ("
                    + r.edition + ")", 0, new double[][] { { lat, lon },
                            Geometry.downwindPoint(lat, lon, s.windFromDeg, r.dwM) }));
        out.add(new Shape(ISO, Kind.CIRCLE, Role.ISOLATION, pre + "ISOLATE "
                + Formats.measured(r.isoM) + " ALL DIRECTIONS (" + r.edition + ")",
                r.isoM, null));
        return out;
    }
}
