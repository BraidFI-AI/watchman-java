package io.moov.watchman.observations;

import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Individual CSV Row 1 - ABBAS, Abu
 * Retest Comments: "All the OFAC references were listed for keyword "ABBAS, Abu" except below.
 * https://sanctionssearch.ofac.treas.gov/Details.aspx?id=13416"
 * 
 * Entity 13416: FAWAZ, Abbas Loutfe
 * Aliases: FOUAZ, Abbas; FAWWAZ, 'Abbas Abu-Ahmad
 * 
 * Investigation: When searching "ABBAS, Abu", should entity 13416 (with alias "FAWWAZ, 'Abbas Abu-Ahmad") be returned?
 * - Alias contains "Abbas" and "Ahmad" but not "Abu" separately
 * - The "'Abbas Abu-Ahmad" portion might be interpreted as first/middle names
 */
@SpringBootTest
public class Row1AbbasSearchTest extends EntityDataIngestionTest {

    @Autowired
    private SearchService searchService;

    @Test
    public void searchAbbasAbu() {
        String query = "ABBAS, Abu";
        List<SearchResult> results = searchService.search(query, 50, 0.70);
        
        System.out.println("\n=== S.I. 1 - ABBAS, Abu Search ===");
        System.out.println("Query: " + query);
        System.out.println("Total results: " + results.size());
        
        for (int i = 0; i < Math.min(results.size(), 20); i++) {
            SearchResult result = results.get(i);
            String marker = result.entity().sourceId().equals("13416") ? " ✅ TARGET" : "";
            System.out.printf("%2d. [%.2f%%] %s (ID: %s)%s%n", 
                i + 1, result.score() * 100, result.entity().name(), result.entity().sourceId(), marker);
            if (result.matchedAlias() != null && !result.matchedAlias().isEmpty()) {
                System.out.println("    Matched Alias: " + result.matchedAlias());
            }
        }
        
        // Check if entity 13416 (FAWAZ, Abbas Loutfe) appears in results
        boolean found = results.stream().anyMatch(r -> r.entity().sourceId().equals("13416"));
        
        if (found) {
            SearchResult target = results.stream().filter(r -> r.entity().sourceId().equals("13416")).findFirst().get();
            int position = results.indexOf(target) + 1;
            System.out.println("\n✅ Entity 13416 found at position: " + position);
            System.out.println("Name: " + target.entity().name());
            System.out.println("Score: " + String.format("%.2f%%", target.score() * 100));
            System.out.println("Aliases: " + String.join(", ", target.entity().altNames()));
            if (target.matchedAlias() != null && !target.matchedAlias().isEmpty()) {
                System.out.println("Matched via alias: " + target.matchedAlias());
            }
        } else {
            System.out.println("\n❌ Entity 13416 (FAWAZ, Abbas Loutfe) NOT FOUND in top 50 results");
            System.out.println("Expected alias match: 'Abbas Abu-Ahmad");
        }
        
        // This is investigation - not asserting pass/fail yet
        System.out.println("\nConsultant expects this entity to appear for query 'ABBAS, Abu'");
    }
}
