package com.atakmap.android.haztak.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/** 0.2: structured reference pages, tap-to-dial numbers and the wind arrow. */
public class ReferenceAndWindTest {

    private final Erg erg = TestData.erg();

    // ------------------------------------------------------------------
    // Reference pages

    @Test
    public void allReferencePagesPresentAndStructured() {
        for (String k : Arrays.asList("how_to_use", "placard_intro",
                "green_intro", "protective_actions", "table1_background",
                "how_to_use_table1", "phone_numbers", "national_response_centers")) {
            Erg.RefPage p = erg.reference.get(k);
            assertNotNull(k, p);
            assertFalse(k, p.title.isEmpty());
            assertFalse(k, p.blocks.isEmpty());
            assertFalse(k, p.pagesLabel().isEmpty());
        }
        assertEquals("p. 1", erg.reference.get("how_to_use").pagesLabel());
        assertEquals("pp. 288-289", erg.reference.get("how_to_use_table1").pagesLabel());
        assertEquals("p. 388 and inside back cover",
                erg.reference.get("phone_numbers").pagesLabel());
    }

    @Test
    public void flowchartHasEveryQuestionInOrder() {
        StringBuilder qs = new StringBuilder();
        Set<String> actions = new HashSet<>();
        for (Erg.RefBlock b : erg.reference.get("how_to_use").blocks) {
            if (b.type.equals("q"))
                qs.append(b.question).append('|');
            if (!b.action.isEmpty())
                actions.add(b.action);
        }
        assertEquals("Do you see an explosive placard or label?|"
                + "Do you know the UN/NA ID number?|"
                + "Do you know the name of the material?|"
                + "Do you see a placard or label?|"
                + "Do you see a railcar or road trailer?|"
                + "Is the material highlighted in green?|"
                + "Is the material on fire?|", qs.toString());
        assertEquals(new HashSet<>(Arrays.asList("EXPLOSIVES", "ID", "NAME",
                "PLACARD", "RAIL", "UNKNOWN")), actions);
        // the fire question has both branches, each two boxes deep
        for (Erg.RefBlock b : erg.reference.get("how_to_use").blocks)
            if (b.question.equals("Is the material on fire?")) {
                assertEquals(2, b.yes.size());
                assertNotNull(b.no.then);
                assertTrue(b.dark);
            }
    }

    @Test
    public void evacuateOrShelterTable() {
        Erg.RefBlock t = null;
        for (Erg.RefBlock b : erg.reference.get("protective_actions").blocks)
            if (b.type.equals("table"))
                t = b;
        assertNotNull(t);
        assertEquals(Arrays.asList("Consider Evacuation:", "Consider Sheltering-in-place:"),
                t.cols);
        assertEquals(6, t.rows.size());
        for (List<String> r : t.rows)
            assertEquals(2, r.size());
        assertEquals("For anyone outdoors.", t.rows.get(3).get(0));
    }

    @Test
    public void provincesTableAndFigure() {
        Erg.RefBlock t = null;
        for (Erg.RefBlock b : erg.reference.get("national_response_centers").blocks)
            if (b.type.equals("table"))
                t = b;
        assertNotNull(t);
        assertEquals(13, t.rows.size());
        assertEquals(1, t.phoneCol);
        for (List<String> r : t.rows)
            assertEquals(r.get(0), 1, Phones.find(r.get(1)).size());
        Erg.RefBlock fig = null;
        for (Erg.RefBlock b : erg.reference.get("how_to_use_table1").blocks)
            if (b.type.equals("figure"))
                fig = b;
        assertNotNull(fig);
        assertEquals("paz", fig.kind);
        assertEquals(9, fig.labels.size());
        assertEquals("1/2 Downwind Distance", fig.figureLabel("1/2 Downwind Distance"));
    }

    // ------------------------------------------------------------------
    // Telephone numbers

    @Test
    public void dialStrings() {
        assertEquals("18882268832", Phones.dialString("1-888-CANUTEC"));
        assertEquals("*666", Phones.dialString("*666"));
        assertEquals("+525555591588", Phones.dialString("+52-55-5559-1588"));
        assertNull(Phones.dialString("2024"));
        assertNull(Phones.dialString("36428"));
    }

    private static List<String> dials(String s) {
        List<String> out = new java.util.ArrayList<>();
        for (Phones.Found f : Phones.find(s))
            out.add(f.dial);
        return out;
    }

    @Test
    public void findsNumbersAsPrinted() {
        assertEquals(Arrays.asList("18882268832"),
                dials("1-888-CANUTEC (226-8832) (in Canada and U.S.)"));
        assertEquals(Arrays.asList("*666"), dials("*666 (STAR 666) cellular (in Canada only)"));
        assertEquals(Arrays.asList("7036954695"),
                dials("703-695-4695/4696 * - Explosives/ammunition incidents"));
        assertEquals(Arrays.asList("5551280000"),
                dials("555128-0000 extensions 36428, 36422, 36469, 37807, 37810"));
        assertEquals(Arrays.asList("+5712886012", "+5719191919"),
                dials("For calls originating elsewhere call: +57-1-288-6012 / +57-1-919-1919"));
        assertTrue(dials("The Transportation of Dangerous Goods Act, 1992 (TDG Act)").isEmpty());
        assertTrue(dials("49 CFR §172.101 and Part 173").isEmpty());
    }

    @Test
    public void everyPhoneCardLineWithDigitsDials() {
        int n = 0;
        for (String k : Arrays.asList("phone_numbers", "national_response_centers"))
            for (Erg.RefBlock b : erg.reference.get(k).blocks)
                if (b.type.equals("phone"))
                    for (String line : b.lines)
                        if (line.matches(".*\\d{3}.*")) {
                            assertFalse(line, Phones.find(line).isEmpty());
                            n++;
                        }
        assertTrue(n > 30);
    }

    // ------------------------------------------------------------------
    // Wind arrow

    private static final double LAT = 38.9, LON = -77.0;

    private static Drawing.Shape find(List<Drawing.Shape> l, String suffix) {
        for (Drawing.Shape s : l)
            if (s.suffix.equals(suffix))
                return s;
        return null;
    }

    @Test
    public void windArrowForCircleOnlyIncident() {
        // gasoline (guide 128), small, no fire: circles only
        Scenario s = Scenario.empty().guideOnly("128", null)
                .withSpill(Scenario.Spill.SMALL).withFire(false);
        HazRecord r = HazRecord.create(erg, s, "A", 1L);
        assertNull(find(Drawing.shapes(r, r.zones(erg), LAT, LON), Drawing.WIND));

        HazRecord w = HazRecord.create(erg, s.withWindFrom(270), "A", 1L);
        List<Drawing.Shape> shapes = Drawing.shapes(w, w.zones(erg), LAT, LON);
        Drawing.Shape a = find(shapes, Drawing.WIND);
        assertNotNull(a);
        assertEquals(Drawing.Role.WIND, a.role);
        assertEquals(Drawing.Kind.LINE, a.kind);
        assertEquals(5, a.points.length);
        // tail at the spill, tip due east (wind from the west)
        assertEquals(LAT, a.points[0][0], 1e-9);
        double brg = Geo.bearing(LAT, LON, a.points[1][0], a.points[1][1]);
        assertEquals(90, brg, 0.5);
        double len = Geo.distance(LAT, LON, a.points[1][0], a.points[1][1]);
        assertEquals(Math.max(Drawing.ARROW_MIN_M, w.isoM * Drawing.ARROW_FACTOR),
                len, 1.0);
        assertTrue(a.title.contains("WIND FROM 270"));
        // isolation circle still drawn last (on top)
        assertEquals(Drawing.ISO, shapes.get(shapes.size() - 1).suffix);
    }

    @Test
    public void noWindArrowWhenSquareOrLineShowsDirection() {
        Scenario chlorine = Scenario.empty().idOnly("1017").withSpill(Scenario.Spill.SMALL)
                .withFire(false).withTimeOfDay(Scenario.TimeOfDay.DAY, false)
                .withWindFrom(270);
        HazRecord r = HazRecord.create(erg, chlorine, "A", 1L);
        List<Drawing.Shape> sh = Drawing.shapes(r, r.zones(erg), LAT, LON);
        assertNotNull(find(sh, Drawing.PAZ));
        assertNull(find(sh, Drawing.WIND));

        Scenario gasLarge = Scenario.empty().guideOnly("128", null)
                .withSpill(Scenario.Spill.LARGE).withFire(false).withWindFrom(90);
        HazRecord g = HazRecord.create(erg, gasLarge, "A", 1L);
        List<Drawing.Shape> sg = Drawing.shapes(g, g.zones(erg), LAT, LON);
        assertNotNull(find(sg, Drawing.DW));
        assertNull(find(sg, Drawing.WIND));
    }

    @Test
    public void windSuffixIsAShapeUid() {
        assertTrue(Drawing.isShapeUid("abc" + Drawing.WIND));
        assertEquals("abc", Drawing.incidentUid("abc.wind"));
        assertTrue(Drawing.allSuffixes().contains(Drawing.WIND));
    }

    @Test
    public void kmlHasWindStyle() {
        Scenario s = Scenario.empty().guideOnly("128", null)
                .withSpill(Scenario.Spill.SMALL).withFire(false).withWindFrom(0);
        HazRecord r = HazRecord.create(erg, s, "A", 1L);
        String kml = Exports.kml(new Exports.Incident("HAZ 1", LAT, LON, r, r.zones(erg)));
        assertTrue(kml.contains("<Style id=\"wind\">"));
        assertTrue(kml.contains("<styleUrl>#wind</styleUrl>"));
    }
}
