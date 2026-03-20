package io.braid.daywatcher.observations;

import io.braid.daywatcher.index.EntityIndex;
import io.braid.daywatcher.model.Entity;
import io.braid.daywatcher.search.EntityScorer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HurrasAlDinScoringTest {

    @Autowired
    private EntityIndex entityIndex;

    @Autowired
    private EntityScorer scorer;

    @Test
    void checkHurrasAlDinScore() {
        System.out.println("\n================================================================");
        System.out.println("HURRAS AL-DIN SCORING FOR 'AL QA'IDA'");
        System.out.println("================================================================\n");

        Entity entity = entityIndex.getAll().stream()
            .filter(e -> e.name().equals("HURRAS AL-DIN"))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Entity not found"));

        String query = "AL QA'IDA";
        double score = scorer.score(query, entity);

        System.out.println("Entity: " + entity.name());
        System.out.println("Query: " + query);
        System.out.println(String.format("Score: %.2f%%", score * 100));
        System.out.println("\nAliases:");
        if (entity.altNames() != null) {
            entity.altNames().forEach(alt -> System.out.println("  - " + alt));
        }

        System.out.println("\n================================================================");
        System.out.println(String.format("Score %.2f%% vs Threshold 88%%: %s", 
            score * 100,
            score >= 0.88 ? "✅ PASS" : "❌ FAIL"));
        System.out.println(String.format("Score %.2f%% vs Alias Threshold 75%%: %s", 
            score * 100,
            score >= 0.75 ? "✅ PASS" : "❌ FAIL"));
    }
}
