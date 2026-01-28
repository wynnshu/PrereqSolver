package prereqsolver.runtime;

import java.io.*;
import java.util.*;

/**
 * Finds all possible course plans to reach a target course.
 */
public class PlanFinder {

    private final PrereqData data;
    private final Set<String> takenCourses;

    public PlanFinder(PrereqData data, Set<String> takenCourses) {
        this.data = data;
        this.takenCourses = new HashSet<>(takenCourses);
        expandTakenCourses();
    }

    /**
     * Recursively mark all prereqs of taken courses as "effectively taken".
     * If you've taken CS 2110, you must have satisfied its prereqs somehow,
     * so we don't need to explore those paths.
     */
    private void expandTakenCourses() {
        Set<String> original = new HashSet<>(takenCourses);
        for (String course : original) {
            addAllPrereqs(course);
        }
    }

    /**
     * Get prereq tree for a course and add all nodes to takenCourses.
     */
    private void addAllPrereqs(String course) {
        Requirement prereqs = data.getRequirement(course);
        if (prereqs == null) return;
        addAllFromTree(prereqs);
    }

    /**
     * Walk the requirement tree and add all courses/specials to takenCourses.
     * Skips PERMISSION nodes since those are course-specific.
     */
    private void addAllFromTree(Requirement req) {
        if (req instanceof Unit unit) {
            String content = unit.getContent();

            // Skip permission nodes — they're course-specific
            if (content.startsWith("PERMISSION")) {
                return;
            }

            if (!takenCourses.contains(content)) {
                takenCourses.add(content);
                addAllPrereqs(content);  // Recurse into this course's prereqs
            }
        } else if (req instanceof Expression expr) {
            addAllFromTree(expr.getLeft());
            addAllFromTree(expr.getRight());
        }
    }

    /**
     * Find all plans to reach a target course.
     */
    public List<Plan> findPlans(String targetCourse) {
        // Already taken — nothing to do
        if (takenCourses.contains(targetCourse)) {
            List<Plan> result = new ArrayList<>();
            result.add(Plan.empty());
            return result;
        }

        if (!data.hasPrereqs(targetCourse)) {
            List<Plan> result = new ArrayList<>();
            result.add(new Plan(new ArrayList<>(List.of(targetCourse))));
            return result;
        }

        Requirement prereqs = data.getRequirement(targetCourse);
        Set<Plan> prereqPlans = getPlans(prereqs, takenCourses);

        List<Plan> result = new ArrayList<>();
        for (Plan plan : prereqPlans) {
            Plan withTarget = plan.append(targetCourse);
            Plan sorted = withTarget.sortTopologically(data);
            result.add(sorted);
        }

        Collections.sort(result);
        return result;
    }

    /**
     * Recursively get all plans to satisfy a requirement.
     */
    private Set<Plan> getPlans(Requirement requirement, Set<String> alreadyPlanned) {
        if (requirement instanceof Unit unit) {
            return getPlansForUnit(unit, alreadyPlanned);
        }

        if (requirement instanceof Expression expr) {
            return getPlansForExpression(expr, alreadyPlanned);
        }

        Set<Plan> result = new HashSet<>();
        result.add(Plan.empty());
        return result;
    }

    /**
     * Get plans for a Unit (leaf node).
     */
    private Set<Plan> getPlansForUnit(Unit unit, Set<String> alreadyPlanned) {
        String course = unit.getContent();

        // Already covered (taken or planned in earlier branch)
        if (alreadyPlanned.contains(course)) {
            Set<Plan> result = new HashSet<>();
            result.add(Plan.empty());
            return result;
        }

        // Special requirement or permission
        if (course.startsWith("SPECIAL") || course.startsWith("PERMISSION")) {
            Set<Plan> result = new HashSet<>();
            result.add(new Plan(new ArrayList<>(List.of(course))));
            return result;
        }

        // Course has no prereqs — base case
        if (!data.hasPrereqs(course)) {
            Set<Plan> result = new HashSet<>();
            result.add(new Plan(new ArrayList<>(List.of(course))));
            return result;
        }

        // Course has prereqs — recurse
        Requirement prereqs = data.getRequirement(course);
        Set<Plan> prereqPlans = getPlans(prereqs, alreadyPlanned);

        // Post-order: add this course to end of each plan
        Set<Plan> result = new HashSet<>();
        for (Plan plan : prereqPlans) {
            result.add(plan.append(course));
        }
        return result;
    }

    /**
     * Get plans for an Expression (AND/OR node).
     */
    private Set<Plan> getPlansForExpression(Expression expr, Set<String> alreadyPlanned) {
        if (expr.getOperator() == TokenType.OR) {
            // Check if either branch is already satisfied
            boolean leftSatisfied = isAlreadySatisfied(expr.getLeft(), alreadyPlanned);
            boolean rightSatisfied = isAlreadySatisfied(expr.getRight(), alreadyPlanned);

            if (leftSatisfied || rightSatisfied) {
                Set<Plan> result = new HashSet<>();
                result.add(Plan.empty());
                return result;
            }

            // Check if either immediate child is a SPECIAL/PERMISSION Unit
            boolean leftIsSpecial = isDirectSpecial(expr.getLeft());
            boolean rightIsSpecial = isDirectSpecial(expr.getRight());

            // If one side is direct SPECIAL and other is not, only explore non-SPECIAL
            if (leftIsSpecial && !rightIsSpecial) {
                return getPlans(expr.getRight(), alreadyPlanned);
            }
            if (rightIsSpecial && !leftIsSpecial) {
                return getPlans(expr.getLeft(), alreadyPlanned);
            }

            // Either both are SPECIAL, or neither is — explore both
            Set<Plan> leftPlans = getPlans(expr.getLeft(), alreadyPlanned);
            Set<Plan> rightPlans = getPlans(expr.getRight(), alreadyPlanned);

            Set<Plan> result = new HashSet<>();
            result.addAll(leftPlans);
            result.addAll(rightPlans);
            return result;
        }

        if (expr.getOperator() == TokenType.AND) {
            Set<Plan> leftPlans = getPlans(expr.getLeft(), alreadyPlanned);

            Set<Plan> result = new HashSet<>();
            for (Plan leftPlan : leftPlans) {
                Set<String> newAlreadyPlanned = new HashSet<>(alreadyPlanned);
                newAlreadyPlanned.addAll(leftPlan.getCourseSet());

                Set<Plan> rightPlans = getPlans(expr.getRight(), newAlreadyPlanned);

                for (Plan rightPlan : rightPlans) {
                    Plan merged;
                    if (leftPlan.isEmpty() || rightPlan.isEmpty()) {
                        merged = Plan.merge(leftPlan, rightPlan);
                    } else if (compareByNumber(leftPlan.highestCourse(), rightPlan.highestCourse()) > 0) {
                        merged = Plan.merge(rightPlan, leftPlan);
                    } else {
                        merged = Plan.merge(leftPlan, rightPlan);
                    }
                    result.add(merged);
                }
            }
            return result;
        }

        Set<Plan> result = new HashSet<>();
        result.add(Plan.empty());
        return result;
    }

    /**
     * Compare two courses by their course number.
     * Falls back to alphabetical comparison if either is not a standard course.
     */
    private int compareByNumber(String course1, String course2) {
        int num1 = extractCourseNumber(course1);
        int num2 = extractCourseNumber(course2);

        if (num1 != -1 && num2 != -1) {
            return Integer.compare(num1, num2);
        }
        // Fallback to alphabetical if not a standard course
        return course1.compareTo(course2);
    }

    /**
     * Extract the course number from a course code like "CS 2110" or "MATH 1920".
     * Returns -1 if no number found.
     */
    private int extractCourseNumber(String course) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(course);
        if (m.find()) {
            return Integer.parseInt(m.group());
        }
        return -1;
    }

    /**
     * Check if a requirement is a direct SPECIAL or PERMISSION unit.
     */
    private boolean isDirectSpecial(Requirement req) {
        if (req instanceof Unit unit) {
            String content = unit.getContent();
            return content.startsWith("SPECIAL") || content.startsWith("PERMISSION");
        }
        return false;
    }

    /**
     * Check if a requirement is already satisfied by planned courses.
     */
    private boolean isAlreadySatisfied(Requirement requirement, Set<String> alreadyPlanned) {

        if (requirement instanceof Unit unit) {
            return alreadyPlanned.contains(unit.getContent());
        }

        if (requirement instanceof Expression expr) {
            boolean leftSat = isAlreadySatisfied(expr.getLeft(), alreadyPlanned);
            boolean rightSat = isAlreadySatisfied(expr.getRight(), alreadyPlanned);

            if (expr.getOperator() == TokenType.OR) {
                return leftSat || rightSat;
            } else { // AND
                return leftSat && rightSat;
            }
        }

        return false;
    }

    /**
     * Test the pathfinder.
     */
    public static void main(String[] args) {
        String tsvFile = args.length > 0 ? args[0] : "tokenized_prereqs_corrected.tsv";

        try {
            PrereqData data = new PrereqData(tsvFile);
            Set<String> taken = Set.of();
            PlanFinder finder = new PlanFinder(data, taken);

            String[] testCourses = {"CS 4701", "CS 3780", "CS 4820"};

            for (String target : testCourses) {
                System.out.println("\n========================================");
                System.out.println("Target: " + target);
                System.out.println("Already taken: " + taken);
                System.out.println("========================================\n");

                List<Plan> plans = finder.findPlans(target);

                System.out.println("Found " + plans.size() + " plan(s):\n");

                int count = 1;
                for (Plan plan : plans) {
                    System.out.println("Plan " + count + " (" + plan.size() + " courses):");
                    System.out.println("  " + plan);
                    System.out.println();
                    count++;

                    if (count > 10) {
                        System.out.println("... and " + (plans.size() - 10) + " more plans");
                        break;
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}