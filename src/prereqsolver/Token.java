package prereqsolver;

/**
 * An enumeration of TokenTypes.
 */
enum TokenType {
    COURSE,     // e.g. "CS 2110"
    AND,        // "and", "&", resolved commas
    OR,         // "or", "one of", resolved commas

    LPAREN,     // "("
    RPAREN,     // ")"

    COMMA,      // The temporary placeholder
    SLASH,      // Converts to `AND`
    SEMICOLON,  // The hard separator
    PERIOD,     // Another hard separator

    EOF         // End of file
}

/**
 * Immutable Token record.
 */
public record Token(TokenType tokenType, String literal) {

    // Compact Constructor for tokens without literals (AND, OR, etc.)
    public Token(TokenType tokenType) {
        this(tokenType, null);
    }

    @Override
    public String toString() {
        if (literal != null) {
            return tokenType + "(" + literal + ")";
        }
        return tokenType.toString();
    }
}