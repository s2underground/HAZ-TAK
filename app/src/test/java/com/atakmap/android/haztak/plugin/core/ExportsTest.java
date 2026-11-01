package com.atakmap.android.haztak.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ExportsTest {

    private final Erg erg = TestData.erg();

    private Exports.Incident chlorine() {
        Scenario s = Scenario.empty().material(erg.entry("1017", "Chlorine"))
                .withSpill(Scenario.Spill.SMALL).withFire(true)
                .withTimeOfDay(Scenario.TimeOfDay.NIGHT, false).withWindFrom(270);
        HazRecord r = HazRecord.create(erg, s, "A", 1793484000000L);
        return new Exports.Incident("HAZ 1 UN1017 Chlorine", 38.9, -77.0, r, r.zones(erg));
    }

    @Test
    public void drawingShapes() {
        Exports.Incident in = chlorine();
        List<Drawing.Shape> s = Drawing.shapes(in.record, in.zones, in.lat, in.lon);
        // fire isolation circle + protective square (fire evacuation = isolation)
        assertEquals(2, s.size());
        assertEquals(Drawing.PAZ, s.get(0).suffix);
        assertEquals(4, s.get(0).points.length);
        assertEquals(Drawing.ISO, s.get(1).suffix);
        assertTrue(s.get(1).title.contains("800 meters (1/2 mile)"));
        assertTrue(Drawing.isShapeUid("abc" + Drawing.PAZ));
        assertEquals("abc", Drawing.incidentUid("abc.ev2"));
        assertEquals("abc", Drawing.incidentUid("abc"));
    }

    @Test
    public void ringOutlinesStayOutsideTheCircle() {
        double r = 804.672;
        double[][] pts = Geo.ring(38.9, -77.0, r);
        for (int i = 0; i + 1 < pts.length; i++) {
            double midLat = (pts[i][0] + pts[i + 1][0]) / 2;
            double midLon = (pts[i][1] + pts[i + 1][1]) / 2;
            assertTrue(Geo.distance(38.9, -77.0, midLat, midLon) >= r - 0.01);
        }
    }

    @Test
    public void kmlAndGpx() {
        Exports.Incident in = chlorine();
        String kml = Exports.kml(in);
        assertTrue(kml.contains("<Polygon>"));
        assertTrue(kml.contains("PROTECTIVE ACTION ZONE 1.5 km (0.9 mi) DOWNWIND (NIGHT)"));
        String gpx = Exports.gpx(in, 0L);
        assertEquals(2, gpx.split("<trk>").length - 1);
        assertTrue(gpx.contains("<sym>Danger Area</sym>"));
        assertTrue(Exports.fileStem(in.name, 1793484000000L).startsWith("HAZ_1_UN1017_Chlorine_31"));
    }
}
