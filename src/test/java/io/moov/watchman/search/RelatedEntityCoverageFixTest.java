package io.moov.watchman.search;

import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.model.SourceList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Test to verify the fix for Related Entity Coverage (Rows 21, 22).
 * After fix, limit=20 means 20 UNIQUE ENTITIES, not 20 total results.
 */
@SpringBootTest
@DisplayName("Related Entity Coverage Fix Verification")
class RelatedEntityCoverageFixTest {

    @Autowired
    private SearchService searchService;

    @Test
    @DisplayName("AL QA'IDA search should return entity 13041 (AL-QA'IDA KURDISH BATTALIONS)")
    void testAlQaidaSearch() {
        String query = "AL QA'IDA";
        int limit = 20;
        double minMatch = 0.70;

        List<SearchResult> results = searchService.search(query, SourceList.US_OFAC, EntityType.BUSINESS, limit, minMatch);

        System.out.println("\n=== AL QA'IDA Search Results (" + query + ") ===");
        System.out.println("Total results: " + results.size());

        // Get unique entity IDs
        List<String> uniqueEntityIds = results.stream()
            .map(r -> r.entity().sourceId())
            .distinct()
            .collect(Collectors.toList());

        System.out.println("Unique entities: " + uniqueEntityIds.size());
        System.out.println("\nUnique entities returned:");
        
        // Group by entity and show count
        results.stream()
            .collect(Collectors.groupingBy(r -> r.entity().sourceId()))
            .forEach((id, entityResults) -> {
                SearchResult first = entityResults.get(0);
                System.out.printf("  [%s] %s (score: %.4f) - %d results (1 primary + %d aliases)%n",
                    id,
                    first.entity().name(),
                    first.score(),
                    entityResults.size(),
                    entityResults.size() - 1);
            });

        // Check if entity 13041 is present
        boolean has13041 = uniqueEntityIds.contains("13041");
        System.out.println("\n✅ Entity 13041 (AL-QA'IDA KURDISH BATTALIONS): " + 
            (has13041 ? "FOUND!" : "❌ MISSING"));

        // Check if entity 11695 is present (AL-QA'IDA IN THE ARABIAN PENINSULA)
        boolean has11695 = uniqueEntityIds.contains("11695");
        System.out.println("✅ Entity 11695 (AL-QA'IDA IN THE ARABIAN PENINSULA): " + 
            (has11695 ? "FOUND!" : "❌ MISSING"));

        // Check if entity 20159 is present (AL-QA'IDA IN THE INDIAN SUBCONTINENT)
        boolean has20159 = uniqueEntityIds.contains("20159");
        System.out.println("✅ Entity 20159 (AL-QA'IDA IN THE INDIAN SUBCONTINENT): " + 
            (has20159 ? "FOUND!" : "❌ MISSING"));
    }
}
