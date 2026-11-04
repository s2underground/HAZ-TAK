package com.atakmap.android.haztak.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class CodecTest {

    private final Erg erg = TestData.erg();

    private HazRecord chlorine() {
        Scenario s = Scenario.empty().material(erg.entry("1017", "Chlorine"))
                .withSpill(Scenario.Spill.LARGE).withFire(false)
                .withTimeOfDay(Scenario.TimeOfDay.NIGHT, true).withWindFrom(270)
                .withContainer("Rail tank car").withWindSpeed(Scenario.Wind.MODERATE);
        return HazRecord.create(erg, s, "ALPHA-1", 1793484000000L).withNumber(3);
    }

    @Test
    public void roundTrip() {
        HazRecord r = chlorine();
        HazRecord back = HazCodec.fromXml(HazCodec.toXml(r), erg);
        assertNotNull(back);
        assertEquals("1017", back.scenario.id);
        assertEquals("Chlorine", back.scenario.name);
        assertEquals(Scenario.Spill.LARGE, back.scenario.spill);
        assertEquals(Boolean.FALSE, back.scenario.fire);
        assertEquals(Scenario.TimeOfDay.NIGHT, back.scenario.tod);
        assertTrue(back.scenario.todFromSun);
        assertEquals(270, back.scenario.windFromDeg, 1e-9);
        assertEquals("Rail tank car", back.scenario.container);
        assertEquals(Scenario.Wind.MODERATE, back.scenario.windSpeed);
        assertEquals("ALPHA-1", back.byCallsign);
        assertEquals(1793484000000L, back.timeMillis);
        assertEquals(3, back.number);
        assertEquals(r.isoM, back.isoM, 0.05); // carried at 0.1 m
        assertEquals(r.pazM, back.pazM, 0.05);
        assertFalse(back.receivedMismatch);
        assertFalse(back.disagrees(erg));
        assertEquals("UN1017 Chlorine", back.label);
    }

    @Test
    public void guideOnlyAndNameUnknownRoundTrip() {
        Scenario g = Scenario.empty().guideOnly("128", "fuel")
                .withSpill(Scenario.Spill.LARGE).withFire(false).withWindFrom(45);
        HazRecord r = HazRecord.create(erg, g, "B", 1L);
        HazRecord back = HazCodec.fromXml(HazCodec.toXml(r), erg);
        assertEquals("128", back.scenario.guide);
        assertEquals("fuel", back.scenario.placardKey);
        assertEquals(r.dwM, back.dwM, 1e-9);
        Scenario u = Scenario.empty().idOnly("1057").withSpill(Scenario.Spill.SMALL)
                .withFire(false);
        HazRecord ru = HazCodec.fromXml(HazCodec.toXml(HazRecord.create(erg, u, "C", 2L)), erg);
        assertTrue(ru.scenario.nameUnknown());
        assertEquals("UN1057 (name not known)", ru.label);
    }

    @Test
    public void explosivesRowWithoutId() {
        Erg.Entry ex = null;
        for (Erg.Entry e : erg.byId(""))
            if (e.name.startsWith("Explosives, division 1.1"))
                ex = e;
        Scenario s = Scenario.empty().material(ex).withSpill(Scenario.Spill.SMALL)
                .withFire(false);
        HazRecord r = HazRecord.create(erg, s, "A", 1L);
        assertEquals("Explosives, division 1.1, 1.2, 1.3 or 1.5 (Guide 112)", r.label);
        HazRecord back = HazCodec.fromXml(HazCodec.toXml(r), erg);
        assertEquals("112", back.scenario.guide);
        assertEquals(ex.name, back.scenario.name);
        assertFalse(back.disagrees(erg));
    }

    @Test
    public void tamperedDistancesAreFlaggedAndIgnored() {
        Map<String, String> a = HazCodec.encode(chlorine());
        a.put("iso_m", "10");
        a.put("paz_m", "100");
        HazRecord r = HazCodec.decode(a, erg);
        assertTrue(r.receivedMismatch);
        assertTrue(r.disagrees(erg));
        assertEquals(10, r.isoM, 1e-9);        // the sender's value is kept...
        assertEquals(1000, r.zones(erg).isolation.radiusM, 1e-9); // ...this build's is drawn
        assertTrue(r.zones(erg).paz.downwindM > 9000);
        // audit: the warning must survive being stored and read back
        HazRecord stored = HazCodec.fromXml(HazCodec.toXml(r), erg);
        assertTrue(stored.disagrees(erg));
        assertEquals(1000, Drawing.shapes(stored, stored.zones(erg), 0, 0)
                .get(Drawing.shapes(stored, stored.zones(erg), 0, 0).size() - 1)
                .radiusM, 1e-9);
    }

    @Test
    public void newerOrUnknownNameDrawsTheSendersDistances() {
        Map<String, String> a = HazCodec.encode(chlorine());
        a.put("mat", "Chlorine (a name a newer edition might use)");
        HazRecord r = HazCodec.decode(a, erg);
        assertNotNull(r); // not ignored: the old record would stay on the map
        assertNull(r.zones(erg));
        assertTrue(r.disagrees(erg));
        assertEquals(1000, r.isoM, 0.05);
    }

    @Test
    public void absurdDistancesFromAnUnknownEditionAreRefused() {
        Map<String, String> a = HazCodec.encode(chlorine());
        a.put("ed", "ERG2024b");
        a.put("iso_m", "50000");
        assertNull(HazCodec.decode(a, erg));
        a.put("iso_m", "0.01");
        assertNull(HazCodec.decode(a, erg));
    }

    @Test
    public void controlCharactersAreDroppedFromXml() {
        assertEquals("AB", Xml.escape("A\u0001B"));
        assertEquals("A&#10;B", Xml.escape("A\nB"));
    }

    @Test
    public void unknownEditionUsesCarriedDistances() {
        Map<String, String> a = HazCodec.encode(chlorine());
        a.put("ed", "ERG2028");
        HazRecord r = HazCodec.decode(a, erg);
        assertNotNull(r);
        assertFalse(r.editionKnown(erg));
        assertNull(r.zones(erg));
        assertEquals(1000, r.isoM, 1e-9);
        // and it can still be drawn
        assertFalse(Drawing.shapes(r, null, 38.9, -77.0).isEmpty());
    }

    @Test
    public void rejectsUnusableData() {
        Map<String, String> a = new LinkedHashMap<>();
        assertNull(HazCodec.decode(a, erg));
        a.put("ed", "ERG2024");
        a.put("id", "12x4");
        assertNull(HazCodec.decode(a, erg));
        a.put("id", "1017");
        a.put("mat", "Not a real name");
        assertNull(HazCodec.decode(a, erg)); // known edition, inputs unusable
        a = HazCodec.encode(chlorine());
        a.put("ed", "ERG2028");
        a.put("iso_m", "1e9");
        assertNull(HazCodec.decode(a, erg)); // out of range, no fallback
    }

    @Test
    public void wireCodesAreFixedStrings() {
        Map<String, String> a = HazCodec.encode(chlorine());
        assertEquals("MODERATE", a.get("ws"));
        assertEquals("ACTIVE", a.get("st"));
        assertEquals("L", a.get("spill"));
        assertEquals("N", a.get("tod"));
        Scenario s = Scenario.empty().material(erg.entry("1836", "Thionyl chloride"))
                .withSpill(Scenario.Spill.SMALL).withFire(false)
                .withTimeOfDay(Scenario.TimeOfDay.DAY, false).withWindFrom(90)
                .withWater(Scenario.Water.UNKNOWN);
        assertEquals("UNKNOWN", HazCodec.encode(HazRecord.create(erg, s, "x", 1L)).get("wat"));
        assertEquals("CLEARED", HazCodec.encode(chlorine().cleared("B", 2L)).get("st"));
    }

    @Test
    public void keepsUnknownAttributes() {
        Map<String, String> a = HazCodec.encode(chlorine());
        a.put("future", "x");
        HazRecord r = HazCodec.decode(a, erg);
        assertEquals("x", HazCodec.encode(r).get("future"));
    }

    @Test
    public void escapesText() {
        Scenario s = Scenario.empty().material(erg.entry("1331", "Matches, \"strike anywhere\""))
                .withSpill(Scenario.Spill.SMALL).withFire(false);
        HazRecord r = HazRecord.create(erg, s, "A&B <c>", 5L);
        String xml = HazCodec.toXml(r);
        assertTrue(xml.contains("&quot;strike anywhere&quot;"));
        HazRecord back = HazCodec.fromXml(xml, erg);
        assertEquals("A&B <c>", back.byCallsign);
        assertEquals("Matches, \"strike anywhere\"", back.scenario.name);
    }

    @Test
    public void fuzzNeverThrows() {
        Random rnd = new Random(42);
        String[] keys = HazCodec.KNOWN;
        Map<String, String> base = HazCodec.encode(chlorine());
        for (int i = 0; i < 20000; i++) {
            Map<String, String> a = new LinkedHashMap<>(base);
            int n = rnd.nextInt(4);
            for (int k = 0; k < n; k++) {
                String key = keys[rnd.nextInt(keys.length)];
                if (rnd.nextBoolean())
                    a.remove(key);
                else
                    a.put(key, junk(rnd));
            }
            HazRecord r = HazCodec.decode(a, erg);
            if (r != null) {
                assertTrue(r.isoM > 0);
                HazCodec.toXml(r);
                Drawing.shapes(r, r.zones(erg), 10, 10);
                Labels.remarks(r, r.zones(erg));
            }
        }
    }

    private static String junk(Random r) {
        String[] pool = { "", "0", "-1", "NaN", "Infinity", "1e308", "270", "S", "L",
                "D", "N", "1", "LAND", "WATER", "UNKNOWN", "LOW", "?", "Rail tank car",
                "ERG2024", "1017", "Chlorine", "<x>", "\u0000\u0001", "999999999" };
        return pool[r.nextInt(pool.length)];
    }
}
