package io.braid.daywatcher.observations;

import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TEGRegressionTest {

    @Autowired
    private SearchService searchService;

    @Test
    void testTEGWithoutPunctuation() {
        System.out.println("\n=== REGRESSION TEST: T.E.G. LIMITED ===");
        System.out.println("Expected: Acronym collapsing should work");
        System.out.println("Query: 'TEG LIMITED' (without periods)");
        System.out.println("Entity in OFAC: 'T.E.G. LIMITED' (with periods)");
        System.out.println();
        
        List<SearchResult> results = searchService.search("TEG LIMITED", 20, 0.88);
        
        System.out.println("Results found: " + results.size());
        for (int i = 0; i < Math.min(10, results.size()); i++) {
            SearchResult r = results.get(i);
            System.out.printf("%d. [%.1f%%] %s%n", 
                i + 1, r.score() * 100, r.entity().name());
        }
        
        boolean found = results.stream()
            .anyMatch(r -> r.entity().name().equals("T.E.G. LIMITED"));
        
        System.out.println();
        System.out.println("T.E.G. LIMITED found: " + (found ? "✅ YES" : "❌ NO"));
        
        if (!found) {
            System.out.println();
            System.out.println("🚨 REGRESSION DETECTED!");
            System.out.println("The acronym collapsing that was previously working is now broken.");
            System.out.println("Check JaroWinklerSimilarity.collapseAcronymTokens() implementation.");
        }
        
        assertTrue(found, "T.E.G. LIMITED should be found when searching 'TEG LIMITED'");
    }
    
    @Test
    void testTEGExactMatch() {
        System.out.println("\n=== EXACT MATCH TEST: T.E.G. LIMITED ===");
        
        List<SearchResult> results = searchService.search("T.E.G. LIMITED", 20, 0.88);
        
        System.out.println("Results found: " + results.size());
        for (int i = 0; i < Math.min(5, results.size()); i++) {
            SearchResult r = results.get(i);
            System.out.printf("%d. [%.1f%%] %s%n", 
                i + 1, r.score() * 100, r.entity().name());
        }
        
        boolean found = results.stream()
            .anyMatch(r -> r.entity().name().equals("T.E.G. LIMITED"));
        
        System.out.println();
        System.out.println("T.E.G. LIMITED found: " + (found ? "✅ YES" : "❌ NO"));
        
        assertTrue(found, "T.E.G. LIMITED should be found with exact search");
    }
}
