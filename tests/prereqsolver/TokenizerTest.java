package prereqsolver;

import org.junit.Test;
import org.junit.Assert;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class TokenizerTest {

    // --- Helper Method ---
    private void assertTokenization(String input, String expected) {
        Tokenizer tokenizer = new Tokenizer(input);
        Queue<Token> tokens = tokenizer.tokenize();

        List<String> tokenStrs = new ArrayList<>();
        for (Token t : tokens) {
            if (t.tokenType() != TokenType.EOF) {
                tokenStrs.add(t.toString());
            }
        }
        String actual = String.join(", ", tokenStrs);
        Assert.assertEquals("Tokenization mismatch for input: " + input, expected, actual);
    }

    // 1. AEM 2240: Mixed List (Comma + AND) with "or equivalents"
    @Test
    public void testAEM2240() {
        String input = "AEM 2100, AEM 2200 and AEM 2225, or equivalents.";
        // Note: "or equivalents" usually implies the list or equivalent, typically parsed as simple list items
        String expected = "COURSE(AEM 2100), AND, " + // Comma maps to AND in this list context
                "COURSE(AEM 2200), AND, " +
                "COURSE(AEM 2225)";
        assertTokenization(input, expected);
    }

    // 2. AEM 2241: List ending in OR (Comma + OR)
    @Test
    public void testAEM2241() {
        String input = "AEM 2100, AEM 2210, or equivalents.";
        // Context implies choice: 2100 OR 2210
        String expected = "COURSE(AEM 2100), OR, COURSE(AEM 2210)";
        assertTokenization(input, expected);
    }

    // 3. AEM 2350: Natural Language Quantity
    @Test
    public void testAEM2350() {
        String input = "at least one full semester university course in introductory economics.";
        String expected = "COURSE(Special requirement: at least one full semester university course in introductory economics)";
        assertTokenization(input, expected);
    }

    // 4. AEM 2555: Named Seminar Requirement
    @Test
    public void testAEM2555() {
        String input = "First-Year Writing Seminar or equivalent.";
        String expected = "COURSE(Special requirement: First-Year Writing Seminar)";
        assertTokenization(input, expected);
    }

    // 5. AEM 2600: Simple Comma List (Implicit AND)
    @Test
    public void testAEM2600() {
        String input = "ECON 1110, MATH 1110.";
        String expected = "COURSE(ECON 1110), AND, COURSE(MATH 1110)";
        assertTokenization(input, expected);
    }

    // 6. ALS 5780: Natural Language + Permission (Rule Applied)
    @Test
    public void testALS5780() {
        String input = "ITA Language Assessment or permission of ITAP.";
        // Rule: Wrap everything before 'or permission' in parens
        String expected = "LPAREN, " +
                "COURSE(Special requirement: ITA Language Assessment), " +
                "RPAREN, OR, " +
                "COURSE(Permission: permission of ITAP)";
        assertTokenization(input, expected);
    }

    // 7. ALS 5790: Course + Permission (Rule Applied)
    @Test
    public void testALS5790() {
        String input = "ALS 5780 or permission of ITAP.";
        String expected = "LPAREN, " +
                "COURSE(ALS 5780), " +
                "RPAREN, OR, " +
                "COURSE(Permission: permission of ITAP)";
        assertTokenization(input, expected);
    }

    // 8. ALS 5800: Simple AND
    @Test
    public void testALS5800() {
        String input = "ALS 5780 and ALS 5790.";
        String expected = "COURSE(ALS 5780), AND, COURSE(ALS 5790)";
        assertTokenization(input, expected);
    }

    // 9. ARAB 5509: Course + Permission (Rule Applied)
    @Test
    public void testARAB5509() {
        String input = "ARAB 2202 or permission of instructor.";
        String expected = "LPAREN, " +
                "COURSE(ARAB 2202), " +
                "RPAREN, OR, " +
                "COURSE(Permission: permission of instructor)";
        assertTokenization(input, expected);
    }

    // 10. BURM 1122: Course + Permission (Rule Applied)
    @Test
    public void testBURM1122() {
        String input = "BURM 3302 or permission of instructor.";
        String expected = "LPAREN, " +
                "COURSE(BURM 3302), " +
                "RPAREN, OR, " +
                "COURSE(Permission: permission of instructor)";
        assertTokenization(input, expected);
    }

    // 11. AEM 1101: Enrollment Limited to (Natural Language)
    @Test
    public void testAEM1101() {
        String input = "Enrollment limited to: Dyson first-year students.";
        String expected = "COURSE(Special requirement: Enrollment limited to: Dyson first-year students)";
        assertTokenization(input, expected);
    }

    // 12. AEM 2210: Negative Constraint
    @Test
    public void testAEM2210() {
        String input = "Enrollment limited to: non-Dyson students.";
        String expected = "COURSE(Special requirement: Enrollment limited to: non-Dyson students)";
        assertTokenization(input, expected);
    }

    // 13. AEM 4880: Complex Enrollment with Semicolon and Permission
    @Test
    public void testAEM4880() {
        String input = "Enrollment limited to: graduate students; seniors by permission of instructors.";
        String expected = "COURSE(Special requirement: Enrollment limited to: graduate students), AND, " +
                "COURSE(Special requirement: seniors), OR, " +
                "COURSE(Permission: permission of instructors)";
        assertTokenization(input, expected);
    }

    // 14. ALS 5900: Long Natural Language with Parentheses
    @Test
    public void testALS5900() {
        String input = "Enrollment limited to: College of Agriculture and Life Sciences (CALS) Master of Professional Studies (MPS) students.";
        String expected = "COURSE(Special requirement: Enrollment limited to: College of Agriculture and Life Sciences), " +
                "LPAREN, COURSE(Special requirement: CALS), RPAREN, " +
                "COURSE(Special requirement: Master of Professional Studies), " +
                "LPAREN, COURSE(Special requirement: MPS), RPAREN, " +
                "COURSE(Special requirement: students)";
        assertTokenization(input, expected);
    }

    // 15. BTRY 7900: Enrollment with Degree Acronym
    @Test
    public void testBTRY7900() {
        String input = "Enrollment limited to: Doctor of Philosophy (PhD) students.";
        String expected = "COURSE(Special requirement: Enrollment limited to: Doctor of Philosophy), " +
                "LPAREN, COURSE(Special requirement: PhD), RPAREN, " +
                "COURSE(Special requirement: students)";
        assertTokenization(input, expected);
    }

    // 16. ARCH 7112: Prerequisite sentence + Enrollment sentence
    @Test
    public void testARCH7112() {
        String input = "Prerequisite: ARCH 5115. Enrollment by ballot only.";
        String expected = "COURSE(Special requirement: Prerequisite:), " +
                "COURSE(ARCH 5115), AND, " + // Period often acts as separator/AND
                "COURSE(Special requirement: Enrollment by ballot only)";
        assertTokenization(input, expected);
    }

    // 17. AEM 2500: Single Course
    @Test
    public void testAEM2500() {
        String input = "ECON 1110.";
        String expected = "COURSE(ECON 1110)";
        assertTokenization(input, expected);
    }

    // 18. AAS 3885: Enrollment List (Comma -> AND)
    @Test
    public void testAAS3885() {
        String input = "Enrollment limited to: sophomores, juniors, and seniors.";
        String expected = "COURSE(Special requirement: Enrollment limited to: sophomores), AND, " +
                "COURSE(Special requirement: juniors), AND, " +
                "COURSE(Special requirement: seniors)";
        assertTokenization(input, expected);
    }

    // 19. BIOEE 1780: Priority List (Comma -> AND)
    @Test
    public void testBIOEE1780() {
        String input = "Priority given to: first-years, sophomores, and transfer students.";
        String expected = "COURSE(Special requirement: Priority given to: first-years), AND, " +
                "COURSE(Special requirement: sophomores), AND, " +
                "COURSE(Special requirement: transfer students)";
        assertTokenization(input, expected);
    }

    // 20. AEM 2400: Enrollment limited to non-majors
    @Test
    public void testAEM2400() {
        String input = "Enrollment limited to: non-business majors.";
        String expected = "COURSE(Special requirement: Enrollment limited to: non-business majors)";
        assertTokenization(input, expected);
    }
}