package io.moov.watchman.observations;

import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Individual CSV Row 50 - KIM, Yong Ju
 * Retest Comments: "Issues still persist."
 * 
 * Entity 55451: KIM, Yong Ju
 * Alias: KIM, Yo'ng-chu
 * 
 * Investigation: Variations "Yong chu KIM", "Yong-chu KIM", "Yo'ng chu KIM" should find entity 55451
 * - Issue appears to be apostrophe normalization in "Yo'ng-chu"
 * - Implementation notes say "Modified TextNormalizer.java:173-176 to remove apostrophes entirely"
 */
@SpringBootTest
public class Row50KimYongJuSearchTest extends EntityDataIngestionTest {

    @Autowired
    private SearchService searchService;

    @Test
    public void searchKimYongJu_NameOrderVariations() {
        String entityId = "55451";
        String[] queries = {
            "KIM, Yong Ju",      // Exact primary name
            "Yong Ju KIM",       // FN-LN order
            "KIM, Yo'ng-chu",    // Exact alias name
            "Yo'ng chu KIM",     // Alias FN-LN order
            "Yong chu KIM",      // Without apostrophe
            "Yong-chu KIM"       // With hyphen
        };
        
        System.out.println("\n=== S.I. 50 - KIM, Yong Ju Name Order Variations ===");
        System.out.println("Target Entity: 55451 - KIM, Yong Ju");
        System.out.println("Alias: KIM, Yo'ng-chu");
        System.out.println();
        
        for (String query : queries) {
            System.out.println("Query: \"" + query + "\"");
            List<SearchResult> results = searchService.search(query, 10, 0.70);
            
            boolean found = results.stream().anyMatch(r -> r.entity().sourceId().equals(entityId));
            
            if (found) {
                SearchResult target = results.stream()
                    .filter(r -> r.entity().sourceId().equals(entityId))
                    .findFirst().get();
                int position = results.indexOf(target) + 1;
                System.out.printf("  ✅ Found at position %d (%.2f%%)%n", position, target.score() * 100);
                if (target.matchedAlias() != null && !target.matchedAlias().isEmpty()) {
                    System.out.println("     Matched via alias: " + target.matchedAlias());
                }
            } else {
                System.out.println("  ❌ NOT FOUND in top 10 results");
                if (results.size() > 0) {
                    System.out.println("     Top result: " + results.get(0).entity().name() + 
                        " (" + String.format("%.2f%%", results.get(0).score() * 100) + ")");
                }
            }
            System.out.println();
        }
        
        System.out.println("=== Checking entity 55451 aliases ===");
        // Search by exact ID to see what aliases are stored
        List<SearchResult> allKims = searchService.search("KIM", 500, 0.60);
        SearchResult target = allKims.stream()
            .filter(r -> r.entity().sourceId().equals(entityId))
            .findFirst()
            .orElse(null);
        
        if (target != null) {
            System.out.println("Entity found: " + target.entity().name());
            System.out.println("Aliases stored: " + target.entity().altNames());
        } else {
            System.out.println("⚠️ Entity not found in search");
        }
        
        System.out.println("\nConsultant expects all variations to return the entity.");
    }
}
