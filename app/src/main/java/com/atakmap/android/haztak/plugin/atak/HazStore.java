package com.atakmap.android.haztak.plugin.atak;

import android.os.Handler;
import android.os.Looper;

import com.atakmap.android.drawing.DrawingToolsMapComponent;
import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.haztak.plugin.core.Drawing;
import com.atakmap.android.haztak.plugin.core.Erg;
import com.atakmap.android.haztak.plugin.core.HazCodec;
import com.atakmap.android.haztak.plugin.core.HazRecord;
import com.atakmap.android.haztak.plugin.core.Labels;
import com.atakmap.android.haztak.plugin.core.Names;
import com.atakmap.android.haztak.plugin.core.Zones;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import gov.tak.api.util.AttributeSet;

/**
 * Incidents on the map. An incident is a normal marker (CoT a-h-G, readable
 * by any TAK client) carrying the {@code __haztak} detail, plus native ATAK
 * drawing items whose UIDs are the marker UID plus a fixed suffix (see
 * {@link Drawing}):
 * <ul>
 * <li>{@code .iso} initial isolation circle (red), anchored to the marker</li>
 * <li>{@code .ev1..} evacuation circles (yellow), anchored to the marker</li>
 * <li>{@code .paz} protective action square (orange polygon)</li>
 * <li>{@code .dw} guide downwind evacuation line (orange)</li>
 * </ul>
 * Circles follow the marker through ATAK's anchor; the square and the line
 * are recomputed when the marker moves. Lifecycle rules (liveness, registry,
 * whole-incident delete) are the ones Blast 1.0 settled on.
 */
public final class HazStore {

    private static final String TAG = "HazStore";

    public static final String META = "haztak.data";
    public static final String META_FLAG = "haztak.zone";
    public static final String META_PARENT = "haztak.parent";
    static final String OPAQUE = "opaque-details";
    public static final String MARKER_TYPE = "a-h-G";

    static final int WIND_BLUE = 0xFF40C4FF;
    static final int RED = 0xFFD50000;
    static final int RED_FILL = 0x44D50000;
    static final int ORANGE = 0xFFFF6D00;
    static final int ORANGE_FILL = 0x33FFAB00;
    static final int YELLOW = 0xFFFFD600;
    static final int YELLOW_FILL = 0x10FFD600;
    static final int GREY = 0xFF9E9E9E;
    static final int GREY_FILL = 0x149E9E9E;

    private final MapView mapView;
    private final Erg erg;
    private final android.content.SharedPreferences prefs;
    private final Set<String> registry = java.util.Collections
            .newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());
    private static final String PREF_REGISTRY = "haztak.registry";
    private volatile String lastScan = "";

    public HazStore(MapView mapView, Erg erg) {
        this.mapView = mapView;
        this.erg = erg;
        this.prefs = com.atakmap.android.preference.AtakPreferences
                .getInstance(mapView.getContext()).getSharedPrefs();
        try {
            Set<String> saved = prefs.getStringSet(PREF_REGISTRY, null);
            if (saved != null)
                registry.addAll(saved);
        } catch (RuntimeException e) {
            Log.w(TAG, "registry unreadable", e);
        }
    }

    public Erg erg() {
        return erg;
    }

    // ------------------------------------------------------------------
    // Liveness and lookup (an item is on the map only while it has a group;
    // ATAK's UID index can still return removed items).

    public static boolean isLive(MapItem mi) {
        return mi != null && mi.getGroup() != null;
    }

    public MapItem liveItem(String uid) {
        if (uid == null)
            return null;
        MapItem mi = mapView.getMapItem(uid);
        return isLive(mi) ? mi : null;
    }

    public Marker liveMarker(String uid) {
        MapItem mi = liveItem(uid);
        if (mi instanceof Marker && read(mi) != null)
            return (Marker) mi;
        return null;
    }

    public void register(String uid) {
        if (uid != null && registry.add(uid))
            saveRegistry();
    }

    private void unregister(String uid) {
        if (uid != null && registry.remove(uid))
            saveRegistry();
    }

    private void saveRegistry() {
        try {
            prefs.edit().putStringSet(PREF_REGISTRY, new HashSet<>(registry))
                    .apply();
        } catch (RuntimeException e) {
            Log.w(TAG, "registry not saved", e);
        }
    }

    public String lastScan() {
        return lastScan;
    }

    // ------------------------------------------------------------------
    // Reading

    /** Store the record XML on an item, mirrored into ATAK's opaque details. */
    static void store(MapItem item, String xml) {
        item.setMetaString(META, xml);
        item.setMetaBoolean(META_FLAG, true);
        try {
            AttributeSet opaque = item.getMetaAttributeSet(OPAQUE);
            if (opaque == null)
                opaque = new AttributeSet();
            opaque.setAttribute(HazCodec.DETAIL, xml);
            item.setMetaAttributeSet(OPAQUE, opaque);
        } catch (RuntimeException e) {
            Log.w(TAG, "opaque mirror failed", e);
        }
    }

    /** The record on a marker, or null. Adopts an opaque copy if needed. */
    public HazRecord read(MapItem item) {
        if (!(item instanceof Marker))
            return null;
        try {
            String xml = item.getMetaString(META, null);
            if (xml != null) {
                HazRecord r = HazCodec.fromXml(xml, erg);
                if (r != null)
                    return r;
            }
            AttributeSet opaque = item.getMetaAttributeSet(OPAQUE);
            if (opaque != null && opaque.containsAttribute(HazCodec.DETAIL)) {
                String s = opaque.getStringAttribute(HazCodec.DETAIL, null);
                HazRecord r = HazCodec.fromXml(s, erg);
                if (r != null) {
                    item.setMetaString(META, HazCodec.toXml(r));
                    item.setMetaBoolean(META_FLAG, true);
                    return r;
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "unreadable haztak data on " + item.getUID(), e);
        }
        return null;
    }

    /** True for one of our shapes. */
    public static boolean isShape(MapItem item) {
        return item != null && Drawing.isShapeUid(item.getUID())
                && item.hasMetaValue(META_PARENT);
    }

    /** The incident marker for a marker or one of its shapes, else null. */
    public Marker incidentFor(MapItem item) {
        if (item == null)
            return null;
        if (item instanceof Marker && read(item) != null)
            return (Marker) item;
        if (isShape(item) || Drawing.isShapeUid(item.getUID())) {
            String parent = item.getMetaString(META_PARENT, null);
            if (parent == null)
                parent = Drawing.incidentUid(item.getUID());
            return liveMarker(parent);
        }
        return null;
    }

    /** All incident markers on the map (registry, UID index, group walk). */
    public List<Marker> all() {
        final java.util.LinkedHashMap<String, Marker> found = new java.util.LinkedHashMap<>();
        int fromRegistry = 0, fromIndex = 0, fromWalk = 0;
        for (String uid : new ArrayList<>(registry)) {
            Marker m = liveMarker(uid);
            if (m != null && found.put(m.getUID(), m) == null)
                fromRegistry++;
        }
        try {
            for (MapItem mi : mapView.getRootGroup().getAllItems()) {
                if (mi instanceof Marker && isLive(mi)
                        && (mi.hasMetaValue(META) || mi.hasMetaValue(OPAQUE))
                        && read(mi) != null
                        && found.put(mi.getUID(), (Marker) mi) == null)
                    fromIndex++;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "index listing failed", e);
        }
        final List<Marker> walked = new ArrayList<>();
        try {
            mapView.getRootGroup().deepForEachItem(new MapGroup.MapItemsCallback() {
                @Override
                public boolean onItemFunction(MapItem item) {
                    if (item instanceof Marker
                            && (item.hasMetaValue(META) || item.hasMetaValue(OPAQUE))
                            && read(item) != null)
                        walked.add((Marker) item);
                    return false;
                }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "group walk failed", e);
        }
        for (Marker m : walked)
            if (found.put(m.getUID(), m) == null)
                fromWalk++;
        for (String uid : found.keySet())
            register(uid);
        boolean pruned = false;
        for (String uid : new ArrayList<>(registry))
            if (!found.containsKey(uid) && mapView.getMapItem(uid) == null)
                pruned |= registry.remove(uid);
        if (pruned)
            saveRegistry();
        lastScan = "found " + found.size() + " (registry " + fromRegistry + "/"
                + registry.size() + ", index +" + fromIndex + ", walk +" + fromWalk
                + ")";
        return new ArrayList<>(found.values());
    }

    public static String nameOf(Marker m) {
        String cs = m.getMetaString("callsign", null);
        if (cs == null || cs.isEmpty())
            cs = m.getTitle();
        return cs == null ? "" : cs;
    }

    public int numberOf(Marker m) {
        HazRecord r = read(m);
        int stored = r != null ? r.number : 0;
        return Math.max(stored, Names.numberIn(nameOf(m)));
    }

    public int nextNumber() {
        int max = 0;
        for (Marker m : all())
            max = Math.max(max, numberOf(m));
        return max + 1;
    }

    // ------------------------------------------------------------------
    // Writing

    public Marker create(GeoPoint at, HazRecord r) {
        int number = nextNumber();
        r = r.withNumber(number);
        String name = Names.initial(number, r.label);
        Marker m = new PlacePointTool.MarkerCreator(at)
                .setUid(UUID.randomUUID().toString())
                .setType(MARKER_TYPE)
                .setCallsign(name)
                .showCotDetails(false)
                .setShowNewRadial(false)
                .setAllowDuplicativeNames(true)
                .setArchive(true)
                .placePoint();
        if (m == null)
            return null;
        register(m.getUID());
        write(m, r);
        return m;
    }

    /** Store a record and bring shapes and labels up to date. */
    public boolean write(Marker m, HazRecord r) {
        if (!isLive(m)) {
            Log.w(TAG, "not writing to removed incident " + m.getUID());
            return false;
        }
        store(m, HazCodec.toXml(r));
        String current = nameOf(m);
        String wanted = Names.withStatus(current.isEmpty()
                ? Names.initial(Math.max(1, r.number), r.label) : current,
                r.isCleared());
        if (!wanted.equals(current))
            setName(m, wanted);
        m.setMetaString("remarks", Labels.mergeRemarks(
                m.getMetaString("remarks", null), Labels.remarks(r, r.zones(erg))));
        ensureShapes(m, r);
        m.persist(mapView.getMapEventDispatcher(), null, HazStore.class);
        return true;
    }

    private static void setName(Marker m, String name) {
        m.setMetaString("callsign", name);
        m.setTitle(name);
    }

    public boolean rename(Marker m, String typed) {
        String clean = Names.sanitize(typed);
        HazRecord r = read(m);
        if (clean == null || r == null || !isLive(m))
            return false;
        if (r.number == 0) {
            int n = numberOf(m);
            if (n > 0)
                r = r.withNumber(n);
        }
        setName(m, Names.withStatus(clean, r.isCleared()));
        return write(m, r);
    }

    /** Draw or update every shape for the incident; remove ones not needed. */
    public void ensureShapes(Marker m, HazRecord r) {
        if (!isLive(m))
            return;
        if (r == null)
            r = read(m);
        if (r == null)
            return;
        watch(m);
        GeoPoint c = m.getPoint();
        if (c == null || !c.isValid())
            return;
        Zones z = r.zones(erg);
        List<Drawing.Shape> shapes = Drawing.shapes(r, z, c.getLatitude(),
                c.getLongitude());
        Set<String> wanted = new HashSet<>();
        for (Drawing.Shape s : shapes) {
            wanted.add(s.suffix);
            try {
                if (s.kind == Drawing.Kind.CIRCLE)
                    circle(m, r, s);
                else
                    polyline(m, r, s);
            } catch (RuntimeException e) {
                Log.e(TAG, "shape " + s.suffix + " failed", e);
            }
        }
        for (String sfx : Drawing.allSuffixes())
            if (!wanted.contains(sfx)) {
                MapItem old = liveItem(m.getUID() + sfx);
                if (old != null && isShape(old))
                    removeOnPurpose(old);
            }
    }

    /**
     * Remove one shape that is no longer needed (e.g. the wind was cleared)
     * without triggering the whole-incident delete.
     */
    private void removeOnPurpose(MapItem item) {
        final String uid = item.getUID();
        expectedRemovals.add(uid);
        item.removeOnGroupChangedListener(groupWatcher);
        watched.remove(item);
        item.removeFromGroup();
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                expectedRemovals.remove(uid);
            }
        }, GONE_CHECK_MS * 4);
    }

    private MapGroup drawingGroup() {
        MapGroup g = DrawingToolsMapComponent.getGroup();
        return g != null ? g : mapView.getRootGroup();
    }

    private void style(com.atakmap.android.maps.Shape item, HazRecord r,
            Drawing.Role role) {
        int stroke, fill;
        double w;
        if (r.isCleared()) {
            stroke = GREY;
            fill = GREY_FILL;
            w = 2;
        } else {
            switch (role) {
                case ISOLATION:
                    stroke = RED;
                    fill = RED_FILL;
                    w = 3;
                    break;
                case PROTECTIVE:
                    stroke = ORANGE;
                    fill = ORANGE_FILL;
                    w = 3;
                    break;
                case EVACUATION:
                    stroke = YELLOW;
                    fill = YELLOW_FILL;
                    w = 3;
                    break;
                case WIND:
                    stroke = WIND_BLUE;
                    fill = 0;
                    w = 3;
                    break;
                default:
                    stroke = ORANGE;
                    fill = 0;
                    w = 4;
            }
        }
        item.setStrokeColor(stroke);
        item.setFillColor(fill);
        item.setStrokeWeight(w);
    }

    private void circle(Marker m, HazRecord r, Drawing.Shape s) {
        String uid = m.getUID() + s.suffix;
        MapItem existing = liveItem(uid);
        DrawingCircle c;
        boolean added = false;
        if (existing instanceof DrawingCircle) {
            c = (DrawingCircle) existing;
        } else if (existing != null) {
            Log.w(TAG, "uid " + uid + " is not a circle; not drawing");
            return;
        } else {
            c = new DrawingCircle(mapView, uid);
            ATAKUtilities.setAuthorInformation(c);
            c.setCenterMarker(m);
            added = true;
        }
        c.setMetaString(META_PARENT, m.getUID());
        c.setMetaBoolean("editable", false);
        c.setMovable(false);
        if (Math.abs(c.getRadius() - s.radiusM) > 0.01)
            c.setRadius(s.radiusM);
        c.setTitle(s.title);
        style(c, r, s.role);
        if (c.getCenterMarker() != m)
            c.setCenterMarker(m);
        if (added) {
            drawingGroup().addItem(c);
            if (c.getCenterMarker() != m)
                c.setCenterMarker(m);
        }
        c.persist(mapView.getMapEventDispatcher(), null, HazStore.class);
        watch(c);
    }

    private void polyline(Marker m, HazRecord r, Drawing.Shape s) {
        String uid = m.getUID() + s.suffix;
        MapItem existing = liveItem(uid);
        DrawingShape d;
        boolean added = false;
        if (existing instanceof DrawingShape) {
            d = (DrawingShape) existing;
        } else if (existing != null) {
            Log.w(TAG, "uid " + uid + " is not a shape; not drawing");
            return;
        } else {
            d = new DrawingShape(mapView, uid);
            ATAKUtilities.setAuthorInformation(d);
            added = true;
        }
        GeoPoint[] pts = new GeoPoint[s.points.length];
        for (int i = 0; i < pts.length; i++)
            pts[i] = new GeoPoint(s.points[i][0], s.points[i][1]);
        d.setPoints(pts);
        d.setClosed(s.kind == Drawing.Kind.POLYGON);
        d.setMetaString(META_PARENT, m.getUID());
        d.setMetaBoolean("editable", false);
        d.setMovable(false);
        d.setTitle(s.title);
        style(d, r, s.role);
        if (added)
            drawingGroup().addItem(d);
        d.persist(mapView.getMapEventDispatcher(), null, HazStore.class);
        watch(d);
    }

    /** The marker and its shapes, marker first (send order). */
    public List<MapItem> items(Marker m) {
        List<MapItem> out = new ArrayList<>();
        out.add(m);
        for (String sfx : Drawing.allSuffixes()) {
            MapItem s = liveItem(m.getUID() + sfx);
            if (s != null && isShape(s))
                out.add(s);
        }
        return out;
    }

    /** The largest shape (for framing the map), or the marker. */
    public MapItem frameItem(Marker m) {
        MapItem best = m;
        for (String sfx : new String[] { Drawing.PAZ, Drawing.EV + 1, Drawing.DW,
                Drawing.ISO }) {
            MapItem s = liveItem(m.getUID() + sfx);
            if (s != null)
                return s;
        }
        return best;
    }

    public void removeShapes(String markerUid) {
        for (String sfx : Drawing.allSuffixes()) {
            MapItem c = liveItem(markerUid + sfx);
            if (c != null)
                c.removeFromGroup();
        }
    }

    public void delete(Marker m) {
        deleteIncident(m.getUID());
    }

    public void deleteIncident(String markerUid) {
        if (markerUid == null)
            return;
        unregister(markerUid);
        MapItem m = liveItem(markerUid);
        if (m != null)
            m.removeFromGroup();
        removeShapes(markerUid);
    }

    // ------------------------------------------------------------------
    // Deleting any part removes the whole incident; moving the marker
    // redraws the square and the line. "Removed" can fire on group moves,
    // so the item must still be missing after a short wait.

    static final long GONE_CHECK_MS = 750;
    /** Extra wait before a removed shape deletes the whole incident. */
    static final long SHAPE_GRACE_MS = 3000;
    static final long MOVE_REDRAW_MS = 300;

    private final Handler main = new Handler(Looper.getMainLooper());
    /** Shapes removed on purpose; their "removed" events are not deletes. */
    private final Set<String> expectedRemovals = java.util.Collections
            .synchronizedSet(new HashSet<String>());
    private final Set<MapItem> watched = java.util.Collections.synchronizedSet(
            java.util.Collections.newSetFromMap(
                    new java.util.WeakHashMap<MapItem, Boolean>()));

    private void watch(MapItem item) {
        if (watched.add(item)) {
            item.addOnGroupChangedListener(groupWatcher);
            if (item instanceof Marker)
                ((Marker) item).addOnPointChangedListener(pointWatcher);
        }
    }

    private final MapItem.OnGroupChangedListener groupWatcher =
            new MapItem.OnGroupChangedListener() {
                @Override
                public void onItemAdded(MapItem item, MapGroup group) {
                }

                @Override
                public void onItemRemoved(MapItem item, MapGroup group) {
                    partRemoved(item.getUID());
                }
            };

    private final Set<String> redrawPending = java.util.Collections
            .synchronizedSet(new HashSet<String>());

    private final PointMapItem.OnPointChangedListener pointWatcher =
            new PointMapItem.OnPointChangedListener() {
                @Override
                public void onPointChanged(final PointMapItem item) {
                    final String uid = item.getUID();
                    if (!redrawPending.add(uid))
                        return;
                    main.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            redrawPending.remove(uid);
                            try {
                                Marker m = liveMarker(uid);
                                if (m != null)
                                    ensureShapes(m, read(m));
                            } catch (RuntimeException e) {
                                Log.e(TAG, "redraw after move failed", e);
                            }
                        }
                    }, MOVE_REDRAW_MS);
                }
            };

    public void partRemoved(final String uid) {
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (expectedRemovals.contains(uid))
                        return;
                    if (liveItem(uid) != null)
                        return;
                    final String incident = Drawing.incidentUid(uid);
                    if (incident.equals(uid)) {
                        deleteIncident(incident); // the marker itself
                        return;
                    }
                    if (!stillWanted(incident, uid))
                        return; // another device's edit removed it: not a delete
                    // Still drawn by this device's copy of the record. Wait
                    // longer in case the edited marker that drops this shape
                    // is still on its way, then decide.
                    main.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (liveItem(uid) == null && !expectedRemovals.contains(uid)
                                        && stillWanted(incident, uid))
                                    deleteIncident(incident);
                            } catch (RuntimeException e) {
                                Log.e(TAG, "incident cleanup failed for " + uid, e);
                            }
                        }
                    }, SHAPE_GRACE_MS);
                } catch (RuntimeException e) {
                    Log.e(TAG, "incident cleanup failed for " + uid, e);
                }
            }
        }, GONE_CHECK_MS);
    }

    /** Does the live incident's current record still draw this shape? */
    private boolean stillWanted(String incidentUid, String shapeUid) {
        Marker m = liveMarker(incidentUid);
        if (m == null)
            return true; // marker gone too: the incident is being deleted
        HazRecord r = read(m);
        GeoPoint c = m.getPoint();
        if (r == null || c == null || !c.isValid())
            return true;
        for (Drawing.Shape s : Drawing.shapes(r, r.zones(erg), c.getLatitude(),
                c.getLongitude()))
            if (shapeUid.equals(incidentUid + s.suffix))
                return true;
        return false;
    }

    public void dispose() {
        main.removeCallbacksAndMessages(null);
        List<MapItem> items;
        synchronized (watched) {
            items = new ArrayList<>(watched);
            watched.clear();
        }
        for (MapItem mi : items) {
            mi.removeOnGroupChangedListener(groupWatcher);
            if (mi instanceof Marker)
                ((Marker) mi).removeOnPointChangedListener(pointWatcher);
        }
    }
}
