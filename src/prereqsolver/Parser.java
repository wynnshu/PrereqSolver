package prereqsolver;

import java.util.Queue;

/**
 * Recursive descent parser for prerequisite expressions.
 *
 * Backus-Naur Form grammar:
 *   expr     ::= orTerm | orTerm AND expr     → Expression(AND, ...)
 *   orTerm   ::= unit | unit OR orTerm        → Expression(OR, ...)
 *   unit     ::= COURSE | '(' expr ')'        → Unit(...)
 *
 * Precedence (highest to lowest):
 *   1. Parentheses
 *   2. OR  (binds tighter)
 *   3. AND (binds looser)
 *
 * Example: "A OR B AND C" parses as "(A OR B) AND C"
 */
public class Parser {

    private final Queue<Token> tokens;
    private Token currentToken;

    public Parser(Queue<Token> tokens) {
        this.tokens = tokens;
        this.currentToken = tokens.poll();
    }

    /**
     * Parse the token stream into a Requirement tree.
     * @return The root of the requirement tree
     * @throws ParseException if the input is malformed
     */
    public Requirement parse() {
        Requirement result = parseExpr();

        // Ensure we consumed all tokens (except EOF)
        if (currentToken.tokenType() != TokenType.EOF) {
            throw new ParseException("Unexpected token after expression: " + currentToken);
        }

        return result;
    }

    /**
     * expr ::= orTerm | orTerm AND expr
     */
    private Requirement parseExpr() {
        Requirement left = parseOrTerm();

        if (currentToken.tokenType() == TokenType.AND) {
            consume(TokenType.AND);
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

        if (currentToken.tokenType() == TokenType.OR) {
            consume(TokenType.OR);
            Requirement right = parseOrTerm();
            return new Expression(TokenType.OR, left, right);
        }

        return left;
    }

    /**
     * unit ::= COURSE | '(' expr ')'
     */
    private Requirement parseUnit() {
        if (currentToken.tokenType() == TokenType.COURSE) {
            String content = currentToken.literal();
            consume(TokenType.COURSE);

            // Check for special cases
            if (content.equalsIgnoreCase("None")) {
                return new Unit(content, true); // autoPass
            }
            return new Unit(content);
        }

        if (currentToken.tokenType() == TokenType.LPAREN) {
            consume(TokenType.LPAREN);
            Requirement inner = parseExpr();
            consume(TokenType.RPAREN);
            return inner;
        }

        throw new ParseException("Expected COURSE or '(', but got: " + currentToken);
    }

    /**
     * Consume the current token if it matches the expected type.
     */
    private void consume(TokenType expected) {
        if (currentToken.tokenType() != expected) {
            throw new ParseException("Expected " + expected + ", but got: " + currentToken);
        }
        currentToken = tokens.poll();
        if (currentToken == null) {
            currentToken = new Token(TokenType.EOF);
        }
    }

    /**
     * Exception thrown when parsing fails.
     */
    public static class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
    }

    /**
     * Test the parser with example inputs.
     */
    public static void main(String[] args) {
        String[] testCases = {
                "CS 2110 or CS 2112 or permission of instructor.",
                "MATH 4710 or BTRY 3010 and MATH 2210.",
                "AEM 2100, AEM 2200 and AEM 2225, or equivalents.",
                "ARCH 1801 and ARCH 2802 or permission of instructor.",
                "one year of Arabic or permission of instructor.",
                "exposure to special relativity (e.g. PHYS 2216) and classical mechanics (e.g. PHYS 3318)",
        };

        for (String test : testCases) {
            System.out.println("=".repeat(70));
            System.out.println("INPUT: " + test);
            System.out.println("-".repeat(70));

            try {
                Tokenizer tokenizer = new Tokenizer(test);
                Queue<Token> tokens = tokenizer.tokenize();

                // Print tokens
                System.out.println("TOKENS: " + tokens);

                // Re-tokenize for parser (since we consumed the queue)
                tokenizer = new Tokenizer(test);
                tokens = tokenizer.tokenize();

                Parser parser = new Parser(tokens);
                Requirement req = parser.parse();

                System.out.println("TREE:   " + req);
            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
            }

            System.out.println();
        }
    }
}
