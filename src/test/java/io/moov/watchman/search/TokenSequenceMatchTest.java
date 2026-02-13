package io.moov.watchman.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SearchServiceImpl.hasTokenSequenceMatch() method.
 * 
 * <p>Tests the private method using reflection to verify token sequence matching logic.
 */
@DisplayName("Token Sequence Match Logic")
class TokenSequenceMatchTest {

    private boolean invokeHasTokenSequenceMatch(String query, String entityName) throws Exception {
        SearchServiceImpl service = new SearchServiceImpl(null, null, null);
        Method method = SearchServiceImpl.class.getDeclaredMethod("hasTokenSequenceMatch", String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, query, entityName);
    }

    @Test
    @DisplayName("Exact match: tokens in same order")
    void exactMatch() throws Exception {
        assertTrue(invokeHasTokenSequenceMatch(
            "Ramon Eduardo ARELLANO FELIX",
            "ARELLANO FELIX, Ramon Eduardo"
        ));
    }

    @Test
    @DisplayName("No match: tokens permuted")
    void permutedTokens() throws Exception {
        assertFalse(invokeHasTokenSequenceMatch(
            "Ramon Eduardo ARELLANO FELIX",
            "ARELLANO FELIX, Eduardo Ramon"
        ));
    }

    @Test
    @DisplayName("Match: entity has extra tokens")
    void entityHasExtraTokens() throws Exception {
        assertTrue(invokeHasTokenSequenceMatch(
            "Ramon FELIX",
            "ARELLANO FELIX, Ramon Eduardo"
        ));
    }

    @Test
    @DisplayName("Match: query without comma")
    void queryWithoutComma() throws Exception {
        assertTrue(invokeHasTokenSequenceMatch(
            "ABBAS Abu",
            "ABBAS, Abu"
        ));
    }

    @Test
    @DisplayName("No match: reversed order")
    void reversedOrder() throws Exception {
        assertFalse(invokeHasTokenSequenceMatch(
            "Abu ABBAS",
            "ABBAS, Abu"
        ));
    }

    @Test
    @DisplayName("Debug: Print normalized tokens")
    void debugTokenization() throws Exception {
        String query = "Ramon Eduardo ARELLANO FELIX";
        String entity1 = "ARELLANO FELIX, Ramon Eduardo";
        String entity2 = "ARELLANO FELIX, Eduardo Ramon";

        System.out.println("\n=== Token Sequence Debug ===");
        System.out.println("Query: " + query);
        System.out.println("Entity 1: " + entity1);
        System.out.println("Entity 2: " + entity2);
        
        boolean match1 = invokeHasTokenSequenceMatch(query, entity1);
        boolean match2 = invokeHasTokenSequenceMatch(query, entity2);
        
        System.out.println("Entity 1 matches: " + match1);
        System.out.println("Entity 2 matches: " + match2);
        System.out.println("=== End Debug ===\n");
        
        assertTrue(match1, "Entity 1 should match");
        assertFalse(match2, "Entity 2 should NOT match");
    }
}
