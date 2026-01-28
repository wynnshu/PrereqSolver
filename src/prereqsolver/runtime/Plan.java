package prereqsolver.runtime;

import java.util.*;

/**
 * Represents a course plan — a sequence of courses to take.
 */
public class Plan implements Comparable<Plan> {
    private final int size;
    private final ArrayList<String> courses;
    private final HashSet<String> courseSet;

    public Plan(ArrayList<String> courses) {
        this.size = courses.size();
        this.courses = courses;
        this.courseSet = new HashSet<>(courses);
    }

    public static Plan empty() {
        return new Plan(new ArrayList<>());
    }

    public static Plan merge(Plan plan1, Plan plan2) {
        ArrayList<String> merged = new ArrayList<>();
        merged.addAll(plan1.courses);
        merged.addAll(plan2.courses);
        return new Plan(merged);
    }

    public Plan append(String course) {
        ArrayList<String> newCourses = new ArrayList<>(this.courses);
        newCourses.add(course);
        return new Plan(newCourses);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean contains(String course) {
        return courseSet.contains(course);
    }

    public HashSet<String> getCourseSet() {
        return courseSet;
    }

    public String highestCourse() {
        if (isEmpty()) return "";
        return courses.getLast();
    }

    @Override
    public int compareTo(Plan other) {
        return Integer.compare(this.size, other.size);
    }

    @Override
    public String toString() {
        return String.join(" → ", courses);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Plan other)) return false;
        return courseSet.equals(other.courseSet);
    }

    @Override
    public int hashCode() {
        return courseSet.hashCode();
    }

    /**
     * Sort the plan topologically so prereqs come before courses that need them.
     * SPECIAL/PERMISSION requirements are placed immediately before the course that needs them.
     */
    public Plan sortTopologically(PrereqData data) {
        ArrayList<String> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        // Visit all regular courses (skip SPECIAL/PERMISSION — they get added by their parent)
        for (String course : courses) {
            if (!course.startsWith("SPECIAL") && !course.startsWith("PERMISSION")) {
                topoVisit(course, data, visited, sorted);
            }
        }

        return new Plan(sorted);
    }

    /**
     * DFS post-order visit for topological sort.
     */
    private void topoVisit(String course, PrereqData data, Set<String> visited, ArrayList<String> sorted) {
        if (visited.contains(course)) return;
        visited.add(course);

        // Get all prereqs for this course
        Set<String> prereqs = getPrereqsInPlan(course, data);

        // Visit regular course prereqs first (not SPECIAL/PERMISSION)
        for (String prereq : prereqs) {
            if (!prereq.startsWith("SPECIAL") && !prereq.startsWith("PERMISSION")) {
                topoVisit(prereq, data, visited, sorted);
            }
        }

        // Add any SPECIAL/PERMISSION that belongs to THIS course (right before it)
        for (String prereq : prereqs) {
            if ((prereq.startsWith("SPECIAL") || prereq.startsWith("PERMISSION")) && !visited.contains(prereq)) {
                visited.add(prereq);
                sorted.add(prereq);
            }
        }

        // Add this course after all its prereqs
        sorted.add(course);
    }

    /**
     * Get all prereqs of a course that are also in this plan.
     */
    private Set<String> getPrereqsInPlan(String course, PrereqData data) {
        Set<String> result = new HashSet<>();

        Requirement req = data.getRequirement(course);
        if (req == null) return result;

        Set<String> allPrereqs = extractAllFromReq(req);

        // Only keep prereqs that are in this plan
        for (String prereq : allPrereqs) {
            if (courseSet.contains(prereq)) {
                result.add(prereq);
            }
        }

        return result;
    }

    /**
     * Extract all course/special/permission strings from a requirement tree.
     */
    private Set<String> extractAllFromReq(Requirement req) {
        Set<String> result = new HashSet<>();

        if (req instanceof Unit unit) {
            result.add(unit.getContent());
        } else if (req instanceof Expression expr) {
            result.addAll(extractAllFromReq(expr.getLeft()));
            result.addAll(extractAllFromReq(expr.getRight()));
        }

        return result;
    }

}