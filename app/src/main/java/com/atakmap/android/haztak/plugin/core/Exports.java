package com.atakmap.android.haztak.plugin.core;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * File exports of one incident: ATAK data package (mission package v2),
 * KML/KMZ for Google Earth and GPX for Garmin. Pure Java; the ATAK layer
 * supplies the CoT XML for the data package. Shapes come from
 * {@link Drawing}, the same list the map uses. (Structure from Blast 1.0.)
 */
public final class Exports {

    private Exports() {
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** What an export needs to know about one incident. */
    public static final class Incident {
        public final String name;
        public final double lat;
        public final double lon;
        public final HazRecord record;
        public final Zones zones; // null for an edition this build lacks

        public Incident(String name, double lat, double lon, HazRecord record,
                Zones zones) {
            this.name = name == null || name.isEmpty() ? "HAZ" : name;
            this.lat = lat;
            this.lon = lon;
            this.record = record;
            this.zones = zones;
        }

        List<Drawing.Shape> shapes() {
            return Drawing.shapes(record, zones, lat, lon);
        }

        String remarks() {
            return Labels.remarks(record, zones);
        }
    }

    // ------------------------------------------------------------------
    // Data package

    public static String manifest(String packageUid, String name,
            Map<String, String> uidToEntry) {
        StringBuilder sb = new StringBuilder();
        sb.append("<MissionPackageManifest version=\"2\">\n");
        sb.append(" <Configuration>\n");
        sb.append("  <Parameter name=\"uid\" value=\"")
                .append(Xml.escape(packageUid)).append("\"/>\n");
        sb.append("  <Parameter name=\"name\" value=\"")
                .append(Xml.escape(name)).append("\"/>\n");
        sb.append(" </Configuration>\n");
        sb.append(" <Contents>\n");
        for (Map.Entry<String, String> e : uidToEntry.entrySet()) {
            sb.append("  <Content ignore=\"false\" zipEntry=\"")
                    .append(Xml.escape(e.getValue())).append("\">\n");
            sb.append("   <Parameter name=\"uid\" value=\"")
                    .append(Xml.escape(e.getKey())).append("\"/>\n");
            sb.append("  </Content>\n");
        }
        sb.append(" </Contents>\n");
        sb.append("</MissionPackageManifest>\n");
        return sb.toString();
    }

    /** Zip entry name for a CoT item: "uid/uid.cot" (uid made path-safe). */
    public static String cotEntry(String uid) {
        String safe = uid.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe + "/" + safe + ".cot";
    }

    public static Map<String, byte[]> dataPackage(String packageUid,
            String name, Map<String, String> uidToCotXml) {
        Map<String, String> entries = new LinkedHashMap<>();
        for (String uid : uidToCotXml.keySet())
            entries.put(uid, cotEntry(uid));
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("MANIFEST/manifest.xml",
                manifest(packageUid, name, entries).getBytes(UTF8));
        for (Map.Entry<String, String> e : uidToCotXml.entrySet())
            files.put(entries.get(e.getKey()), e.getValue().getBytes(UTF8));
        return files;
    }

    // ------------------------------------------------------------------
    // KML / KMZ (colours are aabbggrr)

    static String kmlLine(Drawing.Role role, boolean grey) {
        if (grey)
            return "ff9e9e9e";
        switch (role) {
            case ISOLATION:
                return "ff0000d5";
            case PROTECTIVE:
                return "ff006dff";
            case EVACUATION:
                return "ff00d7ff";
            case WIND:
                return "ffffc440";
            default:
                return "ff006dff";
        }
    }

    static String kmlFill(Drawing.Role role, boolean grey) {
        if (grey)
            return "149e9e9e";
        switch (role) {
            case ISOLATION:
                return "550000d5";
            case PROTECTIVE:
                return "4400abff";
            case EVACUATION:
                return "1a00d7ff";
            default:
                return "00000000";
        }
    }

    static String styleId(Drawing.Role role) {
        switch (role) {
            case ISOLATION:
                return "isolation";
            case PROTECTIVE:
                return "protective";
            case EVACUATION:
                return "evacuation";
            case WIND:
                return "wind";
            default:
                return "downwind";
        }
    }

    public static String kml(Incident in) {
        boolean grey = in.record.isCleared();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>\n");
        tag(sb, "name", in.name);
        tag(sb, "description", in.remarks());
        for (Drawing.Role role : Drawing.Role.values())
            style(sb, styleId(role), kmlLine(role, grey),
                    kmlFill(role, grey), role == Drawing.Role.ISOLATION ? 3 : 4,
                    null);
        style(sb, "spill", null, null, 0, grey ? "ff9e9e9e" : "ff0000ff");
        sb.append("<Folder>\n");
        tag(sb, "name", in.name);
        for (Drawing.Shape s : in.shapes()) {
            sb.append("<Placemark>\n");
            tag(sb, "name", s.title);
            sb.append("<styleUrl>#").append(styleId(s.role))
                    .append("</styleUrl>\n");
            if (s.kind == Drawing.Kind.LINE) {
                sb.append("<LineString><coordinates>");
                coords(sb, s.points, false);
                sb.append("</coordinates></LineString>\n");
            } else {
                double[][] pts = s.kind == Drawing.Kind.CIRCLE
                        ? Geo.ring(in.lat, in.lon, s.radiusM)
                        : s.points;
                sb.append("<Polygon><outerBoundaryIs><LinearRing><coordinates>");
                coords(sb, pts, s.kind == Drawing.Kind.POLYGON);
                sb.append("</coordinates></LinearRing></outerBoundaryIs></Polygon>\n");
            }
            sb.append("</Placemark>\n");
        }
        sb.append("<Placemark>\n");
        tag(sb, "name", in.name);
        tag(sb, "description", in.remarks());
        sb.append("<styleUrl>#spill</styleUrl>\n<Point><coordinates>")
                .append(coord(in.lon)).append(',').append(coord(in.lat))
                .append(",0</coordinates></Point>\n</Placemark>\n");
        sb.append("</Folder>\n</Document>\n</kml>\n");
        return sb.toString();
    }

    private static void coords(StringBuilder sb, double[][] pts, boolean close) {
        for (double[] p : pts)
            sb.append(coord(p[1])).append(',').append(coord(p[0])).append(",0 ");
        if (close && pts.length > 0)
            sb.append(coord(pts[0][1])).append(',').append(coord(pts[0][0]))
                    .append(",0 ");
        sb.setLength(sb.length() - 1);
    }

    private static void style(StringBuilder sb, String id, String line,
            String fill, int width, String icon) {
        sb.append("<Style id=\"").append(id).append("\">");
        if (icon != null)
            sb.append("<IconStyle><color>").append(icon)
                    .append("</color></IconStyle>");
        if (line != null)
            sb.append("<LineStyle><color>").append(line).append("</color><width>")
                    .append(width).append("</width></LineStyle>");
        if (fill != null)
            sb.append("<PolyStyle><color>").append(fill)
                    .append("</color></PolyStyle>");
        sb.append("</Style>\n");
    }

    public static Map<String, byte[]> kmz(Incident in) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("doc.kml", kml(in).getBytes(UTF8));
        return files;
    }

    // ------------------------------------------------------------------
    // GPX 1.1

    /** Garmin symbol for the spill waypoint. */
    public static final String GPX_SYMBOL = "Danger Area";

    public static String gpx(Incident in, long nowMillis) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"HAZ-TAK for ATAK\" "
                + "xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        sb.append("<metadata>");
        tagInline(sb, "name", in.name);
        tagInline(sb, "desc", in.record.edition + " " + in.record.label);
        tagInline(sb, "time", Formats.iso(nowMillis));
        sb.append("</metadata>\n");
        sb.append("<wpt lat=\"").append(coord(in.lat)).append("\" lon=\"")
                .append(coord(in.lon)).append("\">");
        tagInline(sb, "name", in.name);
        tagInline(sb, "cmt", in.record.label);
        tagInline(sb, "desc", in.remarks());
        tagInline(sb, "sym", GPX_SYMBOL);
        sb.append("</wpt>\n");
        String base = Names.base(in.name);
        for (Drawing.Shape s : in.shapes()) {
            double[][] pts;
            if (s.kind == Drawing.Kind.CIRCLE)
                pts = Geo.ring(in.lat, in.lon, s.radiusM);
            else if (s.kind == Drawing.Kind.POLYGON) {
                pts = new double[s.points.length + 1][];
                System.arraycopy(s.points, 0, pts, 0, s.points.length);
                pts[s.points.length] = s.points[0];
            } else
                pts = s.points;
            sb.append("<trk>");
            tagInline(sb, "name", base + " " + s.suffix.substring(1)
                    .toUpperCase(Locale.US));
            tagInline(sb, "desc", s.title);
            sb.append("<trkseg>\n");
            for (double[] p : pts)
                sb.append("<trkpt lat=\"").append(coord(p[0])).append("\" lon=\"")
                        .append(coord(p[1])).append("\"/>\n");
            sb.append("</trkseg></trk>\n");
        }
        sb.append("</gpx>\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------

    public static void zip(Map<String, byte[]> files, OutputStream out)
            throws IOException {
        ZipOutputStream z = new ZipOutputStream(out);
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            z.putNextEntry(new ZipEntry(e.getKey()));
            z.write(e.getValue());
            z.closeEntry();
        }
        z.finish();
    }

    static String coord(double v) {
        return String.format(Locale.US, "%.7f", v);
    }

    private static void tag(StringBuilder sb, String name, String text) {
        tagInline(sb, name, text);
        sb.append('\n');
    }

    private static void tagInline(StringBuilder sb, String name, String text) {
        sb.append('<').append(name).append('>').append(Xml.escape(text))
                .append("</").append(name).append('>');
    }

    /** "HAZ_2_UN1017_Chlorine_312145" style stem for exported files. */
    public static String fileStem(String name, long millis) {
        String dtg = Formats.dtg(millis);
        return Names.fileStem(name) + "_" + dtg.substring(0, 7);
    }
}
