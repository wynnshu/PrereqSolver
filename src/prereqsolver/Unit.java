package prereqsolver;

import java.util.Set;

/**
 * Represents a single, indivisible requirement unit.
 * Can be a Course ID ("CS 2110"), a generic label ("Permission"), or a free pass ("None").
 */
public class Unit implements Requirement {

    private final String content;   // e.g. "CS 2110"
    private final boolean autoPass; // e.g. true for "None"

    // Constructor for standard courses
    public Unit(String content) {
        this(content, false);
    }

    // Constructor for special units (like "None")
    public Unit(String content, boolean autoPass) {
        // Normalize: "cs 2110" -> "CS 2110"
        this.content = content.trim().toUpperCase();
        this.autoPass = autoPass;
    }

    @Override
    public boolean isSatisfied(Set<String> takenCourses) {
        if (autoPass) return true;
        // Check if the user has taken this specific unit
        return takenCourses.contains(content);
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return content;
    }
}