package prereqsolver;

import java.util.Set;

/**
 * A compound expression connecting two Requirements with logic (AND / OR).
 */
public class Expression implements Requirement {

    private final TokenType op; // AND or OR
    private final Requirement left;
    private final Requirement right;

    public Expression(TokenType op, Requirement left, Requirement right) {
        if (op != TokenType.AND && op != TokenType.OR) {
            throw new IllegalArgumentException("Expression requires AND or OR, got: " + op);
        }
        this.op = op;
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean isSatisfied(Set<String> takenCourses) {
        boolean leftSat = left.isSatisfied(takenCourses);

        // Short-circuit logic
        if (op == TokenType.OR && leftSat) return true;
        if (op == TokenType.AND && !leftSat) return false;

        boolean rightSat = right.isSatisfied(takenCourses);

        if (op == TokenType.AND) {
            return leftSat && rightSat;
        } else {
            return leftSat || rightSat;
        }
    }

    @Override
    public String toString() {
        return "(" + left + " " + op + " " + right + ")";
    }

    // Getters for PathFinder
    public TokenType getOperator() {
        return op;
    }

    public Requirement getLeft() {
        return left;
    }

    public Requirement getRight() {
        return right;
    }
}