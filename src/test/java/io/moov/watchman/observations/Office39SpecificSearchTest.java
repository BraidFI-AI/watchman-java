package io.moov.watchman.observations;

import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class Office39SpecificSearchTest {

    @Autowired
    private SearchService searchService;

    @Test
    void searchOffice39() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("ROW 35: OFFICE 39 Search (limit=10, minMatch=0.88)");
        System.out.println("=".repeat(80));
        
        List<SearchResult> results = searchService.search("OFFICE 39", 10, 0.88);
        
        System.out.println("\nTotal results: " + results.size());
        System.out.println();
        
        boolean foundOffice39 = false;
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String name = r.entity().name();
            System.out.printf("%2d. [%.1f%%] %s%n", i + 1, r.score() * 100, name);
            
            if (r.matchedAlias() != null && !r.matchedAlias().isEmpty()) {
                System.out.println("    Alias: " + r.matchedAlias());
            }
            
            if (name.toUpperCase().contains("OFFICE 39") || name.toUpperCase().contains("OFFICE #39")) {
                foundOffice39 = true;
                System.out.println("    ✅ OFFICE 39 FOUND");
            }
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("OFFICE 39 in top 10: " + foundOffice39);
        
        if (!foundOffice39) {
            System.out.println("\n❌ OFFICE 39 NOT VISIBLE IN TOP 10");
            System.out.println("   Checking if it exists at all...");
            
            List<SearchResult> moreResults = searchService.search("OFFICE 39", 50, 0.70);
            for (int i = 0; i < moreResults.size(); i++) {
                if (moreResults.get(i).entity().name().toUpperCase().contains("OFFICE 39")) {
                    System.out.println("   Found at position: " + (i + 1));
                    System.out.println("   Score: " + String.format("%.1f%%", moreResults.get(i).score() * 100));
                    break;
                }
            }
        }
        System.out.println("=".repeat(80));
    }
}
