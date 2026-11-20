package com.example.barot_marco_s2432621;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class TrafficMapFragment extends Fragment implements OnMapReadyCallback {

    private GoogleMap mMap;
    private TrafficViewModel viewModel;
    private final HashMap<String, TrafficSituations> markerMap = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_traffic_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(TrafficViewModel.class);

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Toast.makeText(requireContext(), "Failed to load map.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Nullable
            @Override
            public View getInfoWindow(@NonNull Marker marker) {
                return null;
            }

            @Nullable
            @Override
            public View getInfoContents(@NonNull Marker marker) {
                LinearLayout info = new LinearLayout(requireContext());
                info.setOrientation(LinearLayout.VERTICAL);

                TextView title = new TextView(requireContext());
                title.setTextColor(Color.BLACK);
                title.setGravity(Gravity.CENTER);
                title.setTypeface(null, Typeface.BOLD);
                title.setText(marker.getTitle());

                TextView snippet = new TextView(requireContext());
                snippet.setTextColor(Color.DKGRAY);
                snippet.setText(marker.getSnippet());
                snippet.setMaxWidth(800);

                info.addView(title);
                info.addView(snippet);
                return info;
            }
        });

        mMap.setOnInfoWindowClickListener(marker -> {
            TrafficSituations situation = markerMap.get(marker.getId());
            if (situation != null) {
                showSituationDetails(situation);
            }
        });

        viewModel.getDisplayedSituations().observe(getViewLifecycleOwner(), this::updateMapMarkers);
    }

    private void updateMapMarkers(ArrayList<TrafficSituations> situations) {
        if (mMap == null) return;
        mMap.clear();
        markerMap.clear();

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        int validMarkersCount = 0;
        LatLng singlePosition = null;

        for (TrafficSituations situation : situations) {
            String category = situation.getCategory();
            float markerColor = BitmapDescriptorFactory.HUE_RED;
            int polylineColor = Color.RED;
            if ("ROADWORKS".equals(category)) {
                markerColor = BitmapDescriptorFactory.HUE_ORANGE;
                polylineColor = Color.parseColor("#FF9800");
            }

            String roadName = situation.getRoadName() != null ? situation.getRoadName() : "Unknown Road";
            String title = roadName + " - " + formatSituationType(situation.getType());

            String description = situation.getDescription();
            if (description != null && description.length() > 80) {
                description = description.substring(0, 77) + "...";
            } else if (description == null) {
                description = "No description available";
            }

            // Draw polyline if multiple valid coordinates exist for road segment
            ArrayList<TrafficSituations.Coordinate> coords = situation.getCoordinates();
            if (coords != null && coords.size() >= 2) {
                PolylineOptions polylineOptions = new PolylineOptions()
                        .color(polylineColor)
                        .width(10f);

                for (TrafficSituations.Coordinate coord : coords) {
                    if (coord.latitude >= -90.0 && coord.latitude <= 90.0
                            && coord.longitude >= -180.0 && coord.longitude <= 180.0
                            && (coord.latitude != 0.0 || coord.longitude != 0.0)) {

                        LatLng pt = new LatLng(coord.latitude, coord.longitude);
                        polylineOptions.add(pt);
                        builder.include(pt);
                        validMarkersCount++;
                        singlePosition = pt;
                    }
                }

                if (polylineOptions.getPoints().size() >= 2) {
                    mMap.addPolyline(polylineOptions);
                }
            }

            // Primary single marker position
            double lat = situation.getLatitude();
            double lng = situation.getLongitude();

            if (!Double.isNaN(lat) && !Double.isNaN(lng)
                    && lat >= -90.0 && lat <= 90.0
                    && lng >= -180.0 && lng <= 180.0
                    && (lat != 0.0 || lng != 0.0)) {

                LatLng position = new LatLng(lat, lng);
                builder.include(position);
                validMarkersCount++;
                singlePosition = position;

                Marker marker = mMap.addMarker(new MarkerOptions()
                        .position(position)
                        .title(title)
                        .snippet(description)
                        .icon(BitmapDescriptorFactory.defaultMarker(markerColor)));

                if (marker != null) {
                    markerMap.put(marker.getId(), situation);
                }
            }
        }

        if (validMarkersCount > 1) {
            mMap.setOnMapLoadedCallback(() -> {
                try {
                    LatLngBounds bounds = builder.build();
                    int padding = 150;
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
                } catch (Exception e) {
                    Log.e("TrafficMapFragment", "Error setting bounds", e);
                }
            });
        } else if (validMarkersCount == 1 && singlePosition != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(singlePosition, 12));
        } else {
            LatLng scotland = new LatLng(56.4907, -4.2026);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(scotland, 7));
        }
    }

    private void showSituationDetails(TrafficSituations situation) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_traffic_details, null);
        TextView roadText = dialogView.findViewById(R.id.detailRoad);
        TextView typeText = dialogView.findViewById(R.id.detailType);
        TextView descriptionText = dialogView.findViewById(R.id.detailDescription);
        TextView impactText = dialogView.findViewById(R.id.detailImpact);
        TextView startDateText = dialogView.findViewById(R.id.detailStartDate);
        TextView endDateText = dialogView.findViewById(R.id.detailEndDate);

        roadText.setText(formatValue(situation.getRoadName()));
        typeText.setText(formatSituationType(situation.getType()));
        descriptionText.setText(formatValue(situation.getDescription()));

        String impact = situation.getImpact();
        if (impact != null && impact.startsWith("numberOfOperationalLanes:")) {
            impact = "Operational lanes: " + impact.substring("numberOfOperationalLanes:".length()).trim();
        }
        impactText.setText(formatValue(impact));

        startDateText.setText(formatDate(situation.getStartDate()));
        endDateText.setText(formatDate(situation.getEndDate()));

        new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .show();
    }

    private String formatSituationType(String type) {
        if (type == null || type.trim().isEmpty()) return "Unknown incident";
        switch (type) {
            case "RoadOrCarriagewayOrLaneManagement": return "Road Management";
            case "Accident": return "Traffic Accident";
            case "MaintenanceWorks": return "Maintenance Works";
            case "ConstructionWorks": return "Construction Works";
            case "VehicleObstruction": return "Vehicle Obstruction";
            case "GeneralObstruction": return "Road Obstruction";
            default: return type;
        }
    }

    private String formatDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) return "Not available";
        try {
            OffsetDateTime date = OffsetDateTime.parse(dateString);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM yyyy, h:mm a", Locale.UK);
            return date.format(formatter);
        } catch (Exception e) {
            return dateString;
        }
    }

    private String formatValue(String value) {
        return (value == null || value.trim().isEmpty()) ? "Not available" : value;
    }
}