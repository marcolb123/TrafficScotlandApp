package com.example.barot_marco_s2432621;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

public class TrafficListFragment extends Fragment {

    private TrafficViewModel viewModel;

    private Button startButton;
    private TextView statusText;
    private ListView trafficListView;
    private EditText searchRoadInput;
    private Button searchRoadButton;
    private Button resetSearchButton;
    private EditText distanceInput;
    private Button searchDistanceButton;

    private ArrayAdapter<String> trafficAdapter;
    private ArrayList<String> trafficItems = new ArrayList<>();
    private ArrayList<TrafficSituations> displayedSituations = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_traffic_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind views
        startButton = view.findViewById(R.id.startButton);
        statusText = view.findViewById(R.id.statusText);
        trafficListView = view.findViewById(R.id.trafficListView);
        searchRoadInput = view.findViewById(R.id.searchRoadInput);
        searchRoadButton = view.findViewById(R.id.searchRoadButton);
        resetSearchButton = view.findViewById(R.id.resetSearchButton);
        distanceInput = view.findViewById(R.id.distanceInput);
        searchDistanceButton = view.findViewById(R.id.searchDistanceButton);
        Button btnShowMap = view.findViewById(R.id.btnShowMap);

        // Adapter
        trafficAdapter = new ArrayAdapter<String>(requireContext(), R.layout.item_traffic, trafficItems) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View row = convertView;
                if (row == null) {
                    row = getLayoutInflater().inflate(R.layout.item_traffic, parent, false);
                }

                ImageView icon = row.findViewById(R.id.trafficIcon);
                TextView typeText = row.findViewById(R.id.trafficType);
                TextView roadText = row.findViewById(R.id.trafficRoad);
                TextView descriptionText = row.findViewById(R.id.trafficDescription);

                if (position < displayedSituations.size()) {
                    TrafficSituations situation = displayedSituations.get(position);

                    icon.setImageResource(getSituationIcon(situation));
                    typeText.setText(formatSituationType(situation.getType()));

                    String category = situation.getCategory() == null ? "UNKNOWN" : situation.getCategory();
                    roadText.setText("[" + category + "] " + formatValue(situation.getRoadName()));
                    descriptionText.setText(formatValue(situation.getDescription()));

                    row.setBackgroundColor(Color.WHITE);
                    if ("ROADWORKS".equals(category)) {
                        String duration = situation.getDurationCategory();
                        if ("LONG".equals(duration)) {
                            row.setBackgroundColor(Color.parseColor("#FFF3E0"));
                        } else if ("SHORT".equals(duration)) {
                            row.setBackgroundColor(Color.parseColor("#F1F8E9"));
                        }
                    } else if ("INCIDENT".equals(category)) {
                        row.setBackgroundColor(Color.parseColor("#FFEBEE"));
                    }
                }
                return row;
            }
        };
        trafficListView.setAdapter(trafficAdapter);

        // List item click
        trafficListView.setOnItemClickListener((parent, view1, position, id) -> {
            if (position < displayedSituations.size()) {
                showSituationDetails(displayedSituations.get(position));
            }
        });

        // Initialize ViewModel (scoping it to the Activity)
        viewModel = new ViewModelProvider(requireActivity()).get(TrafficViewModel.class);

        // Observe ViewModel Data
        viewModel.getDisplayedSituations().observe(getViewLifecycleOwner(), situations -> {
            if (situations == null) return;
            Log.d("TrafficDebug", "Fragment observed displayedSituations change. Count=" + situations.size());
            displayedSituations.clear();
            displayedSituations.addAll(situations);
            
            trafficItems.clear();
            for (TrafficSituations s : situations) {
                trafficItems.add(formatValue(s.getDescription()));
            }
            trafficAdapter.notifyDataSetChanged();
            Log.d("TrafficDebug", "Fragment adapter notified with count=" + trafficItems.size());
        });

        viewModel.getStatusMessage().observe(getViewLifecycleOwner(), msg -> {
            statusText.setText(msg);
        });

        viewModel.getIsDownloading().observe(getViewLifecycleOwner(), isDownloading -> {
            startButton.setEnabled(!isDownloading);
        });

        // Listeners
        startButton.setOnClickListener(v -> viewModel.fetchData());
        
        searchRoadButton.setOnClickListener(v -> {
            viewModel.searchRoadQuery = searchRoadInput.getText().toString();
            viewModel.applyFilters();
        });
        
        searchDistanceButton.setOnClickListener(v -> {
            viewModel.distanceQuery = distanceInput.getText().toString();
            viewModel.applyFilters();
        });
        
        resetSearchButton.setOnClickListener(v -> {
            searchRoadInput.setText("");
            distanceInput.setText("");
            viewModel.searchRoadQuery = "";
            viewModel.distanceQuery = "";
            viewModel.applyFilters();
        });

        btnShowMap.setOnClickListener(v -> {
            // Replace fragment
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new TrafficMapFragment())
                    .addToBackStack(null)
                    .commit();
        });

        // Restore view state
        searchRoadInput.setText(viewModel.searchRoadQuery);
        distanceInput.setText(viewModel.distanceQuery);

        Log.d("TrafficDebug", "Fragment onViewCreated: hasDownloaded=" + viewModel.hasDownloaded());

        // First startup load
        if (!viewModel.hasDownloaded()) {
            Log.d("TrafficDebug", "Fragment calling fetchData");
            viewModel.fetchData();
        } else {
            Log.d("TrafficDebug", "Fragment skipping fetchData because already downloaded");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        viewModel.searchRoadQuery = searchRoadInput.getText().toString();
        viewModel.distanceQuery = distanceInput.getText().toString();
    }

    private int getSituationIcon(TrafficSituations situation) {
        String type = situation.getType() == null ? "" : situation.getType();
        String normalizedType = type.toLowerCase(Locale.ROOT);
        
        if (normalizedType.contains("accident")) {
            return R.drawable.ic_accident;
        } else if (normalizedType.contains("vehicle")) {
            return R.drawable.ic_vehicle;
        } else if (normalizedType.contains("lane") || normalizedType.contains("carriageway")) {
            return R.drawable.ic_lane;
        } else if (normalizedType.contains("obstruction")) {
            return R.drawable.ic_obstruction;
        } else if ("ROADWORKS".equals(situation.getCategory()) || normalizedType.contains("work")) {
            return R.drawable.ic_roadworks;
        }
        return R.drawable.ic_information;
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

    private String formatValue(String value) {
        return (value == null || value.trim().isEmpty()) ? "Not available" : value;
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
}