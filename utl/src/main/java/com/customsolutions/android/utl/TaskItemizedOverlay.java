package com.customsolutions.android.utl;

import java.util.ArrayList;

import android.graphics.Point;
import android.graphics.drawable.Drawable;

import org.osmdroid.api.IMapView;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.OverlayItem;

public class TaskItemizedOverlay extends BalloonItemizedOverlay<OverlayItem>
{
	private ArrayList<OverlayItem> _overlays = new ArrayList<OverlayItem>();

	public TaskItemizedOverlay(Drawable defaultMarker, MapView mapView)
	{
		super(defaultMarker,mapView);
		// super(boundCenterBottom(defaultMarker),mapView);
		
		// The default marker is assumed here to be bottom-centered.  Get the balloon
		// vertical offset based on the drawable size:
		setBalloonBottomOffset(defaultMarker.getIntrinsicHeight());
	}

	@Override
	protected OverlayItem createItem(int i)
	{
		return _overlays.get(i);
	}

	@Override
	public int size()
	{
		return _overlays.size();
	}

	public void addOverlay(OverlayItem overlay, UTLLocation loc) 
	{
		overlay.setMarkerHotspot(OverlayItem.HotspotPlace.BOTTOM_CENTER);
		// boundCenterBottom(overlay.getMarker(0));
	    _overlays.add(overlay);
	    _locations.add(loc);
	    populate();
	}
	
	public void clearAll()
	{
		_overlays.clear();
		_locations.clear();
		populate();
	}
	
	// Handle a tap on an item:
	@Override
	protected boolean onBalloonTap(int index, OverlayItem item)
	{
		return true;
	}

	@Override
	public boolean onSnapToItem(int x, int y, Point snapPoint, IMapView mapView)
	{
		return false;
	}
}
