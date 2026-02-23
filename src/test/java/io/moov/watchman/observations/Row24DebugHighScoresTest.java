package io.moov.watchman.observations;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.search.EntityScorer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

/**
 * Debug why many unrelated entities score 1.0000 for "SMARTMET LLC".
 */
@SpringBootTest
@DisplayName("Row 24 - Debug Why Many Entities Score 1.0")
public class Row24DebugHighScoresTest {

    @Autowired
    private EntityIndex entityIndex;

    @Autowired
    private EntityScorer entityScorer;

    @Test
    @DisplayName("Check why unrelated entities score 1.0 for 'SMARTMET LLC'")
    void checkHighScores() {
        String query = "SMARTMET LLC";
        System.out.println("=== Query: '" + query + "' ===\n");
        
        // Check some entities that scored 1.0000 in the search results
        String[] testEntityIds = {
            "30971",  // ACCENTURE BUILDING MATERIALS
            "30665",  // AL-AMER FOR MANUFACTURE OF PLASTIC
            "21207",  // AL-QASIUN
            "47769"   // LIMITED LIABILITY COMPANY SMARTMET (target)
        };
        
        for (String id : testEntityIds) {
            Optional<Entity> entityOpt = entityIndex.getAll().stream()
                .filter(e -> id.equals(e.sourceId()))
                .findFirst();
            
            if (entityOpt.isEmpty()) {
                System.out.println("Entity " + id + " not found\n");
                continue;
            }
            
            Entity entity = entityOpt.get();
            double score = entityScorer.score(query, entity);
            
            System.out.println("Entity ID: " + id);
            System.out.println("  Name: " + entity.name());
            if (entity.preparedFields() != null) {
                System.out.println("  normalizedPrimaryName: '" + entity.preparedFields().normalizedPrimaryName() + "'");
            }
            System.out.println("  Score: " + String.format("%.4f", score));
            System.out.println();
        }
    }
}
