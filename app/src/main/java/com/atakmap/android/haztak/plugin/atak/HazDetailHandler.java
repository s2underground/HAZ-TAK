package com.atakmap.android.haztak.plugin.atak;

import android.os.Handler;
import android.os.Looper;

import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.haztak.plugin.core.HazCodec;
import com.atakmap.android.haztak.plugin.core.HazRecord;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.coremap.cot.event.CotAttribute;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reads and writes the {@code <__haztak/>} detail on incident markers. */
public final class HazDetailHandler extends CotDetailHandler {

    private static final String TAG = "HazDetailHandler";

    /** Wait for the sender's shapes before drawing missing ones ourselves. */
    private static final long SHAPE_GRACE_MS = 4000;

    private final HazStore store;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable onChange;

    public HazDetailHandler(HazStore store) {
        super(HazCodec.DETAIL);
        this.store = store;
    }

    public void setOnChange(Runnable r) {
        onChange = r;
    }

    @Override
    public boolean isSupported(MapItem item, CotEvent event, CotDetail detail) {
        return item instanceof Marker;
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail root) {
        try {
            HazRecord r = store.read(item);
            if (r == null)
                return false;
            CotDetail d = new CotDetail(HazCodec.DETAIL);
            for (Map.Entry<String, String> e : HazCodec.encode(r).entrySet())
                d.setAttribute(e.getKey(), e.getValue());
            root.addChild(d);
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "toCotDetail failed", e);
            return false;
        }
    }

    @Override
    public CommsMapComponent.ImportResult toItemMetadata(MapItem item,
            CotEvent event, CotDetail detail) {
        try {
            Map<String, String> a = new LinkedHashMap<>();
            CotAttribute[] attrs = detail.getAttributes();
            if (attrs != null)
                for (CotAttribute ca : attrs)
                    a.put(ca.getName(), ca.getValue());
            HazRecord r = HazCodec.decode(a, store.erg());
            if (r == null) {
                Log.w(TAG, "ignoring unusable __haztak on " + item.getUID());
                return CommsMapComponent.ImportResult.IGNORE;
            }
            HazStore.store(item, HazCodec.toXml(r));
            final String uid = item.getUID();
            store.register(uid);
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    afterImport(uid);
                }
            }, SHAPE_GRACE_MS);
            return CommsMapComponent.ImportResult.SUCCESS;
        } catch (RuntimeException e) {
            Log.e(TAG, "toItemMetadata failed", e);
            return CommsMapComponent.ImportResult.FAILURE;
        }
    }

    private void afterImport(String uid) {
        try {
            Marker m = store.liveMarker(uid);
            if (m != null) {
                store.register(uid);
                store.ensureShapes(m, store.read(m));
            }
            if (onChange != null)
                onChange.run();
        } catch (RuntimeException e) {
            Log.e(TAG, "post-import update failed", e);
        }
    }

    public void dispose() {
        main.removeCallbacksAndMessages(null);
        onChange = null;
    }
}
