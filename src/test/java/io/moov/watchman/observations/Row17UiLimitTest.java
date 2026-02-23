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
 * Test Row 17 with UI default limit=10 to see if alias boost fix helped
 */
@SpringBootTest
@DisplayName("Row 17 - Test with UI default limit=10")
class Row17UiLimitTest {

    @Autowired
    private EntityIndex entityIndex;
    
    @Autowired
    private SearchService searchService;

    @Test
    @DisplayName("Search 'CK ID' with limit=10 (UI default)")
    void searchCkIdWithUiLimit() {
        List<SearchResult> results = searchService.search("CK ID", 10, 0.70);
        
        System.out.println("\n=== Search: 'CK ID' with limit=10 (UI default) ===");
        System.out.println("Total results: " + results.size());
        
        // Print all results
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            System.out.printf("%d. [Score: %.4f] ID: %s | %s%n",
                i + 1,
                result.score(),
                result.entity().sourceId(),
                result.entity().name());
        }
        
        // Check if entity 55865 is in results
        boolean foundTarget = results.stream()
            .anyMatch(r -> "55865".equals(r.entity().sourceId()));
        
        if (foundTarget) {
            SearchResult targetResult = results.stream()
                .filter(r -> "55865".equals(r.entity().sourceId()))
                .findFirst()
                .get();
            
            int position = results.indexOf(targetResult) + 1;
            System.out.println("\n✅ Target entity 55865 found at position " + position 
                + " with score " + targetResult.score());
        } else {
            System.out.println("\n❌ Target entity 55865 (CK ID CO. LTD) NOT FOUND in top 10");
            
            // Check where it actually is
            List<SearchResult> allResults = searchService.search("CK ID", 100, 0.70);
            Optional<SearchResult> targetInAll = allResults.stream()
                .filter(r -> "55865".equals(r.entity().sourceId()))
                .findFirst();
            
            if (targetInAll.isPresent()) {
                int actualPosition = allResults.indexOf(targetInAll.get()) + 1;
                System.out.println("   Found at position " + actualPosition + " with limit=100");
                System.out.println("   Score: " + targetInAll.get().score());
            }
        }
        
        // This should pass after fix
        assertThat(foundTarget)
            .describedAs("Entity 55865 should be in top 10 results for 'CK ID' query")
            .isTrue();
    }

    @Test
    @DisplayName("Search 'CK ID CO' with limit=10 (more specific)")
    void searchCkIdCoWithUiLimit() {
        List<SearchResult> results = searchService.search("CK ID CO", 10, 0.70);
        
        System.out.println("\n=== Search: 'CK ID CO' with limit=10 ===");
        System.out.println("Total results: " + results.size());
        
        // Print all results
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            System.out.printf("%d. [Score: %.4f] ID: %s | %s%n",
                i + 1,
                result.score(),
                result.entity().sourceId(),
                result.entity().name());
        }
        
        // Check if entity 55865 is in results
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
            System.out.println("\n❌ Target entity 55865 (CK ID CO. LTD) NOT FOUND in top 10");
        }
        
        assertThat(foundTarget)
            .describedAs("Entity 55865 should be in top 10 results for 'CK ID CO' query")
            .isTrue();
    }

    @Test
    @DisplayName("Count how many entities score 1.0 for 'CK ID'")
    void countPerfectScores() {
        List<SearchResult> results = searchService.search("CK ID", 200, 0.70);
        
        long perfectScores = results.stream()
            .filter(r -> r.score() >= 0.999)
            .count();
        
        System.out.println("\n=== Score Distribution for 'CK ID' ===");
        System.out.println("Total results: " + results.size());
        System.out.println("Perfect scores (>= 0.999): " + perfectScores);
        
        // Show score distribution
        System.out.println("\nTop 20 scores:");
        for (int i = 0; i < Math.min(20, results.size()); i++) {
            SearchResult result = results.get(i);
            System.out.printf("%d. %.4f - %s%n",
                i + 1,
                result.score(),
                result.entity().name());
        }
        
        // Find target entity's position
        Optional<SearchResult> target = results.stream()
            .filter(r -> "55865".equals(r.entity().sourceId()))
            .findFirst();
        
        if (target.isPresent()) {
            int position = results.indexOf(target.get()) + 1;
            System.out.println("\nTarget entity 55865 position: " + position);
            System.out.println("Target entity score: " + target.get().score());
        }
    }
}
