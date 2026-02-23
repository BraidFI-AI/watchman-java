package io.moov.watchman.observations;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.EntityScorer;
import io.moov.watchman.search.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

/**
 * Compare search() results vs direct scoring to find discrepancy.
 */
@SpringBootTest
@DisplayName("Row 24 - Compare Search vs Direct Scoring")
public class Row24CompareSearchVsScoringTest {

    @Autowired
    private EntityIndex entityIndex;

    @Autowired
    private EntityScorer entityScorer;
    
    @Autowired
    private SearchService searchService;

    @Test
    @DisplayName("Compare search results vs direct scoring for 'SMARTMET LLC'")
    void compareSearchAndScoring() {
        String query = "SMARTMET LLC";
        
        // Get search results
        List<SearchResult> searchResults = searchService.search(query, 50, 0.70);
        
        System.out.println("=== Search Results for '" + query + "' (limit=50, minMatch=0.70) ===");
        System.out.println("Total results: " + searchResults.size());
        System.out.println();
        
        // Check if entity 47769 is in results
        boolean found47769 = searchResults.stream()
            .anyMatch(r -> "47769".equals(r.entity().sourceId()));
        System.out.println("Entity 47769 in search results: " + (found47769 ? "YES" : "NO"));
        System.out.println();
        
        // Show first 10 search results
        System.out.println("Top 10 search results:");
        for (int i = 0; i < Math.min(10, searchResults.size()); i++) {
            SearchResult result = searchResults.get(i);
            System.out.printf("%d. [Search Score: %.4f] ID: %s | %s%n",
                i + 1,
                result.score(),
                result.entity().sourceId(),
                result.entity().name());
        }
        System.out.println();
        
        // Now directly score the same entities
        System.out.println("Direct scoring of same entities:");
        for (int i = 0; i < Math.min(10, searchResults.size()); i++) {
            SearchResult result = searchResults.get(i);
            Entity entity = result.entity();
            
            double directScore = entityScorer.score(query, entity);
            
            // Also test Entity-to-Entity scoring like the search does
            Entity queryEntity = Entity.of(null, query, null, null);
            double entityToEntityScore = entityScorer.scoreWithBreakdown(queryEntity, entity).totalWeightedScore();
            
            System.out.printf("%d. ID: %s | Search: %.4f | Direct: %.4f | E2E: %.4f | %s%n",
                i + 1,
                entity.sourceId(),
                result.score(),
                directScore,
                entityToEntityScore,
                entity.name());
        }
        System.out.println();
        
        // Check entity 47769 directly
        Optional<Entity> entity47769 = entityIndex.getAll().stream()
            .filter(e -> "47769".equals(e.sourceId()))
            .findFirst();
        
        if (entity47769.isPresent()) {
            double directScore = entityScorer.score(query, entity47769.get());
            System.out.println("=== Entity 47769 (target) ===");
            System.out.println("Name: " + entity47769.get().name());
            System.out.println("Direct score: " + directScore);
            System.out.println("In search results: " + (found47769 ? "YES" : "NO"));
            if (found47769) {
                SearchResult result = searchResults.stream()
                    .filter(r -> "47769".equals(r.entity().sourceId()))
                    .findFirst()
                    .get();
                System.out.println("Search score: " + result.score());
                System.out.println("Position: " + (searchResults.indexOf(result) + 1));
            }
        }
    }
}
