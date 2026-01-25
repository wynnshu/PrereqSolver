package prereqsolver;

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
}