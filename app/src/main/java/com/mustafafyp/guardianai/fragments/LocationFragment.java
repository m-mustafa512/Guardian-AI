package com.mustafafyp.guardianai.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mustafafyp.guardianai.R;
import com.mustafafyp.guardianai.models.Location; // Ensure this model exists
import com.mustafafyp.guardianai.utils.Constant; // Check your util path

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class LocationFragment extends Fragment {

	private MapView mapView;
	private DatabaseReference databaseReference;
	private String childEmail; // Passed from Activity

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		// Init OSMDroid configuration
		Configuration.getInstance().setUserAgentValue(getActivity().getPackageName());

		View view = inflater.inflate(R.layout.fragment_location, container, false);
		mapView = view.findViewById(R.id.map); // Make sure ID is 'map' in XML

		mapView.setMultiTouchControls(true);
		mapView.getController().setZoom(18.0);

		// Get Child Email from Arguments
		if (getArguments() != null) {
			childEmail = getArguments().getString(Constant.CHILD_EMAIL_EXTRA);
		}

		setupRealtimeTracking();
		return view;
	}

	private void setupRealtimeTracking() {
		if (childEmail == null) return;

		databaseReference = FirebaseDatabase.getInstance().getReference("users").child("childs");

		// Find the child UID by email query
		databaseReference.orderByChild("email").equalTo(childEmail)
				.addListenerForSingleValueEvent(new ValueEventListener() {
					@Override
					public void onDataChange(@NonNull DataSnapshot snapshot) {
						if(snapshot.exists()) {
							for(DataSnapshot child : snapshot.getChildren()) {
								listenToLocation(child.getRef().child("location"));
							}
						}
					}
					@Override
					public void onCancelled(@NonNull DatabaseError error) {}
				});
	}

	private void listenToLocation(DatabaseReference locationRef) {
		locationRef.addValueEventListener(new ValueEventListener() {
			@Override
			public void onDataChange(@NonNull DataSnapshot snapshot) {
				if(snapshot.exists()) {
					// Assuming your Location model has latitude/longitude
					Double lat = snapshot.child("latitude").getValue(Double.class);
					Double lng = snapshot.child("longitude").getValue(Double.class);

					if(lat != null && lng != null) {
						updateMap(lat, lng);
					}
				}
			}
			@Override
			public void onCancelled(@NonNull DatabaseError error) {}
		});
	}

	private void updateMap(double lat, double lng) {
		GeoPoint point = new GeoPoint(lat, lng);
		mapView.getController().setCenter(point);

		mapView.getOverlays().clear();
		Marker marker = new Marker(mapView);
		marker.setPosition(point);
		marker.setTitle("Child Location");
		marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
		marker.setIcon(getResources().getDrawable(R.drawable.ic_location_child)); // Ensure this drawable exists
		mapView.getOverlays().add(marker);
		mapView.invalidate(); // Refresh map
	}
}