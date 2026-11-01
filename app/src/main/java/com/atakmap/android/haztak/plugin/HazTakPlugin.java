package com.atakmap.android.haztak.plugin;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.haztak.plugin.atak.HazDetailHandler;
import com.atakmap.android.haztak.plugin.atak.HazMenuHandler;
import com.atakmap.android.haztak.plugin.atak.HazStore;
import com.atakmap.android.haztak.plugin.atak.Sharing;
import com.atakmap.android.haztak.plugin.core.Erg;
import com.atakmap.android.haztak.plugin.core.HazRecord;
import com.atakmap.android.haztak.plugin.ui.HazPane;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.coremap.log.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

/**
 * HAZ-TAK: hazardous materials initial response from the 2024 Emergency
 * Response Guidebook. Material lookup (ID number, name, placard), the guides
 * word for word, and the guidebook's isolation and protective action zones
 * drawn as native ATAK shapes any TAK client can see. Offline; no network
 * code, no permissions.
 */
public final class HazTakPlugin implements IPlugin {

    private static final String TAG = "HazTakPlugin";
    static final String DATA_ASSET = "erg2024.json";

    private Context pluginContext;
    private final IHostUIService uiService;
    private final ToolbarItem toolbarItem;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Pane pane;
    private HazPane controller;

    private MapView mapView;
    private volatile Erg erg;
    private volatile String loadError;
    private volatile boolean started;
    private HazStore store;
    private Sharing sharing;
    private HazDetailHandler detailHandler;
    private HazMenuHandler menuHandler;
    private MapEventDispatcher.MapEventDispatchListener removeListener;

    public HazTakPlugin(IServiceController serviceController) {
        final PluginContextProvider ctxProvider = serviceController
                .getService(PluginContextProvider.class);
        if (ctxProvider != null) {
            pluginContext = ctxProvider.getPluginContext();
            pluginContext.setTheme(R.style.ATAKPluginTheme);
        }
        uiService = serviceController.getService(IHostUIService.class);
        toolbarItem = new ToolbarItem.Builder(
                pluginContext.getString(R.string.app_name),
                MarshalManager.marshal(icon(), android.graphics.drawable.Drawable.class,
                        gov.tak.api.commons.graphics.Bitmap.class))
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        showPane();
                    }
                }).setIdentifier(pluginContext.getPackageName())
                .build();
    }

    private Drawable icon() {
        return pluginContext.getResources().getDrawable(R.drawable.ic_launcher,
                pluginContext.getTheme());
    }

    @Override
    public void onStart() {
        started = true;
        mapView = MapView.getMapView();
        if (uiService != null)
            uiService.addToolbarItem(toolbarItem);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Erg loaded = Erg.parse(readAsset(DATA_ASSET));
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (started)
                                ready(loaded);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "ERG data could not be loaded", e);
                    loadError = e.getClass().getSimpleName() + ": " + e.getMessage();
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (controller != null)
                                controller.onShow();
                        }
                    });
                }
            }
        }, "HAZ-TAK data");
        t.setDaemon(true);
        t.start();
    }

    private String readAsset(String name) throws java.io.IOException {
        try (InputStream in = pluginContext.getAssets().open(name)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 20);
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0)
                bo.write(buf, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Data loaded: wire everything up (main thread). */
    private void ready(Erg loaded) {
        erg = loaded;
        store = new HazStore(mapView, erg);
        sharing = new Sharing(store);
        try {
            detailHandler = new HazDetailHandler(store);
            detailHandler.setOnChange(new Runnable() {
                @Override
                public void run() {
                    if (controller != null)
                        controller.onShow();
                }
            });
            CotDetailManager.getInstance().registerHandler(detailHandler);
        } catch (RuntimeException e) {
            Log.e(TAG, "detail handler registration failed", e);
        }
        try {
            menuHandler = new HazMenuHandler(mapView.getContext(), pluginContext,
                    R.drawable.ic_haztak_radial, store, new HazMenuHandler.Listener() {
                        @Override
                        public void openZone(Marker zone) {
                            if (showPane() && controller != null)
                                controller.openIncident(zone);
                        }

                        @Override
                        public void placeAt(Marker target) {
                            if (showPane() && controller != null)
                                controller.placeAt(target);
                        }
                    });
            MapMenuReceiver.getInstance().registerMapMenuHandler(menuHandler);
        } catch (RuntimeException e) {
            Log.e(TAG, "radial menu registration failed", e);
        }
        removeListener = new MapEventDispatcher.MapEventDispatchListener() {
            @Override
            public void onMapEvent(MapEvent event) {
                try {
                    MapItem item = event.getItem();
                    if (item == null)
                        return;
                    if ((item instanceof Marker
                            && item.getMetaBoolean(HazStore.META_FLAG, false))
                            || HazStore.isShape(item))
                        store.partRemoved(item.getUID());
                } catch (RuntimeException e) {
                    Log.e(TAG, "incident cleanup failed", e);
                }
            }
        };
        mapView.getMapEventDispatcher().addMapEventListener(MapEvent.ITEM_REMOVED,
                removeListener);
        try {
            for (Marker m : store.all()) {
                HazRecord r = store.read(m);
                if (r != null)
                    store.ensureShapes(m, r);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "startup adoption failed", e);
        }
        if (controller != null)
            controller.setData(store, sharing);
    }

    @Override
    public void onStop() {
        started = false;
        main.removeCallbacksAndMessages(null);
        if (uiService != null) {
            if (pane != null && uiService.isPaneVisible(pane))
                uiService.closePane(pane);
            uiService.removeToolbarItem(toolbarItem);
        }
        if (menuHandler != null) {
            try {
                MapMenuReceiver.getInstance().unregisterMapMenuHandler(menuHandler);
            } catch (RuntimeException e) {
                Log.e(TAG, "radial menu unregister failed", e);
            }
            menuHandler = null;
        }
        if (detailHandler != null) {
            try {
                CotDetailManager.getInstance().unregisterHandler(detailHandler);
            } catch (RuntimeException e) {
                Log.e(TAG, "detail handler unregister failed", e);
            }
            detailHandler.dispose();
            detailHandler = null;
        }
        if (removeListener != null && mapView != null) {
            mapView.getMapEventDispatcher().removeMapEventListener(
                    MapEvent.ITEM_REMOVED, removeListener);
            removeListener = null;
        }
        if (controller != null) {
            controller.dispose();
            controller = null;
            pane = null;
        }
        if (store != null)
            store.dispose();
    }

    /** Show the pane; false if ATAK offers no UI service. */
    private boolean showPane() {
        if (uiService == null || mapView == null)
            return false;
        if (pane == null) {
            controller = new HazPane(pluginContext, mapView);
            if (store != null)
                controller.setData(store, sharing);
            pane = new PaneBuilder(controller.getView())
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.45D)
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.6D)
                    .build();
        }
        controller.setLoadError(loadError);
        if (!uiService.isPaneVisible(pane)) {
            // Opening the pane: nothing pre-selected for a new incident.
            controller.resetNew();
            controller.onShow();
            uiService.showPane(pane, null);
        } else {
            controller.onShow();
        }
        return true;
    }
}
