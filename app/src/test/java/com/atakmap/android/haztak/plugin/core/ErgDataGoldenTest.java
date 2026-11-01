package com.atakmap.android.haztak.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Values checked against the printed ERG2024 (page numbers in the names are
 * the printed pages). The extraction itself is cross-checked in
 * tools/erg/REPORT.md; these tests pin the bundled asset.
 */
public class ErgDataGoldenTest {

    private final Erg erg = TestData.erg();

    @Test
    public void sourceAndCounts() {
        assertEquals("ERG2024", erg.edition);
        assertEquals("180eb92e649d1ada7d2f39855da70d5742ff211d6863a8804ed2abb835c96e30",
                erg.sourceSha256);
        assertEquals(2771, erg.index.size());
        assertEquals(1984, erg.ids().size());
        assertEquals(64, erg.guides.size());
        assertEquals(537, erg.table1.size());
        assertEquals(108, erg.table2.size());
        assertEquals(6, erg.table3.size());
        int t3rows = 0;
        for (Erg.T3Gas g : erg.table3)
            t3rows += g.rows.size();
        assertEquals(22, t3rows);
        assertEquals(28, erg.placards.size());
        int hl = 0;
        for (Erg.Entry e : erg.index)
            if (e.highlighted())
                hl++;
        assertEquals(522, hl);
    }

    @Test
    public void yellowPage29_firstEntries() {
        assertEquals("125", erg.entry("1005", "Ammonia, anhydrous").guide);
        Erg.Entry cl = erg.entry("1017", "Chlorine");
        assertEquals("124", cl.guide);
        assertTrue(cl.highlighted());
        assertTrue(cl.tih());
        assertTrue(cl.flags.contains("T3"));
        assertEquals(29, cl.yellowPage);
        assertEquals("119P", erg.entry("1040", "Ethylene oxide").guide);
        assertTrue(erg.entry("1040", "Ethylene oxide").polymerization());
        // Explosives rows have no ID number
        boolean found = false;
        for (Erg.Entry e : erg.byId(""))
            if (e.name.equals("Explosives, division 1.4 or 1.6")) {
                assertEquals("114", e.guide);
                found = true;
            }
        assertTrue(found);
    }

    @Test
    public void notHighlighted_gasoline() {
        Erg.Entry g = erg.entry("1203", "Gasoline");
        assertEquals("128", g.guide);
        assertFalse(g.highlighted());
        assertTrue(erg.table1For(g).isEmpty());
    }

    @Test
    public void table1Page290_chlorine() {
        Erg.T1Row r = erg.table1For(erg.entry("1017", "Chlorine")).get(0);
        assertEquals(290, r.page);
        assertEquals("60 m (200 ft)", r.small.isolate.label());
        assertEquals("0.3 km (0.2 mi)", r.small.day.label());
        assertEquals("1.5 km (0.9 mi)", r.small.night.label());
        assertTrue(r.largeTable3);
        assertNull(r.large);
    }

    @Test
    public void table1Page290_boronTrifluoride() {
        Erg.T1Row r = erg.table1For(erg.entry("1008", "Boron trifluoride")).get(0);
        assertEquals("30 m (100 ft)", r.small.isolate.label());
        assertEquals("0.2 km (0.1 mi)", r.small.day.label());
        assertEquals("0.7 km (0.5 mi)", r.small.night.label());
        assertEquals("400 m (1,250 ft)", r.large.isolate.label());
        assertEquals("2.4 km (1.5 mi)", r.large.day.label());
        assertEquals("4.7 km (2.9 mi)", r.large.night.label());
    }

    @Test
    public void table1_plusDistance_nickelCarbonyl() {
        Erg.T1Row r = erg.table1For(erg.entry("1259", "Nickel carbonyl")).get(0);
        assertEquals("1,000 m (3,000 ft)", r.large.isolate.label());
        assertEquals("11.0+ km (7.0+ mi)", r.large.night.label());
        assertTrue(r.large.night.more);
        // drawn at the larger of 11.0 km and 7.0 mi
        assertEquals(7.0 * Units.M_PER_MI, r.large.night.metres(), 1e-6);
    }

    @Test
    public void table1_landAndWater_thionylChloride() {
        Erg.Entry e = erg.entry("1836", "Thionyl chloride");
        assertEquals(2, erg.table1For(e).size());
        Erg.T1Row land = null, water = null;
        for (Erg.T1Row r : erg.table1For(e))
            if (r.variant == Erg.Variant.LAND)
                land = r;
            else if (r.variant == Erg.Variant.WATER)
                water = r;
        assertNotNull(land);
        assertNotNull(water);
        assertEquals("600 m (2,000 ft)", water.large.isolate.label());
        assertEquals("7.6 km (4.7 mi)", water.large.day.label());
        assertEquals("11.0+ km (7.0+ mi)", water.large.night.label());
    }

    @Test
    public void table2_sulfurChlorides() {
        Erg.T2Row r = erg.table2For(erg.entry("1828", "Sulfur chlorides"));
        assertEquals(Arrays.asList("HCl", "SO2", "H2S"), r.gases);
    }

    @Test
    public void table3Page341_chlorine() {
        Erg.T3Gas g = erg.table3For("1017");
        assertEquals(341, g.page);
        Erg.T3Row rail = g.row("Rail tank car");
        assertEquals("1,000 m (3,000 ft)", rail.isolate.label());
        assertEquals("9.6 km (6.0 mi)", rail.day.get(Erg.WindSpeed.LOW).label());
        assertEquals("6.3 km (3.9 mi)", rail.day.get(Erg.WindSpeed.MODERATE).label());
        assertEquals("5.1 km (3.2 mi)", rail.day.get(Erg.WindSpeed.HIGH).label());
        assertEquals("11.0+ km (7.0+ mi)", rail.night.get(Erg.WindSpeed.LOW).label());
        assertEquals("8.9 km (5.6 mi)", rail.night.get(Erg.WindSpeed.MODERATE).label());
        assertEquals("6.5 km (4.1 mi)", rail.night.get(Erg.WindSpeed.HIGH).label());
        assertEquals(4, g.rows.size());
        assertNotNull(g.row("Multiple small cylinders or single ton cylinder"));
    }

    @Test
    public void table3Page341_ammonia() {
        Erg.T3Gas g = erg.table3For("1005");
        Erg.T3Row rail = g.row("Rail tank car");
        assertEquals("300 m (1,000 ft)", rail.isolate.label());
        assertEquals("1.6 km (1.0 mi)", rail.day.get(Erg.WindSpeed.LOW).label());
        assertEquals("4.1 km (2.6 mi)", rail.night.get(Erg.WindSpeed.LOW).label());
        assertNotNull(g.row("Agricultural nurse tank"));
    }

    @Test
    public void table3_covers_sixGases() {
        Set<String> ids = new HashSet<>();
        for (Erg.T3Gas g : erg.table3)
            for (String[] m : g.materials)
                ids.add(m[0]);
        assertEquals(new HashSet<>(Arrays.asList("1005", "1017", "1040", "1050",
                "2186", "1052", "1079")), ids);
    }

    @Test
    public void guide124Page178() {
        Erg.Guide g = erg.guide("124");
        assertEquals("Gases - Toxic and/or Corrosive - Oxidizing", g.title);
        assertEquals(178, g.page);
        assertEquals("section", g.blocks.get(0).type);
        assertEquals("POTENTIAL HAZARDS", g.blocks.get(0).html);
    }

    @Test
    public void guide112_evacuationDistances() {
        Erg.Guide g = erg.guide("112");
        Erg.GuideDistance im = g.under("Immediate precautionary measure").get(0);
        assertEquals(500, im.metres);
        assertEquals("1/3 mile", im.other);
        assertEquals("Isolate spill or leak area immediately for at least 500 meters (1/3 mile) in all directions.",
                im.text);
        assertEquals(800, g.under("Large Spill").get(0).metres);
        assertEquals(1600, g.under("Fire").get(0).metres);
    }

    @Test
    public void blankGuides() {
        assertTrue(erg.guide("121").blank);
        assertTrue(erg.guide("167").blank);
        assertFalse(erg.guide("111").blank);
    }

    @Test
    public void guideLookupIgnoresP() {
        assertEquals("119", erg.guide("119P").number);
    }

    @Test
    public void placards() {
        assertEquals("153", erg.placard("corrosive").guide);
        assertEquals("111", erg.placard("dangerous").guide);
        assertEquals("147", erg.placard("li_ion").guide);
    }

    @Test
    public void rulesPresent() {
        assertEquals("288", erg.rule("unknown_name").page);
        assertTrue(erg.rule("paz_square").text.contains("is a square"));
        assertEquals("back cover", erg.rule("initial_phase").page);
    }

    // ---------------- consistency properties ----------------

    @Test
    public void everyHighlightedEntryHasTable1Rows() {
        for (Erg.Entry e : erg.index)
            assertEquals(e.toString(), e.highlighted(), !erg.table1For(e).isEmpty());
    }

    @Test
    public void everyReferencedGuideExists() {
        for (Erg.Entry e : erg.index)
            assertNotNull(e.toString(), erg.guide(e.guide));
        for (Erg.Placard p : erg.placards)
            assertNotNull(p.key, erg.guide(p.guide));
    }

    @Test
    public void everyTable3ReferenceHasAGas() {
        for (Erg.T1Row r : erg.table1)
            if (r.largeTable3)
                assertNotNull(r.id, erg.table3For(r.id));
    }

    @Test
    public void table1DistancesAreSane() {
        for (Erg.T1Row r : erg.table1)
            for (Erg.T1Dist d : new Erg.T1Dist[] { r.small, r.large }) {
                if (d == null)
                    continue;
                assertTrue(r.name, d.isolate.m >= 30 && d.isolate.m <= 1000);
                // printed feet are rounded to round numbers (400 m is printed
                // as 1,250 ft); they stay within about 10 percent
                assertEquals(r.name, d.isolate.m, d.isolate.ft * Units.M_PER_FT,
                        d.isolate.m * 0.1 + 10);
                // HAZ-TAK draws the larger of the two
                assertTrue(d.isolate.radiusM() >= d.isolate.m
                        && d.isolate.radiusM() >= d.isolate.ft * Units.M_PER_FT);
                assertTrue(r.name, d.day.metres() > 0 && d.night.metres() > 0);
                // ERG: night distances are never shorter than day
                assertTrue(r.name, d.night.metres() >= d.day.metres() - 1e-6);
            }
    }

    @Test
    public void everyGuideHasItsSections() {
        for (Erg.Guide g : erg.guides.values()) {
            if (g.blank)
                continue;
            Set<String> heads = new HashSet<>();
            for (Erg.Block b : g.blocks)
                if (b.type.equals("head") || b.type.equals("section"))
                    heads.add(b.html);
            assertTrue(g.number, heads.containsAll(Arrays.asList("POTENTIAL HAZARDS",
                    "PUBLIC SAFETY", "EMERGENCY RESPONSE", "EVACUATION", "FIRST AID",
                    "PROTECTIVE CLOTHING", "SPILL OR LEAK")));
            assertFalse(g.number, g.under("Immediate precautionary measure").isEmpty());
        }
    }
}
