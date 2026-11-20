package com.example.barot_marco_s2432621;

import java.io.Serializable;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;

public class TrafficSituations implements Serializable {

    private String id;
    private String type;
    private String description;
    private String roadName;
    private String impact;
    private String startDate;
    private String endDate;

    private double latitude = Double.NaN;
    private double longitude = Double.NaN;
    private final ArrayList<Coordinate> coordinates = new ArrayList<>();

    public static class Coordinate implements Serializable {
        public double latitude;
        public double longitude;

        public Coordinate(double lat, double lon) {
            this.latitude = lat;
            this.longitude = lon;
        }
    }

    private String category;

    public TrafficSituations(
            String id,
            String type,
            String description,
            String roadName) {

        this.id = id;
        this.type = type;
        this.description = description;
        this.roadName = roadName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRoadName() {
        return roadName;
    }

    public void setRoadName(String roadName) {
        this.roadName = roadName;
    }

    public String getImpact() {
        return impact;
    }

    public void setImpact(String impact) {
        this.impact = impact;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public void addCoordinate(double lat, double lon) {
        if (!Double.isNaN(lat) && !Double.isNaN(lon)
                && lat >= -90.0 && lat <= 90.0
                && lon >= -180.0 && lon <= 180.0
                && (lat != 0.0 || lon != 0.0)) {

            if (Double.isNaN(this.latitude)) {
                this.latitude = lat;
                this.longitude = lon;
            }

            // Deduplicate consecutive identical points
            if (!coordinates.isEmpty()) {
                Coordinate last = coordinates.get(coordinates.size() - 1);
                if (Math.abs(last.latitude - lat) < 0.000001 && Math.abs(last.longitude - lon) < 0.000001) {
                    return;
                }
            }
            coordinates.add(new Coordinate(lat, lon));
        }
    }

    public ArrayList<Coordinate> getCoordinates() {
        return coordinates;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public long getDurationHours() {
        if (startDate == null || endDate == null
                || startDate.isEmpty() || endDate.isEmpty()) {
            return -1;
        }

        try {
            OffsetDateTime start = OffsetDateTime.parse(startDate);
            OffsetDateTime end = OffsetDateTime.parse(endDate);

            long minutes = Duration.between(start, end).toMinutes();

            if (minutes < 0) {
                return -1;
            }

            return (minutes + 59) / 60;

        } catch (Exception e) {
            return -1;
        }
    }

    public String getDurationCategory() {
        long hours = getDurationHours();

        if (hours == -1) {
            return "UNKNOWN";
        }

        if (hours < 24) {
            return "SHORT";
        }

        return "LONG";
    }
}