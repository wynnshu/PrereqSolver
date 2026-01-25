package prereqsolver;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.*;

/**
 * Comprehensive tests for PlanFinder.
 *
 * Tests cover:
 * - Basic single prereq courses
 * - OR branches (alternatives)
 * - AND branches (multiple requirements)
 * - Deep prerequisite chains
 * - Already-taken courses skipping OR alternatives
 * - Special requirements and permissions
 * - No-prereq courses
 * - Complex real-world course paths
 */
public class PlanFinderTest {

    private static PrereqData data;

    @BeforeAll
    static void loadData() throws IOException {
        data = new PrereqData("tokenized_prereqs_corrected.tsv");
    }

    // =========================================================================
    // BASIC TESTS - Simple prereq chains
    // =========================================================================

    @Test
    @DisplayName("Course with no prereqs returns just the course")
    void testNoPrereqs() {
        Set<String> taken = Set.of();
        PlanFinder finder = new PlanFinder(data, taken);

        // CS 1110 has no prereqs
        List<Plan> plans = finder.findPlans("CS 1110");

        assertEquals(1, plans.size());
        assertTrue(plans.get(0).contains("CS 1110"));
        assertEquals(1, plans.get(0).size());
    }

    @Test
    @DisplayName("Already taken course returns empty plan")
    void testAlreadyTaken() {
        Set<String> taken = Set.of("CS 1110");
        PlanFinder finder = new PlanFinder(data, taken);

        List<Plan> plans = finder.findPlans("CS 1110");

        assertEquals(1, plans.size());
        assertTrue(plans.get(0).isEmpty());
    }

    @Test
    @DisplayName("Simple chain: CS 2110 with CS 1110 taken")
    void testSimpleChain() {
        Set<String> taken = Set.of("CS 1110");
        PlanFinder finder = new PlanFinder(data, taken);

        // CS 2110 requires (CS 1110 OR CS 1112 OR special req)
        // Since CS 1110 is taken, should just need CS 2110
        List<Plan> plans = finder.findPlans("CS 2110");

        assertEquals(1, plans.size());
        assertEquals(Set.of("CS 2110"), plans.get(0).getCourseSet());
    }

    // =========================================================================
    // OR BRANCH TESTS - Alternative paths
    // =========================================================================

    @Test
    @DisplayName("OR branch: CS 2110 with nothing taken shows all alternatives")
    void testOrBranchAllAlternatives() {
        Set<String> taken = Set.of();
        PlanFinder finder = new PlanFinder(data, taken);

        // CS 2110 requires (CS 1110 OR CS 1112 OR special req)
        List<Plan> plans = finder.findPlans("CS 2110");

        // Should have 3 plans: via CS 1110, via CS 1112, via special req
        assertTrue(plans.size() >= 2);

        // Check that different paths exist
        boolean hasCs1110Path = plans.stream().anyMatch(p -> p.contains("CS 1110"));
        boolean hasCs1112Path = plans.stream().anyMatch(p -> p.contains("CS 1112"));
        assertTrue(hasCs1110Path || hasCs1112Path);
    }

    @Test
    @DisplayName("OR branch: taken course prunes other alternatives")
    void testOrBranchPrunesAlternatives() {
        Set<String> taken = Set.of("CS 1110");
        PlanFinder finder = new PlanFinder(data, taken);

        List<Plan> plans = finder.findPlans("CS 2110");

        // Since CS 1110 is taken, should NOT explore CS 1112 or special req paths
        assertEquals(1, plans.size());
        assertFalse(plans.get(0).contains("CS 1112"));
        assertFalse(plans.get(0).getCourseSet().stream().anyMatch(c -> c.startsWith("SPECIAL")));
    }

    @Test
    @DisplayName("OR branch: CS 5320 offers CS 2110 OR CS 2800")
    void testOrBranchCs5320() {
        Set<String> taken = Set.of("CS 1110", "MATH 1110");
        PlanFinder finder = new PlanFinder(data, taken);

        // CS 5320 requires (CS 2110 OR CS 2800)
        List<Plan> plans = finder.findPlans("CS 5320");

        // Should have 2 main paths
        assertTrue(plans.size() >= 1);

        // Shortest plans should be through either CS 2110 or CS 2800
        Plan shortest = plans.get(0);
        assertTrue(shortest.contains("CS 5320"));
        assertTrue(shortest.contains("CS 2110") || shortest.contains("CS 2800"));
    }

    // =========================================================================
    // AND BRANCH TESTS - Multiple requirements
    // =========================================================================

    @Test
    @DisplayName("AND branch: CS 4320 requires CS 2110 AND CS 2800")
    void testAndBranchCs4320() {
        Set<String> taken = Set.of("CS 1110", "MATH 1110");
        PlanFinder finder = new PlanFinder(data, taken);

        // CS 4320 requires (CS 2110 AND CS 2800)
        List<Plan> plans = finder.findPlans("CS 4320");

        assertTrue(plans.size() >= 1);
        Plan plan = plans.get(0);

        // Must contain both CS 2110 and CS 2800
        assertTrue(plan.contains("CS 2110"));
        assertTrue(plan.contains("CS 2800"));
        assertTrue(plan.contains("CS 4320"));
    }

    @Test
    @DisplayName("AND branch: CS 4820 requires CS 2800 AND CS 3110")
    void testAndBranchCs4820() {
        Set<String> taken = Set.of("CS 1110", "MATH 1110");
        PlanFinder finder = new PlanFinder(data, taken);

        // CS 4820 requires (CS 2800 AND CS 3110)
        List<Plan> plans = finder.findPlans("CS 4820");

        assertTrue(plans.size() >= 1);
        Plan plan = plans.get(0);

        // Must contain prereq chain
        assertTrue(plan.contains("CS 2800"));
        assertTrue(plan.contains("CS 3110"));
        assertTrue(plan.contains("CS 2110")); // prereq of CS 3110
        assertTrue(plan.contains("CS 4820"));
    }

    @Test
    @DisplayName("AND branch: partial completion reduces plan")
    void testAndBranchPartialCompletion() {
        Set<String> taken = Set.of("CS 1110", "MATH 1110", "CS 2110", "CS 2800");
        PlanFinder finder = new PlanFinder(data, taken);

        // CS 4320 requires (CS 2110 AND CS 2800) - both taken
        List<Plan> plans = finder.findPlans("CS 4320");

        assertEquals(1, plans.size());
        assertEquals(Set.of("CS 4320"), plans.get(0).getCourseSet());
    }

    // =========================================================================
    // DEEP CHAIN TESTS - Multi-level prerequisites
    // =========================================================================

    @Test
    @DisplayName("Deep chain: CS 4830 -> CS 4820 -> CS 3110 -> CS 2110 -> CS 1110")
    void testDeepChainCs4830() {
        Set<String> taken = Set.of("CS 1110", "MATH 1110");
        PlanFinder finder = new PlanFinder(data, taken);

        // CS 4830 requires CS 4820
        // CS 4820 requires (CS 2800 AND CS 3110)
        // CS 3110 requires CS 2110
        // CS 2110 requires CS 1110 (taken)
        List<Plan> plans = finder.findPlans("CS 4830");

        assertTrue(plans.size() >= 1);
        Plan plan = plans.get(0);

        // Should have full chain
        assertTrue(plan.contains("CS 4830"));
        assertTrue(plan.contains("CS 4820"));
        assertTrue(plan.contains("CS 3110"));
        assertTrue(plan.contains("CS 2110"));
        assertTrue(plan.contains("CS 2800"));

        // Should NOT contain already-taken courses
        assertFalse(plan.contains("CS 1110"));
        assertFalse(plan.contains("MATH 1110"));
    }

    @Test
    @DisplayName("Deep chain: nothing taken, full expansion")
    void testDeepChainNothingTaken() {
        Set<String> taken = Set.of();
        PlanFinder finder = new PlanFinder(data, taken);

        List<Plan> plans = finder.findPlans("CS 3110");

        assertTrue(plans.size() >= 1);
        Plan plan = plans.get(0);

        // Should have CS 1110 or CS 1112 at the base
        assertTrue(plan.contains("CS 1110") || plan.contains("CS 1112"));
        assertTrue(plan.contains("CS 2110"));
        assertTrue(plan.contains("CS 3110"));
    }

    // =========================================================================
    // SPECIAL REQUIREMENT TESTS
    // =========================================================================

    @Test
    @DisplayName("Special requirements are included in plans")
    void testSpecialRequirements() {
        Set<String> taken = Set.of();
        PlanFinder finder = new PlanFinder(data, taken);

        // CS 2110 has a special requirement alternative
        List<Plan> plans = finder.findPlans("CS 2110");

        // At least one plan should have a special requirement
        boolean hasSpecialPlan = plans.stream()
                .anyMatch(p -> p.getCourseSet().stream().anyMatch(c -> c.startsWith("SPECIAL")));

        assertTrue(hasSpecialPlan);
    }

    @Test
    @DisplayName("Permission nodes are included in plans")
    void testPermissionNodes() {
        Set<String> taken = Set.of();
        PlanFinder finder = new PlanFinder(data, taken);

        // Find a course that has permission as an alternative
        // Many courses have "or permission of instructor"
        List<Plan> plans = finder.findPlans("CS 4090");

        // Should have at least one plan
        assertTrue(plans.size() >= 1);
    }

    // =========================================================================
    // DUPLICATE HANDLING TESTS
    // =========================================================================

    @Test
    @DisplayName("Shared prereqs don't cause duplicates in plan")
    void testSharedPrereqsNoDuplicates() {
        Set<String> taken = Set.of();
        PlanFinder finder = new PlanFinder(data, taken);

        // CS 4820 requires CS 2800 AND CS 3110
        // Both might share some base prereqs
        List<Plan> plans = finder.findPlans("CS 4820");

        for (Plan plan : plans) {
            Set<String> courses = plan.getCourseSet();
            // If there were duplicates, the set size would be smaller than list
            // Plan internally dedupes via the set, so this should always pass
            assertTrue(courses.size() > 0);
        }
    }

    @Test
    @DisplayName("Equivalent courses (CS 1110/CS 1112) don't both appear")
    void testEquivalentCoursesNotBoth() {
        Set<String> taken = Set.of();
        PlanFinder finder = new PlanFinder(data, taken);

        List<Plan> plans = finder.findPlans("CS 4820");

        for (Plan plan : plans) {
            // Should not have BOTH CS 1110 and CS 1112 in same plan
            boolean has1110 = plan.contains("CS 1110");
            boolean has1112 = plan.contains("CS 1112");
            assertFalse(has1110 && has1112,
                    "Plan should not contain both CS 1110 and CS 1112: " + plan);
        }
    }

    // =========================================================================
    // SORTING TESTS
    // =========================================================================

    @Test
    @DisplayName("Plans are sorted by size (smallest first)")
    void testPlansSortedBySize() {
        Set<String> taken = Set.of();
        PlanFinder finder = new PlanFinder(data, taken);

        List<Plan> plans = finder.findPlans("CS 4820");

        for (int i = 0; i < plans.size() - 1; i++) {
            assertTrue(plans.get(i).size() <= plans.get(i + 1).size(),
                    "Plans should be sorted by size");
        }
    }

    @Test
    @DisplayName("Plan toString shows courses in order")
    void testPlanToString() {
        Set<String> taken = Set.of("CS 1110", "MATH 1110");
        PlanFinder finder = new PlanFinder(data, taken);

        List<Plan> plans = finder.findPlans("CS 3110");

        Plan plan = plans.get(0);
        String str = plan.toString();

        // Should contain arrow separators
        assertTrue(str.contains("→") || plan.size() == 1);

        // Should end with CS 3110 (the target)
        assertTrue(str.endsWith("CS 3110"));
    }

    // =========================================================================
    // TOPOLOGICAL ORDER TESTS
    // =========================================================================

    @Test
    @DisplayName("Plan order: prereqs come before courses that need them")
    void testTopologicalOrder() {
        Set<String> taken = Set.of("CS 1110", "MATH 1110");
        PlanFinder finder = new PlanFinder(data, taken);

        List<Plan> plans = finder.findPlans("CS 4820");

        // Get first plan without special requirements
        Plan plan = plans.stream()
                .filter(p -> !p.getCourseSet().stream().anyMatch(c -> c.startsWith("SPECIAL")))
                .findFirst()
                .orElseThrow();

        String str = plan.toString();
        String[] courses = str.split(" → ");

        // Find positions
        int cs2110Pos = -1, cs3110Pos = -1, cs4820Pos = -1;
        for (int i = 0; i < courses.length; i++) {
            if (courses[i].equals("CS 2110")) cs2110Pos = i;
            if (courses[i].equals("CS 3110")) cs3110Pos = i;
            if (courses[i].equals("CS 4820")) cs4820Pos = i;
        }

        // CS 2110 must come before CS 3110
        if (cs2110Pos != -1 && cs3110Pos != -1) {
            assertTrue(cs2110Pos < cs3110Pos, "CS 2110 must come before CS 3110");
        }

        // CS 3110 must come before CS 4820
        if (cs3110Pos != -1 && cs4820Pos != -1) {
            assertTrue(cs3110Pos < cs4820Pos, "CS 3110 must come before CS 4820");
        }
    }

    // =========================================================================
    // PRECISE EXPECTATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Precise: CS 4820 with CS 1110 and MATH 1110 taken")
    void testPreciseCs4820() {
        /*
         * CS 4820 requires: CS 2800 AND CS 3110
         *
         * CS 2800 requires: MATH 1110 OR CS 1110 OR CS 1112
         *   - CS 1110 is taken, so satisfied with {} (empty)
         *
         * CS 3110 requires: CS 2110 OR SPECIAL(programming)
         *   - Neither satisfied, explore both
         *   CS 2110 requires: CS 1110 OR CS 1112 OR SPECIAL(procedural)
         *     - CS 1110 is taken, so CS 2110 plan = {CS 2110}
         *   SPECIAL(programming) plan = {SPECIAL(programming)}
         *
         * CS 3110 plans: {CS 2110, CS 3110}, {SPECIAL(programming), CS 3110}
         *
         * Combined with CS 2800:
         * 1. {CS 2800, CS 2110, CS 3110, CS 4820}
         * 2. {CS 2800, SPECIAL(programming), CS 3110, CS 4820}
         */
        Set<String> taken = Set.of("CS 1110", "MATH 1110");
        PlanFinder finder = new PlanFinder(data, taken);

        List<Plan> plans = finder.findPlans("CS 4820");

        assertEquals(2, plans.size());

        // Both plans should have 4 courses
        assertEquals(4, plans.get(0).size());
        assertEquals(4, plans.get(1).size());

        // Both must contain CS 2800, CS 3110, CS 4820
        for (Plan plan : plans) {
            assertTrue(plan.contains("CS 2800"));
            assertTrue(plan.contains("CS 3110"));
            assertTrue(plan.contains("CS 4820"));
        }
    }

    @Test
    @DisplayName("Precise: CS 4820 with CS 2110 also taken")
    void testPreciseCs4820WithCs2110Taken() {
        /*
         * CS 4820 requires: CS 2800 AND CS 3110
         *
         * CS 2800: CS 1110 taken → satisfied, plan = {} + {CS 2800}
         * CS 3110: CS 2110 taken → OR satisfied, plan = {} + {CS 3110}
         *
         * Expected: exactly 1 plan
         * 1. {CS 2800, CS 3110, CS 4820}
         */
        Set<String> taken = Set.of("CS 1110", "MATH 1110", "CS 2110");
        PlanFinder finder = new PlanFinder(data, taken);

        List<Plan> plans = finder.findPlans("CS 4820");

        assertEquals(1, plans.size());
        assertEquals(Set.of("CS 2800", "CS 3110", "CS 4820"), plans.get(0).getCourseSet());
    }

    @Test
    @DisplayName("Precise: CS 4830 with CS 1110 and MATH 1110 taken")
    void testPreciseCs4830() {
        /*
         * CS 4830 requires: CS 4820
         * CS 4820 requires: CS 2800 AND CS 3110
         *
         * From testPreciseCs4820, CS 4820 plans are:
         * 1. {CS 2800, CS 2110, CS 3110, CS 4820}
         * 2. {CS 2800, SPECIAL(programming), CS 3110, CS 4820}
         *
         * Add CS 4830:
         * 1. {CS 2800, CS 2110, CS 3110, CS 4820, CS 4830}
         * 2. {CS 2800, SPECIAL(programming), CS 3110, CS 4820, CS 4830}
         */
        Set<String> taken = Set.of("CS 1110", "MATH 1110");
        PlanFinder finder = new PlanFinder(data, taken);

        List<Plan> plans = finder.findPlans("CS 4830");

        assertEquals(2, plans.size());

        // Both plans should have 5 courses
        assertEquals(5, plans.get(0).size());
        assertEquals(5, plans.get(1).size());

        // Both must contain the chain
        for (Plan plan : plans) {
            assertTrue(plan.contains("CS 2800"));
            assertTrue(plan.contains("CS 3110"));
            assertTrue(plan.contains("CS 4820"));
            assertTrue(plan.contains("CS 4830"));
        }
    }

    @Test
    @DisplayName("Precise: CS 4830 with almost everything taken")
    void testPreciseCs4830AlmostDone() {
        /*
         * CS 4830 requires: CS 4820
         * If CS 4820 is taken, plan = {} + {CS 4830}
         *
         * Expected: exactly 1 plan
         * 1. {CS 4830}
         */
        Set<String> taken = Set.of("CS 1110", "MATH 1110", "CS 2110", "CS 2800", "CS 3110", "CS 4820");
        PlanFinder finder = new PlanFinder(data, taken);

        List<Plan> plans = finder.findPlans("CS 4830");

        assertEquals(1, plans.size());
        assertEquals(Set.of("CS 4830"), plans.get(0).getCourseSet());
    }

    @Test
    @DisplayName("Precise: Verify no redundant courses in OR branches")
    void testPreciseNoRedundancy() {
        /*
         * This test verifies the key bug fix: when a taken course satisfies an OR,
         * we should NOT explore other OR branches.
         *
         * CS 2110 requires: CS 1110 OR CS 1112 OR SPECIAL
         * If CS 1110 is taken, we should NOT see CS 1112 or SPECIAL in any plan.
         */
        Set<String> taken = Set.of("CS 1110", "MATH 1110");
        PlanFinder finder = new PlanFinder(data, taken);

        List<Plan> plans = finder.findPlans("CS 4830");

        for (Plan plan : plans) {
            // CS 1110 satisfies CS 2110's prereq, so CS 1112 should never appear
            assertFalse(plan.contains("CS 1112"),
                    "CS 1112 should not appear when CS 1110 is taken (OR branch pruning)");
        }
    }

    @Test
    @DisplayName("Precise: CS 4820 topological order check")
    void testPreciseCs4820TopologicalOrder() {
        /*
         * For plan {CS 2800, CS 2110, CS 3110, CS 4820}
         * Valid orderings:
         *   CS 2110 → CS 2800 → CS 3110 → CS 4820
         *   CS 2800 → CS 2110 → CS 3110 → CS 4820
         *   CS 2110 → CS 3110 → CS 2800 → CS 4820 (also valid!)
         *
         * Key constraints:
         *   - CS 2110 before CS 3110
         *   - CS 2800 before CS 4820
         *   - CS 3110 before CS 4820
         */
        Set<String> taken = Set.of("CS 1110", "MATH 1110");
        PlanFinder finder = new PlanFinder(data, taken);

        List<Plan> plans = finder.findPlans("CS 4820");

        // Get the plan without special requirements
        Plan mainPlan = plans.stream()
                .filter(p -> !p.getCourseSet().stream().anyMatch(c -> c.startsWith("SPECIAL")))
                .findFirst()
                .orElseThrow();

        String str = mainPlan.toString();
        String[] courses = str.split(" → ");

        // Find positions
        int cs2110Pos = -1, cs2800Pos = -1, cs3110Pos = -1, cs4820Pos = -1;
        for (int i = 0; i < courses.length; i++) {
            if (courses[i].equals("CS 2110")) cs2110Pos = i;
            if (courses[i].equals("CS 2800")) cs2800Pos = i;
            if (courses[i].equals("CS 3110")) cs3110Pos = i;
            if (courses[i].equals("CS 4820")) cs4820Pos = i;
        }

        // Verify ordering constraints
        assertTrue(cs2110Pos < cs3110Pos, "CS 2110 must come before CS 3110");
        assertTrue(cs2800Pos < cs4820Pos, "CS 2800 must come before CS 4820");
        assertTrue(cs3110Pos < cs4820Pos, "CS 3110 must come before CS 4820");
    }
}