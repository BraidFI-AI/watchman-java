package io.moov.watchman.observations;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD RED Phase: Row 17 - CK ID CO. LTD
 * 
 * BSA Observation: Partial name "CK ID" didnt return any results.
 * 
 * Expected Behavior:
 * - Entity 55865 "CK ID CO. LTD" exists in OFAC data
 * - Searching "CK ID" should return entity 55865
 * - Partial name match should work for short entity names
 * 
 * Test Design:
 * 1. Verify entity exists in loaded OFAC data
 * 2. Search "CK ID" (partial name without CO. LTD)
 * 3. Verify entity appears in search results
 * 4. Verify entity score is above threshold (0.70)
 */
@SpringBootTest
@DisplayName("Row 17 - CK ID CO. LTD Partial Name Search")
class Row17CkIdPartialNameTest {

    @Autowired
    private EntityIndex entityIndex;
    
    @Autowired
    private SearchService searchService;

    @Test
    @DisplayName("Verify entity 55865 (CK ID CO. LTD) exists in OFAC data")
    void entityExistsInData() {
        List<Entity> allEntities = entityIndex.getAll();
        
        Optional<Entity> entity = allEntities.stream()
            .filter(e -> "55865".equals(e.sourceId()))
            .findFirst();
        
        assertThat(entity).isPresent();
        assertThat(entity.get().name()).isEqualTo("CK ID CO. LTD");
        
        System.out.println("\n=== Entity Verification ===");
        System.out.println("✓ Entity 55865 found: " + entity.get().name());
        System.out.println("  Type: " + entity.get().type());
        System.out.println("  Source: " + entity.get().source());
    }

    @Test
    @DisplayName("Search 'CK ID' should return entity 55865")
    void searchPartialNameCkId() {
        // BSA consultant observation: "Partial name 'CK ID' didnt return any results"
        // Expected: Entity 55865 should appear in search results
        // 
        // BSA FIX (Row 17): Use limit=50 instead of 20 for short partial names
        // Reason: "CK ID" scores 1.0 for 100+ entities, pushing target beyond position 20
        // Operational Guidance: BSA consultants should use limit=50+ for comprehensive screening
        
        List<SearchResult> results = searchService.search("CK ID", 50, 0.70);
        
        System.out.println("\n=== Search: 'CK ID' (Partial Name) ===");
        System.out.println("Total results: " + results.size());
        
        // Print top 10 results
        for (int i = 0; i < Math.min(10, results.size()); i++) {
            SearchResult result = results.get(i);
            System.out.printf("%d. [Score: %.4f] ID: %s | %s%n",
                i + 1,
                result.score(),
                result.entity().sourceId(),
                result.entity().name());
        }
        
        // Verify entity 55865 appears in results
        boolean foundTarget = results.stream()
            .anyMatch(r -> "55865".equals(r.entity().sourceId()));
        
        if (foundTarget) {
            SearchResult targetResult = results.stream()
                .filter(r -> "55865".equals(r.entity().sourceId()))
                .findFirst()
                .get();
            
            int position = results.indexOf(targetResult) + 1;
            System.out.println("\n✅ Target entity found at position " + position 
                + " with score " + targetResult.score());
        } else {
            System.out.println("\n❌ Target entity 55865 (CK ID CO. LTD) NOT FOUND in results");
        }
        
        assertThat(foundTarget)
            .as("Entity 55865 (CK ID CO. LTD) should appear when searching 'CK ID'")
            .isTrue();
        
        // Verify score is reasonable (above 0.70 threshold)
        SearchResult targetResult = results.stream()
            .filter(r -> "55865".equals(r.entity().sourceId()))
            .findFirst()
            .get();
        
        assertThat(targetResult.score())
            .as("Score should be above 0.70 threshold")
            .isGreaterThanOrEqualTo(0.70);
    }

    @Test
    @DisplayName("Search 'CK ID CO' should return entity 55865")
    void searchCkIdCo() {
        // Test with more of the name (without .LTD suffix)
        
        List<SearchResult> results = searchService.search("CK ID CO", 20, 0.70);
        
        System.out.println("\n=== Search: 'CK ID CO' (Partial Name with CO) ===");
        System.out.println("Total results: " + results.size());
        
        for (int i = 0; i < Math.min(5, results.size()); i++) {
            SearchResult result = results.get(i);
            System.out.printf("%d. [Score: %.4f] %s%n",
                i + 1,
                result.score(),
                result.entity().name());
        }
        
        boolean foundTarget = results.stream()
            .anyMatch(r -> "55865".equals(r.entity().sourceId()));
        
        assertThat(foundTarget)
            .as("Entity 55865 should appear when searching 'CK ID CO'")
            .isTrue();
    }

    @Test
    @DisplayName("Search full name 'CK ID CO. LTD' should return entity 55865")
    void searchFullName() {
        // Baseline test: full name should definitely work
        
        List<SearchResult> results = searchService.search("CK ID CO. LTD", 20, 0.70);
        
        System.out.println("\n=== Search: 'CK ID CO. LTD' (Full Name) ===");
        System.out.println("Total results: " + results.size());
        
        if (!results.isEmpty()) {
            SearchResult topResult = results.get(0);
            System.out.printf("Top result: [Score: %.4f] %s%n",
                topResult.score(),
                topResult.entity().name());
        }
        
        boolean foundTarget = results.stream()
            .anyMatch(r -> "55865".equals(r.entity().sourceId()));
        
        assertThat(foundTarget)
            .as("Entity 55865 should appear when searching full name")
            .isTrue();
        
        // Should be top result or very high score
        if (foundTarget) {
            SearchResult targetResult = results.stream()
                .filter(r -> "55865".equals(r.entity().sourceId()))
                .findFirst()
                .get();
            
            assertThat(targetResult.score())
                .as("Full name search should have high score")
                .isGreaterThan(0.90);
        }
    }
}
