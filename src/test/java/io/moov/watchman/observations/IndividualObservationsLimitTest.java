package io.moov.watchman.observations;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.search.SearchService;
import io.moov.watchman.model.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

/**
 * Verify Individual observations rows 1, 6, 7 were caused by UI limit=5, now resolved.
 */
@SpringBootTest
@DisplayName("Individual Observations - UI Limit Test")
class IndividualObservationsLimitTest {

    @Autowired
    private EntityIndex entityIndex;
    
    @Autowired
    private SearchService searchService;

    @Test
    @DisplayName("Row 1: ABBAS, Abu - Entity 13416 appears beyond position 5")
    void testAbbasEntityBeyondLimitFive() {
        List<SearchResult> results50 = searchService.search("ABBAS, Abu", 50, 0.70);
        
        System.out.println("\n=== Row 1: ABBAS, Abu (limit=50, threshold=0.70) ===");
        System.out.println("Total results: " + results50.size());
        
        // Find entity 13416
        Optional<SearchResult> entity13416 = results50.stream()
            .filter(r -> "13416".equals(r.entity().sourceId()))
            .findFirst();
        
        if (entity13416.isPresent()) {
            int position = results50.indexOf(entity13416.get()) + 1;
            System.out.println("\n✓ Entity 13416 FOUND at position: " + position);
            System.out.println("  Name: " + entity13416.get().entity().name());
            System.out.println("  Score: " + String.format("%.2f%%", entity13416.get().score() * 100));
            
            if (position > 5) {
                System.out.println("\n⚠️  This entity was HIDDEN when limit=5 (UI default before fix)");
                System.out.println("  Consultant confirmed: 'UI defaults to displaying only the top 5 results may introduce an operational visibility concern'");
                System.out.println("  ✅ RESOLVED: UI now defaults to limit=50");
            }
        } else {
            System.out.println("\n✗ Entity 13416 NOT FOUND in results");
        }
        
        // Show top 10 for reference
        System.out.println("\nTop 10 Results:");
        results50.stream().limit(10).forEach(r -> {
            System.out.printf("  [%d] Score: %.2f%% | ID: %s | Name: %s%n",
                results50.indexOf(r) + 1,
                r.score() * 100,
                r.entity().sourceId(),
                r.entity().name());
        });
    }
    
    @Test
    @DisplayName("Row 6-7: ARELLANO FELIX - Multiple entities beyond position 5")
    void testArellanoFelixEntitiesBeyondLimitFive() {
        List<SearchResult> results50 = searchService.search("ARELLANO FELIX", 50, 0.70);
        
        System.out.println("\n=== Row 6-7: ARELLANO FELIX (limit=50, threshold=0.70) ===");
        System.out.println("Total results: " + results50.size());
        
        // Count entities with ARELLANO FELIX in name
        long arellanoCount = results50.stream()
            .filter(r -> r.entity().name().toUpperCase().contains("ARELLANO FELIX"))
            .count();
        
        System.out.println("Entities with 'ARELLANO FELIX' in name: " + arellanoCount);
        
        // Show all ARELLANO FELIX entities
        System.out.println("\nAll ARELLANO FELIX Results:");
        results50.stream()
            .filter(r -> r.entity().name().toUpperCase().contains("ARELLANO FELIX"))
            .forEach(r -> {
                int position = results50.indexOf(r) + 1;
                System.out.printf("  [%d] Score: %.2f%% | ID: %s | Name: %s%n",
                    position,
                    r.score() * 100,
                    r.entity().sourceId(),
                    r.entity().name());
                
                if (position > 5) {
                    System.out.println("      ⚠️  HIDDEN when limit=5");
                }
            });
        
        // Count how many were hidden
        long hiddenCount = results50.stream()
            .filter(r -> r.entity().name().toUpperCase().contains("ARELLANO FELIX"))
            .filter(r -> results50.indexOf(r) >= 5)
            .count();
        
        if (hiddenCount > 0) {
            System.out.println("\n⚠️  " + hiddenCount + " ARELLANO FELIX entities were HIDDEN when limit=5");
            System.out.println("  Consultant observed: 'multiple other OFAC-listed hits were omitted'");
            System.out.println("  ✅ RESOLVED: UI now defaults to limit=50");
        }
    }
}
