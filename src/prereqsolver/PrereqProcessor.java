package prereqsolver;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Processes Cornell course catalog through multiple phases:
 *
 * Phase 1: Python script fetches JSON (external)
 * Phase 2: Load JSON → Map of course code → prereq string
 * Phase 3: Tokenize all prereqs → save to TSV for manual review
 * Phase 4: Parse tokenized prereqs → Requirement trees (later)
 * Phase 5: Graph algorithms (later)
 */
public class PrereqProcessor {

    /**
     * =========================================================================
     * PHASE 2: Load catalog JSON into a map
     * =========================================================================
     *
     * Input:  JSON file path
     * Output: Map<String, String> where key = "CS 2110", value = prereq text
     */
    public static Map<String, String> loadCatalog(String jsonFilePath) throws IOException {
        CatalogLoader loader = new CatalogLoader(jsonFilePath);
        return loader.load();
    }

    /**
     * =========================================================================
     * PHASE 3: Tokenize all prereqs and save to TSV file
     * =========================================================================
     *
     * Input:  Map of course code → prereq string
     * Output: TSV file with columns:
     *         1. Course code (e.g., "CS 2110")
     *         2. Original prereq string
     *         3. Tokenized result (e.g., "COURSE(CS 1110) OR COURSE(CS 1112)")
     *         4. Status: "OK" or error message
     *
     * The TSV can be opened in Excel/Sheets for manual review and correction.
     */
    public static void tokenizeAndSaveToTSV(Map<String, String> prereqMap, String outputPath) throws IOException {

        int success = 0;
        int failed = 0;

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            // Write header row
            writer.println("COURSE_CODE\tPREREQ_STRING\tTOKENS\tSTATUS");

            for (Map.Entry<String, String> entry : prereqMap.entrySet()) {
                String courseCode = entry.getKey();
                String prereqString = entry.getValue();

                String tokens;
                String status;

                try {
                    // Tokenize
                    Tokenizer tokenizer = new Tokenizer(prereqString);
                    Queue<Token> tokenQueue = tokenizer.tokenize();

                    // Convert to string representation (excluding EOF)
                    StringBuilder sb = new StringBuilder();
                    for (Token t : tokenQueue) {
                        if (t.tokenType() != TokenType.EOF) {
                            if (sb.length() > 0) sb.append(" ");
                            sb.append(t.toString());
                        }
                    }
                    tokens = sb.toString();

                    // Validate token sequence
                    // Re-tokenize for validation since we consumed the queue
                    tokenizer = new Tokenizer(prereqString);
                    List<Token> tokenList = new ArrayList<>(tokenizer.tokenize());
                    String validationError = Tokenizer.validateTokenSequence(tokenList);

                    if (validationError != null) {
                        status = "INVALID: " + validationError;
                        failed++;
                    } else {
                        status = "OK";
                        success++;
                    }

                } catch (Exception e) {
                    tokens = "ERROR";
                    status = "EXCEPTION: " + e.getMessage();
                    failed++;
                }

                // Write row (escape any tabs in the prereq string just in case)
                String safePrereq = prereqString.replace("\t", " ").replace("\n", " ");
                String safeTokens = tokens.replace("\t", " ").replace("\n", " ");
                String safeStatus = status.replace("\t", " ").replace("\n", " ");

                writer.println(courseCode + "\t" + safePrereq + "\t" + safeTokens + "\t" + safeStatus);
            }
        }

        System.out.println("Tokenization complete:");
        System.out.println("  Success: " + success);
        System.out.println("  Failed:  " + failed);
        System.out.println("  Total:   " + prereqMap.size());
        System.out.println("  Output:  " + outputPath);
    }

    /**
     * =========================================================================
     * MAIN: Run Phases 2-3
     * =========================================================================
     */
    public static void main(String[] args) {
        // Default file paths
        String jsonFile = "cornell_catalog_FA25_under6000.json";
        String tsvOutput = "tokenized_prereqs.tsv";

        // Allow command line overrides
        if (args.length >= 1) {
            jsonFile = args[0];
        }
        if (args.length >= 2) {
            tsvOutput = args[1];
        }

        try {
            // Phase 2: Load catalog
            System.out.println("=== PHASE 2: Loading catalog ===");
            System.out.println("Input: " + jsonFile);
            Map<String, String> prereqMap = loadCatalog(jsonFile);
            System.out.println("Loaded " + prereqMap.size() + " courses with prerequisites.\n");

            // Phase 3: Tokenize and save
            System.out.println("=== PHASE 3: Tokenizing ===");
            tokenizeAndSaveToTSV(prereqMap, tsvOutput);

            System.out.println("\n=== NEXT STEPS ===");
            System.out.println("1. Open " + tsvOutput + " in Excel or Google Sheets");
            System.out.println("2. Filter by STATUS column to find errors");
            System.out.println("3. Review and note any patterns that need fixing in Tokenizer");
            System.out.println("4. Once satisfied, proceed to Phase 4 (parsing)");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}