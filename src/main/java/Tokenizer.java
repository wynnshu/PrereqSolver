package prereqsolver;

import java.util.*;
import java.util.regex.*;

/**
 * Tokenizer for prerequisite strings.
 * Uses a buffer-based approach to resolve ambiguous commas into AND or OR tokens.
 */
public class Tokenizer {

    private final String input;
    private int pos = 0;
    private boolean skipNextRparen = false;  // Flag for skipping RPAREN after e.g. block

    // Pattern to match course codes: 2-5 uppercase letters + optional space + 4 digits
    private static final Pattern COURSE_PATTERN = Pattern.compile(
            "^([A-Z]{2,5})\\s*(\\d{4})"
    );

    // Special terminals
    private static final Pattern PERMISSION_PATTERN = Pattern.compile(
            "^permission of (the )?(instructor|department|[A-Z]+)( required)?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern EQUIVALENT_PATTERN = Pattern.compile(
            "^(their\\s+)?equivalents?",
            Pattern.CASE_INSENSITIVE
    );

    // Skip phrases - things to ignore
    private static final String[] SKIP_PHRASES = {
            // Recommendations (not required)
            "also highly recommended",
            "highly recommended",
            "is strongly recommended",
            "is useful",
            "is recommended",
            "recommended prerequisite:",
            "recommended corequisite:",
            "recommended",
            // Corequisites (we only care about prerequisites)
            "prerequisite or corequisite:",
            "corequisite:",
            "corequisites:",
            "prerequisite:",
            "prerequisites:",
            // Common filler
            "with ",        // "with" followed by space (to not match "within")
            "e.g.",
            "i.e.",
            "at least",
            "either ",      // "either X or Y" - skip the word "either"
            "also ",        // "also HD 2830" - skip "also"
            "plus ",        // "plus BIOG 1500" - should be AND, but skip for now
            "one of the following",
            "any of the following",
            "at the level of ",  // "at the level of PHYS 3318"
            "equivalent to ",    // "equivalent to MATH 2940"
            "co-registration in ", // "co-registration in MATH 1110"
            "courses:",
            "course:",
            // Grade/performance prefixes that lead to a course
            "excellent performance in ",
            "nonadvancing grade in ",
            "completion of ",
    };

    public Tokenizer(String input) {
        this.input = input;
    }

    /**
     * Main entry point: tokenizes the input and resolves comma ambiguity.
     */
    public Queue<Token> tokenize() {
        List<Token> rawTokens = scanAllTokens();
        List<Token> cleanedTokens = removeOrphanedParens(rawTokens);
        List<Token> wrappedTokens = wrapBeforeOrPermission(cleanedTokens);
        // Resolve commas BEFORE removing dangling operators
        // This way "A, B, or equivalent" keeps the OR for comma resolution
        Queue<Token> resolvedTokens = resolveCommas(wrappedTokens);
        // NOW remove dangling operators (the trailing OR after "or equivalent")
        List<Token> resolvedList = new ArrayList<>(resolvedTokens);
        List<Token> noDangling = removeDanglingOperators(resolvedList);
        return new LinkedList<>(noDangling);
    }

    /**
     * First pass: scan all tokens without resolving commas.
     */
    private List<Token> scanAllTokens() {
        List<Token> tokens = new ArrayList<>();

        while (pos < input.length()) {
            skipWhitespace();
            if (pos >= input.length()) break;

            Token token = scanNextToken();
            if (token != null) {
                tokens.add(token);
            }
        }

        tokens.add(new Token(TokenType.EOF));
        return tokens;
    }

    /**
     * Scan a single token from current position.
     */
    private Token scanNextToken() {
        if (pos >= input.length()) {
            return new Token(TokenType.EOF);
        }

        String remaining = input.substring(pos);
        String remainingLower = remaining.toLowerCase();

        char c = input.charAt(pos);

        // Check for "(or" pattern - treat as just OR (paren is noise)
        if (c == '(' && remainingLower.length() > 3 && remainingLower.startsWith("(or ")) {
            pos += 1; // skip the paren, OR will be caught next iteration
            return null;
        }

        // Check for "(e.g." or "(e.g.," pattern - return OR to connect vague req to example
        if (c == '(' && remainingLower.length() > 5 &&
                (remainingLower.startsWith("(e.g.") || remainingLower.startsWith("(e.g.,"))) {
            // Skip "(e.g." or "(e.g.,"
            if (remainingLower.startsWith("(e.g.,")) {
                pos += 6;
            } else {
                pos += 5;
            }
            skipWhitespace();
            // Mark that we need to skip the closing RPAREN
            skipNextRparen = true;
            // Return OR to connect the vague requirement to the example course
            return new Token(TokenType.OR);
        }

        // Skip RPAREN if we're closing an e.g. block
        if (c == ')' && skipNextRparen) {
            pos++;
            skipNextRparen = false;
            return null;
        }

        // Single character tokens
        switch (c) {
            case '(' -> { pos++; return new Token(TokenType.LPAREN); }
            case ')' -> { pos++; return new Token(TokenType.RPAREN); }
            case ',' -> { pos++; return new Token(TokenType.COMMA); }
            case ';' -> { pos++; return new Token(TokenType.SEMICOLON); }
            case '/' -> { pos++; return new Token(TokenType.SLASH); }
            case '.' -> {
                // Check if this is end of input (period at end) or mid-sentence
                if (pos == input.length() - 1 ||
                        input.substring(pos + 1).trim().isEmpty()) {
                    pos++;
                    return new Token(TokenType.PERIOD);
                }
                // Check if next sentence is about corequisites (we only want prereqs)
                String afterPeriod = input.substring(pos + 1).trim().toLowerCase();
                if (afterPeriod.startsWith("corequisite") ||
                        afterPeriod.startsWith("prerequisite or corequisite") ||
                        afterPeriod.startsWith("highly recommended corequisite")) {
                    // Stop processing - rest is corequisite info, not prereqs
                    pos = input.length();
                    return new Token(TokenType.EOF);
                }
                // Check if next sentence starts with a word that indicates non-prereq content
                if (afterPeriod.startsWith("students ") ||
                        afterPeriod.startsWith("no ") ||
                        afterPeriod.startsWith("this ") ||
                        afterPeriod.startsWith("familiarity ") ||
                        afterPeriod.startsWith("basic ") ||
                        afterPeriod.startsWith("knowledge ") ||
                        afterPeriod.startsWith("electromagnetism ") ||
                        afterPeriod.startsWith("recommended ")) {
                    // Stop processing - rest is not prereq info
                    pos = input.length();
                    return new Token(TokenType.EOF);
                }
                // Mid-sentence period - skip it
                pos++;
                return null;
            }
            case '-' -> {
                // Check if this is a course range like "MATH 1110-MATH 1120"
                // Look ahead to see if a course code follows
                String afterHyphen = input.substring(pos + 1).trim();
                Matcher rangeCourseMatcher = COURSE_PATTERN.matcher(afterHyphen);
                if (rangeCourseMatcher.find() && rangeCourseMatcher.start() == 0) {
                    // This is a course range - treat hyphen as AND (take both courses)
                    pos++;
                    return new Token(TokenType.AND);
                }
                // Not a course range - skip the hyphen
                pos++;
                return null;
            }
        }

        // Try to match course code first
        Matcher courseMatcher = COURSE_PATTERN.matcher(remaining);
        if (courseMatcher.find()) {
            String dept = courseMatcher.group(1);
            String num = courseMatcher.group(2);
            pos += courseMatcher.end();
            return new Token(TokenType.COURSE, dept + " " + num);
        }

        // Check for "permission of instructor/department"
        Matcher permMatcher = PERMISSION_PATTERN.matcher(remaining);
        if (permMatcher.find()) {
            pos += permMatcher.end();
            return new Token(TokenType.COURSE, "Permission: " + permMatcher.group(0));
        }

        // Check for "equivalent(s)" / "their equivalent(s)" - SKIP entirely
        // Also consume any trailing comma (since it's attached to the skipped equivalent)
        Matcher equivMatcher = EQUIVALENT_PATTERN.matcher(remaining);
        if (equivMatcher.find()) {
            pos += equivMatcher.end();
            // Skip trailing comma if present
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == ',') {
                pos++;
            }
            return null;  // Skip equivalents - too messy to handle distribution
        }

        // Check for "or" keyword
        if (remainingLower.startsWith("or ") || remainingLower.equals("or")) {
            pos += 2;
            return new Token(TokenType.OR);
        }

        // Check for "and" keyword
        if (remainingLower.startsWith("and ") || remainingLower.equals("and")) {
            pos += 3;
            return new Token(TokenType.AND);
        }

        // Check for skip phrases (things to ignore but continue parsing)
        for (String phrase : SKIP_PHRASES) {
            if (remainingLower.startsWith(phrase)) {
                pos += phrase.length();

                // Special handling for "with " - skip until we hit OR/AND/comma/period/end
                // This handles "with grade of B-" or "with average of B- or better"
                if (phrase.equals("with ")) {
                    while (pos < input.length()) {
                        String rest = input.substring(pos).toLowerCase();
                        // Stop if we hit an operator keyword or end of meaningful content
                        if (rest.startsWith(" or ") || rest.startsWith(" and ") ||
                                rest.startsWith(",") || rest.startsWith(";") ||
                                rest.startsWith(".") || rest.startsWith(")")) {
                            break;
                        }
                        // Also stop if we hit a course code
                        if (COURSE_PATTERN.matcher(input.substring(pos)).find() &&
                                COURSE_PATTERN.matcher(input.substring(pos)).start() == 0) {
                            break;
                        }
                        pos++;
                    }
                    return null;
                }

                // Special handling: if phrase ends with pattern that leads to colon,
                // skip everything up to and including the colon
                skipWhitespace();
                if (pos < input.length()) {
                    // Look for colon ahead (skip intermediate words)
                    int colonPos = input.indexOf(':', pos);
                    if (colonPos != -1 && colonPos - pos < 50) {
                        // Check if there are only non-course words between here and colon
                        String betweenText = input.substring(pos, colonPos);
                        if (!COURSE_PATTERN.matcher(betweenText).find()) {
                            pos = colonPos + 1; // Skip to after colon
                        }
                    }
                }
                return null; // Skip this phrase
            }
        }

        // Check for grade requirements like "grade of B+" that weren't caught by "with "
        // These patterns: "grade of B- in CHIN 1122" or "grade of B+ or higher in CHIN 2202"
        if (remainingLower.startsWith("grade of ") ||
                remainingLower.startsWith("a grade of ") ||
                remainingLower.startsWith("grades of ") ||
                remainingLower.startsWith("minimum grade of ") ||
                remainingLower.startsWith("minimum grades of ") ||
                remainingLower.startsWith("an a") ||  // "an A- or better"
                remainingLower.startsWith("a b") ||   // "a B+ or higher"
                remainingLower.startsWith("a c")) {   // "a C or above"
            // Skip until we hit a course code (the grade requirement applies to the course)
            while (pos < input.length()) {
                // Check if we've reached a course code
                if (COURSE_PATTERN.matcher(input.substring(pos)).find() &&
                        COURSE_PATTERN.matcher(input.substring(pos)).start() == 0) {
                    break;
                }
                // Also stop at major delimiters
                String rest = input.substring(pos).toLowerCase();
                if (rest.startsWith(". ") || rest.startsWith(";")) {
                    break;
                }
                pos++;
            }
            return null;
        }

        // Check for vague requirements - scan until we hit a delimiter
        // This catches things like "one year of Arabic", "reading knowledge of Japanese"
        if (Character.isLetter(c) || Character.isDigit(c)) {
            return scanVagueRequirement();
        }

        // Unknown character - skip it
        pos++;
        return null;
    }

    /**
     * Scan a vague requirement (non-course text) until we hit a delimiter.
     */
    private Token scanVagueRequirement() {
        int start = pos;
        StringBuilder sb = new StringBuilder();

        while (pos < input.length()) {
            char c = input.charAt(pos);

            // Stop at delimiters
            if (c == ',' || c == ';' || c == '(' || c == ')' || c == '.') {
                break;
            }

            // Check if we're about to hit a keyword
            String remaining = input.substring(pos).toLowerCase();
            if (remaining.startsWith(" or ") || remaining.startsWith(" and ")) {
                break;
            }

            // Check if we're about to hit a course code
            String remainingUpper = input.substring(pos);
            if (COURSE_PATTERN.matcher(remainingUpper).find() && pos > start) {
                // Only break if we've already captured something
                if (COURSE_PATTERN.matcher(remainingUpper).lookingAt()) {
                    break;
                }
            }

            sb.append(c);
            pos++;
        }

        String text = sb.toString().trim();

        // Filter out empty or very short captures
        if (text.isEmpty() || text.length() < 3) {
            return null;
        }

        // Filter out known noise phrases
        String lower = text.toLowerCase();
        if (lower.equals("of") || lower.equals("the") || lower.equals("a") ||
                lower.equals("an") || lower.equals("in") || lower.equals("for") ||
                lower.equals("to") || lower.equals("by") || lower.equals("at") ||
                lower.equals("one") || lower.equals("two") || lower.equals("their")) {
            return null;
        }

        return new Token(TokenType.COURSE, "Special requirement: " + text);
    }

    /**
     * Skip whitespace characters.
     */
    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    /**
     * Remove orphaned parentheses (parens that don't have matching pairs or
     * that only contain operators).
     */
    private List<Token> removeOrphanedParens(List<Token> tokens) {
        List<Token> result = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            Token tok = tokens.get(i);

            // Skip RPAREN that follows an operator (orphaned closing paren)
            if (tok.tokenType() == TokenType.RPAREN) {
                if (i > 0) {
                    TokenType prev = tokens.get(i - 1).tokenType();
                    if (prev == TokenType.OR || prev == TokenType.AND ||
                            prev == TokenType.LPAREN) {
                        continue; // skip this orphaned rparen
                    }
                }
            }

            // Skip LPAREN followed by OR with no matching content
            // Pattern: LPAREN OR ... RPAREN where RPAREN comes right after content
            // We'll handle this more simply: just remove lone parens
            if (tok.tokenType() == TokenType.LPAREN) {
                // Look ahead - if next non-whitespace is OR/AND, skip this paren
                if (i + 1 < tokens.size()) {
                    TokenType next = tokens.get(i + 1).tokenType();
                    if (next == TokenType.OR || next == TokenType.AND) {
                        continue; // skip this paren
                    }
                }
            }

            result.add(tok);
        }

        return result;
    }

    /**
     * Wrap everything before "OR permission" in parentheses.
     * This ensures permission bypasses the entire preceding expression.
     *
     * Example: "A and B or permission" -> "(A and B) or permission"
     */
    private List<Token> wrapBeforeOrPermission(List<Token> tokens) {
        // Find the index of "OR" followed by a permission token
        int orPermissionIndex = -1;
        for (int i = 0; i < tokens.size() - 1; i++) {
            Token tok = tokens.get(i);
            Token next = tokens.get(i + 1);

            if (tok.tokenType() == TokenType.OR &&
                    next.tokenType() == TokenType.COURSE &&
                    next.literal() != null &&
                    next.literal().toLowerCase().startsWith("permission")) {
                orPermissionIndex = i;
                break;
            }
        }

        // If no "or permission" found, return as-is
        if (orPermissionIndex == -1) {
            return tokens;
        }

        // Get content to wrap, removing any trailing COMMAs
        List<Token> content = new ArrayList<>(tokens.subList(0, orPermissionIndex));
        while (!content.isEmpty() &&
                content.get(content.size() - 1).tokenType() == TokenType.COMMA) {
            content.remove(content.size() - 1);
        }

        List<Token> result = new ArrayList<>();

        // If there's content, wrap it in parens
        if (!content.isEmpty()) {
            result.add(new Token(TokenType.LPAREN));
            result.addAll(content);
            result.add(new Token(TokenType.RPAREN));
        }

        // Add the rest (OR, permission, and anything after)
        for (int i = orPermissionIndex; i < tokens.size(); i++) {
            result.add(tokens.get(i));
        }

        return result;
    }

    /**
     * Remove dangling operators (OR/AND at end of token stream, or OR/AND followed by OR/AND).
     * This can happen when "equivalent" is skipped, leaving behind "... or" with nothing after.
     */
    private List<Token> removeDanglingOperators(List<Token> tokens) {
        List<Token> result = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            Token tok = tokens.get(i);

            // Skip OR/AND if it's at the end or followed by certain tokens
            if (tok.tokenType() == TokenType.OR || tok.tokenType() == TokenType.AND) {
                // Check what comes next
                if (i + 1 >= tokens.size()) {
                    continue; // Skip - nothing after
                }
                TokenType next = tokens.get(i + 1).tokenType();
                // Skip if followed by EOF, PERIOD, another operator, or RPAREN
                if (next == TokenType.EOF || next == TokenType.PERIOD ||
                        next == TokenType.OR || next == TokenType.AND ||
                        next == TokenType.RPAREN) {
                    continue; // Skip this dangling operator
                }
            }

            result.add(tok);
        }

        return result;
    }

    /**
     * Second pass: resolve comma ambiguity using buffer.
     *
     * Rules:
     * - Comma followed eventually by OR (with no AND before it): commas become OR
     * - Comma followed eventually by AND (with no OR before it): commas become AND
     * - Comma right before OR: discard (it's just punctuation grouping)
     * - Comma right before AND: discard (it's just punctuation grouping)
     * - Comma with no operators until EOF: commas become AND
     */
    private Queue<Token> resolveCommas(List<Token> rawTokens) {
        Queue<Token> result = new ArrayDeque<>();
        List<Token> buffer = new ArrayList<>();

        for (int i = 0; i < rawTokens.size(); i++) {
            Token tok = rawTokens.get(i);

            switch (tok.tokenType()) {
                case OR -> {
                    // Remove trailing comma if present (punctuation before OR)
                    if (!buffer.isEmpty() &&
                            buffer.get(buffer.size() - 1).tokenType() == TokenType.COMMA) {
                        buffer.remove(buffer.size() - 1);
                    }

                    // Determine what commas in buffer should become
                    // Look back: if there's an AND in buffer, commas are AND
                    // Otherwise, commas become OR
                    TokenType commaReplacement = bufferContainsAnd(buffer) ?
                            TokenType.AND : TokenType.OR;

                    flushBuffer(buffer, result, commaReplacement);
                    result.add(tok);
                }
                case AND -> {
                    // Remove trailing comma if present (punctuation before AND)
                    if (!buffer.isEmpty() &&
                            buffer.get(buffer.size() - 1).tokenType() == TokenType.COMMA) {
                        buffer.remove(buffer.size() - 1);
                    }

                    // Commas before AND become AND
                    flushBuffer(buffer, result, TokenType.AND);
                    result.add(tok);
                }
                case SLASH -> {
                    // Slash is always OR - flush buffer first
                    // Don't change commas in buffer - they stay as-is for later resolution
                    flushBuffer(buffer, result, TokenType.AND);
                    result.add(new Token(TokenType.OR));
                }
                case SEMICOLON -> {
                    // Semicolon is a hard separator - flush as AND, keep semicolon
                    flushBuffer(buffer, result, TokenType.AND);
                    result.add(tok);
                }
                case PERIOD, EOF -> {
                    // End of expression - remove trailing comma and flush as AND
                    if (!buffer.isEmpty() &&
                            buffer.get(buffer.size() - 1).tokenType() == TokenType.COMMA) {
                        buffer.remove(buffer.size() - 1);
                    }
                    flushBuffer(buffer, result, TokenType.AND);
                    if (tok.tokenType() == TokenType.EOF) {
                        result.add(tok);
                    }
                }
                default -> {
                    // COURSE, COMMA, LPAREN, RPAREN - add to buffer
                    buffer.add(tok);
                }
            }
        }

        return result;
    }

    /**
     * Check if buffer contains an AND token.
     */
    private boolean bufferContainsAnd(List<Token> buffer) {
        for (Token t : buffer) {
            if (t.tokenType() == TokenType.AND) {
                return true;
            }
        }
        return false;
    }

    /**
     * Flush buffer to result, replacing COMMA tokens with the specified type.
     */
    private void flushBuffer(List<Token> buffer, Queue<Token> result, TokenType commaReplacement) {
        for (Token t : buffer) {
            if (t.tokenType() == TokenType.COMMA) {
                result.add(new Token(commaReplacement));
            } else {
                result.add(t);
            }
        }
        buffer.clear();
    }

    /**
     * Test the tokenizer with example inputs.
     */
    public static void main(String[] args) {
        String[] testCases = {
                "AEM 2100, AEM 2200 and AEM 2225, or equivalents.",
                "ARCH 1801 and ARCH 2802 or permission of instructor.",
                "STSCI 2150 or STSCI 2200/BTRY 3010, BTRY 3080, MATH 1920, MATH 2210 or their equivalents, STSCI 3200/BTRY 3020 or BTRY 6020.",
                "at least one full semester university course in introductory economics.",
                "one semester of introductory biology (or AP biology) and one semester of chemistry, (or AP Chemistry).",
                "CHEM 2070 or CHEM 2090, MATH 1920, and PHYS 1112 or permission of instructor.",
                "BTRY 3080 or MATH 4710 or equivalent and BTRY 3010 or equivalent.",
                "BTRY 3020, BTRY 6020, or equivalent with BTRY 3080 or MATH 4710 also highly recommended.",
                "one year of Arabic or permission of instructor.",
                "reading knowledge of Japanese."
        };

        List<String> errors = new ArrayList<>();

        for (String test : testCases) {
            System.out.println("=" .repeat(70));
            System.out.println("INPUT: " + test);
            System.out.println("-".repeat(70));

            Tokenizer tokenizer = new Tokenizer(test);
            Queue<Token> tokens = tokenizer.tokenize();

            System.out.println("TOKENS:");
            List<String> tokenStrs = new ArrayList<>();
            List<Token> tokenList = new ArrayList<>(tokens);
            for (Token t : tokenList) {
                if (t.tokenType() != TokenType.EOF) {
                    tokenStrs.add(t.toString());
                }
            }
            System.out.println("  " + String.join(", ", tokenStrs));

            // Validate token sequence
            String validationError = validateTokenSequence(tokenList);
            if (validationError != null) {
                System.out.println("  ⚠️  VALIDATION ERROR: " + validationError);
                errors.add(test + " -> " + validationError);
            }

            System.out.println();
        }

        // Summary of errors
        if (!errors.isEmpty()) {
            System.out.println("=" .repeat(70));
            System.out.println("TOKENIZATION ERRORS (will crash parser):");
            System.out.println("=" .repeat(70));
            for (String error : errors) {
                System.out.println("  • " + error);
            }
        }
    }

    /**
     * Validates token sequence for patterns that will crash the parser.
     * Returns error message if invalid, null if valid.
     */
    public static String validateTokenSequence(List<Token> tokens) {
        for (int i = 0; i < tokens.size() - 1; i++) {
            TokenType curr = tokens.get(i).tokenType();
            TokenType next = tokens.get(i + 1).tokenType();

            // Check for consecutive operators: OR OR, AND AND, OR AND, AND OR
            if ((curr == TokenType.OR || curr == TokenType.AND) &&
                    (next == TokenType.OR || next == TokenType.AND)) {
                return "Consecutive operators at position " + i + ": " + curr + " " + next;
            }

            // Check for consecutive COURSE tokens (missing operator)
            if (curr == TokenType.COURSE && next == TokenType.COURSE) {
                return "Consecutive COURSE tokens at position " + i + " (missing operator)";
            }

            // Check for operator at start (after LPAREN is ok)
            if (i == 0 && (curr == TokenType.OR || curr == TokenType.AND)) {
                return "Starts with operator: " + curr;
            }

            // Check for LPAREN followed by operator
            if (curr == TokenType.LPAREN && (next == TokenType.OR || next == TokenType.AND)) {
                return "LPAREN followed by operator at position " + i;
            }

            // Check for operator followed by RPAREN
            if ((curr == TokenType.OR || curr == TokenType.AND) && next == TokenType.RPAREN) {
                return "Operator followed by RPAREN at position " + i;
            }

            // Check for operator followed by EOF
            if ((curr == TokenType.OR || curr == TokenType.AND) && next == TokenType.EOF) {
                return "Ends with operator: " + curr;
            }
        }

        return null; // Valid
    }
}