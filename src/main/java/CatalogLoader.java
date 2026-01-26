package prereqsolver;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Loads the Cornell course catalog JSON and builds a map of course codes to prerequisite strings.
 *
 * Usage:
 *   CatalogLoader loader = new CatalogLoader("cornell_catalog_FA25_under5000.json");
 *   Map<String, String> prereqMap = loader.load();
 *
 *   // prereqMap: "CS 2110" -> "CS 1110 or CS 1112"
 */
public class CatalogLoader {

    private final String jsonFilePath;

    public CatalogLoader(String jsonFilePath) {
        this.jsonFilePath = jsonFilePath;
    }

    /**
     * Loads the JSON file and returns a map of course code -> prerequisite string.
     * Only includes courses that have non-empty prerequisites.
     *
     * @return Map where key is "SUBJECT CATALOGNBR" (e.g., "CS 2110") and value is the prereq string
     */
    public Map<String, String> load() throws IOException {
        Map<String, String> prereqMap = new LinkedHashMap<>();

        String jsonContent = Files.readString(Path.of(jsonFilePath));

        // Simple JSON parsing - find each course object and extract fields
        // The JSON is an array of course objects
        List<CourseData> courses = parseCoursesFromJson(jsonContent);

        for (CourseData course : courses) {
            if (course.prereq != null && !course.prereq.isBlank()) {
                String courseCode = course.subject + " " + course.catalogNbr;
                prereqMap.put(courseCode, course.prereq);
            }
        }

        return prereqMap;
    }

    /**
     * Simple record to hold extracted course data.
     */
    private record CourseData(String subject, String catalogNbr, String prereq) {}

    /**
     * Parse the JSON array and extract relevant fields from each course object.
     * This is a simple parser that doesn't require external JSON libraries.
     */
    private List<CourseData> parseCoursesFromJson(String json) {
        List<CourseData> courses = new ArrayList<>();

        // Find each course object in the array
        int pos = 0;
        while (pos < json.length()) {
            // Find the start of next object
            int objStart = json.indexOf('{', pos);
            if (objStart == -1) break;

            // Find matching closing brace (handling nested objects)
            int objEnd = findMatchingBrace(json, objStart);
            if (objEnd == -1) break;

            String courseJson = json.substring(objStart, objEnd + 1);

            // Extract fields
            String subject = extractStringField(courseJson, "subject");
            String catalogNbr = extractStringField(courseJson, "catalogNbr");
            String prereq = extractStringField(courseJson, "catalogPrereq");

            if (subject != null && catalogNbr != null) {
                courses.add(new CourseData(subject, catalogNbr, prereq));
            }

            pos = objEnd + 1;
        }

        return courses;
    }

    /**
     * Find the matching closing brace for an opening brace.
     */
    private int findMatchingBrace(String json, int openPos) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = openPos; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escape) {
                escape = false;
                continue;
            }

            if (c == '\\' && inString) {
                escape = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }

        return -1;
    }

    /**
     * Extract a string field value from a JSON object.
     */
    private String extractStringField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"";
        int keyPos = json.indexOf(pattern);
        if (keyPos == -1) return null;

        // Find the colon after the key
        int colonPos = json.indexOf(':', keyPos + pattern.length());
        if (colonPos == -1) return null;

        // Skip whitespace
        int valueStart = colonPos + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= json.length()) return null;

        // Check if value is null
        if (json.substring(valueStart).startsWith("null")) {
            return null;
        }

        // Check if value is a string (starts with quote)
        if (json.charAt(valueStart) != '"') {
            return null;
        }

        // Find the closing quote (handling escapes)
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (int i = valueStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escape) {
                sb.append(c);
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            if (c == '"') {
                return sb.toString();
            }

            sb.append(c);
        }

        return null;
    }

    /**
     * Test the loader.
     */
    public static void main(String[] args) {
        String jsonFile = args.length > 0 ? args[0] : "cornell_catalog_FA25_under5000.json";

        try {
            CatalogLoader loader = new CatalogLoader(jsonFile);
            Map<String, String> prereqMap = loader.load();

            System.out.println("Loaded " + prereqMap.size() + " courses with prerequisites.\n");

            // Print first 20 entries
            int count = 0;
            for (Map.Entry<String, String> entry : prereqMap.entrySet()) {
                System.out.println(entry.getKey() + ":");
                System.out.println("  " + entry.getValue());
                System.out.println();

                if (++count >= 20) break;
            }

        } catch (IOException e) {
            System.err.println("Error loading catalog: " + e.getMessage());
            e.printStackTrace();
        }
    }
}