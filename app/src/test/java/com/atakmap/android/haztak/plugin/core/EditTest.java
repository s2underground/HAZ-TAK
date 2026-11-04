package com.atakmap.android.haztak.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 0.4: editing an incident (material, situation) and the edit trail. */
public class EditTest {

    private final Erg erg = TestData.erg();

    private HazRecord chlorine() {
        Scenario s = Scenario.empty().material(erg.entry("1017", "Chlorine"))
                .withSpill(Scenario.Spill.SMALL).withFire(false)
                .withTimeOfDay(Scenario.TimeOfDay.DAY, false).withWindFrom(270);
        return HazRecord.create(erg, s, "ALPHA", 1793484000000L).withNumber(4);
    }

    @Test
    public void editChangesMaterialLabelAndDistancesButKeepsIdentity() {
        HazRecord r = chlorine();
        Scenario ammonia = r.scenario.material(erg.entry("1005", "Ammonia, anhydrous"));
        HazRecord e = r.updated(erg, ammonia).edited("BRAVO", 1793487600000L);
        assertEquals("UN1005 Ammonia, anhydrous", e.label);
        assertEquals("ALPHA", e.byCallsign);
        assertEquals(r.timeMillis, e.timeMillis);
        assertEquals(4, e.number);
        assertEquals(Zones.compute(erg, ammonia).isolation.radiusM, e.isoM, 1e-9);
        assertTrue(e.wasEdited());
        assertEquals("BRAVO", e.editedBy());
        assertEquals(1793487600000L, e.editedMillis());
        assertFalse(r.wasEdited());
        assertTrue(Labels.provenance(e).contains("Edited by BRAVO"));
    }

    @Test
    public void editTrailSurvivesTheWire() {
        HazRecord e = chlorine().updated(erg, chlorine().scenario
                .withSpill(Scenario.Spill.LARGE).withContainer("Rail tank car")
                .withWindSpeed(Scenario.Wind.LOW)).edited("BRAVO", 1793487600000L);
        String xml = HazCodec.toXml(e);
        assertTrue(xml.contains("eby=\"BRAVO\""));
        HazRecord back = HazCodec.fromXml(xml, erg);
        assertEquals("BRAVO", back.editedBy());
        assertEquals(1793487600000L, back.editedMillis());
        assertEquals(Scenario.Spill.LARGE, back.scenario.spill);
        // the same detail encodes the same way (edit-conflict check compares it)
        assertEquals(xml, HazCodec.toXml(back));
        // a second edit replaces the trail, it doesn't add another
        HazRecord again = back.edited("CHARLIE", 1793491200000L);
        assertEquals("CHARLIE", again.editedBy());
        assertEquals(1, HazCodec.toXml(again).split("eby=").length - 1);
    }

    @Test
    public void defaultNameFollowsTheMaterial() {
        HazRecord r = chlorine();
        assertEquals("HAZ 4 UN1017 Chlorine", Names.initial(4, r.label));
        HazRecord e = r.updated(erg, r.scenario.material(erg.entry("1005",
                "Ammonia, anhydrous")));
        assertEquals("HAZ 4 UN1005 Ammonia, anhydrous", Names.initial(4, e.label));
    }

    @Test
    public void editRemovingTheSquareDropsItsShape() {
        HazRecord r = chlorine();
        assertTrue(hasSuffix(r, Drawing.PAZ));
        // edited to a non-green material: the square goes, the arrow appears
        HazRecord e = r.updated(erg, r.scenario.material(erg.entry("1830", "Sulfuric acid")));
        assertFalse(hasSuffix(e, Drawing.PAZ));
        assertTrue(hasSuffix(e, Drawing.WIND));
    }

    private boolean hasSuffix(HazRecord r, String sfx) {
        for (Drawing.Shape s : Drawing.shapes(r, r.zones(erg), 38.9, -77.0))
            if (s.suffix.equals(sfx))
                return true;
        return false;
    }

    @Test
    public void changeKeepsTheSituationAnswers() {
        Scenario s = chlorine().scenario.withDoubled(true);
        Scenario u = s.unidentified();
        assertFalse(u.identified());
        assertEquals(s.spill, u.spill);
        assertEquals(s.fire, u.fire);
        assertEquals(s.tod, u.tod);
        assertEquals(270, u.windFromDeg, 0);
        assertTrue(u.doubled);
        assertEquals(null, u.container);
    }

    @Test
    public void conflictCheckIgnoresAttributeOrder() {
        HazRecord e = chlorine().edited("BRAVO", 1793487600000L);
        // a receiver's CotDetail keeps attributes in hash order
        java.util.Map<String, String> shuffled = new java.util.HashMap<>(HazCodec.encode(e));
        StringBuilder sb = new StringBuilder("<").append(HazCodec.DETAIL);
        java.util.List<String> keys = new java.util.ArrayList<>(shuffled.keySet());
        java.util.Collections.reverse(keys);
        for (String k : keys)
            sb.append(' ').append(k).append("=\"").append(Xml.escape(shuffled.get(k))).append('"');
        sb.append("/>");
        HazRecord back = HazCodec.fromXml(sb.toString(), erg);
        assertEquals(HazCodec.encode(e), HazCodec.encode(back));
    }
}
