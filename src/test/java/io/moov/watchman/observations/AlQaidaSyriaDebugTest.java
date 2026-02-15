package io.moov.watchman.observations;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AlQaidaSyriaDebugTest {

    @Autowired
    private EntityIndex entityIndex;

    @Autowired
    private SearchService searchService;

    @Test
    void debugSyriaEntity() {
        System.out.println("\n================================================================");
        System.out.println("AL-QAIDA SYRIA ENTITY DEBUG");
        System.out.println("================================================================\n");

        // Find entity with alias "AL-QAIDA IN SYRIA" 
        Entity syriaEntity = entityIndex.getAll().stream()
            .filter(e -> e.altNames() != null && e.altNames().stream()
                .anyMatch(alt -> alt.toUpperCase().contains("AL-QAIDA") && alt.toUpperCase().contains("SYRIA")))
            .findFirst()
            .orElse(null);

        if (syriaEntity == null) {
            System.out.println("❌ NO ENTITY FOUND with alias containing 'AL-QAIDA' and 'SYRIA'");
            return;
        }

        System.out.println("Entity ID: " + syriaEntity.id());
        System.out.println("Name: " + syriaEntity.name());
        System.out.println("Type: " + syriaEntity.type());
        System.out.println("\nAliases containing SYRIA:");
        if (syriaEntity.altNames() != null) {
            syriaEntity.altNames().stream()
                .filter(alt -> alt.toUpperCase().contains("SYRIA"))
                .forEach(alt -> System.out.println("  - " + alt));
        }

        System.out.println("\n================================================================");
        System.out.println("SEARCH RESULTS for 'AL QA'IDA' (limit=50, minMatch=0.88)");
        System.out.println("================================================================\n");

        var results = searchService.search("AL QA'IDA", 50, 0.88);
        boolean found = false;
        for (int i = 0; i < results.size(); i++) {
            var r = results.get(i);
            if (r.entity().id().equals(syriaEntity.id())) {
                System.out.println(String.format("✅ FOUND at position %d with score %.2f%%", 
                    i + 1, r.score() * 100));
                found = true;
                break;
            }
        }

        if (!found) {
            System.out.println("❌ NOT FOUND in top 50 results");
            System.out.println("\nTrying with lower threshold (minMatch=0.50):");
            
            var moreResults = searchService.search("AL QA'IDA", 100, 0.50);
            for (int i = 0; i < moreResults.size(); i++) {
                var r = moreResults.get(i);
                if (r.entity().id().equals(syriaEntity.id())) {
                    System.out.println(String.format("   Found at position %d with score %.2f%%", 
                        i + 1, r.score() * 100));
                    break;
                }
            }
        }
    }
}
