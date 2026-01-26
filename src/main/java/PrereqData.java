package prereqsolver;

import java.io.*;
import java.util.*;

/**
 * Loads and holds prerequisite data from the corrected TSV file.
 * Parses token strings into ASTs on demand and caches them.
 *
 * This is the main data class used at runtime for pathfinding.
 */
public class PrereqData {

    // course code → token string (from TSV)
    private final Map<String, String> tokenStrings;

    // course code → parsed Requirement (lazy cache)
    private final Map<String, Requirement> cache;

    // All course codes that have prerequisites
    private final Set<String> coursesWithPrereqs;

    /**
     * Load prerequisite data from a TSV file.
     *
     * Expected TSV format (tab-separated):
     *   COURSE_CODE    PREREQ_STRING    TOKENS    STATUS
     *   CS 2110        CS 1110 or ...   COURSE(CS 1110) OR ...    OK
     *
     * @param tsvPath Path to the corrected TSV file
     */
    public PrereqData(String tsvPath) throws IOException {
        this.tokenStrings = new LinkedHashMap<>();
        this.cache = new HashMap<>();
        this.coursesWithPrereqs = new LinkedHashSet<>();

        loadTSV(tsvPath);
    }

    /**
     * Load the TSV file into memory.
     */
    private void loadTSV(String tsvPath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(tsvPath))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                // Skip header
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                // Split by tab
                String[] parts = line.split("\t");
                if (parts.length < 4) {
                    continue; // malformed line
                }

                String courseCode = parts[0].trim();
                // parts[1] is PREREQ_STRING (original text) - we don't need it
                String tokens = parts[2].trim();
                String status = parts[3].trim();

                // Only load if status is OK
                if (status.startsWith("OK") && !tokens.isEmpty()) {
                    tokenStrings.put(courseCode, tokens);
                    coursesWithPrereqs.add(courseCode);
                }
            }
        }

        System.out.println("Loaded " + tokenStrings.size() + " courses with prerequisites.");
    }

    /**
     * Get the parsed Requirement for a course.
     * Parses on first access and caches the result.
     *
     * @param courseCode e.g., "CS 2110"
     * @return The Requirement AST, or null if course has no prerequisites
     */
    public Requirement getRequirement(String courseCode) {
        // No prereqs for this course
        if (!tokenStrings.containsKey(courseCode)) {
            return null;
        }

        // Check cache
        if (cache.containsKey(courseCode)) {
            return cache.get(courseCode);
        }

        // Parse and cache
        String tokens = tokenStrings.get(courseCode);
        try {
            Requirement req = TokenStringParser.parse(tokens);
            cache.put(courseCode, req);
            return req;
        } catch (Exception e) {
            System.err.println("Failed to parse prereqs for " + courseCode + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if a course has prerequisites.
     */
    public boolean hasPrereqs(String courseCode) {
        return tokenStrings.containsKey(courseCode);
    }

    /**
     * Get all course codes that have prerequisites.
     */
    public Set<String> getCoursesWithPrereqs() {
        return Collections.unmodifiableSet(coursesWithPrereqs);
    }

    /**
     * Get the raw token string for a course (for debugging).
     */
    public String getTokenString(String courseCode) {
        return tokenStrings.get(courseCode);
    }

    /**
     * Get total number of courses with prerequisites.
     */
    public int size() {
        return tokenStrings.size();
    }

    /**
     * Extract all course codes mentioned in a Requirement AST.
     * Useful for building dependency graphs.
     */
    public static Set<String> extractCourses(Requirement req) {
        Set<String> courses = new HashSet<>();
        extractCoursesHelper(req, courses);
        return courses;
    }

    private static void extractCoursesHelper(Requirement req, Set<String> courses) {
        if (req instanceof Unit unit) {
            String name = unit.getContent();
            // Only include actual course codes, not special requirements or permissions
            if (!name.startsWith("SPECIAL") && !name.startsWith("PERMISSION")) {
                courses.add(name);
            }
        } else if (req instanceof Expression expr) {
            extractCoursesHelper(expr.getLeft(), courses);
            extractCoursesHelper(expr.getRight(), courses);
        }
    }

    /**
     * Test loading a TSV file.
     */
    public static void main(String[] args) {
        String tsvFile = args.length > 0 ? args[0] : "tokenized_prereqs_corrected.tsv";

        try {
            PrereqData data = new PrereqData(tsvFile);

            // Test a few lookups
            String[] testCourses = {"CS 2110", "CS 3110", "MATH 2940", "PHYS 2213"};

            for (String course : testCourses) {
                System.out.println("\n" + course + ":");
                if (data.hasPrereqs(course)) {
                    System.out.println("  Token string: " + data.getTokenString(course));
                    Requirement req = data.getRequirement(course);
                    System.out.println("  Parsed AST:   " + req);
                    System.out.println("  Courses mentioned: " + extractCourses(req));
                } else {
                    System.out.println("  No prerequisites");
                }
            }

        } catch (IOException e) {
            System.err.println("Error loading TSV: " + e.getMessage());
            e.printStackTrace();
        }
    }
}