package com.atakmap.android.haztak.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GeometryAndSunTest {

    private static final double LAT = 38.9, LON = -77.0;

    @Test
    public void squareMatchesTheGuidebookFigure() {
        // Wind FROM the west (270): the square lies to the east of the spill.
        double side = 1500;
        double[][] c = Geometry.square(LAT, LON, 270, side);
        // Upwind corners: half the side to each side of the spill, N and S.
        assertEquals(side / 2, Geo.distance(LAT, LON, c[0][0], c[0][1]), 1e-3);
        assertEquals(side / 2, Geo.distance(LAT, LON, c[3][0], c[3][1]), 1e-3);
        assertEquals(0.0, Geo.bearing(LAT, LON, c[0][0], c[0][1]), 0.01);   // left looking east = north
        assertEquals(180.0, Geo.bearing(LAT, LON, c[3][0], c[3][1]), 0.01);
        // Far corners: one side downwind, half a side across.
        double far = Math.hypot(side, side / 2);
        assertEquals(far, Geo.distance(LAT, LON, c[1][0], c[1][1]), 1e-3);
        assertEquals(far, Geo.distance(LAT, LON, c[2][0], c[2][1]), 1e-3);
        // Side lengths
        assertEquals(side, Geo.distance(c[0][0], c[0][1], c[3][0], c[3][1]), 0.5);
        assertEquals(side, Geo.distance(c[0][0], c[0][1], c[1][0], c[1][1]), 0.5);
        assertEquals(side, Geo.distance(c[1][0], c[1][1], c[2][0], c[2][1]), 0.5);
        assertTrue(c[1][1] > LON && c[2][1] > LON); // east
    }

    @Test
    public void insideAndOutsideTheSquare() {
        double side = 1000;
        // 500 m east (downwind) of the spill, on the axis: inside
        double[] p = Geo.destination(LAT, LON, 90, 500);
        double[] rel = Geometry.relative(LAT, LON, 270, p[0], p[1]);
        assertEquals(500, rel[1], 0.5);
        assertEquals(0, rel[2], 0.5);
        assertTrue(Geometry.inSquare(rel, side));
        // 100 m west (upwind): outside
        p = Geo.destination(LAT, LON, 270, 100);
        rel = Geometry.relative(LAT, LON, 270, p[0], p[1]);
        assertTrue(rel[1] < 0);
        assertFalse(Geometry.inSquare(rel, side));
        // 900 m downwind, 400 m to the side: inside; 600 m to the side: outside
        double[] q = Geo.destination(LAT, LON, 90 + Math.toDegrees(Math.atan2(400, 900)),
                Math.hypot(900, 400));
        assertTrue(Geometry.inSquare(Geometry.relative(LAT, LON, 270, q[0], q[1]), side));
        q = Geo.destination(LAT, LON, 90 + Math.toDegrees(Math.atan2(600, 900)),
                Math.hypot(900, 600));
        assertFalse(Geometry.inSquare(Geometry.relative(LAT, LON, 270, q[0], q[1]), side));
        // 1100 m downwind: beyond the far edge
        q = Geo.destination(LAT, LON, 90, 1100);
        assertFalse(Geometry.inSquare(Geometry.relative(LAT, LON, 270, q[0], q[1]), side));
    }

    @Test
    public void squareEdgeDistance() {
        double[] rel = { 500, 500, 0 };
        assertEquals(-500, Geometry.squareEdgeDistance(rel, 1000), 1e-9);
        rel = new double[] { 0, -30, 0 };
        assertEquals(30, Geometry.squareEdgeDistance(rel, 1000), 1e-9);
    }

    @Test
    public void downwindLine() {
        double[] e = Geometry.downwindPoint(LAT, LON, 0, 300); // wind from north
        assertEquals(300, Geo.distance(LAT, LON, e[0], e[1]), 1e-3);
        assertEquals(180, Geo.bearing(LAT, LON, e[0], e[1]), 0.01);
    }

    @Test
    public void positionCheck() {
        Erg erg = TestData.erg();
        Zones z = Zones.compute(erg, Scenario.empty().material(erg.entry("1017", "Chlorine"))
                .withSpill(Scenario.Spill.SMALL).withFire(false)
                .withTimeOfDay(Scenario.TimeOfDay.NIGHT, false).withWindFrom(270));
        double[] p = Geo.destination(LAT, LON, 45, 20);
        assertEquals(PositionCheck.Where.ISOLATION,
                PositionCheck.check(z, LAT, LON, 270, p[0], p[1], 5).where);
        p = Geo.destination(LAT, LON, 90, 800);
        assertEquals(PositionCheck.Where.PROTECTIVE,
                PositionCheck.check(z, LAT, LON, 270, p[0], p[1], 5).where);
        p = Geo.destination(LAT, LON, 270, 800);
        PositionCheck.Result r = PositionCheck.check(z, LAT, LON, 270, p[0], p[1], 5);
        assertEquals(PositionCheck.Where.OUTSIDE, r.where);
        assertTrue(r.headline().contains("upwind"));
        // near the isolation edge (61 m) with 10 m GPS error
        p = Geo.destination(LAT, LON, 0, 65);
        assertTrue(PositionCheck.check(z, LAT, LON, 270, p[0], p[1], 10).nearEdge);
    }

    /** Reference values from PyEphem 4.x (geometric elevation, no refraction). */
    private static final double[][] EPHEM = {
            { 38.9, -77.0, 1793484000000L, 0.7337 },  // 2026-10-31 22:00:00
            { 38.9, -77.0, 1793442600000L, -12.9977 },  // 2026-10-31 10:30:00
            { 38.9, -77.0, 1782061200000L, 74.3954 },  // 2026-06-21 17:00:00
            { 64.8, -147.7, 1797886800000L, 1.2492 },  // 2026-12-21 21:00:00
            { 64.8, -147.7, 1782028800000L, 0.8825 },  // 2026-06-21 08:00:00
            { -33.9, 151.2, 1773972000000L, 56.3027 },  // 2026-03-20 02:00:00
            { 0.0, 0.0, 1773986400000L, -1.8800 },  // 2026-03-20 06:00:00
            { 51.5, -0.1, 1790186400000L, -1.3682 },  // 2026-09-23 18:00:00
            { 35.7, 139.7, 1767225600000L, 19.5573 },  // 2026-01-01 00:00:00
            { -54.8, -68.3, 1797822000000L, -9.3647 },  // 2026-12-21 03:00:00
            { -24.663, -125.694, 1878801093000L, -79.6562 },
            { -63.24, 115.659, 1729335041000L, 3.7674 },
            { -18.804, -159.12, 1840280943000L, -52.5424 },
            { -39.942, -149.059, 1816319433000L, -25.5142 },
            { -60.22, -147.343, 1818023202000L, 14.3841 },
            { -61.725, 23.563, 1763992453000L, 34.2178 },
            { 18.288, 29.879, 1720673167000L, 15.7087 },
            { 10.794, -37.195, 1763413401000L, -15.0938 },
            { -63.478, 129.049, 1781808601000L, -37.7412 },
            { -11.321, 14.647, 1857320677000L, -19.7178 },
            { -26.813, 113.805, 1752580568000L, -27.8438 },
            { -55.572, 25.634, 1754498444000L, -15.9776 },
            { -17.864, 17.188, 1720921987000L, -48.5619 },
            { 9.012, 42.843, 1837322450000L, 60.3558 },
            { 25.256, -26.067, 1788395438000L, -52.6237 },
            { -4.816, 152.439, 1801128725000L, -19.4024 },
            { -28.033, 105.977, 1891702089000L, -38.9145 },
            { 39.176, -150.532, 1784664709000L, 58.6594 },
            { 3.528, 135.049, 1824550211000L, -41.3281 },
            { -29.689, 172.863, 1735760241000L, 24.6277 },
            { 1.671, -120.614, 1795887106000L, 49.0033 },
            { -48.722, -3.973, 1714591817000L, -24.2303 },
            { 64.683, -152.057, 1853874519000L, 21.8227 },
            { 10.224, 135.172, 1788288156000L, -31.4813 },
            { -22.383, -53.936, 1837392324000L, -64.7007 },
            { 11.185, -15.766, 1729191683000L, -5.0450 },
            { 62.255, -9.325, 1882349201000L, 34.4600 },
            { -60.9, 83.217, 1787176796000L, -28.6768 },
            { 20.598, 177.515, 1823692983000L, -65.8824 },
            { -30.157, -41.115, 1883557297000L, 18.3063 },
    };

    @Test
    public void sunElevationMatchesEphem() {
        for (double[] c : EPHEM) {
            double e = Sun.elevation(c[0], c[1], (long) c[2]);
            assertEquals("lat " + c[0] + " lon " + c[1] + " t " + (long) c[2], c[3], e, 0.05);
        }
    }

    @Test
    public void dayNight() {
        // Washington DC, 31 Oct 2026: sunset about 22:05 UTC
        assertEquals(Scenario.TimeOfDay.DAY, Sun.timeOfDay(38.9, -77.0, 1793484000000L));
        assertEquals(Scenario.TimeOfDay.NIGHT, Sun.timeOfDay(38.9, -77.0, 1793442600000L));
    }
}
