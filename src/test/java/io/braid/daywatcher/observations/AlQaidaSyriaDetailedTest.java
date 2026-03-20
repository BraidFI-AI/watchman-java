package io.braid.daywatcher.observations;

import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class AlQaidaSyriaDetailedTest {

    @Autowired
    private SearchService searchService;

    @Test
    void searchAlQaidaForSyria() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("AL QAIDA Search - Extended to 100 results");
        System.out.println("Looking for: HURRAS AL-DIN (alias: AL-QAIDA IN SYRIA)");
        System.out.println("=".repeat(80));
        
        // Lower threshold to 0.70 to catch HURRAS AL-DIN
        List<SearchResult> results = searchService.search("AL QAIDA", 100, 0.70);
        
        System.out.println("\nTotal results (threshold 0.70): " + results.size());
        System.out.println();
        
        boolean found = false;
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String name = r.entity().name();
            String alias = r.matchedAlias() != null ? r.matchedAlias() : "";
            
            if (name.contains("HURRAS") || 
                alias.toUpperCase().contains("SYRIA") ||
                name.toUpperCase().contains("SYRIA")) {
                found = true;
                System.out.printf("%2d. [%.1f%%] %s%n", i + 1, r.score() * 100, name);
                if (r.matchedAlias() != null) {
                    System.out.println("    Matched Alias: " + r.matchedAlias());
                }
                if (name.contains("HURRAS")) {
                    System.out.println("    ⭐ THIS IS HURRAS AL-DIN");
                }
            }
        }
        
        if (!found) {
            System.out.println("❌ No SYRIA-related entities found");
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Comparing with OFAC:");
        System.out.println("OFAC shows 'AL-QAIDA IN SYRIA' at position 8");
        System.out.println("This is actually HURRAS AL-DIN entity with that alias");
        System.out.println("=".repeat(80));
    }
}
