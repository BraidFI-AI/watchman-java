package io.moov.watchman.observations;

import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verify that PALESTINE ISLAMIC JIHAD - SHAQAQI FACTION (ID 4707) is loaded
 * with its alias "ABU GHUNAYM SQUAD OF THE HIZBALLAH BAYT AL-MAQDIS"
 */
@SpringBootTest
@DisplayName("PIJ Entity Loading Test")
class PijEntityLoadedTest {

    @Autowired
    private SearchService searchService;

    @Test
    @DisplayName("Verify PIJ entity 4707 is loaded from OFAC data")
    void pijEntityShouldBeLoaded() {
        // Search for exact entity name
        String query = "PALESTINE ISLAMIC JIHAD SHAQAQI FACTION";
        List<SearchResult> results = searchService.search(query, 20, 0.80);
        
        System.out.println(String.format("\n=== PIJ Entity Load Test ==="));
        System.out.println(String.format("Query: '%s'", query));
        System.out.println(String.format("Results: %d\n", results.size()));
        
        // Check if PIJ appears
        results.stream()
            .filter(r -> r.entity().name().toUpperCase().contains("PALESTINE ISLAMIC JIHAD"))
            .forEach(r -> {
                System.out.println(String.format("✅ Found: %s (ID: %s, Score: %.4f)", 
                    r.entity().name(), r.entity().id(), r.score()));
                
                // Check for ABU GHUNAYM alias
                if (r.entity().altNames() != null) {
                    r.entity().altNames().stream()
                        .filter(alias -> alias.toUpperCase().contains("ABU GHUNAYM"))
                        .forEach(alias -> System.out.println(String.format("   Has alias: %s", alias)));
                }
            });
        
        boolean pijFound = results.stream()
            .anyMatch(r -> r.entity().name().toUpperCase().contains("PALESTINE ISLAMIC JIHAD"));
        
        assertTrue(pijFound, "PIJ entity should be loaded from OFAC data");
    }
    
    @Test
    @DisplayName("Verify ABU GHUNAYM alias is attached to PIJ")
    void pijShouldHaveAbuGhunaymAlias() {
        String query = "PALESTINE ISLAMIC JIHAD";
        List<SearchResult> results = searchService.search(query, 5, 0.80);
        
        System.out.println("\n=== PIJ Alias Check ===");
        
        SearchResult pij = results.stream()
            .filter(r -> r.entity().name().toUpperCase().contains("PALESTINE ISLAMIC JIHAD"))
            .findFirst()
            .orElse(null);
        
        assertNotNull(pij, "PIJ entity not found in search results");
        
        System.out.println(String.format("Entity: %s (ID: %s)", pij.entity().name(), pij.entity().id()));
        System.out.println(String.format("Total altNames: %d", 
            pij.entity().altNames() != null ? pij.entity().altNames().size() : 0));
        
        if (pij.entity().altNames() != null) {
            pij.entity().altNames().forEach(alias -> 
                System.out.println(String.format("  - %s", alias)));
        }
        
        boolean hasAbuGhunaymAlias = pij.entity().altNames() != null && 
            pij.entity().altNames().stream()
                .anyMatch(alias -> alias.toUpperCase().contains("ABU GHUNAYM"));
        
        assertTrue(hasAbuGhunaymAlias, 
            "PIJ should have ABU GHUNAYM SQUAD alias");
    }
}
