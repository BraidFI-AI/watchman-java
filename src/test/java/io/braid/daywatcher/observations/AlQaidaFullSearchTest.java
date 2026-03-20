package io.braid.daywatcher.observations;

import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class AlQaidaFullSearchTest {

    @Autowired
    private SearchService searchService;

    @Test
    void showAllResultsAbove70Percent() {
        System.out.println("\n================================================================");
        System.out.println("ALL AL QA'IDA RESULTS > 70% (limit=100, minMatch=0.70)");
        System.out.println("================================================================\n");

        List<SearchResult> results = searchService.search("AL QA'IDA", 100, 0.70);

        System.out.println("Total results: " + results.size());
        System.out.println();

        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String alias = r.matchedAlias() != null ? " [Alias: " + r.matchedAlias() + "]" : "";
            String marker = "";
            
            if (r.entity().name().equals("HURRAS AL-DIN") || r.entity().name().contains("SYRIA")) {
                marker = " ⭐⭐⭐ ";
            }
            
            System.out.println(String.format("%3d. [%.2f%%] %s%s%s", 
                i + 1, r.score() * 100, 
                r.entity().name(), alias, marker));
            
            if (i >= 49) break; // Show first 50
        }
    }
}
