package com.example.barot_marco_s2432621;

import org.junit.Test;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class TrafficSituationsTest {

    @Test
    public void testDataModelGettersAndSetters() {
        TrafficSituations situation = new TrafficSituations("S1", "Accident", "Accident on road", "M8");
        situation.setCategory("INCIDENT");
        situation.setImpact("numberOfOperationalLanes: 2");
        situation.setStartDate("2026-03-01T08:00:00Z");
        situation.setEndDate("2026-03-01T12:00:00Z");

        assertEquals("S1", situation.getId());
        assertEquals("Accident", situation.getType());
        assertEquals("Accident on road", situation.getDescription());
        assertEquals("M8", situation.getRoadName());
        assertEquals("INCIDENT", situation.getCategory());
        assertEquals("numberOfOperationalLanes: 2", situation.getImpact());
        assertEquals("2026-03-01T08:00:00Z", situation.getStartDate());
        assertEquals("2026-03-01T12:00:00Z", situation.getEndDate());
    }

    @Test
    public void testShortDurationCalculation() {
        TrafficSituations situation = new TrafficSituations("S2", "MaintenanceWorks", "Roadworks", "A82");
        situation.setStartDate("2026-03-01T08:00:00Z");
        situation.setEndDate("2026-03-01T16:00:00Z"); // 8 hours

        assertEquals(8, situation.getDurationHours());
        assertEquals("SHORT", situation.getDurationCategory());
    }

    @Test
    public void testLongDurationCalculation() {
        TrafficSituations situation = new TrafficSituations("S3", "ConstructionWorks", "Major Roadworks", "M74");
        situation.setStartDate("2026-03-01T08:00:00Z");
        situation.setEndDate("2026-03-05T08:00:00Z"); // 96 hours

        assertEquals(96, situation.getDurationHours());
        assertEquals("LONG", situation.getDurationCategory());
    }

    @Test
    public void testUnknownDurationCalculation() {
        TrafficSituations situation = new TrafficSituations("S4", "Accident", "Accident", "A9");
        // Missing dates
        assertEquals(-1, situation.getDurationHours());
        assertEquals("UNKNOWN", situation.getDurationCategory());

        // Malformed dates
        situation.setStartDate("INVALID_DATE");
        situation.setEndDate("INVALID_DATE");
        assertEquals(-1, situation.getDurationHours());
        assertEquals("UNKNOWN", situation.getDurationCategory());
    }

    @Test
    public void testCoordinateValidationAndDeduplication() {
        TrafficSituations situation = new TrafficSituations("S5", "Accident", "Accident", "A1");

        // Valid coordinates
        situation.addCoordinate(55.8642, -4.2518);
        assertEquals(55.8642, situation.getLatitude(), 0.0001);
        assertEquals(-4.2518, situation.getLongitude(), 0.0001);

        // Duplicate coordinate - should be ignored
        situation.addCoordinate(55.8642, -4.2518);
        assertEquals(1, situation.getCoordinates().size());

        // Second valid distinct coordinate for segment
        situation.addCoordinate(55.8700, -4.2400);
        assertEquals(2, situation.getCoordinates().size());

        // Invalid latitude (> 90) - should be ignored
        situation.addCoordinate(95.0, -4.2000);
        assertEquals(2, situation.getCoordinates().size());

        // Invalid longitude (< -180) - should be ignored
        situation.addCoordinate(55.0, -190.0);
        assertEquals(2, situation.getCoordinates().size());

        // Invalid zero coordinates (0, 0) - should be ignored
        situation.addCoordinate(0.0, 0.0);
        assertEquals(2, situation.getCoordinates().size());
    }

    @Test
    public void testRoadNameRegexMatching() {
        Pattern pattern = Pattern.compile("\\b(?:M|A|B)\\d{1,4}(?:\\(M\\))?\\b", Pattern.CASE_INSENSITIVE);

        Matcher m1 = pattern.matcher("Delays on M8 Eastbound Junction 15");
        assertTrue(m1.find());
        assertEquals("M8", m1.group());

        Matcher m2 = pattern.matcher("Roadworks near A82 Loch Lomond");
        assertTrue(m2.find());
        assertEquals("A82", m2.group());

        Matcher m3 = pattern.matcher("Incident on B808 Queen Drive");
        assertTrue(m3.find());
        assertEquals("B808", m3.group());

        Matcher m4 = pattern.matcher("General road closure in Glasgow City Centre");
        assertFalse(m4.find());
    }

    @Test
    public void testHaversineDistanceCalculation() {
        // Glasgow reference coordinates
        double glasgowLat = 55.8642;
        double glasgowLon = -4.2518;

        // Paisley coordinates (approx 11 km from Glasgow)
        double paisleyLat = 55.8456;
        double paisleyLon = -4.4239;

        // Edinburgh coordinates (approx 67 km from Glasgow)
        double edinburghLat = 55.9533;
        double edinburghLon = -3.1883;

        double distPaisley = calculateHaversineKm(glasgowLat, glasgowLon, paisleyLat, paisleyLon);
        double distEdinburgh = calculateHaversineKm(glasgowLat, glasgowLon, edinburghLat, edinburghLon);

        assertTrue("Paisley should be within 15km of Glasgow", distPaisley <= 15.0);
        assertTrue("Edinburgh should be further than 50km from Glasgow", distEdinburgh > 50.0);
    }

    private double calculateHaversineKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}