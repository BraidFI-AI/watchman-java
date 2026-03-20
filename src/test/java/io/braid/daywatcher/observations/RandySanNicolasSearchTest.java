package io.braid.daywatcher.observations;

import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class RandySanNicolasSearchTest {

    @Autowired
    private SearchService searchService;

    @Test
    void searchRandySanNicolas() {
        System.out.println("\n================================================================");
        System.out.println("SEARCH: Randy San Nicolas (limit=20, minMatch=0.88)");
        System.out.println("================================================================\n");

        List<SearchResult> results = searchService.search("Randy San Nicolas", 20, 0.88);

        System.out.println("Total results: " + results.size());
        System.out.println();

        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String alias = r.matchedAlias() != null ? " [Alias: " + r.matchedAlias() + "]" : "";
            
            System.out.println(String.format("%2d. [%.2f%%] %s%s", 
                i + 1, r.score() * 100, r.entity().name(), alias));
            
            // Analyze which tokens matched
            String name = r.matchedAlias() != null ? r.matchedAlias() : r.entity().name();
            String nameLower = name.toLowerCase();
            
            boolean hasRandy = nameLower.contains("randy");
            boolean hasSan = nameLower.contains("san");
            boolean hasNicolas = nameLower.contains("nicolas");
            
            if (hasSan && !hasRandy && !hasNicolas) {
                System.out.println("    ⚠️  WEAK MATCH: Only 'San' token matched (1/3 tokens = 33%)");
            } else if (hasRandy || hasNicolas) {
                System.out.println("    ✓ Strong match: Contains Randy or Nicolas");
            }
        }
        
        System.out.println("\n================================================================");
        System.out.println("PROBLEM: If all results score 100%, single-token matches (like");
        System.out.println("'SAN' organizations) rank equal to full name matches.");
        System.out.println("================================================================");
    }
}
