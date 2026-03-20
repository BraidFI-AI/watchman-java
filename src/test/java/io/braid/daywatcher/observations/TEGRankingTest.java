package io.braid.daywatcher.observations;

import io.braid.daywatcher.model.Entity;
import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.search.SearchService;
import io.braid.daywatcher.index.EntityIndex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Check how many entities score 100% for "teg limited" query.
 */
@SpringBootTest
public class TEGRankingTest {

    @Autowired
    private SearchService searchService;
    
    @Autowired
    private EntityIndex entityIndex;

    @Test
    void findAllPerfectMatches() {
        // Search with NO limit to get all results
        System.out.println("🔎 Searching for 'teg limited' with minMatch=0.88, NO LIMIT");
        List<SearchResult> allResults = searchService.search("teg limited", null, null, Integer.MAX_VALUE, 0.88);
        
        // Count how many have 100% score
        long perfectMatches = allResults.stream()
            .filter(r -> r.score() >= 0.9999)  // 100% allowing for floating point  
            .count();
        
        // Find T.E.G. LIMITED position
        int tegPosition = -1;
        for (int i = 0; i < allResults.size(); i++) {
            if ("T.E.G. LIMITED".equals(allResults.get(i).entity().name())) {
                tegPosition = i + 1; // 1-based
                break;
            }
        }
        
        System.out.println("\n===  RESULTS ===");
        System.out.println("Total results: " + allResults.size());
        System.out.println("Perfect matches (100%): " + perfectMatches);
        System.out.println("T.E.G. LIMITED position: " + (tegPosition > 0 ? "#" + tegPosition : "NOT FOUND"));
        
        if (tegPosition > 0) {
            SearchResult tegResult = allResults.get(tegPosition - 1);
            System.out.println("T.E.G. LIMITED score: " + String.format("%.2f%%", tegResult.score() * 100));
            System.out.println("Matched via: " + (tegResult.matchedAlias() != null ? "alias: " + tegResult.matchedAlias() : "primary name"));
        }
        
        // Show first 30 results to see what's ranking higher
        System.out.println("\nFirst 30 results:");
        for (int i = 0; i < Math.min(allResults.size(), 30); i++) {
            SearchResult r = allResults.get(i);
            boolean isTEG = "T.E.G. LIMITED".equals(r.entity().name());
            System.out.printf("  %2d. %.2f%% - %s (ID: %s)%s%s%n", 
                i+1, 
                r.score() * 100,
                r.entity().name(),
                r.entity().id(),
                r.matchedAlias() != null ? " [via alias: " + r.matchedAlias() + "]" : "",
                isTEG ? " ← T.E.G. LIMITED!" : ""
            );
        }
        
        assertThat(tegPosition)
            .withFailMessage("T.E.G. LIMITED should be found in results")
            .isGreaterThan(0);
        
        assertThat(tegPosition)
            .withFailMessage("T.E.G. LIMITED should be in top 20 (currently at position " + tegPosition + ")")
            .isLessThanOrEqualTo(20);
    }
}
