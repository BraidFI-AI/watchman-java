package io.moov.watchman.observations;

import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class AlQaidaTop30Test {

    @Autowired
    private SearchService searchService;

    @Test
    void showTop30() {
        System.out.println("\n================================================================");
        System.out.println("TOP 30 RESULTS FOR 'AL QA'IDA' (limit=30, minMatch=0.88)");
        System.out.println("================================================================\n");

        List<SearchResult> results = searchService.search("AL QA'IDA", 30, 0.88);

        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String alias = r.matchedAlias() != null ? r.matchedAlias() : "";
            String marker = "";
            
            if (r.entity().name().equals("HURRAS AL-DIN") || 
                r.entity().name().contains("SYRIA") ||
                alias.contains("SYRIA")) {
                marker = " ⭐⭐⭐ SYRIA ENTITY";
            }
            
            System.out.println(String.format("%2d. [%.1f%%] %s", i + 1, r.score() * 100, r.entity().name()));
            if (!alias.isEmpty()) {
                System.out.println("    Alias: " + alias);
            }
            System.out.println(marker);
        }
    }
}
