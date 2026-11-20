package com.example.barot_marco_s2432621;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class TrafficXmlParserTest {

    private TrafficXmlParser parser;

    @Before
    public void setUp() {
        parser = new TrafficXmlParser();
    }

    @Test
    public void testParseIncident() throws Exception {
        String xml = "<situation xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" id=\"INC_101\">" +
                "  <situationRecord xsi:type=\"Accident\" id=\"REC_101\">" +
                "    <overallStartTime>2026-03-01T10:00:00Z</overallStartTime>" +
                "    <overallEndTime>2026-03-01T12:00:00Z</overallEndTime>" +
                "    <generalPublicComment><comment><values><value>Accident on M8 Eastbound</value></values></comment></generalPublicComment>" +
                "    <groupOfLocations>" +
                "      <locationForDisplay>" +
                "        <latitude>55.8642</latitude>" +
                "        <longitude>-4.2518</longitude>" +
                "      </locationForDisplay>" +
                "    </groupOfLocations>" +
                "  </situationRecord>" +
                "</situation>";

        ArrayList<TrafficSituations> results = parser.parse(xml);
        assertEquals(1, results.size());

        TrafficSituations situation = results.get(0);
        assertEquals("INC_101", situation.getId());
        assertEquals("Accident", situation.getType());
        assertEquals("Accident on M8 Eastbound", situation.getDescription());
        assertEquals("M8", situation.getRoadName());
        assertEquals(55.8642, situation.getLatitude(), 0.0001);
        assertEquals(-4.2518, situation.getLongitude(), 0.0001);
    }

    @Test
    public void testParseRoadworks() throws Exception {
        String xml = "<situation xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" id=\"RW_202\">" +
                "  <situationRecord xsi:type=\"MaintenanceWorks\" id=\"REC_202\">" +
                "    <roadName>A82</roadName>" +
                "    <numberOfLanesRestricted>1</numberOfLanesRestricted>" +
                "    <generalPublicComment><comment><values><value>Roadworks near Loch Lomond</value></values></comment></generalPublicComment>" +
                "    <pointCoordinates>" +
                "      <latitude>56.0000</latitude>" +
                "      <longitude>-4.5000</longitude>" +
                "    </pointCoordinates>" +
                "  </situationRecord>" +
                "</situation>";

        ArrayList<TrafficSituations> results = parser.parse(xml);
        assertEquals(1, results.size());

        TrafficSituations situation = results.get(0);
        assertEquals("RW_202", situation.getId());
        assertEquals("MaintenanceWorks", situation.getType());
        assertEquals("A82", situation.getRoadName());
        assertTrue(situation.getImpact().contains("numberOfLanesRestricted"));
        assertEquals(56.0, situation.getLatitude(), 0.0001);
        assertEquals(-4.5, situation.getLongitude(), 0.0001);
    }

    @Test
    public void testParseMissingOptionalFields() throws Exception {
        String xml = "<situation xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" id=\"INC_303\">" +
                "  <situationRecord xsi:type=\"GeneralObstruction\">" +
                "    <generalPublicComment><comment><values><value>Debris on road</value></values></comment></generalPublicComment>" +
                "  </situationRecord>" +
                "</situation>";

        ArrayList<TrafficSituations> results = parser.parse(xml);
        assertEquals(1, results.size());

        TrafficSituations situation = results.get(0);
        assertEquals("INC_303", situation.getId());
        assertEquals("GeneralObstruction", situation.getType());
        assertEquals("Debris on road", situation.getDescription());
        assertEquals("Not available", situation.getRoadName());
        assertTrue(Double.isNaN(situation.getLatitude()));
        assertTrue(Double.isNaN(situation.getLongitude()));
    }

    @Test
    public void testParseMissingCoordinates() throws Exception {
        String xml = "<situation xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" id=\"INC_404\">" +
                "  <situationRecord xsi:type=\"VehicleObstruction\">" +
                "    <generalPublicComment><comment><values><value>Broken down vehicle on A9</value></values></comment></generalPublicComment>" +
                "  </situationRecord>" +
                "</situation>";

        ArrayList<TrafficSituations> results = parser.parse(xml);
        assertEquals(1, results.size());

        TrafficSituations situation = results.get(0);
        assertEquals("A9", situation.getRoadName());
        assertTrue(Double.isNaN(situation.getLatitude()));
        assertEquals(0, situation.getCoordinates().size());
    }

    @Test
    public void testParseMultipleCoordinates() throws Exception {
        String xml = "<situation xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" id=\"RW_505\">" +
                "  <situationRecord xsi:type=\"ConstructionWorks\">" +
                "    <generalPublicComment><comment><values><value>Resurfacing work on M74</value></values></comment></generalPublicComment>" +
                "    <pointCoordinates><latitude>55.7000</latitude><longitude>-4.0000</longitude></pointCoordinates>" +
                "    <pointCoordinates><latitude>55.7500</latitude><longitude>-3.9500</longitude></pointCoordinates>" +
                "  </situationRecord>" +
                "</situation>";

        ArrayList<TrafficSituations> results = parser.parse(xml);
        assertEquals(1, results.size());

        TrafficSituations situation = results.get(0);
        assertEquals(2, situation.getCoordinates().size());
        assertEquals(55.7000, situation.getCoordinates().get(0).latitude, 0.0001);
        assertEquals(-4.0000, situation.getCoordinates().get(0).longitude, 0.0001);
        assertEquals(55.7500, situation.getCoordinates().get(1).latitude, 0.0001);
        assertEquals(-3.9500, situation.getCoordinates().get(1).longitude, 0.0001);
    }

    @Test
    public void testParseUnknownTags() throws Exception {
        String xml = "<situation xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" id=\"INC_606\">" +
                "  <unknownRootTag>" +
                "    <futureField>Unexpected Data</futureField>" +
                "  </unknownRootTag>" +
                "  <situationRecord xsi:type=\"Accident\">" +
                "    <unknownRecordTag>Skip Me</unknownRecordTag>" +
                "    <generalPublicComment><comment><values><value>Accident on A1</value></values></comment></generalPublicComment>" +
                "  </situationRecord>" +
                "</situation>";

        ArrayList<TrafficSituations> results = parser.parse(xml);
        assertEquals(1, results.size());
        assertEquals("A1", results.get(0).getRoadName());
    }

    @Test
    public void testParseInvalidNumericalValues() throws Exception {
        String xml = "<situation xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" id=\"INC_707\">" +
                "  <situationRecord xsi:type=\"Accident\">" +
                "    <pointCoordinates><latitude>NOT_A_NUMBER</latitude><longitude>-4.1000</longitude></pointCoordinates>" +
                "    <generalPublicComment><comment><values><value>Accident on B808</value></values></comment></generalPublicComment>" +
                "  </situationRecord>" +
                "</situation>";

        ArrayList<TrafficSituations> results = parser.parse(xml);
        assertEquals(1, results.size());
        // Invalid latitude should be ignored cleanly without crash
        assertTrue(Double.isNaN(results.get(0).getLatitude()));
    }

    @Test(expected = Exception.class)
    public void testParseMalformedXml() throws Exception {
        String xml = "<situation id=\"INC_808\"><situationRecord><unclosedTag>test</situation>";
        parser.parse(xml);
    }
}