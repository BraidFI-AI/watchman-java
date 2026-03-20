package io.braid.daywatcher.observations;

import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class PijDirectSearchTest {

    @Autowired
    private SearchService searchService;

    @Test
    void searchDirectly() {
        // Search for PIJ by its primary name
        List<SearchResult> results1 = searchService.search("PALESTINE ISLAMIC JIHAD", 5, 0.80);
        
        System.out.println("\n=== Search: PALESTINE ISLAMIC JIHAD ===");
        System.out.println("Results: " + results1.size());
        results1.forEach(r -> {
            System.out.println(String.format("%.4f - %s (ID: %s)", r.score(), r.entity().name(), r.entity().id()));
            if (r.entity().id().equals("4707")) {
                System.out.println("  Aliases: " + r.entity().altNames());
            }
        });
        
        // Search for the problematic query
        List<SearchResult> results2 = searchService.search("HIZBALLAH BAYT AL-MAQDIS", 5, 0.50);
        System.out.println("\n=== Search: HIZBALLAH BAYT AL-MAQDIS ===");
        System.out.println("Results: " + results2.size());
        results2.forEach(r -> {
            System.out.println(String.format("%.4f - %s (ID: %s) [alias: %s]", 
                r.score(), r.entity().name(), r.entity().id(), r.matchedAlias()));
        });
    }
}
