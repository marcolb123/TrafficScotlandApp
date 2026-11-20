package com.example.barot_marco_s2432621;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import android.util.Log;

import java.util.ArrayList;

public class TrafficViewModel extends AndroidViewModel {
    
    private final TrafficRepository repository;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    private final MutableLiveData<ArrayList<TrafficSituations>> allTrafficSituations = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<ArrayList<TrafficSituations>> displayedSituations = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>("Press Start to load traffic incidents");
    private final MutableLiveData<Boolean> isDownloading = new MutableLiveData<>(false);
    private final MutableLiveData<String> lastUpdateTime = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> isCachedData = new MutableLiveData<>(false);
    
    public String searchRoadQuery = "";
    public String distanceQuery = "";
    
    private final long REFRESH_INTERVAL = 60 * 60 * 1000; // 60 minutes
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            fetchData();
        }
    };

    public TrafficViewModel(@NonNull Application application) {
        super(application);
        repository = new TrafficRepository(application);
        
        // Observe repository data changes
        allTrafficSituations.observeForever(situations -> {
            applyFilters();
            scheduleRefresh();
        });
    }
    
    public void fetchData() {
        Log.d("TrafficDebug", "ViewModel fetchData called");
        if (Boolean.TRUE.equals(isDownloading.getValue())) {
            return;
        }
        handler.removeCallbacks(refreshRunnable);
        repository.fetchTrafficData(allTrafficSituations, statusMessage, isDownloading, lastUpdateTime, isCachedData);
    }
    
    private void scheduleRefresh() {
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }
    
    public void applyFilters() {
        Log.d("TrafficDebug", "ViewModel applyFilters triggered");
        ArrayList<TrafficSituations> all = allTrafficSituations.getValue();
        if (all == null) {
            Log.d("TrafficDebug", "applyFilters: allTrafficSituations is null");
            return;
        }
        
        Log.d("TrafficDebug", "applyFilters processing " + all.size() + " situations");
        
        ArrayList<TrafficSituations> results = new ArrayList<>();
        String query = searchRoadQuery.trim().toUpperCase();
        
        if (!query.isEmpty()) {
            for (TrafficSituations situation : all) {
                String roadName = situation.getRoadName();
                String desc = situation.getDescription();
                
                boolean matchesRoad = false;
                if (roadName != null) {
                    matchesRoad = java.util.regex.Pattern.compile("(?<![A-Z0-9])" + java.util.regex.Pattern.quote(query) + "(?![A-Z0-9])").matcher(roadName.toUpperCase()).find();
                }
                boolean matchesDescription = false;
                if (desc != null) {
                    matchesDescription = java.util.regex.Pattern.compile("(?<![A-Z0-9])" + java.util.regex.Pattern.quote(query) + "(?![A-Z0-9])").matcher(desc.toUpperCase()).find();
                }
                
                if (matchesRoad || matchesDescription) {
                    results.add(situation);
                }
            }
            updateStatusForSearch(results.size(), query);
        } else if (!distanceQuery.trim().isEmpty()) {
            try {
                double radiusKm = Double.parseDouble(distanceQuery.trim());
                // Glasgow coords
                android.location.Location glasgow = new android.location.Location("");
                glasgow.setLatitude(55.8642);
                glasgow.setLongitude(-4.2518);
                
                for (TrafficSituations situation : all) {
                    double lat = situation.getLatitude();
                    double lng = situation.getLongitude();
                    if (Double.isNaN(lat) || Double.isNaN(lng)) continue;
                    
                    android.location.Location loc = new android.location.Location("");
                    loc.setLatitude(lat);
                    loc.setLongitude(lng);
                    
                    if (glasgow.distanceTo(loc) / 1000.0 <= radiusKm) {
                        results.add(situation);
                    }
                }
                statusMessage.setValue("Found " + results.size() + " situations within " + radiusKm + " km of Glasgow" + getUpdatedTimeString());
            } catch (Exception e) {
                results.addAll(all);
                statusMessage.setValue("Invalid distance format.");
            }
        } else {
            results.addAll(all);
            
            int incCount = 0;
            int rwCount = 0;
            for (TrafficSituations s : all) {
                if ("INCIDENT".equals(s.getCategory())) incCount++;
                else if ("ROADWORKS".equals(s.getCategory())) rwCount++;
            }
            statusMessage.setValue("Incidents: " + incCount + " | Roadworks: " + rwCount + getUpdatedTimeString());
        }
        
        Log.d("TrafficDebug", "applyFilters finished with " + results.size() + " results");
        displayedSituations.setValue(results);
    }
    
    private void updateStatusForSearch(int size, String query) {
        statusMessage.setValue("Found " + size + " situations for " + query + getUpdatedTimeString());
    }
    
    private String getUpdatedTimeString() {
        String time = lastUpdateTime.getValue();
        if (time != null && !time.isEmpty()) {
            return " (Updated: " + time + ")";
        }
        return "";
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        handler.removeCallbacks(refreshRunnable);
    }

    public boolean hasDownloaded() {
        return allTrafficSituations.getValue() != null && !allTrafficSituations.getValue().isEmpty();
    }
    
    // Getters for LiveData
    public LiveData<ArrayList<TrafficSituations>> getDisplayedSituations() { return displayedSituations; }
    public LiveData<String> getStatusMessage() { return statusMessage; }
    public LiveData<Boolean> getIsDownloading() { return isDownloading; }
    public LiveData<Boolean> getIsCachedData() { return isCachedData; }
}