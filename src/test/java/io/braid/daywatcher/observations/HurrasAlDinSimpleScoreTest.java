package io.braid.daywatcher.observations;

import io.braid.daywatcher.index.EntityIndex;
import io.braid.daywatcher.model.Entity;
import io.braid.daywatcher.search.EntityScorer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HurrasAlDinSimpleScoreTest {

    @Autowired
    private EntityIndex entityIndex;

    @Autowired
    private EntityScorer scorer;

    @Test
    void checkSimpleScore() {
        System.out.println("\n================================================================");
        System.out.println("HURRAS AL-DIN SIMPLE SCORE TEST");
        System.out.println("================================================================\n");

        Entity entity = entityIndex.getAll().stream()
            .filter(e -> e.name().equals("HURRAS AL-DIN"))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Entity not found"));

        String query = "AL QA'IDA";

        // Use the simple score method (no context)
        double score = scorer.score(query, entity);

        System.out.println("Entity: " + entity.name());
        System.out.println("Query: " + query);
        System.out.println("Score (simple): " + String.format("%.2f%%", score * 100));
        
        System.out.println("\n================================================================");
        if (score >= 0.75) {
            System.out.println("✅ Score >= 75% (alias threshold) - SHOULD APPEAR");
        } else if (score >= 0.88) {
            System.out.println("✅ Score >= 88% (name threshold) - SHOULD APPEAR");
        } else {
            System.out.println("❌ Score < 75% - WILL NOT APPEAR");
        }
    }
}
