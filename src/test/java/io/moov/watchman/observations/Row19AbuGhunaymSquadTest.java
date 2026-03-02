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
 * TDD RED Phase: Row 19 - ABU GHUNAYM SQUAD investigation
 * 
 * Observation from R1-Entity.csv Row 19:
 * - Entity: "ABU GHUNAYM SQUAD OF THE HIZBALLAH BAYT AL-MAQDIS"
 * - Search query: "HIZBALLAH BAYT AL-MAQDIS"
 * - Problem: Search returns "HIZBALLAH" entity first, omits the main "ABU GHUNAYM SQUAD" entity
 * - Expected: "ABU GHUNAYM SQUAD" should appear in results with query coverage boost
 * 
 * BSA Note: "Added query coverage boost in JaroWinklerSimilarity.bestPairJaro() - 
 * when ALL query tokens match with scores ≥0.95, applies 8% boost capped at 1.0"
 * 
 * This test verifies the specific entity matching behavior.
 */
@SpringBootTest
@DisplayName("Row 19: ABU GHUNAYM SQUAD - Query Coverage Boost")
class Row19AbuGhunaymSquadTest {

    @Autowired
    private SearchService searchService;

    @Test
    @DisplayName("RED: Search 'HIZBALLAH BAYT AL-MAQDIS' should return ABU GHUNAYM SQUAD entity")
    void searchShouldReturnAbuGhunaymSquad() {
        // GIVEN: Search query from BSA consultant observation
        String query = "HIZBALLAH BAYT AL-MAQDIS";
        int limit = 20;
        double minMatch = 0.88;

        // WHEN: Execute search
        List<SearchResult> results = searchService.search(query, limit, minMatch);

        System.out.println("\n=== Row 19: HIZBALLAH BAYT AL-MAQDIS Search Results ===");
        System.out.println(String.format("Query: '%s' | Limit: %d | MinMatch: %.2f", query, limit, minMatch));
        System.out.println(String.format("Total results: %d", results.size()));
        System.out.println("\nTop 10 results:");
        
        results.stream()
            .limit(10)
            .forEach(r -> {
                String aliasInfo = r.matchedAlias() != null ? 
                    String.format(" (matched alias: %s)", r.matchedAlias()) : "";
                System.out.println(String.format("  %.4f - %s%s", r.score(), r.entity().name(), aliasInfo));
            });

        // THEN: ABU GHUNAYM SQUAD should appear in results
        boolean found = results.stream()
            .anyMatch(r -> r.entity().name().toUpperCase().contains("ABU GHUNAYM SQUAD"));

        if (!found) {
            System.out.println("\n❌ FAIL: 'ABU GHUNAYM SQUAD' not found in top " + limit + " results");
            System.out.println("Expected entity: ABU GHUNAYM SQUAD OF THE HIZBALLAH BAYT AL-MAQDIS");
        } else {
            System.out.println("\n✅ PASS: 'ABU GHUNAYM SQUAD' found in results");
        }

        assertTrue(found, 
            "Expected 'ABU GHUNAYM SQUAD' to appear when searching 'HIZBALLAH BAYT AL-MAQDIS' (query coverage boost should prioritize full token match)");
    }

    @Test
    @DisplayName("RED: ABU GHUNAYM SQUAD should rank higher than generic HIZBALLAH")
    void abuGhunaymSquadShouldRankHigherThanHizballah() {
        // GIVEN: Search query
        String query = "HIZBALLAH BAYT AL-MAQDIS";
        int limit = 20;
        double minMatch = 0.88;

        // WHEN: Execute search
        List<SearchResult> results = searchService.search(query, limit, minMatch);

        // THEN: Find positions of both entities
        int abuGhunaymPosition = -1;
        int hizballahPosition = -1;

        for (int i = 0; i < results.size(); i++) {
            String name = results.get(i).entity().name().toUpperCase();
            if (name.contains("ABU GHUNAYM SQUAD")) {
                abuGhunaymPosition = i;
            }
            if (name.equals("HIZBALLAH") || name.equals("HIZBOLLAH") || name.equals("HEZBOLLAH")) {
                hizballahPosition = i;
            }
        }

        System.out.println("\n=== Row 19: Ranking Analysis ===");
        System.out.println(String.format("ABU GHUNAYM SQUAD position: %d", abuGhunaymPosition));
        System.out.println(String.format("HIZBALLAH position: %d", hizballahPosition));

        assertNotEquals(-1, abuGhunaymPosition, 
            "ABU GHUNAYM SQUAD should be in results");

        if (hizballahPosition != -1) {
            assertTrue(abuGhunaymPosition < hizballahPosition,
                String.format("ABU GHUNAYM SQUAD (pos %d) should rank higher than HIZBALLAH (pos %d) due to query coverage boost",
                    abuGhunaymPosition, hizballahPosition));
        }
    }

    @Test
    @DisplayName("Diagnostic: Check PALESTINE ISLAMIC JIHAD with ABU GHUNAYM alias")
    void verifyAbuGhunaymSquadEntityExists() {
        // GIVEN: ABU GHUNAYM SQUAD is an alias of PALESTINE ISLAMIC JIHAD - SHAQAQI FACTION (ID 4707)
        String query = "HIZBALLAH BAYT AL-MAQDIS";

        System.out.println("\n=== Diagnostic: PIJ with ABU GHUNAYM SQUAD Alias ===");
        System.out.println("Expected: PALESTINE ISLAMIC JIHAD - SHAQAQI FACTION (ID 4707)");
        System.out.println("Alias: ABU GHUNAYM SQUAD OF THE HIZBALLAH BAYT AL-MAQDIS");

        List<SearchResult> results = searchService.search(query, 50, 0.70);
        System.out.println(String.format("\nQuery: '%s' - %d results", query, results.size()));
        
        // Find PIJ entity with ABU GHUNAYM alias
        boolean found = results.stream()
            .anyMatch(r -> 
                r.entity().name().toUpperCase().contains("PALESTINE ISLAMIC JIHAD") ||
                (r.matchedAlias() != null && r.matchedAlias().toUpperCase().contains("ABU GHUNAYM")) ||
                (r.entity().altNames() != null && r.entity().altNames().stream()
                    .anyMatch(alias -> alias.toUpperCase().contains("ABU GHUNAYM")))
            );
        
        if (found) {
            System.out.println("\n✅ PIJ entity with ABU GHUNAYM alias found!");
            results.stream()
                .filter(r -> 
                    r.entity().name().toUpperCase().contains("PALESTINE ISLAMIC JIHAD") ||
                    (r.matchedAlias() != null && r.matchedAlias().toUpperCase().contains("ABU GHUNAYM")) ||
                    (r.entity().altNames() != null && r.entity().altNames().stream()
                        .anyMatch(alias -> alias.toUpperCase().contains("ABU GHUNAYM")))
                )
                .forEach(r -> {
                    int position = results.indexOf(r) + 1;
                    System.out.println(String.format("\n  Position #%d - Score: %.4f", position, r.score()));
                    System.out.println(String.format("  Entity: %s (ID: %s)", r.entity().name(), r.entity().id()));
                    if (r.matchedAlias() != null) {
                        System.out.println(String.format("  Matched Alias: %s", r.matchedAlias()));
                    }
                    if (r.entity().altNames() != null) {
                        r.entity().altNames().stream()
                            .filter(alias -> alias.toUpperCase().contains("ABU GHUNAYM"))
                            .forEach(alias -> System.out.println(String.format("  Has Alias: %s", alias)));
                    }
                });
        } else {
            System.out.println("\n❌ PIJ with ABU GHUNAYM alias NOT found");
            System.out.println("\nTop 10 results:");
            results.stream()
                .limit(10)
                .forEach(r -> {
                    System.out.println(String.format("  %.4f - %s", r.score(), r.entity().name()));
                    if (r.matchedAlias() != null) {
                        System.out.println(String.format("           (alias: %s)", r.matchedAlias()));
                    }
                });
        }
    }
}
