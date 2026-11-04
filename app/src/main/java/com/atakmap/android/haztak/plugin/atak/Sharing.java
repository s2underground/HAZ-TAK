package com.atakmap.android.haztak.plugin.atak;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.haztak.plugin.core.Drawing;
import com.atakmap.android.haztak.plugin.core.HazRecord;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.comms.CotDispatcher;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sending incidents. The marker goes first and the shapes a second later,
 * then once more: receivers import CoT in parallel, and a circle imported
 * before its centre marker is never drawn by ATAK (see sendToAll).
 */
public final class Sharing {

    private static final String TAG = "HazSharing";

    /** ATAK's remote-delete event type (CotDeleteImporter). */
    static final String DELETE_TYPE = "t-x-d-d";

    private final HazStore store;

    public Sharing(HazStore store) {
        this.store = store;
    }

    /**
     * Broadcast the incident to everyone: commo sends a broadcast to every
     * output (mesh and all TAK server connections). The marker goes first,
     * then its shapes, then forced deletes for the shapes it doesn't draw.
     */
    public int sendToAll(final Marker m) {
        final CotDispatcher d = CotMapComponent.getExternalDispatcher();
        List<MapItem> items = store.items(m);
        // 1. The marker alone. Receiving ATAK clients import CoT in parallel,
        // so a circle sent right behind its marker can be imported first:
        // ATAK then "defers" it and never anchors or draws it (ATAK only
        // replays deferred CoT at startup). Seen on the 0.6 release build.
        CotEvent marker = CotEventFactory.createCotEvent(m);
        if (marker == null || !marker.isValid()) {
            Log.w(TAG, "no CoT for marker " + m.getUID());
            return 0;
        }
        d.dispatchToBroadcast(marker);
        final String uid = m.getUID();
        // 2. The shapes once receivers have created the marker, then the
        // removals of shapes the incident no longer has.
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendShapes(uid, true);
            }
        }, SHAPES_AFTER_MS);
        // 3. The shapes once more: anchors any circle a slow link still
        // delivered before its marker (a second send fixed it in testing).
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendShapes(uid, false);
            }
        }, REPEAT_AFTER_MS);
        return items.size();
    }

    /** Wait before the shapes, so receivers have imported the marker. */
    static final long SHAPES_AFTER_MS = 1000;
    /** Send the shapes a second time after this. */
    static final long REPEAT_AFTER_MS = 5000;

    private final Handler main = new Handler(Looper.getMainLooper());

    private void sendShapes(String uid, boolean withRemovals) {
        try {
            Marker m = store.liveMarker(uid);
            if (m == null)
                return; // deleted meanwhile
            CotDispatcher d = CotMapComponent.getExternalDispatcher();
            for (MapItem mi : store.items(m)) {
                if (mi == m)
                    continue;
                CotEvent ev = CotEventFactory.createCotEvent(mi);
                if (ev == null || !ev.isValid()) {
                    Log.w(TAG, "no CoT for " + mi.getUID());
                    continue;
                }
                d.dispatchToBroadcast(ev);
            }
            if (!withRemovals)
                return;
            // Every shape the incident does NOT draw now is removed everywhere
            // (cheap, idempotent): clients without HAZ-TAK, and clients that
            // missed an earlier update or got an old square replayed by a TAK
            // server, drop stale zones. HAZ-TAK receivers never delete an
            // incident because of a removed shape (HazStore.partRemoved).
            Map<String, String> now = shapesOf(m, store.read(m));
            if (now == null)
                return; // can't tell what the incident draws: delete nothing
            for (String sfx : Drawing.allSuffixes()) {
                String su = m.getUID() + sfx;
                if (now.containsKey(su))
                    continue;
                d.dispatchToBroadcast(deleteEvent(su, sfx.equals(Drawing.ISO)
                        || sfx.startsWith(Drawing.EV) ? CIRCLE_TYPE : SHAPE_TYPE));
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "sending shapes failed for " + uid, e);
        }
    }

    public void dispose() {
        main.removeCallbacksAndMessages(null);
    }

    /** Shapes a record draws for this marker: uid -> CoT type; null if unknown. */
    private Map<String, String> shapesOf(Marker m, HazRecord r) {
        GeoPoint c = m.getPoint();
        if (r == null || c == null || !c.isValid())
            return null;
        Map<String, String> out = new HashMap<>();
        for (Drawing.Shape s : Drawing.shapes(r, r.zones(store.erg()),
                c.getLatitude(), c.getLongitude()))
            out.put(m.getUID() + s.suffix, s.kind == Drawing.Kind.CIRCLE
                    ? CIRCLE_TYPE : SHAPE_TYPE);
        return out;
    }

    static final String CIRCLE_TYPE = "u-d-c-c";
    static final String SHAPE_TYPE = "u-d-f";


    /** Open ATAK's contact picker with the marker and its shapes. */
    public void sendTo(Marker m) {
        List<MapItem> items = store.items(m);
        String[] uids = new String[items.size()];
        for (int i = 0; i < uids.length; i++)
            uids[i] = items.get(i).getUID();
        Intent i = new Intent(ContactPresenceDropdown.SEND_LIST);
        i.putExtra("targetsUID", uids);
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    /**
     * Ask every client to remove the incident (ATAK's forced remote delete),
     * then remove it here.
     */
    public void deleteForEveryone(Marker m) {
        CotDispatcher d = CotMapComponent.getExternalDispatcher();
        for (MapItem mi : store.items(m)) {
            try {
                d.dispatchToBroadcast(deleteEvent(mi));
            } catch (RuntimeException e) {
                Log.e(TAG, "delete event failed for " + mi.getUID(), e);
            }
        }
        store.delete(m);
    }

    static CotEvent deleteEvent(MapItem item) {
        return deleteEvent(item.getUID(), item.getType());
    }

    static CotEvent deleteEvent(String uid, String type) {
        CoordinatedTime now = new CoordinatedTime();
        CotEvent ev = new CotEvent();
        ev.setUID(java.util.UUID.randomUUID().toString());
        ev.setType(DELETE_TYPE);
        ev.setHow("h-g-i-g-o");
        ev.setTime(now);
        ev.setStart(now);
        ev.setStale(now.addMinutes(20));
        ev.setPoint(new CotPoint(0, 0, CotPoint.UNKNOWN, CotPoint.UNKNOWN,
                CotPoint.UNKNOWN));
        CotDetail detail = new CotDetail("detail");
        CotDetail link = new CotDetail("link");
        link.setAttribute("uid", uid);
        link.setAttribute("relation", "none");
        link.setAttribute("type", type);
        detail.addChild(link);
        detail.addChild(new CotDetail("__forcedelete"));
        ev.setDetail(detail);
        return ev;
    }
}
