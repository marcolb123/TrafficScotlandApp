package com.example.barot_marco_s2432621;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TrafficXmlParser {

    private static final String TAG = "TrafficParser";

    private void logD(String message) {
        try {
            Log.d(TAG, message);
        } catch (Throwable ignored) {}
    }

    private void logE(String message, Throwable t) {
        try {
            Log.e(TAG, message, t);
        } catch (Throwable ignored) {}
    }

    public ArrayList<TrafficSituations> parse(String xmlData) throws Exception {

        if (xmlData == null || xmlData.trim().isEmpty()) {
            throw new Exception("XML data is empty");
        }

        ArrayList<TrafficSituations> situations = new ArrayList<>();

        try {
            XmlPullParser parser = null;
            try {
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                factory.setNamespaceAware(true);
                parser = factory.newPullParser();
            } catch (Throwable e) {
                try {
                    Class<?> kxmlClass = Class.forName("org.kxml2.io.KXmlParser");
                    parser = (XmlPullParser) kxmlClass.getDeclaredConstructor().newInstance();
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
                } catch (Throwable t) {
                    throw new Exception("Unable to create XmlPullParser: " + e.getMessage(), e);
                }
            }

            parser.setInput(new StringReader(xmlData));
            int eventType = parser.getEventType();

            String id = "";
            String type = "";
            StringBuilder descriptionBuilder = new StringBuilder();
            String roadNumber = "";
            String roadName = "";
            String impact = "";
            String startDate = "";
            String endDate = "";

            double currentLat = Double.NaN;
            double currentLon = Double.NaN;

            TrafficSituations currentSituation = null;
            boolean insideSituation = false;
            boolean insideComment = false;
            int commentDepth = -1;

            String currentTag = "";

            while (eventType != XmlPullParser.END_DOCUMENT) {

                switch (eventType) {

                    case XmlPullParser.START_TAG:
                        currentTag = parser.getName();

                        if (currentTag.equalsIgnoreCase("situation")) {
                            insideSituation = true;
                            insideComment = false;
                            commentDepth = -1;

                            id = parser.getAttributeValue(null, "id");
                            if (id == null) id = "";

                            type = "";
                            descriptionBuilder.setLength(0);
                            roadNumber = "";
                            roadName = "";
                            impact = "";
                            startDate = "";
                            endDate = "";
                            currentLat = Double.NaN;
                            currentLon = Double.NaN;

                            currentSituation = new TrafficSituations(id, "", "", "");
                        }
                        else if (insideSituation && currentTag.equalsIgnoreCase("situationRecord")) {
                            if (id.isEmpty()) {
                                String recordId = parser.getAttributeValue(null, "id");
                                if (recordId != null && !recordId.isEmpty()) {
                                    id = recordId;
                                }
                            }

                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attrName = parser.getAttributeName(i);
                                if (attrName.equalsIgnoreCase("type") || attrName.toLowerCase().endsWith(":type")) {
                                    String attrVal = parser.getAttributeValue(i);
                                    if (attrVal != null && !attrVal.isEmpty()) {
                                        if (attrVal.contains(":")) {
                                            attrVal = attrVal.substring(attrVal.indexOf(":") + 1);
                                        }
                                        type = attrVal;
                                        break;
                                    }
                                }
                            }
                        }
                        else if (insideSituation && currentTag.equalsIgnoreCase("generalPublicComment")) {
                            insideComment = true;
                            commentDepth = parser.getDepth();
                        }
                        break;

                    case XmlPullParser.TEXT:
                        String text = parser.getText();
                        if (text != null) {
                            text = text.trim();
                        }

                        if (insideSituation && text != null && !text.isEmpty()) {

                            if (insideComment) {
                                if (currentTag.equalsIgnoreCase("value")
                                        || currentTag.equalsIgnoreCase("comment")
                                        || currentTag.equalsIgnoreCase("text")
                                        || currentTag.equalsIgnoreCase("generalPublicComment")) {

                                    if (descriptionBuilder.length() > 0) {
                                        descriptionBuilder.append(" ");
                                    }
                                    descriptionBuilder.append(text);
                                }
                            }
                            else if (currentTag.equalsIgnoreCase("roadNumber")) {
                                if (roadNumber.isEmpty()) {
                                    roadNumber = text;
                                }
                            }
                            else if (currentTag.equalsIgnoreCase("roadName")) {
                                if (roadName.isEmpty()) {
                                    roadName = text;
                                }
                            }
                            else if (currentTag.equalsIgnoreCase("capacityRemaining")
                                    || currentTag.equalsIgnoreCase("trafficStatus")
                                    || currentTag.equalsIgnoreCase("impactOnTraffic")
                                    || currentTag.equalsIgnoreCase("delays")
                                    || currentTag.equalsIgnoreCase("numberOfLanesRestricted")
                                    || currentTag.equalsIgnoreCase("numberOfOperationalLanes")
                                    || currentTag.equalsIgnoreCase("trafficConstrictionType")
                                    || currentTag.equalsIgnoreCase("delayTimeValue")) {

                                if (impact.isEmpty()) {
                                    impact = currentTag + ": " + text;
                                }
                            }
                            else if (currentTag.equalsIgnoreCase("overallStartTime")
                                    || currentTag.equalsIgnoreCase("startTime")) {
                                if (startDate.isEmpty()) {
                                    startDate = text;
                                }
                            }
                            else if (currentTag.equalsIgnoreCase("overallEndTime")
                                    || currentTag.equalsIgnoreCase("endTime")) {
                                if (endDate.isEmpty()) {
                                    endDate = text;
                                }
                            }
                            else if (currentTag.equalsIgnoreCase("latitude")) {
                                try {
                                    currentLat = Double.parseDouble(text);
                                } catch (NumberFormatException e) {
                                    logE("Invalid latitude: " + text, e);
                                    currentLat = Double.NaN;
                                }
                            }
                            else if (currentTag.equalsIgnoreCase("longitude")) {
                                try {
                                    currentLon = Double.parseDouble(text);
                                } catch (NumberFormatException e) {
                                    logE("Invalid longitude: " + text, e);
                                    currentLon = Double.NaN;
                                }
                            }

                            if (!Double.isNaN(currentLat) && !Double.isNaN(currentLon) && currentSituation != null) {
                                currentSituation.addCoordinate(currentLat, currentLon);
                                currentLat = Double.NaN;
                                currentLon = Double.NaN;
                            }
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        String endTag = parser.getName();

                        if (endTag.equalsIgnoreCase("generalPublicComment")
                                && insideComment
                                && parser.getDepth() == commentDepth) {
                            insideComment = false;
                            commentDepth = -1;
                        }

                        if (endTag.equalsIgnoreCase("situation") && insideSituation) {
                            String finalDescription = descriptionBuilder.toString().trim();
                            String finalRoad = roadName;
                            if (finalRoad.isEmpty()) {
                                finalRoad = roadNumber;
                            }
                            if (finalRoad.isEmpty()) {
                                finalRoad = extractRoadName(finalDescription);
                            }

                            if (currentSituation != null) {
                                currentSituation.setId(id);
                                currentSituation.setType(type);
                                currentSituation.setDescription(finalDescription);
                                currentSituation.setRoadName(finalRoad);
                                currentSituation.setImpact(impact);
                                currentSituation.setStartDate(startDate);
                                currentSituation.setEndDate(endDate);

                                situations.add(currentSituation);
                            }

                            insideSituation = false;
                            insideComment = false;
                            commentDepth = -1;
                            currentSituation = null;
                        }

                        currentTag = "";
                        break;
                }

                eventType = parser.next();
            }

        } catch (XmlPullParserException e) {
            logE("XML PullParser Exception", e);
            throw new Exception("Malformed XML structure: " + e.getMessage(), e);
        } catch (Exception e) {
            logE("Parsing Exception", e);
            throw e;
        }

        logD("Total situations parsed: " + situations.size());
        return situations;
    }

    private String extractRoadName(String description) {
        if (description == null || description.isEmpty()) {
            return "Not available";
        }

        Pattern pattern = Pattern.compile(
                "\\b(?:M|A|B)\\d{1,4}(?:\\(M\\))?\\b",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(description);

        if (matcher.find()) {
            return matcher.group();
        }

        return "Not available";
    }
}