package io.moov.watchman.observations;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.SearchService;
import io.moov.watchman.index.EntityIndex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Debug Row 31: Check if T.E.G. LIMITED is now in top 20 after tie-breaker fix.
 */
@SpringBootTest
public class Row31DebugTest {

    @Autowired
    private SearchService searchService;

    @Test
    void checkTEGInTop20() {
        System.out.println("🔎 Searching for 'teg limited' with minMatch=0.88, limit=20");
        List<SearchResult> results = searchService.search("teg limited", null, null, 20, 0.88);
        
        System.out.println("\nTop 20 results:");
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            boolean isTEG = "T.E.G. LIMITED".equals(r.entity().name());
            System.out.printf("  %2d. %.2f%% - %s%s%s%n", 
                i+1, 
                r.score() * 100,
                r.entity().name(),
                r.matchedAlias() != null ? " [via: " + r.matchedAlias() + "]" : "",
                isTEG ? " ← TARGET!" : ""
            );
        }
        
        boolean found = results.stream()
            .anyMatch(r -> "T.E.G. LIMITED".equals(r.entity().name()));
        
        System.out.println("\n" + (found ? "✅ SUCCESS: T.E.G. LIMITED in top 20!" : "❌ FAIL: T.E.G. LIMITED NOT in top 20"));
    }
}
