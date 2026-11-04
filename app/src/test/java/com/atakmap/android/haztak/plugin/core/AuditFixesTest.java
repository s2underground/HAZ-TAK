package com.atakmap.android.haztak.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/** 0.5: fixes from the pre-release audit (Nov 2026). */
public class AuditFixesTest {

    private final Erg erg = TestData.erg();
    private static final double LAT = 38.9, LON = -77.0;

    private Scenario guide(String g) {
        return Scenario.empty().guideOnly(g, null);
    }

    // Guide 113 p. 157: "Stop all traffic and clear the area for at least
    // 1600 meters (1 mile) in all directions and let burn."
    @Test
    public void guide113CargoFireClearsTheArea1600m() {
        Zones z = Zones.compute(erg, Scenario.empty().idOnly("1310")
                .withSpill(Scenario.Spill.LARGE).withFire(true));
        assertTrue(z.usable());
        double biggest = z.isolation.radiusM;
        Zones.Circle cargo = null;
        for (Zones.Circle c : z.rings) {
            biggest = Math.max(biggest, c.radiusM);
            if (c.title.startsWith("Fire reaches cargo"))
                cargo = c;
        }
        assertNotNull(cargo);
        assertTrue(biggest >= 1600);
        assertTrue(cargo.atLeast);
        assertTrue(cargo.cite.contains("EMERGENCY RESPONSE") && cargo.cite.contains("p. 157"));
        assertEquals(804.672, z.isolation.radiusM, 1e-3); // 1/2 mile > 800 m
    }

    @Test
    public void everyGuideFireDistanceIsCovered() {
        // no fire case draws less than any all-directions fire distance in its guide
        for (Erg.Guide g : erg.guides.values()) {
            if (g.distances.isEmpty())
                continue;
            for (Scenario.Spill sp : Scenario.Spill.values()) {
                Zones z = Zones.compute(erg, guide(g.number).withSpill(sp).withFire(true));
                if (!z.usable())
                    continue;
                double biggest = z.isolation.radiusM;
                for (Zones.Circle c : z.rings)
                    biggest = Math.max(biggest, c.radiusM);
                for (Erg.GuideDistance d : g.distances)
                    if ("Fire".equals(d.sub) && d.allDirections()
                            && !"other".equals(d.action))
                        assertTrue("guide " + g.number + " " + d.text,
                                biggest + 0.5 >= d.radiusM());
            }
        }
    }

    // Guide 112: "ISOLATE for 1600 meters ...; also, initiate evacuation
    // including emergency responders for 1600 meters ..."
    @Test
    public void sameFireIsolationAndEvacuationSayBoth() {
        Scenario s = guide("112").withSpill(Scenario.Spill.LARGE).withFire(true);
        Zones z = Zones.compute(erg, s);
        assertTrue(z.isolation.evacuate);
        assertTrue(z.isolation.responders);
        HazRecord r = HazRecord.create(erg, s, "A", 1L);
        String t = Labels.isolationTitle(r, z.isolation);
        assertTrue(t, t.startsWith("ISOLATE AND EVACUATE 1600 meters"));
        assertTrue(t, t.contains("INCLUDING RESPONDERS"));
    }

    @Test
    public void guideWordingAtLeastIsKept() {
        Zones z = Zones.compute(erg, guide("128").withSpill(Scenario.Spill.LARGE)
                .withFire(false));
        assertTrue(z.isolation.atLeast);
        assertEquals("at least 50 meters (150 feet)", Labels.distance(z.isolation));
        assertTrue(z.downwind.atLeast);
        HazRecord r = HazRecord.create(erg, guide("128").withSpill(Scenario.Spill.LARGE)
                .withFire(false), "A", 1L);
        assertTrue(Labels.lineTitle(r, z.downwind).contains("AT LEAST 300 meters"));
        // Table 1 distances are not "at least"
        Zones c = Zones.compute(erg, Scenario.empty().material(erg.entry("1017", "Chlorine"))
                .withSpill(Scenario.Spill.SMALL).withFire(false)
                .withTimeOfDay(Scenario.TimeOfDay.DAY, false).withWindFrom(0));
        assertFalse(c.isolation.atLeast);
    }

    @Test
    public void downwindIncreaseSentenceIsShown() {
        Zones z = Zones.compute(erg, guide("153").withSpill(Scenario.Spill.SMALL)
                .withFire(false));
        assertTrue(z.notes.toString(), z.notes.toString().contains(
                "in the downwind direction, as necessary"));
        Zones g128 = Zones.compute(erg, guide("128").withSpill(Scenario.Spill.SMALL)
                .withFire(false));
        assertFalse(g128.notes.toString().contains("as necessary"));
    }

    // ------------------------------------------------------------------
    // Position check

    @Test
    public void downwindEvacuationCountsAsInside() {
        Scenario s = guide("128").withSpill(Scenario.Spill.LARGE).withFire(false)
                .withWindFrom(270);
        Zones z = Zones.compute(erg, s);
        double[] p = Geo.destination(LAT, LON, 90, 200); // 200 m due east, downwind
        PositionCheck.Result r = PositionCheck.check(z, LAT, LON, 270, p[0], p[1], 5);
        assertEquals(PositionCheck.Where.DOWNWIND, r.where);
        double[] up = Geo.destination(LAT, LON, 270, 200); // upwind
        assertEquals(PositionCheck.Where.OUTSIDE,
                PositionCheck.check(z, LAT, LON, 270, up[0], up[1], 5).where);
        // no wind entered: anywhere within the distance is "possibly"
        Zones nz = Zones.compute(erg, s.withWindFrom(Double.NaN));
        assertEquals(PositionCheck.Where.POSSIBLE,
                PositionCheck.check(nz, LAT, LON, Double.NaN, up[0], up[1], 5).where);
        int[] n = PositionCheck.count(z, LAT, LON, 270, new double[][] { p, up });
        assertEquals(1, n[PositionCheck.Where.DOWNWIND.ordinal()]);
        assertEquals(1, n[PositionCheck.Where.OUTSIDE.ordinal()]);
    }

    @Test
    public void squareCornersReadBackInside() {
        // the drawn corner must be inside (or on) the checked square
        for (double lat : new double[] { 0, 40, 70 }) {
            double side = 11265 * 2;
            double[][] sq = Geometry.square(lat, LON, 270, side);
            for (double[] c : new double[][] { sq[1], sq[2] }) {
                double[] rel = Geometry.relative(lat, LON, 270, c[0], c[1]);
                assertTrue("lat " + lat + " " + rel[1] + "," + rel[2],
                        Math.abs(Math.abs(rel[2]) - side / 2) < 1.0
                                && Math.abs(rel[1] - side) < 1.0);
            }
        }
    }

    // ------------------------------------------------------------------
    // Wind not known yet

    private Scenario chlorineNoWind() {
        return Scenario.empty().material(erg.entry("1017", "Chlorine"))
                .withSpill(Scenario.Spill.SMALL).withFire(false)
                .withTimeOfDay(Scenario.TimeOfDay.NIGHT, false);
    }

    @Test
    public void windLaterDrawsIsolationAndSaysTheSquareIsMissing() {
        Zones blocked = Zones.compute(erg, chlorineNoWind());
        assertTrue(blocked.missing.contains(Zones.Need.WIND_DIRECTION));
        Scenario s = chlorineNoWind().withWindLater(true);
        Zones z = Zones.compute(erg, s);
        assertTrue(z.usable());
        assertNotNull(z.paz);
        assertTrue(z.notes.toString().contains("PROTECTIVE ACTION ZONE NOT DRAWN"));
        HazRecord r = HazRecord.create(erg, s, "A", 1L);
        List<Drawing.Shape> sh = Drawing.shapes(r, r.zones(erg), LAT, LON);
        assertEquals(1, sh.size());
        assertTrue(sh.get(0).title.contains("PROTECTIVE ACTION ZONE NOT DRAWN"));
        // within the square's reach in any direction: "possibly"
        double[] p = Geo.destination(LAT, LON, 180, 1000);
        assertEquals(PositionCheck.Where.POSSIBLE,
                PositionCheck.check(z, LAT, LON, Double.NaN, p[0], p[1], 5).where);
        // entering the wind clears the flag and draws the square
        Scenario w = s.withWindFrom(270);
        assertFalse(w.windLater);
        HazRecord rw = HazRecord.create(erg, w, "A", 1L);
        assertEquals(2, Drawing.shapes(rw, rw.zones(erg), LAT, LON).size());
    }

    @Test
    public void windLaterSurvivesTheWire() {
        HazRecord r = HazRecord.create(erg, chlorineNoWind().withWindLater(true), "A", 1L);
        String xml = HazCodec.toXml(r);
        assertTrue(xml.contains("wl=\"1\""));
        HazRecord back = HazCodec.fromXml(xml, erg);
        assertNotNull(back);
        assertTrue(back.scenario.windLater);
        assertNotNull(back.zones(erg).paz);
    }

    @Test
    public void spellingSuggestions() {
        assertTrue(Search.suggest(erg, "amonia", 5).toString(),
                Search.suggest(erg, "amonia", 5).toString().contains("Ammonia"));
        assertTrue(Search.suggest(erg, "gasolene", 5).contains("Gasoline"));
        assertTrue(Search.suggest(erg, "chlorne", 5).contains("Chlorine"));
        assertTrue(Search.suggest(erg, "h2s", 5).isEmpty()); // too short to guess
        assertTrue(Search.suggest(erg, "zzzzzzzz", 5).isEmpty());
    }

    @Test
    public void guide140FireNoteSaysWhoseDistanceItIs() {
        Zones z = Zones.compute(erg, guide("140").withSpill(Scenario.Spill.SMALL)
                .withFire(true));
        assertTrue(z.notes.toString().contains("ammonium nitrate"));
    }

    @Test
    public void numbersAreMetresFirst() {
        assertEquals("200 m (656 ft)", Formats.measured(200));
    }

    // regression review of 0.5
    @Test
    public void rejectedOrNewerSenderStaysFlaggedAfterStorage() {
        HazRecord r = HazRecord.create(erg, chlorineNoWind().withWindFrom(270), "A", 1L);
        java.util.Map<String, String> a = HazCodec.encode(r);
        a.put("iso_m", "0.5");
        HazRecord got = HazCodec.decode(a, erg);
        assertTrue(got.disagrees(erg));
        assertTrue(HazCodec.fromXml(HazCodec.toXml(got), erg).disagrees(erg));
        // editing on this device replaces the sender's numbers
        assertFalse(got.updated(erg, got.scenario).disagrees(erg));

        java.util.Map<String, String> b = HazCodec.encode(r);
        b.put("v", "99");
        HazRecord newer = HazCodec.decode(b, erg);
        assertTrue(newer.disagrees(erg));
        assertTrue(HazCodec.fromXml(HazCodec.toXml(newer), erg).disagrees(erg));
    }
}
