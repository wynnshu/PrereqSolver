package prereqsolver;

import java.util.Set;

/**
 * Represents a node in the prerequisite dependency tree.
 * Equivalent of Expression.java in an interpreter
 */
public interface Requirement {

    /**
     * Checks if this requirement is met given the user's course history.
     *
     * @param takenCourses A set containing the course codes (e.g. "CS 2110")
     * that the student has already completed.
     * @return true if the requirement is satisfied, false otherwise.
     */
    boolean isSatisfied(Set<String> takenCourses);
}