package io.moov.watchman.observations;

import io.moov.watchman.search.SearchService;
import io.moov.watchman.model.SearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class AlQaidaTop50Test {
    
    @Autowired
    private SearchService searchService;

    @Test
    void showTop50AlQaidaResults() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("AL QA'IDA TOP 50 - Find where JIHAD/SYRIA entities appear");
        System.out.println("=".repeat(80) + "\n");
        
        List<SearchResult> results = searchService.search("AL QA'IDA", 50, 0.88);
        
        for (int i = 0; i < Math.min(50, results.size()); i++) {
            SearchResult r = results.get(i);
            String name = r.entity().name();
            String alias = r.matchedAlias() != null ? r.matchedAlias() : "";
            
            System.out.printf("%2d. [%5.1f%%] %s%n", i + 1, r.score() * 100, name);
            if (!alias.isEmpty()) {
                System.out.println("    Alias: " + alias);
            }
            
            // Flag the entities consultant is looking for
            if ((name.toUpperCase().contains("JIHAD") && name.toUpperCase().contains("IRAQ")) ||
                (alias.toUpperCase().contains("JIHAD") && alias.toUpperCase().contains("IRAQ"))) {
                System.out.println("    ⭐ CONSULTANT WANTS TO SEE THIS (JIHAD IN IRAQ)");
            }
            if (name.toUpperCase().contains("SYRIA") || alias.toUpperCase().contains("SYRIA")) {
                System.out.println("    ⭐ CONSULTANT WANTS TO SEE THIS (SYRIA)");
            }
        }
    }
}
