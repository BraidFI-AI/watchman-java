package io.braid.daywatcher.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Debug test for hasTokenSequenceMatch() method.
 * 
 * <p><strong>Row 6 Issue</strong>: Query "Ramon Eduardo ARELLANO FELIX" should match:
 * - "ARELLANO FELIX, Ramon Eduardo" ✓ exact token sequence
 * - "ARELLANO FELIX, Eduardo Ramon" ✗ permuted token sequence
 */
@SpringBootTest
@DisplayName("Debug: hasTokenSequenceMatch() method")
public class TokenSequenceMatchDebugTest {

    @Autowired
    private SearchService searchService;
    
    private Method hasTokenSequenceMatchMethod;

    @BeforeEach
    void setUp() throws Exception {
        // Access private method via reflection
        hasTokenSequenceMatchMethod = SearchServiceImpl.class.getDeclaredMethod(
            "hasTokenSequenceMatch", String.class, String.class);
        hasTokenSequenceMatchMethod.setAccessible(true);
    }

    private boolean testSequenceMatch(String query, String entityName) throws Exception {
        return (boolean) hasTokenSequenceMatchMethod.invoke(searchService, query, entityName);
    }

    @Test
    @DisplayName("Row 6: Ramon Eduardo ARELLANO FELIX → ARELLANO FELIX, Ramon Eduardo (should match)")
    void testExactSequenceMatch() throws Exception {
        String query = "Ramon Eduardo ARELLANO FELIX";
        String entityName = "ARELLANO FELIX, Ramon Eduardo";
        
        System.out.println("\n=== TEST: Exact Sequence Match ===");
        System.out.println("Query:  " + query);
        System.out.println("Entity: " + entityName);
        
        boolean result = testSequenceMatch(query, entityName);
        System.out.println("Result: " + result + " (expected: true)");
        System.out.println();
        
        assertTrue(result, "Should match: query tokens 'ramon eduardo arellano felix' appear in entity 'arellano felix ramon eduardo' in same order");
    }

    @Test
    @DisplayName("Row 6: Ramon Eduardo ARELLANO FELIX → ARELLANO FELIX, Eduardo Ramon (should NOT match)")
    void testPermutedSequenceNoMatch() throws Exception {
        String query = "Ramon Eduardo ARELLANO FELIX";
        String entityName = "ARELLANO FELIX, Eduardo Ramon";
        
        System.out.println("\n=== TEST: Permuted Sequence (No Match) ===");
        System.out.println("Query:  " + query);
        System.out.println("Entity: " + entityName);
        
        boolean result = testSequenceMatch(query, entityName);
        System.out.println("Result: " + result + " (expected: false)");
        System.out.println();
        
        assertFalse(result, "Should NOT match: query has 'ramon eduardo' but entity has 'eduardo ramon' (wrong order)");
    }

    @Test
    @DisplayName("Comma format: ARELLANO FELIX, Ramon Eduardo → ARELLANO FELIX, Ramon Eduardo")
    void testCommaFormatMatch() throws Exception {
        String query = "ARELLANO FELIX, Ramon Eduardo";
        String entityName = "ARELLANO FELIX, Ramon Eduardo";
        
        System.out.println("\n=== TEST: Comma Format Match ===");
        System.out.println("Query:  " + query);
        System.out.println("Entity: " + entityName);
        
        boolean result = testSequenceMatch(query, entityName);
        System.out.println("Result: " + result + " (expected: true)");
        System.out.println();
        
        assertTrue(result, "Should match: identical strings");
    }

    @Test
    @DisplayName("Debug: Show normalized tokens")
    void debugTokenization() throws Exception {
        String query = "Ramon Eduardo ARELLANO FELIX";
        String entity1 = "ARELLANO FELIX, Ramon Eduardo";
        String entity2 = "ARELLANO FELIX, Eduardo Ramon";
        
        System.out.println("\n=== TOKEN ANALYSIS ===");
        System.out.println("Query: " + query);
        System.out.println("Normalized: " + normalizeForDebug(query));
        System.out.println("Tokens: " + java.util.Arrays.toString(normalizeForDebug(query).split("\\s+")));
        System.out.println();
        
        System.out.println("Entity 1 (should match): " + entity1);
        System.out.println("Normalized: " + normalizeForDebug(entity1));
        System.out.println("Tokens: " + java.util.Arrays.toString(normalizeForDebug(entity1).split("\\s+")));
        System.out.println("Match result: " + testSequenceMatch(query, entity1));
        System.out.println();
        
        System.out.println("Entity 2 (should NOT match): " + entity2);
        System.out.println("Normalized: " + normalizeForDebug(entity2));
        System.out.println("Tokens: " + java.util.Arrays.toString(normalizeForDebug(entity2).split("\\s+")));
        System.out.println("Match result: " + testSequenceMatch(query, entity2));
        System.out.println();
    }

    private String normalizeForDebug(String text) {
        return text.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }
}
