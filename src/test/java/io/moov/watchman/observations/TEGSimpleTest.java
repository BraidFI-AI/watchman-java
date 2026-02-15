package io.moov.watchman.observations;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.SearchService;
import io.moov.watchman.index.EntityIndex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple test to check if T.E.G. LIMITED search works after normalization fix.
 */
@SpringBootTest
public class TEGSimpleTest {

    @Autowired
    private SearchService searchService;
    
    @Autowired
    private EntityIndex entityIndex;

    @Test
    void testTEGSearch() {
        // First verify T.E.G. LIMITED is in index and normalized
        List<Entity> allEntities = entityIndex.getAll();
        Entity tegEntity = allEntities.stream()
            .filter(e -> "T.E.G. LIMITED".equals(e.name()))
            .findFirst()
           .orElse(null);
        
        assertThat(tegEntity).isNotNull();
        System.out.println("✅ T.E.G. LIMITED found in index:");
        System.out.println("   ID: " + tegEntity.id());
        System.out.println("   Name: " + tegEntity.name());
        System.out.println("   preparedFields: " + (tegEntity.preparedFields() != null ? "NOT NULL" : "NULL"));
        
        if (tegEntity.preparedFields() != null) {
            System.out.println("   normalizedPrimaryName: " + tegEntity.preparedFields().normalizedPrimaryName());
        }
        
        // Now search for TEG LIMITED (without periods)
        System.out.println("\n🔎 Searching for 'teg limited' with minMatch=0.88, limit=20");
        List<SearchResult> results = searchService.search("teg limited", null, null, 20, 0.88);
        
        System.out.println("Results: " + results.size());
        for (int i = 0; i < Math.min(results.size(), 20); i++) {
            SearchResult r = results.get(i);
            System.out.printf("  %2d. %.2f%% - %s (ID: %s)%s%n", 
                i+1, 
                r.score() * 100,
                r.entity().name(),
                r.entity().id(),
                r.matchedAlias() != null ? " [via alias: " + r.matchedAlias() + "]" : ""
            );
        }
        
        // Check if T.E.G. LIMITED is in the results
        boolean found = results.stream()
            .anyMatch(r -> "T.E.G. LIMITED".equals(r.entity().name()));
        
        System.out.println("\n" + (found ? "✅ SUCCESS: T.E.G. LIMITED found in results!" : "❌ FAIL: T.E.G. LIMITED NOT in results"));
        
        assertThat(found)
            .withFailMessage("T.E.G. LIMITED should be found with search 'teg limited' (acronym collapsing)")
            .isTrue();
    }
}
