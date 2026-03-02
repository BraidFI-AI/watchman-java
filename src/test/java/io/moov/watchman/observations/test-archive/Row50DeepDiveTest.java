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
 * Deep dive into why "KIM, Yo'ng-chu" doesn't find entity 55451
 */
@SpringBootTest
public class Row50DeepDiveTest extends EntityDataIngestionTest {

    @Autowired
    private SearchService searchService;
    
    @Autowired
    private EntityScorer entityScorer;
    
    @Autowired
    private EntityIndex entityIndex;

    @Test
    public void compareScoresForExactAlias() {
        String query = "KIM, Yo'ng-chu";
        String targetId = "55451";
        
        System.out.println("\n=== Deep Dive: Why doesn't exact alias match? ===");
        System.out.println("Query: \"" + query + "\"");
        System.out.println();
        
        // Get top 10 results from search service
        System.out.println("--- Top 10 Search Results ---");
        List<SearchResult> topResults = searchService.search(query, 10, 0.70);
        for (int i = 0; i < topResults.size(); i++) {
            SearchResult r = topResults.get(i);
            String marker = r.entity().sourceId().equals(targetId) ? " ✅ TARGET" : "";
            System.out.printf("%2d. [%.2f%%] %s (ID: %s)%s%n", 
                i + 1, r.score() * 100, r.entity().name(), r.entity().sourceId(), marker);
            if (r.matchedAlias() != null && !r.matchedAlias().isEmpty()) {
                System.out.println("     Matched alias: " + r.matchedAlias());
            }
        }
        System.out.println();
        
        // Find our target entity
        Entity target = entityIndex.getAll().stream()
            .filter(e -> e.sourceId().equals(targetId))
            .findFirst()
            .orElse(null);
            
        if (target == null) {
            System.out.println("❌ Target entity not found in index");
            return;
        }
        
        System.out.println("--- Target Entity Details ---");
        System.out.println("ID: " + target.sourceId());
        System.out.println("Primary Name: " + target.name());
        System.out.println("Aliases: " + target.altNames());
        System.out.println();
        
        // Score the target entity directly
        System.out.println("--- Direct Scoring of Target Entity ---");
        ScoreBreakdown targetBreakdown = entityScorer.scoreWithBreakdown(query, target);
        System.out.printf("Name Score: %.2f%%%n", targetBreakdown.nameScore() * 100);
        System.out.printf("Alt Names Score: %.2f%%%n", targetBreakdown.altNamesScore() * 100);
        System.out.printf("TOTAL Score: %.2f%%%n", targetBreakdown.totalWeightedScore() * 100);
        System.out.println();
        
        // Score the top 3 results directly to see why they scored higher
        System.out.println("--- Direct Scoring of Top 3 Results ---");
        for (int i = 0; i < Math.min(3, topResults.size()); i++) {
            Entity entity = topResults.get(i).entity();
            ScoreBreakdown breakdown = entityScorer.scoreWithBreakdown(query, entity);
            System.out.printf("%d. %s (ID: %s)%n", i + 1, entity.name(), entity.sourceId());
            System.out.printf("   Primary Name: \"%s\"%n", entity.name());
            System.out.printf("   Aliases: %s%n", entity.altNames());
            System.out.printf("   Name Score: %.2f%%  |  Alt Names Score: %.2f%%  |  TOTAL: %.2f%%%n", 
                breakdown.nameScore() * 100, 
                breakdown.altNamesScore() * 100,
                breakdown.totalWeightedScore() * 100);
            System.out.println();
        }
    }
}
