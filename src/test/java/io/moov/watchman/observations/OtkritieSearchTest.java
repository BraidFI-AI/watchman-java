package io.moov.watchman.observations;

import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class OtkritieSearchTest {

    @Autowired
    private SearchService searchService;

    @Test
    void searchOtkritie() {
        System.out.println("\n================================================================");
        System.out.println("OTKRITIE SEARCH (limit=20, minMatch=0.88)");
        System.out.println("================================================================\n");

        List<SearchResult> results = searchService.search("OTKRITIE", 20, 0.88);

        System.out.println("Total results: " + results.size());
        System.out.println();

        boolean foundBank = false;
        boolean foundLtd = false;

        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String name = r.entity().name();
            String alias = r.matchedAlias() != null ? " [Alias: " + r.matchedAlias() + "]" : "";
            
            System.out.println(String.format("%2d. [%.2f%%] %s%s", 
                i + 1, r.score() * 100, name, alias));
            
            if (name.contains("OTKRITIE BANK") || name.contains("OTKRYTIE BANK")) {
                foundBank = true;
                System.out.println("    ✅ BANK found");
            }
            if (name.contains("OTKRITIE LTD") || name.contains("OTKRYTIE LTD")) {
                foundLtd = true;
                System.out.println("    ✅ LTD found");
            }
        }

        System.out.println("\n================================================================");
        System.out.println("OTKRITIE BANK in top 20: " + foundBank);
        System.out.println("OTKRITIE LTD in top 20: " + foundLtd);
        
        if (!foundBank) {
            System.out.println("\n❌ OTKRITIE BANK missing - checking if it exists" );
        }
    }
}
