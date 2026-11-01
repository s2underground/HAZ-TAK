package com.atakmap.android.haztak.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** Names, remarks, search and units. */
public class TextTest {

    private final Erg erg = TestData.erg();

    @Test
    public void names() {
        assertEquals("HAZ 3 UN1017 Chlorine", Names.initial(3, "UN1017 Chlorine"));
        assertTrue(Names.initial(1, "UN1234 A very long material name that goes on and on")
                .endsWith("..."));
        assertEquals(3, Names.numberIn("HAZ 3 UN1017 Chlorine"));
        assertEquals(0, Names.numberIn("BLAST 3 Car"));
        assertEquals("HAZ 3 X CLEARED", Names.withStatus("HAZ 3 X", true));
        assertEquals("HAZ 3 X", Names.withStatus("HAZ 3 X CLEARED", false));
        assertNull(Names.sanitize("  \u0001 "));
        assertEquals("HAZ_3_UN1017_Chlorine", Names.fileStem("HAZ 3 UN1017 Chlorine"));
        assertEquals("HAZ", Names.fileStem("///"));
    }

    @Test
    public void remarksKeepUserText() {
        Scenario s = Scenario.empty().material(erg.entry("1203", "Gasoline"))
                .withSpill(Scenario.Spill.SMALL).withFire(false);
        HazRecord r = HazRecord.create(erg, s, "A", 1L);
        String block = Labels.remarks(r, r.zones(erg));
        assertTrue(block.startsWith(Labels.REMARKS_PREFIX));
        assertTrue(block.endsWith(Labels.REMARKS_END));
        assertTrue(block.contains("50 meters (150 feet)"));
        String mine = "Driver says tanker was full.";
        String merged = Labels.mergeRemarks(mine + "\n" + block + "\nMore notes", block);
        assertTrue(merged.startsWith(mine));
        assertTrue(merged.endsWith("More notes"));
        assertEquals(block + "\n\n" + mine, Labels.mergeRemarks(mine, block));
    }

    @Test
    public void searchById() {
        assertEquals("1017", Search.cleanId("UN 1017"));
        assertEquals("1993", Search.cleanId("na1993"));
        assertNull(Search.cleanId("101"));
        List<Erg.Entry> r = Search.byId(erg, "UN1075");
        assertTrue(r.size() >= 9);
        assertTrue(Search.byId(erg, "0000").isEmpty());
    }

    @Test
    public void searchByName() {
        List<Erg.Entry> r = Search.byName(erg, "ammonia anhydrous");
        assertEquals("Ammonia, anhydrous", r.get(0).name);
        assertEquals("Chlorine", Search.byName(erg, "chlorine").get(0).name);
        assertEquals("Gasoline", Search.byName(erg, "GASOLINE").get(0).name);
        assertTrue(Search.byName(erg, "x").isEmpty());
        assertTrue(Search.byName(erg, "sulf").size() <= Search.MAX_RESULTS);
    }

    @Test
    public void units() {
        assertEquals(100.584, Units.otherToMetres("330 feet"), 1e-9);
        assertEquals(804.672, Units.otherToMetres("1/2 mile"), 1e-9);
        assertEquals(1609.344, Units.otherToMetres("1 mile"), 1e-9);
        assertEquals(536.448, Units.otherToMetres("1/3 mile"), 1e-9);
        assertTrue(Double.isNaN(Units.otherToMetres("about a mile")));
    }

    @Test
    public void formats() {
        assertEquals("270° (W)", Formats.bearing(270));
        assertEquals("0° (N)", Formats.bearing(360));
        assertEquals("NNE", Formats.compass(20));
        assertEquals("1,000", Formats.thousands(1000));
        assertEquals("164 ft (50 m)", Formats.measured(50));
        assertEquals("7.0 mi (11.3 km)", Formats.measured(7 * Units.M_PER_MI));
    }
}
