package io.moov.watchman.observations;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.EntityScorer;
import io.moov.watchman.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Debug why primary name "KIM, Yong Ju" doesn't find entity 55451
 */
@SpringBootTest
public class Row50PrimaryNameTest extends EntityDataIngestionTest {

    @Autowired
    private SearchService searchService;
    
    @Autowired
    private EntityScorer entityScorer;
    
    @Autowired
    private EntityIndex entityIndex;

    @Test
    public void debugPrimaryNameSearch() {
        String query = "KIM, Yong Ju";
        String targetId = "55451";
        
        System.out.println("\n=== Debug: Primary Name \"KIM, Yong Ju\" ===");
        System.out.println("Query: \"" + query + "\"");
        System.out.println();
        
        // Get target entity and score it directly
        Entity target = entityIndex.getAll().stream()
            .filter(e -> e.sourceId().equals(targetId))
            .findFirst()
            .orElse(null);
            
        if (target != null) {
            ScoreBreakdown directScore = entityScorer.scoreWithBreakdown(query, target);
            System.out.println("Direct scoring of entity 55451:");
            System.out.printf("  Primary Name: \"%s\"%n", target.name());
            System.out.printf("  Name Score: %.2f%%  |  Alt Names Score: %.2f%%  |  Total: %.2f%%%n", 
                directScore.nameScore() * 100, 
                directScore.altNamesScore() * 100,
                directScore.totalWeightedScore() * 100);
            System.out.println();
        }
        
        System.out.println("--- Top 50 Search Results (looking for 55451) ---");
        List<SearchResult> results = searchService.search(query, 50, 0.70);
        boolean found = false;
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            if (r.entity().sourceId().equals(targetId) || i < 10) {
                String marker = r.entity().sourceId().equals(targetId) ? " ✅ TARGET" : "";
                System.out.printf("%2d. [%.2f%%] %s (ID: %s)%s%n", 
                    i + 1, r.score() * 100, r.entity().name(), r.entity().sourceId(), marker);
                if (r.entity().sourceId().equals(targetId)) {
                    found = true;
                    if (r.matchedAlias() != null) {
                        System.out.println("     Matched alias: " + r.matchedAlias());
                    }
                }
            }
        }
        
        if (!found) {
            System.out.println("\n⚠️ Target entity NOT in top 15 results - scores 100% but missing!");
        }
    }
}
