package com.atakmap.android.haztak.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/** The guidebook's rules for choosing distances (ERG2024 pp. 28, 280-289). */
public class ZonesTest {

    private static final double FT = Units.M_PER_FT;
    private static final double MI = Units.M_PER_MI;
    private final Erg erg = TestData.erg();

    private Scenario mat(String id, String name) {
        Erg.Entry e = erg.entry(id, name);
        assertNotNull(id + " " + name, e);
        return Scenario.empty().material(e);
    }

    private static Scenario ready(Scenario s, Scenario.Spill spill, boolean fire,
            Scenario.TimeOfDay tod) {
        return s.withSpill(spill).withFire(fire).withTimeOfDay(tod, false)
                .withWindFrom(270);
    }

    @Test
    public void nothingIdentified() {
        Zones z = Zones.compute(erg, Scenario.empty());
        assertEquals(Arrays.asList(Zones.Need.IDENTIFY), z.missing);
        assertFalse(z.usable());
    }

    @Test
    public void spillAndFireAreAlwaysRequired() {
        Zones z = Zones.compute(erg, mat("1203", "Gasoline"));
        assertEquals(Arrays.asList(Zones.Need.SPILL, Zones.Need.FIRE), z.missing);
    }

    @Test
    public void highlightedNeedsTimeOfDayAndWind() {
        Zones z = Zones.compute(erg, mat("1017", "Chlorine")
                .withSpill(Scenario.Spill.SMALL).withFire(false));
        assertEquals(Arrays.asList(Zones.Need.TIME_OF_DAY, Zones.Need.WIND_DIRECTION),
                z.missing);
    }

    @Test
    public void chlorineSmallDay_table1() {
        Zones z = Zones.compute(erg, ready(mat("1017", "Chlorine"),
                Scenario.Spill.SMALL, false, Scenario.TimeOfDay.DAY));
        assertTrue(z.usable());
        assertEquals(Zones.Basis.TABLE1, z.basis);
        assertEquals("60 m (200 ft)", z.isolation.label);
        // larger of 60 m and 200 ft
        assertEquals(200 * FT, z.isolation.radiusM, 1e-9);
        assertEquals("0.3 km (0.2 mi)", z.paz.label);
        assertEquals(0.2 * MI, z.paz.downwindM, 1e-9);
        assertTrue(z.paz.cite.contains("Table 1") && z.paz.cite.contains("p. 290"));
        assertTrue(z.rings.isEmpty());
        assertNull(z.downwind);
    }

    @Test
    public void chlorineSmallNight() {
        Zones z = Zones.compute(erg, ready(mat("1017", "Chlorine"),
                Scenario.Spill.SMALL, false, Scenario.TimeOfDay.NIGHT));
        assertEquals("1.5 km (0.9 mi)", z.paz.label);
        assertEquals(1500, z.paz.downwindM, 1e-9); // 1.5 km > 0.9 mi
    }

    @Test
    public void chlorineLarge_needsContainerAndWindSpeed() {
        Zones z = Zones.compute(erg, ready(mat("1017", "Chlorine"),
                Scenario.Spill.LARGE, false, Scenario.TimeOfDay.NIGHT));
        assertEquals(Arrays.asList(Zones.Need.CONTAINER, Zones.Need.WIND_SPEED),
                z.missing);
    }

    @Test
    public void chlorineLarge_table3RailModerateNight() {
        Zones z = Zones.compute(erg, ready(mat("1017", "Chlorine"),
                Scenario.Spill.LARGE, false, Scenario.TimeOfDay.NIGHT)
                .withContainer("Rail tank car").withWindSpeed(Scenario.Wind.MODERATE));
        assertTrue(z.usable());
        assertEquals(Zones.Basis.TABLE3, z.basis);
        assertEquals("1,000 m (3,000 ft)", z.isolation.label);
        assertEquals(1000, z.isolation.radiusM, 1e-9);
        assertEquals("8.9 km (5.6 mi)", z.paz.label);
        assertEquals(5.6 * MI, z.paz.downwindM, 1e-6);
        assertTrue(z.paz.cite.contains("Table 3") && z.paz.cite.contains("Rail tank car"));
        assertTrue(z.rules.contains("more_than_one_package"));
    }

    @Test
    public void chlorineLarge_unknownContainerAndWind_largest() {
        Zones z = Zones.compute(erg, ready(mat("1017", "Chlorine"),
                Scenario.Spill.LARGE, false, Scenario.TimeOfDay.NIGHT)
                .withContainer(Scenario.CONTAINER_UNKNOWN)
                .withWindSpeed(Scenario.Wind.UNKNOWN));
        assertEquals("1,000 m (3,000 ft)", z.isolation.label);
        assertEquals("11.0+ km (7.0+ mi)", z.paz.label);
        assertTrue(z.paz.more);
        assertTrue(z.rules.contains("plus_distance"));
        assertFalse(z.notes.isEmpty());
    }

    @Test
    public void ammoniaSmallUsesTable1_notTable3() {
        Zones z = Zones.compute(erg, ready(mat("1005", "Ammonia, anhydrous"),
                Scenario.Spill.SMALL, false, Scenario.TimeOfDay.DAY));
        assertEquals(Zones.Basis.TABLE1, z.basis);
        assertEquals("30 m (100 ft)", z.isolation.label);
        assertEquals("0.1 km (0.1 mi)", z.paz.label);
        assertEquals(0.1 * MI, z.paz.downwindM, 1e-9);
    }

    @Test
    public void ammoniaLargeAgriculturalNurseTank() {
        Zones z = Zones.compute(erg, ready(mat("1005", "Anhydrous ammonia"),
                Scenario.Spill.LARGE, false, Scenario.TimeOfDay.DAY)
                .withContainer("Agricultural nurse tank").withWindSpeed(Scenario.Wind.LOW));
        assertEquals("60 m (200 ft)", z.isolation.label);
        assertEquals("0.5 km (0.3 mi)", z.paz.label);
    }

    @Test
    public void waterReactive_asksLandOrWater() {
        Zones z = Zones.compute(erg, ready(mat("1836", "Thionyl chloride"),
                Scenario.Spill.LARGE, false, Scenario.TimeOfDay.NIGHT));
        assertEquals(Arrays.asList(Zones.Need.WATER), z.missing);
    }

    @Test
    public void waterReactive_water_land_unknown() {
        Scenario base = ready(mat("1836", "Thionyl chloride"),
                Scenario.Spill.LARGE, false, Scenario.TimeOfDay.DAY);
        Zones water = Zones.compute(erg, base.withWater(Scenario.Water.WATER));
        assertEquals("7.6 km (4.7 mi)", water.paz.label);
        Zones land = Zones.compute(erg, base.withWater(Scenario.Water.LAND));
        assertTrue(land.paz.downwindM < water.paz.downwindM);
        Zones unk = Zones.compute(erg, base.withWater(Scenario.Water.UNKNOWN));
        assertEquals(water.paz.downwindM, unk.paz.downwindM, 1e-9);
        assertTrue(unk.isolation.radiusM >= land.isolation.radiusM);
        assertTrue(unk.rules.contains("land_water_larger"));
    }

    @Test
    public void waterOnlyEntry_onLand_usesGuide() {
        // Table 1 lists it only "(when spilled in water)": on land, Table 1
        // and Table 2 do not apply; the guide's distances are used (p. 28).
        Zones z = Zones.compute(erg, mat("1162", "Dimethyldichlorosilane")
                .withSpill(Scenario.Spill.SMALL).withFire(false)
                .withWater(Scenario.Water.LAND));
        assertTrue(z.usable());
        assertEquals(Zones.Basis.GUIDE, z.basis);
        assertNull(z.paz);
        assertTrue(z.rules.contains("water_only_entry"));
        assertEquals(Arrays.asList("155"), z.guides);
    }

    @Test
    public void notHighlighted_gasolineSmall() {
        Zones z = Zones.compute(erg, mat("1203", "Gasoline")
                .withSpill(Scenario.Spill.SMALL).withFire(false));
        assertTrue(z.usable());
        assertEquals(Zones.Basis.GUIDE, z.basis);
        assertEquals("50 meters (150 feet)", z.isolation.label);
        assertEquals(50, z.isolation.radiusM, 1e-9); // 50 m > 150 ft
        assertNull(z.paz);
        assertNull(z.downwind);
        assertFalse(z.needsWind);
    }

    @Test
    public void notHighlighted_gasolineLarge_downwindLine() {
        Zones z = Zones.compute(erg, mat("1203", "Gasoline")
                .withSpill(Scenario.Spill.LARGE).withFire(false));
        assertNotNull(z.downwind);
        assertEquals("300 meters (1000 feet)", z.downwind.label);
        assertEquals(1000 * FT, z.downwind.lengthM, 1e-9);
        assertTrue(z.needsWind);
        assertTrue(z.usable()); // wind is optional for a guide-only line
    }

    @Test
    public void notHighlighted_gasolineFire() {
        Zones z = Zones.compute(erg, mat("1203", "Gasoline")
                .withSpill(Scenario.Spill.LARGE).withFire(true));
        assertEquals("Isolation (fire)", z.isolation.title);
        assertEquals("800 meters (1/2 mile)", z.isolation.label);
        assertEquals(0.5 * MI, z.isolation.radiusM, 1e-9);
        // fire evacuation is the same 800 m: no second ring
        assertTrue(z.rings.isEmpty());
    }

    @Test
    public void highlightedWithFire_guideFireDistanceIsIsolation() {
        // "the fire hazard distance should be used as an isolation distance
        // and Table 1 should be used to protect downwind" (p. 280)
        Zones z = Zones.compute(erg, ready(mat("1017", "Chlorine"),
                Scenario.Spill.SMALL, true, Scenario.TimeOfDay.DAY));
        assertEquals("Isolation (fire)", z.isolation.title);
        assertEquals("800 meters (1/2 mile)", z.isolation.label);
        assertEquals("0.3 km (0.2 mi)", z.paz.label);
        assertTrue(z.rules.contains("fire_rule"));
    }

    @Test
    public void placardExplosives_guide112() {
        Scenario s = Scenario.empty().guideOnly("112", "exp_1_1");
        Zones small = Zones.compute(erg, s.withSpill(Scenario.Spill.SMALL).withFire(false));
        assertEquals("500 meters (1/3 mile)", small.isolation.label);
        assertEquals(MI / 3, small.isolation.radiusM, 1e-9);
        Zones large = Zones.compute(erg, s.withSpill(Scenario.Spill.LARGE).withFire(false));
        assertEquals(1, large.rings.size());
        assertEquals("800 meters (1/2 mile)", large.rings.get(0).label);
        Zones fire = Zones.compute(erg, s.withSpill(Scenario.Spill.LARGE).withFire(true));
        assertEquals("1600 meters (1 mile)", fire.isolation.label);
        assertEquals(MI, fire.isolation.radiusM, 1e-9);
        assertTrue(fire.rings.isEmpty());
    }

    @Test
    public void unknownCargo_guide111() {
        Zones z = Zones.compute(erg, Scenario.empty().guideOnly("111", "unknown")
                .withSpill(Scenario.Spill.SMALL).withFire(false));
        assertEquals("100 meters (330 feet)", z.isolation.label);
        assertEquals(330 * FT, z.isolation.radiusM, 1e-9);
    }

    @Test
    public void nameUnknown_usesLargestAcrossEntries() {
        // UN1057: lighters (Guide 115, 100 m) and non-pressurized lighters
        // (Guide 128, 50 m)
        Zones z = Zones.compute(erg, Scenario.empty().idOnly("1057")
                .withSpill(Scenario.Spill.SMALL).withFire(false));
        assertTrue(z.usable());
        assertEquals(Arrays.asList("115", "128"), z.guides);
        assertEquals("100 meters (330 feet)", z.isolation.label);
        assertTrue(z.rules.contains("unknown_name"));
    }

    @Test
    public void nameUnknown_highlighted_largestTable1Entry() {
        Zones one = Zones.compute(erg, ready(mat("1053", "Hydrogen sulfide"),
                Scenario.Spill.LARGE, false, Scenario.TimeOfDay.NIGHT));
        Zones any = Zones.compute(erg, ready(Scenario.empty().idOnly("1053"),
                Scenario.Spill.LARGE, false, Scenario.TimeOfDay.NIGHT));
        assertEquals(one.paz.downwindM, any.paz.downwindM, 1e-9);
        assertEquals("6.3 km (4.0 mi)", any.paz.label);
    }

    @Test
    public void doubledForCatastrophicRelease() {
        Zones z = Zones.compute(erg, ready(mat("1017", "Chlorine"),
                Scenario.Spill.SMALL, false, Scenario.TimeOfDay.DAY).withDoubled(true));
        assertEquals(2 * 200 * FT, z.isolation.radiusM, 1e-9);
        assertEquals(2 * 0.2 * MI, z.paz.downwindM, 1e-9);
        assertTrue(z.isolation.label.endsWith("x2"));
        assertTrue(z.rules.contains("double_catastrophic"));
    }

    @Test
    public void everyIndexEntryProducesUsableZones() {
        // Exhaustive: every material, both spill sizes, fire or not, all
        // inputs answered (unknown where offered).
        for (Erg.Entry e : erg.index) {
            for (Scenario.Spill sp : Scenario.Spill.values())
                for (boolean fire : new boolean[] { false, true }) {
                    Scenario s = Scenario.empty().material(e).withSpill(sp)
                            .withFire(fire).withTimeOfDay(Scenario.TimeOfDay.NIGHT, false)
                            .withWindFrom(0).withWater(Scenario.Water.UNKNOWN)
                            .withContainer(Scenario.CONTAINER_UNKNOWN)
                            .withWindSpeed(Scenario.Wind.UNKNOWN);
                    Zones z = Zones.compute(erg, s);
                    if (erg.guide(e.guide).blank)
                        continue;
                    assertTrue(e + " " + sp + " " + fire + " " + z.missing + " " + z.notes,
                            z.usable());
                    assertTrue(e.toString(), z.isolation.radiusM > 0);
                    assertEquals(e.toString(), e.highlighted(), z.paz != null);
                    // without fire the downwind distance is never shorter than
                    // half the isolation distance (with fire, the guide's fire
                    // isolation can be much larger than a small-spill square)
                    if (z.paz != null && !fire)
                        assertTrue(e.toString(), z.paz.downwindM >= z.isolation.radiusM * 0.5);
                }
        }
    }

    @Test
    public void everyPlacardProducesUsableZones() {
        for (Erg.Placard p : erg.placards) {
            Zones z = Zones.compute(erg, Scenario.empty().guideOnly(p.guide, p.key)
                    .withSpill(Scenario.Spill.LARGE).withFire(true));
            assertTrue(p.key, z.usable());
        }
    }
}
