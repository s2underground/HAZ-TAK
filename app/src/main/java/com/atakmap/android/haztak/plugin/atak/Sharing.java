package com.atakmap.android.haztak.plugin.atak;

import android.content.Intent;

import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.comms.CotDispatcher;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.List;

/**
 * Sending incidents. The marker always goes first so receivers can anchor
 * the circles to it (ATAK defers a circle until its centre marker exists).
 * (From Blast 1.0.)
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
     * output (mesh and all TAK server connections).
     */
    public int sendToAll(Marker m) {
        CotDispatcher d = CotMapComponent.getExternalDispatcher();
        int n = 0;
        for (MapItem mi : store.items(m)) {
            CotEvent ev = CotEventFactory.createCotEvent(mi);
            if (ev == null || !ev.isValid()) {
                Log.w(TAG, "no CoT for " + mi.getUID());
                continue;
            }
            d.dispatchToBroadcast(ev);
            n++;
        }
        return n;
    }

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
        link.setAttribute("uid", item.getUID());
        link.setAttribute("relation", "none");
        link.setAttribute("type", item.getType());
        detail.addChild(link);
        detail.addChild(new CotDetail("__forcedelete"));
        ev.setDetail(detail);
        return ev;
    }
}
