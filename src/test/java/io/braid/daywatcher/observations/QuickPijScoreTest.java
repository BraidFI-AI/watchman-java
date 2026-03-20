package io.braid.daywatcher.observations;

import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class QuickPijScoreTest {

    @Autowired
    private SearchService searchService;

    @Test
    void checkPijScore() {
        // Lower minMatch to see if PIJ appears at ANY score
        List<SearchResult> results = searchService.search("HIZBALLAH BAYT AL-MAQDIS", 50, 0.50);
        
        System.out.println("\n=== PIJ Score Check (minMatch=0.50) ===");
        System.out.println("Total results: " + results.size());
        
        // Find PIJ
        results.stream()
            .filter(r -> r.entity().name().toUpperCase().contains("PALESTINE ISLAMIC JIHAD") ||
                        r.entity().id().equals("4707"))
            .forEach(r -> {
                int position = results.indexOf(r) + 1;
                System.out.println(String.format("\n✅ FOUND PIJ at position %d:", position));
                System.out.println(String.format("   Entity: %s", r.entity().name()));
                System.out.println(String.format("   Score: %.4f", r.score()));
                System.out.println(String.format("   Matched Alias: %s", r.matchedAlias()));
            });
        
        boolean found = results.stream().anyMatch(r -> r.entity().id().equals("4707"));
        if (!found) {
            System.out.println("\n❌ PIJ NOT FOUND even with minMatch=0.50");
            System.out.println("\nTop 10 results:");
            results.stream().limit(10).forEach(r -> 
                System.out.println(String.format("  %.4f - %s", r.score(), r.entity().name())));
        }
    }
}
