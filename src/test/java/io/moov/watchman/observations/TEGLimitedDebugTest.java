package io.moov.watchman.observations;

import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class TEGLimitedDebugTest {

    @Autowired
    private SearchService searchService;

    @Test
    void debugTEGSearch() {
        System.out.println("\n=== TEG LIMITED Search Debug ===");
        
        // Try various search patterns
        String[] queries = {
            "T.E.G. LIMITED",
            "TEG LIMITED",
            "T.E.G.",
            "TEG",
            "T E G LIMITED"
        };
        
        for (String query : queries) {
            List<SearchResult> results = searchService.search(query, 20, 0.88);
            System.out.println("\nQuery: \"" + query + "\"");
            System.out.println("Results: " + results.size());
            
            for (int i = 0; i < Math.min(5, results.size()); i++) {
                SearchResult r = results.get(i);
                System.out.printf("  %d. [%.1f%%] %s%n", 
                    i + 1, r.score() * 100, r.entity().name());
                    
                if (r.entity().name().contains("T.E.G.")) {
                    System.out.println("     ✅ FOUND TEG LIMITED");
                }
            }
            
            boolean found = results.stream()
                .anyMatch(r -> r.entity().name().contains("T.E.G."));
            System.out.println("  TEG LIMITED found: " + (found ? "✅ YES" : "❌ NO"));
        }
    }
}
