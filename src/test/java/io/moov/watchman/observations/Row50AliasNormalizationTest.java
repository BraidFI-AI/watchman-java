package io.moov.watchman.observations;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.SearchService;
import io.moov.watchman.similarity.TextNormalizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Debug test for Row 50 - KIM, Yong Ju alias matching issue  
 * Testing why "KIM, Yo'ng-chu" doesn't find entity 55451 but "Yo'ng chu KIM" does
 */
@SpringBootTest
public class Row50AliasNormalizationTest extends EntityDataIngestionTest {

    @Autowired
    private SearchService searchService;

    @Test
    public void debugAliasNormalization() {
        // The exact alias from OFAC
        String query = "KIM, Yo'ng-chu";
        String entityId = "55451";
        
        System.out.println("\n=== Debug: Alias Normalization ===");
        System.out.println("Query: \"" + query + "\"");
        
        // Normalize the query to see what it becomes
        TextNormalizer normalizer = new TextNormalizer();
        String normalized = normalizer.lowerAndRemovePunctuation(query);
        System.out.println("Normalized query: \"" + normalized + "\"");
        System.out.println();
        
        // Get the target entity and check its aliases
        List<SearchResult> allKims = searchService.search("KIM Yong", 500, 0.50);
        Entity target = allKims.stream()
            .map(SearchResult::entity)
            .filter(e -> e.sourceId().equals(entityId))
            .findFirst()
            .orElse(null);
        
        if (target != null) {
            System.out.println("Target entity: " + target.name());
            System.out.println("Raw aliases: " + target.altNames());
            for (String alias : target.altNames()) {
                String normAlias = normalizer.lowerAndRemovePunctuation(alias);
                System.out.printf("  Alias: \"%s\"%n", alias);
                System.out.printf("    Normalized: \"%s\"%n", normAlias);
            }
            System.out.println();
            
            System.out.println("Normalization comparison:");
            System.out.println("  Query:  \"" + query + "\" -> \"" + normalized + "\"");
            String aliasNorm = normalizer.lowerAndRemovePunctuation(target.altNames().get(0));
            System.out.println("  Alias:  \"" + target.altNames().get(0) + "\" -> \"" + aliasNorm + "\"");
            System.out.println("  Match: " + normalized.equals(aliasNorm));
        } else {
            System.out.println("❌ Target entity not found in KIM Yong search");
        }
        
        System.out.println();
        System.out.println("--- Search results for exact alias ---");
        List<SearchResult> results = searchService.search(query, 5, 0.70);
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String marker = r.entity().sourceId().equals(entityId) ? " ✅" : "";
            System.out.printf("%d. %s (ID: %s, Score: %.2f%%)%s%n",
                i + 1, r.entity().name(), r.entity().sourceId(), r.score() * 100, marker);
        }
    }
}
