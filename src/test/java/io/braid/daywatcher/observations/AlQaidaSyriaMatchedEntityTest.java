package io.braid.daywatcher.observations;

import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class AlQaidaSyriaMatchedEntityTest {

    @Autowired
    private SearchService searchService;

    @Test
    void findWhatMatchesSyria() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("What entity matches 'SYRIA' pattern in AL QA'IDA search?");
        System.out.println("=".repeat(80));
        
        List<SearchResult> results = searchService.search("AL QA'IDA", 10, 0.88);
        
        System.out.println("\nSearching for pattern: SYRIA");
        System.out.println();
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String name = r.entity().name().toUpperCase();
            String matchedAlias = r.matchedAlias() != null ? r.matchedAlias().toUpperCase() : "";
            
            boolean nameMatch = name.matches(".*SYRIA.*");
            boolean matchedAliasMatch = matchedAlias.matches(".*SYRIA.*");
            boolean anyAliasMatch = r.entity().altNames() != null && r.entity().altNames().stream()
                .anyMatch(alt -> alt.toUpperCase().matches(".*SYRIA.*"));
            
            if (nameMatch || matchedAliasMatch || anyAliasMatch) {
                System.out.printf("Position: %d%n", i + 1);
                System.out.println("Entity name: " + r.entity().name());
                System.out.println("Score: " + (r.score() * 100) + "%");
                System.out.println("Matched Alias: " + r.matchedAlias());
                System.out.println("Name contains SYRIA? " + nameMatch);
                System.out.println("Matched alias contains SYRIA? " + matchedAliasMatch);
                System.out.println("Any alias contains SYRIA? " + anyAliasMatch);
                
                if (anyAliasMatch && r.entity().altNames() != null) {
                    System.out.println("Matching aliases:");
                    r.entity().altNames().stream()
                        .filter(alt -> alt.toUpperCase().contains("SYRIA"))
                        .forEach(alt -> System.out.println("  - " + alt));
                }
                System.out.println();
            }
        }
        
        System.out.println("=".repeat(80));
    }
}
