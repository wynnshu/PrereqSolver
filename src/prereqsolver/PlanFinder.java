package prereqsolver;

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
            result.add(plan.append(targetCourse));
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

            // Neither satisfied — explore both as alternatives
            Set<Plan> leftPlans = getPlans(expr.getLeft(), alreadyPlanned);
            Set<Plan> rightPlans = getPlans(expr.getRight(), alreadyPlanned);

            Set<Plan> result = new HashSet<>();
            result.addAll(leftPlans);
            result.addAll(rightPlans);

            // Filter out SPECIAL/PERMISSION paths if real course paths exist
            Set<Plan> realCoursePlans = new HashSet<>();
            Set<Plan> specialPlans = new HashSet<>();

            for (Plan plan : result) {
                boolean hasSpecial = plan.getCourseSet().stream()
                        .anyMatch(c -> c.startsWith("SPECIAL") || c.startsWith("PERMISSION"));
                if (hasSpecial) {
                    specialPlans.add(plan);
                } else {
                    realCoursePlans.add(plan);
                }
            }

            // Prefer real course plans; only use special plans if no real ones exist
            if (!realCoursePlans.isEmpty()) {
                return realCoursePlans;
            }
            return specialPlans;
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
                    } else if (leftPlan.highestCourse().compareTo(rightPlan.highestCourse()) > 0) {
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
            Set<String> taken = Set.of("MATH 2220");
            PlanFinder finder = new PlanFinder(data, taken);

            String[] testCourses = {"CS 3110", "CS 4820", "MATH 4710"};

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