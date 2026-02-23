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
 * TDD RED Phase: Row 24 - LIMITED LIABILITY COMPANY SMARTMET
 * 
 * BSA Observation: "SMARTMET LLC" & "LLC SMARTMET" didn't return the main entity
 * 
 * Expected Behavior:
 * - Entity 47769 "LIMITED LIABILITY COMPANY SMARTMET" exists in OFAC data
 * - Searching "SMARTMET LLC" should return entity 47769
 * - Searching "LLC SMARTMET" should return entity 47769 (name order independence)
 * - Company qualifier placement (LLC) should not prevent matching
 * 
 * Test Design:
 * 1. Verify entity exists in loaded OFAC data
 * 2. Search "SMARTMET LLC" (qualifier after name)
 * 3. Search "LLC SMARTMET" (qualifier before name)
 * 4. Search "SMARTMET" (base name only)
 * 5. Verify entity appears in all variations
 */
@SpringBootTest
@DisplayName("Row 24 - SMARTMET Name Order and LLC Placement")
class Row24SmartmetNameOrderTest {

    @Autowired
    private EntityIndex entityIndex;
    
    @Autowired
    private SearchService searchService;

    @Test
    @DisplayName("Verify entity 47769 (LIMITED LIABILITY COMPANY SMARTMET) exists in OFAC data")
    void entityExistsInData() {
        List<Entity> allEntities = entityIndex.getAll();
        
        Optional<Entity> entity = allEntities.stream()
            .filter(e -> "47769".equals(e.sourceId()))
            .findFirst();
        
        assertThat(entity).isPresent();
        assertThat(entity.get().name()).contains("SMARTMET");
        
        System.out.println("\n=== Entity Verification ===");
        System.out.println("✓ Entity 47769 found: " + entity.get().name());
        System.out.println("  Type: " + entity.get().type());
        System.out.println("  Source: " + entity.get().source());
        
        if (!entity.get().altNames().isEmpty()) {
            System.out.println("  Aliases: " + String.join(", ", entity.get().altNames()));
        }
    }

    @Test
    @DisplayName("Search 'SMARTMET LLC' should return entity 47769")
    void searchSmartmetLlc() {
        // BSA consultant observation: "SMARTMET LLC" didn't return the main entity
        // Expected: Entity 47769 should appear in search results
        
        List<SearchResult> results = searchService.search("SMARTMET LLC", 20, 0.70);
        
        System.out.println("\n=== Search: 'SMARTMET LLC' (LLC after name) ===");
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
        
        // Verify entity 47769 appears in results
        boolean foundTarget = results.stream()
            .anyMatch(r -> "47769".equals(r.entity().sourceId()));
        
        if (foundTarget) {
            SearchResult targetResult = results.stream()
                .filter(r -> "47769".equals(r.entity().sourceId()))
                .findFirst()
                .get();
            
            int position = results.indexOf(targetResult) + 1;
            System.out.println("\n✅ Target entity found at position " + position 
                + " with score " + targetResult.score());
        } else {
            System.out.println("\n❌ Target entity 47769 (LIMITED LIABILITY COMPANY SMARTMET) NOT FOUND in results");
        }
        
        assertThat(foundTarget)
            .as("Entity 47769 should appear when searching 'SMARTMET LLC'")
            .isTrue();
        
        // Verify score is reasonable (above 0.70 threshold)
        SearchResult targetResult = results.stream()
            .filter(r -> "47769".equals(r.entity().sourceId()))
            .findFirst()
            .get();
        
        assertThat(targetResult.score())
            .as("Score should be above 0.70 threshold")
            .isGreaterThanOrEqualTo(0.70);
    }

    @Test
    @DisplayName("Search 'LLC SMARTMET' should return entity 47769")
    void searchLlcSmartmet() {
        // BSA consultant observation: "LLC SMARTMET" didn't return the main entity
        // Expected: Entity 47769 should appear (name order independence)
        
        List<SearchResult> results = searchService.search("LLC SMARTMET", 20, 0.70);
        
        System.out.println("\n=== Search: 'LLC SMARTMET' (LLC before name) ===");
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
        
        // Verify entity 47769 appears in results
        boolean foundTarget = results.stream()
            .anyMatch(r -> "47769".equals(r.entity().sourceId()));
        
        if (foundTarget) {
            SearchResult targetResult = results.stream()
                .filter(r -> "47769".equals(r.entity().sourceId()))
                .findFirst()
                .get();
            
            int position = results.indexOf(targetResult) + 1;
            System.out.println("\n✅ Target entity found at position " + position 
                + " with score " + targetResult.score());
        } else {
            System.out.println("\n❌ Target entity 47769 NOT FOUND in results");
        }
        
        assertThat(foundTarget)
            .as("Entity 47769 should appear when searching 'LLC SMARTMET' (name order independence)")
            .isTrue();
    }

    @Test
    @DisplayName("Search 'SMARTMET' (base name) should return entity 47769")
    void searchSmartmetOnly() {
        // Baseline test: core name without qualifiers
        
        List<SearchResult> results = searchService.search("SMARTMET", 20, 0.70);
        
        System.out.println("\n=== Search: 'SMARTMET' (Base Name Only) ===");
        System.out.println("Total results: " + results.size());
        
        // Print top 5 results
        for (int i = 0; i < Math.min(5, results.size()); i++) {
            SearchResult result = results.get(i);
            System.out.printf("%d. [Score: %.4f] %s%n",
                i + 1,
                result.score(),
                result.entity().name());
        }
        
        boolean foundTarget = results.stream()
            .anyMatch(r -> "47769".equals(r.entity().sourceId()));
        
        assertThat(foundTarget)
            .as("Entity 47769 should appear when searching base name 'SMARTMET'")
            .isTrue();
    }

    @Test
    @DisplayName("Search full name 'LIMITED LIABILITY COMPANY SMARTMET' should return entity 47769")
    void searchFullName() {
        // Baseline test: full name should definitely work
        
        List<SearchResult> results = searchService.search("LIMITED LIABILITY COMPANY SMARTMET", 20, 0.70);
        
        System.out.println("\n=== Search: 'LIMITED LIABILITY COMPANY SMARTMET' (Full Name) ===");
        System.out.println("Total results: " + results.size());
        
        if (!results.isEmpty()) {
            SearchResult topResult = results.get(0);
            System.out.printf("Top result: [Score: %.4f] %s%n",
                topResult.score(),
                topResult.entity().name());
        }
        
        boolean foundTarget = results.stream()
            .anyMatch(r -> "47769".equals(r.entity().sourceId()));
        
        assertThat(foundTarget)
            .as("Entity 47769 should appear when searching full name")
            .isTrue();
        
        // Should be top result or very high score
        if (foundTarget) {
            SearchResult targetResult = results.stream()
                .filter(r -> "47769".equals(r.entity().sourceId()))
                .findFirst()
                .get();
            
            assertThat(targetResult.score())
                .as("Full name search should have high score")
                .isGreaterThan(0.90);
        }
    }
}
