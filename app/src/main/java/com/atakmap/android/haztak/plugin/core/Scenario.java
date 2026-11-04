package com.atakmap.android.haztak.plugin.core;

/**
 * Everything the user chose for one incident. Immutable; the pane builds a
 * new one for every change. Nothing is pre-selected except the time of day,
 * which is filled in from the sun and can be changed.
 *
 * Identification is one of:
 * <ul>
 * <li>a specific index entry (ID number and material name);</li>
 * <li>an ID number whose exact name isn't known: every entry with that ID is
 * a candidate and the largest distances are used (ERG2024 p. 288);</li>
 * <li>a guide only (from a placard, Guide 111 for unknown cargo, or one of
 * the explosives index rows, which have no ID number and refer to Guide 112
 * or 114 by division; ERG2024 p. 28 note 2).</li>
 * </ul>
 */
public final class Scenario {

    public enum Spill {
        SMALL, LARGE
    }

    public enum TimeOfDay {
        DAY, NIGHT
    }

    /** Where a water-reactive material was spilled. */
    public enum Water {
        LAND, WATER, UNKNOWN
    }

    /** Table 3 wind speed; UNKNOWN uses the largest of the three. */
    public enum Wind {
        LOW, MODERATE, HIGH, UNKNOWN
    }

    /** Table 3 container that isn't known: the largest row is used. */
    public static final String CONTAINER_UNKNOWN = "?";

    // identification
    public final String id;          // "" when identified by guide only
    public final String name;        // exact material name; null = name not known (largest)
    public final String guide;       // guide number when identified by guide only
    public final String placardKey;  // placard used, if any
    // situation
    public final Spill spill;        // null = not chosen
    public final Boolean fire;       // null = not chosen
    public final TimeOfDay tod;      // null = not set
    public final boolean todFromSun;
    public final double windFromDeg; // NaN = not set
    public final Water water;        // null = not chosen
    public final String container;   // Table 3 container label, CONTAINER_UNKNOWN, or null
    public final Wind windSpeed;     // null = not chosen
    public final boolean doubled;
    /**
     * The user said the wind direction isn't known yet: the isolation circle
     * can be drawn now, the downwind zones only once the wind is entered.
     */
    public final boolean windLater;    // catastrophic/instantaneous release: distances x2

    public Scenario(String id, String name, String guide, String placardKey,
            Spill spill, Boolean fire, TimeOfDay tod, boolean todFromSun,
            double windFromDeg, Water water, String container, Wind windSpeed,
            boolean doubled) {
        this(id, name, guide, placardKey, spill, fire, tod, todFromSun,
                windFromDeg, water, container, windSpeed, doubled, false);
    }

    public Scenario(String id, String name, String guide, String placardKey,
            Spill spill, Boolean fire, TimeOfDay tod, boolean todFromSun,
            double windFromDeg, Water water, String container, Wind windSpeed,
            boolean doubled, boolean windLater) {
        this.id = id == null ? "" : id;
        this.name = name;
        this.guide = guide;
        this.placardKey = placardKey;
        this.spill = spill;
        this.fire = fire;
        this.tod = tod;
        this.todFromSun = todFromSun;
        this.windFromDeg = windFromDeg;
        this.water = water;
        this.container = container;
        this.windSpeed = windSpeed;
        this.doubled = doubled;
        // "wind not known yet" only means something while there is no wind
        this.windLater = windLater && Double.isNaN(windFromDeg);
    }

    public static Scenario empty() {
        return new Scenario("", null, null, null, null, null, null, false,
                Double.NaN, null, null, null, false);
    }

    /** Identified by a specific index entry. */
    public Scenario material(Erg.Entry e) {
        if (e.id.isEmpty()) // explosives rows: guide by division, no ID
            return new Scenario("", e.name, e.guideNumber(), null, spill, fire,
                    tod, todFromSun, windFromDeg, null, null, null, doubled, windLater);
        return new Scenario(e.id, e.name, null, null, spill, fire, tod,
                todFromSun, windFromDeg, null, null, null, doubled, windLater);
    }

    /** Identified by ID number only (shipping name not known). */
    public Scenario idOnly(String id) {
        return new Scenario(id, null, null, null, spill, fire, tod,
                todFromSun, windFromDeg, null, null, null, doubled, windLater);
    }

    /** Identified by guide only (placard or unknown cargo). */
    public Scenario guideOnly(String guide, String placardKey) {
        return new Scenario("", null, guide, placardKey, spill, fire, tod,
                todFromSun, windFromDeg, null, null, null, doubled, windLater);
    }

    /**
     * Not identified any more (the user tapped Change); the situation
     * answers that don't depend on the material stay.
     */
    public Scenario unidentified() {
        return new Scenario("", null, null, null, spill, fire, tod, todFromSun,
                windFromDeg, null, null, null, doubled, windLater);
    }

    public Scenario withSpill(Spill s) {
        return new Scenario(id, name, guide, placardKey, s, fire, tod,
                todFromSun, windFromDeg, water, container, windSpeed, doubled, windLater);
    }

    public Scenario withFire(Boolean f) {
        return new Scenario(id, name, guide, placardKey, spill, f, tod,
                todFromSun, windFromDeg, water, container, windSpeed, doubled, windLater);
    }

    public Scenario withTimeOfDay(TimeOfDay t, boolean fromSun) {
        return new Scenario(id, name, guide, placardKey, spill, fire, t,
                fromSun, windFromDeg, water, container, windSpeed, doubled, windLater);
    }

    public Scenario withWindFrom(double deg) {
        double d = Double.isNaN(deg) ? deg : Formats.norm360(deg);
        return new Scenario(id, name, guide, placardKey, spill, fire, tod,
                todFromSun, d, water, container, windSpeed, doubled, windLater);
    }

    public Scenario withWater(Water w) {
        return new Scenario(id, name, guide, placardKey, spill, fire, tod,
                todFromSun, windFromDeg, w, container, windSpeed, doubled, windLater);
    }

    public Scenario withContainer(String c) {
        return new Scenario(id, name, guide, placardKey, spill, fire, tod,
                todFromSun, windFromDeg, water, c, windSpeed, doubled, windLater);
    }

    public Scenario withWindSpeed(Wind w) {
        return new Scenario(id, name, guide, placardKey, spill, fire, tod,
                todFromSun, windFromDeg, water, container, w, doubled, windLater);
    }

    public Scenario withWindLater(boolean later) {
        return new Scenario(id, name, guide, placardKey, spill, fire, tod,
                todFromSun, windFromDeg, water, container, windSpeed, doubled,
                later);
    }

    public Scenario withDoubled(boolean x2) {
        return new Scenario(id, name, guide, placardKey, spill, fire, tod,
                todFromSun, windFromDeg, water, container, windSpeed, x2, windLater);
    }

    /** Situation choices cleared, identification kept (for a new incident). */
    public Scenario resetSituation() {
        return new Scenario(id, name, guide, placardKey, null, null, null,
                false, Double.NaN, null, null, null, false);
    }

    public boolean identified() {
        return !id.isEmpty() || guide != null;
    }

    public boolean byGuideOnly() {
        return id.isEmpty() && guide != null;
    }

    public boolean nameUnknown() {
        return !id.isEmpty() && name == null;
    }

    public boolean hasWind() {
        return !Double.isNaN(windFromDeg);
    }
}
