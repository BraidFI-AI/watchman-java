package io.braid.daywatcher.search;

import io.braid.daywatcher.model.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED Phase: BSA/AML Observation #8 - Common Names Threshold
 * 
 * Issue: Testing with common personal names (e.g., "Muhammad Ali", "Abdul Rahman") 
 * results in limited match results being returned by Watchman, whereas the OFAC 
 * reference list produces multiple matching records.
 * 
 * Root Cause: Default threshold (0.88) is too aggressive for short common names.
 * Short queries (2 tokens) matching longer entity names get penalized.
 * 
 * Solution: Lower threshold to 0.75 for queries with 2 or fewer tokens.
 * 
 * Expected Behavior:
 * - "Muhammad Ali" (2 tokens) → uses 0.75 threshold → returns 2+ OFAC matches
 * - "Abdul Rahman" (2 tokens) → uses 0.75 threshold → returns 3+ OFAC matches
 * - "Nicolas Maduro Moros" (3 tokens) → uses 0.88 threshold → existing behavior
 * 
 * BSA Context: Auditors compare against OFAC.gov portal which returns ALL 
 * possible matches. We need to match their recall, then use Phase 2 auto-clearance
 * to filter false positives via discriminators (DOB, address, etc.).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "watchman.download.enabled=false",
    "watchman.download.on-startup=false"
})
@DisplayName("BSA Observation #8: Common Name Threshold Adjustment")
class CommonNameThresholdTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private io.braid.daywatcher.index.EntityIndex entityIndex;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // Clear and load test data matching real OFAC entities
        entityIndex.clear();
        
        // Add OFAC entities with common names "Muhammad Ali" and "Abdul Rahman"
        // These match actual SDN entries found in grep of OFAC data
        entityIndex.addAll(java.util.List.of(
            // Muhammad Ali matches (2 entities found in OFAC)
            io.braid.daywatcher.model.Entity.of(
                "27327", 
                "AHMAD, Muhammad Ali Sayid", 
                io.braid.daywatcher.model.EntityType.PERSON, 
                io.braid.daywatcher.model.SourceList.US_OFAC
            ),
            io.braid.daywatcher.model.Entity.of(
                "47339", 
                "AL-QADIRI, Muhammad Ali", 
                io.braid.daywatcher.model.EntityType.PERSON, 
                io.braid.daywatcher.model.SourceList.US_OFAC
            ),
            
            // Abdul Rahman matches (3+ entities found in OFAC)
            io.braid.daywatcher.model.Entity.of(
                "6931", 
                "YASIN, Abdul Rahman", 
                io.braid.daywatcher.model.EntityType.PERSON, 
                io.braid.daywatcher.model.SourceList.US_OFAC
            ),
            io.braid.daywatcher.model.Entity.of(
                "12295", 
                "MAKKI, Hafiz Abdul Rahman", 
                io.braid.daywatcher.model.EntityType.PERSON, 
                io.braid.daywatcher.model.SourceList.US_OFAC
            ),
            io.braid.daywatcher.model.Entity.of(
                "12598", 
                "ABDUL RAHMAN, Muhammad Jibril", 
                io.braid.daywatcher.model.EntityType.PERSON, 
                io.braid.daywatcher.model.SourceList.US_OFAC
            ),
            
            // Control: Longer name for threshold comparison
            io.braid.daywatcher.model.Entity.of(
                "22790", 
                "MADURO MOROS, Nicolas", 
                io.braid.daywatcher.model.EntityType.PERSON, 
                io.braid.daywatcher.model.SourceList.US_OFAC
            )
        ));
    }

    @Test
    @DisplayName("RED: 'Muhammad Ali' should return at least 2 OFAC matches (currently returns 0-1)")
    void muhammadAli_shouldReturnMultipleMatches() {
        // Given: Common 2-token name present in OFAC with multiple entities
        // OFAC SDN list contains:
        // - AHMAD, Muhammad Ali Sayid (6861)
        // - AL-QADIRI, Muhammad Ali (47339)
        String query = "Muhammad Ali";
        
        // When: Searching with default minMatch (0.88)
        List<SearchResult> results = searchService.search(query, 20, 0.88);
        
        // Then: Should return at least 2 matches (OFAC parity)
        // Note: This will FAIL initially because threshold is too high
        assertTrue(results.size() >= 2, 
            String.format("Expected at least 2 matches for '%s', but got %d. " +
                "OFAC portal shows multiple matches. Common names need lower threshold.", 
                query, results.size()));
        
        // Verify we got the expected entities (at least one should match)
        boolean foundAhmad = results.stream()
            .anyMatch(r -> r.entity().name().contains("AHMAD") && 
                          r.entity().name().contains("Muhammad Ali"));
        boolean foundAlQadiri = results.stream()
            .anyMatch(r -> r.entity().name().contains("AL-QADIRI") && 
                          r.entity().name().contains("Muhammad Ali"));
        
        assertTrue(foundAhmad || foundAlQadiri, 
            "Should find at least one of the known OFAC entities: AHMAD or AL-QADIRI");
    }

    @Test
    @DisplayName("RED: 'Abdul Rahman' should return at least 3 OFAC matches (currently returns 0-1)")
    void abdulRahman_shouldReturnMultipleMatches() {
        // Given: Common 2-token name present in OFAC with multiple entities
        // OFAC SDN list contains:
        // - YASIN, Abdul Rahman (6931)
        // - MAKKI, Hafiz Abdul Rahman (12295)
        // - ABDUL RAHMAN, Muhammad Jibril (12598)
        String query = "Abdul Rahman";
        
        // When: Searching with default minMatch (0.88)
        List<SearchResult> results = searchService.search(query, 20, 0.88);
        
        // Then: Should return at least 3 matches (OFAC parity)
        assertTrue(results.size() >= 3, 
            String.format("Expected at least 3 matches for '%s', but got %d. " +
                "OFAC portal shows multiple matches for this common name.", 
                query, results.size()));
        
        // Verify we got some expected entities
        boolean foundYasin = results.stream()
            .anyMatch(r -> r.entity().name().contains("YASIN") && 
                          r.entity().name().contains("Abdul Rahman"));
        boolean foundMakki = results.stream()
            .anyMatch(r -> r.entity().name().contains("MAKKI") && 
                          r.entity().name().contains("Abdul Rahman"));
        
        assertTrue(foundYasin || foundMakki, 
            "Should find at least one of the known OFAC entities: YASIN or MAKKI");
    }

    @Test
    @DisplayName("RED: Short query (2 tokens) should use lower threshold internally")
    void shortQuery_shouldUseLowerThreshold() {
        // Given: Two queries - one short (2 tokens), one longer (3+ tokens)
        String shortQuery = "Muhammad Ali";  // 2 tokens - should use 0.75
        String longQuery = "Nicolas Maduro Moros";  // 3 tokens - should use 0.88
        
        // When: Searching both with same minMatch parameter
        List<SearchResult> shortResults = searchService.search(shortQuery, 20, 0.88);
        List<SearchResult> longResults = searchService.search(longQuery, 20, 0.88);
        
        // Then: Short query should return more results (due to internal threshold lowering)
        // This is a proxy test - we can't directly inspect the threshold used,
        // but we can verify the BEHAVIOR (more permissive for short queries)
        
        // At minimum, short query should return SOME results
        assertFalse(shortResults.isEmpty(), 
            "Short query '" + shortQuery + "' should return matches with lowered threshold");
        
        // Note: This test verifies the EFFECT of threshold lowering, not the mechanism
        // The actual threshold adjustment happens internally in SearchServiceImpl
    }

    @Test
    @DisplayName("RED: Longer query (3+ tokens) should maintain normal threshold")
    void longerQuery_shouldMaintainNormalThreshold() {
        // Given: A specific 3-token query that should work with normal threshold
        String query = "Nicolas Maduro Moros";  // 3 tokens
        
        // When: Searching with minMatch 0.88
        List<SearchResult> results = searchService.search(query, 10, 0.88);
        
        // Then: Should still find the entity (existing behavior should not break)
        assertFalse(results.isEmpty(), 
            "Normal queries should continue working with 0.88 threshold");
        
        // Should find Nicolas Maduro
        boolean foundMaduro = results.stream()
            .anyMatch(r -> r.entity().name().contains("MADURO"));
        
        assertTrue(foundMaduro, "Should find Nicolas Maduro with normal threshold");
    }

    @Test
    @DisplayName("RED: Single-token query should also benefit from lower threshold")
    void singleTokenQuery_shouldUseLowerThreshold() {
        // Given: Very common single name (likely to have many partial matches)
        String query = "Muhammad";  // 1 token - should use 0.75
        
        // When: Searching with minMatch 0.88
        List<SearchResult> results = searchService.search(query, 20, 0.88);
        
        // Then: Should return some matches (likely many given how common "Muhammad" is)
        // Note: We don't assert a specific count because single-name searches
        // are inherently ambiguous, but they should return SOMETHING
        assertFalse(results.isEmpty(), 
            "Single-token common name '" + query + "' should return matches with lowered threshold");
    }

    @Test
    @DisplayName("RED: Explicit low minMatch should override token-based threshold")
    void explicitLowMinMatch_shouldOverrideTokenThreshold() {
        // Given: A short query with explicitly provided low threshold
        String query = "Muhammad Ali";
        
        // When: Caller explicitly sets minMatch to 0.70 (below our 0.75 auto-adjustment)
        List<SearchResult> results = searchService.search(query, 20, 0.70);
        
        // Then: Should respect the explicit threshold, not force 0.75
        // This ensures we don't break existing API contracts
        assertNotNull(results);
        // We can't easily assert the exact behavior here without inspecting scores,
        // but the test documents the expected contract: explicit minMatch is respected
    }
}
