package io.braid.daywatcher.observations;

import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class OtkrititeBankMatchedAliasTest {

    @Autowired
    private SearchService searchService;

    @Test
    void checkOtkrititeBankMatchedAlias() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("OTKRITIE BANK - What is the matchedAlias?");
        System.out.println("=".repeat(80));
        
        List<SearchResult> results = searchService.search("OTKRITIE", 20, 0.88);
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String name = r.entity().name();
            
            if (name.contains("PUBLIC JOINT STOCK COMPANY BANK FINANCIAL CORPORATION OTKRITIE")) {
                System.out.println("\nPosition: " + (i + 1));
                System.out.println("Entity name: " + name);
                System.out.println("Score: " + (r.score() * 100) + "%");
                System.out.println("Matched Alias: " + (r.matchedAlias() != null ? r.matchedAlias() : "NULL"));
                
                if (r.entity().altNames() != null) {
                    System.out.println("\nAll aliases:");
                    r.entity().altNames().forEach(alt -> {
                        System.out.println("  - " + alt);
                    });
                }
                
                System.out.println("\nDoes matchedAlias contain 'OTKRITIE BANK'? " + 
                    (r.matchedAlias() != null && r.matchedAlias().toUpperCase().contains("OTKRITIE BANK")));
                break;
            }
        }
    }
}
