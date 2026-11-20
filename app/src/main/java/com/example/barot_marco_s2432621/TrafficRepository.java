package com.example.barot_marco_s2432621;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrafficRepository {

    private static final String UNPLANNED_EVENTS_URL = "https://datex2.trafficscotland.org/rest/2.3/publications/UnplannedEvents/Content.xml";
    private static final String ROADWORKS_URL = "https://datex2.trafficscotland.org/rest/2.3/publications/CurrentRoadworks/Content.xml";

    private final ExecutorService executorService;
    private final Context context;

    public TrafficRepository(Context context) {
        this.context = context.getApplicationContext();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    private void saveToCache(String fileName, String data) {
        try (FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE)) {
            fos.write(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e("TrafficRepository", "Error saving cache file: " + fileName, e);
        }
    }

    private String readFromCache(String fileName) throws Exception {
        File file = new File(context.getFilesDir(), fileName);
        if (!file.exists()) {
            throw new Exception("Cache file does not exist");
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.openFileInput(fileName), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private String downloadXml(String urlString) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(10000);
            connection.setConnectTimeout(15000);
            connection.setRequestMethod("GET");
            connection.setDoInput(true);

            String credentials = BuildConfig.CLIENT_ID + ":" + BuildConfig.CLIENT_KEY;
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            connection.setRequestProperty("Authorization", "Basic " + encodedCredentials);

            int responseCode = connection.getResponseCode();
            Log.d("TrafficDebug", "HTTP response code: " + responseCode + " for URL: " + urlString);
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP Error: " + responseCode);
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            return sb.toString();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public void fetchTrafficData(MutableLiveData<ArrayList<TrafficSituations>> dataLiveData,
                                 MutableLiveData<String> statusLiveData,
                                 MutableLiveData<Boolean> isDownloadingLiveData,
                                 MutableLiveData<String> updateTimeLiveData,
                                 MutableLiveData<Boolean> isCachedLiveData) {
                                     
        isDownloadingLiveData.postValue(true);
        statusLiveData.postValue("Downloading incidents and roadworks...");

        executorService.execute(() -> {
            Log.d("TrafficDebug", "Repository background thread started");
            boolean isCachedData = false;
            String incidentsXml = null;
            String roadworksXml = null;
            String updateTime = null;

            try {
                incidentsXml = downloadXml(UNPLANNED_EVENTS_URL);
                roadworksXml = downloadXml(ROADWORKS_URL);

                Log.d("TrafficDebug", "Downloaded XML lengths: incidents=" + (incidentsXml != null ? incidentsXml.length() : 0) + ", roadworks=" + (roadworksXml != null ? roadworksXml.length() : 0));

                if (incidentsXml == null || incidentsXml.isEmpty() || roadworksXml == null || roadworksXml.isEmpty()) {
                    throw new Exception("Downloaded XML is empty");
                }

                saveToCache("incidents_cache.xml", incidentsXml);
                saveToCache("roadworks_cache.xml", roadworksXml);

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK);
                updateTime = OffsetDateTime.now().format(formatter);
                context.getSharedPreferences("TrafficAppPrefs", Context.MODE_PRIVATE)
                        .edit().putString("last_update_time", updateTime).apply();

            } catch (Exception networkException) {
                Log.e("TrafficRepository", "Network error, attempting to load cache", networkException);

                try {
                    incidentsXml = readFromCache("incidents_cache.xml");
                    roadworksXml = readFromCache("roadworks_cache.xml");

                    if (incidentsXml == null || incidentsXml.isEmpty() || roadworksXml == null || roadworksXml.isEmpty()) {
                        throw new Exception("Cache is empty or invalid");
                    }

                    updateTime = context.getSharedPreferences("TrafficAppPrefs", Context.MODE_PRIVATE)
                            .getString("last_update_time", "Unknown");

                    isCachedData = true;
                } catch (Exception cacheException) {
                    Log.e("TrafficRepository", "Error loading offline data", cacheException);
                    isDownloadingLiveData.postValue(false);
                    statusLiveData.postValue("Unable to download traffic information and no offline data available.");
                    return;
                }
            }

            try {
                TrafficXmlParser parser = new TrafficXmlParser();

                ArrayList<TrafficSituations> incidents = parser.parse(incidentsXml);
                for (TrafficSituations situation : incidents) {
                    situation.setCategory("INCIDENT");
                }

                ArrayList<TrafficSituations> roadworks = parser.parse(roadworksXml);
                for (TrafficSituations situation : roadworks) {
                    situation.setCategory("ROADWORKS");
                }

                ArrayList<TrafficSituations> allSituations = new ArrayList<>();
                allSituations.addAll(incidents);
                allSituations.addAll(roadworks);
                
                Log.d("TrafficDebug", "Parsed incidents count: " + incidents.size());
                Log.d("TrafficDebug", "Parsed roadworks count: " + roadworks.size());
                Log.d("TrafficDebug", "Total situations: " + allSituations.size());

                updateTimeLiveData.postValue(updateTime + (isCachedData ? " (Offline Data)" : ""));
                isCachedLiveData.postValue(isCachedData);
                dataLiveData.postValue(allSituations);
                
                isDownloadingLiveData.postValue(false);
                Log.d("TrafficDebug", "Successfully posted LiveData with " + allSituations.size() + " items");

            } catch (Exception e) {
                Log.e("TrafficDebug", "Error parsing traffic data", e);
                isDownloadingLiveData.postValue(false);
                statusLiveData.postValue("Unable to parse traffic information.");
            }
        });
    }
}