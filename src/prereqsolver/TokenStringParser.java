package prereqsolver;

/**
 * Parses token strings from the corrected TSV into Requirement ASTs.
 *
 * Input format examples:
 *   "COURSE(CS 1110)"
 *   "COURSE(CS 1110) OR COURSE(CS 1112)"
 *   "COURSE(CS 2110) AND COURSE(CS 2800)"
 *   "LPAREN COURSE(CS 1110) OR COURSE(CS 1112) RPAREN AND COURSE(MATH 1920)"
 *
 * Grammar (same as Parser.java - OR binds tighter than AND):
 *   expr    ::= orTerm | orTerm AND expr
 *   orTerm  ::= unit | unit OR orTerm
 *   unit    ::= COURSE(...) | LPAREN expr RPAREN
 */
public class TokenStringParser {

    private final String input;
    private int pos;

    public TokenStringParser(String input) {
        this.input = input.trim();
        this.pos = 0;
    }

    /**
     * Static convenience method.
     */
    public static Requirement parse(String tokenString) {
        if (tokenString == null || tokenString.trim().isEmpty()) {
            return null;
        }
        TokenStringParser parser = new TokenStringParser(tokenString);
        return parser.parseExpr();
    }

    /**
     * expr ::= orTerm | orTerm AND expr
     */
    private Requirement parseExpr() {
        Requirement left = parseOrTerm();

        skipWhitespace();
        if (matchKeyword("AND")) {
            Requirement right = parseExpr();
            return new Expression(TokenType.AND, left, right);
        }

        return left;
    }

    /**
     * orTerm ::= unit | unit OR orTerm
     */
    private Requirement parseOrTerm() {
        Requirement left = parseUnit();

        skipWhitespace();
        if (matchKeyword("OR")) {
            Requirement right = parseOrTerm();
            return new Expression(TokenType.OR, left, right);
        }

        return left;
    }

    /**
     * unit ::= COURSE(...) | LPAREN expr RPAREN
     */
    private Requirement parseUnit() {
        skipWhitespace();

        // Check for LPAREN
        if (matchKeyword("LPAREN")) {
            Requirement inner = parseExpr();
            skipWhitespace();
            if (!matchKeyword("RPAREN")) {
                throw new RuntimeException("Expected RPAREN at position " + pos + " in: " + input);
            }
            return inner;
        }

        // Must be COURSE(...)
        if (matchKeyword("COURSE(")) {
            int start = pos;
            // Find the closing paren - but handle nested parens in course names
            // Actually, course names don't have parens, so just find the next )
            int depth = 1;
            while (pos < input.length() && depth > 0) {
                char c = input.charAt(pos);
                if (c == '(') depth++;
                else if (c == ')') depth--;
                if (depth > 0) pos++;
            }

            String courseName = input.substring(start, pos).trim();
            pos++; // skip the closing )

            return new Unit(courseName);
        }

        throw new RuntimeException("Expected COURSE or LPAREN at position " + pos + " in: " + input);
    }

    /**
     * Skip whitespace.
     */
    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    /**
     * Try to match a keyword at current position. If matched, advance pos and return true.
     */
    private boolean matchKeyword(String keyword) {
        skipWhitespace();
        if (input.substring(pos).startsWith(keyword)) {
            pos += keyword.length();
            return true;
        }
        return false;
    }

    /**
     * Test the parser.
     */
    public static void main(String[] args) {
        String[] testCases = {
                "COURSE(CS 1110)",
                "COURSE(CS 1110) OR COURSE(CS 1112)",
                "COURSE(CS 2110) AND COURSE(CS 2800)",
                "COURSE(CS 1110) OR COURSE(CS 1112) AND COURSE(MATH 1920)",
                "LPAREN COURSE(CS 1110) OR COURSE(CS 1112) RPAREN AND COURSE(MATH 1920)",
                "COURSE(PHYS 3318) AND LPAREN COURSE(PHYS 1116) OR COURSE(PHYS 2216) RPAREN",
                "LPAREN COURSE(CHEM 1560) OR COURSE(CHEM 2070) OR COURSE(CHEM 2080) RPAREN AND LPAREN COURSE(CHEM 1570) OR COURSE(CHEM 3570) OR COURSE(CHEM 3580) RPAREN AND COURSE(FDSC 4170)",
                "COURSE(Permission: permission of instructor)",
                "COURSE(Special requirement: one year of calculus)",
        };

        for (String test : testCases) {
            System.out.println("Input:  " + test);
            try {
                Requirement req = parse(test);
                System.out.println("Parsed: " + req);
                System.out.println();
            } catch (Exception e) {
                System.out.println("ERROR:  " + e.getMessage());
                System.out.println();
            }
        }
    }
}