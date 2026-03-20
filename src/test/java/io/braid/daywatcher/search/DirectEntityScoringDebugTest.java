package io.braid.daywatcher.search;

import io.braid.daywatcher.index.EntityIndex;
import io.braid.daywatcher.model.Entity;
import io.braid.daywatcher.model.ScoreBreakdown;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Debug test to directly score entity 13041 (AL-QA'IDA KURDISH BATTALIONS)
 * against query "AL QA'IDA" using the actual EntityScorer
 */
@SpringBootTest
@DisplayName("Direct Entity Scoring Debug")
class DirectEntityScoringDebugTest {

    @Autowired
    private EntityIndex entityIndex;

    @Autowired
    private EntityScorer entityScorer;

    @Test
    @DisplayName("Score entity 13041 against 'AL QA'IDA' query")
    void scoreEntity13041() {
        // Find entity 13041
        Entity entity13041 = entityIndex.getAll().stream()
            .filter(e -> "13041".equals(e.sourceId()))
            .findFirst()
            .orElse(null);

        if (entity13041 == null) {
            System.out.println("\n❌ Entity 13041 NOT FOUND in index");
            return;
        }

        System.out.println("\n=== Entity 13041 Found ===");
        System.out.println("ID: " + entity13041.id());
        System.out.println("Source ID: " + entity13041.sourceId());
        System.out.println("Name: " + entity13041.name());
        System.out.println("Alt Names: " + entity13041.altNames());

        // Score it against "AL QA'IDA"
        String query = "AL QA'IDA";
        ScoreBreakdown breakdown = entityScorer.scoreWithBreakdown(query, entity13041);

        System.out.println("\n=== Scoring Results ===");
        System.out.println("Query: " + query);
        System.out.println("Candidate: " + entity13041.name());
        System.out.println("\nScore Breakdown:");
        System.out.println("  Name Score: " + breakdown.nameScore());
        System.out.println("  Alt Names Score: " + breakdown.altNamesScore());
        System.out.println("  Total Weighted Score: " + breakdown.totalWeightedScore());
        System.out.println("\nThreshold check:");
        System.out.println("  0.70 threshold: " + (breakdown.totalWeightedScore() >= 0.70 ? "✅ PASS" : "❌ FAIL"));
        System.out.println("  0.75 threshold: " + (breakdown.totalWeightedScore() >= 0.75 ? "✅ PASS" : "❌ FAIL"));
    }

    @Test
    @DisplayName("Score all AL-QA entities and show top 10")
    void scoreAllAlQaEntities() {
        String query = "AL QA'IDA";

        System.out.println("\n=== Scoring all 'AL QA' entities against '" + query + "' ===\n");

        entityIndex.getAll().stream()
            .filter(e -> e.name().toUpperCase().contains("AL QA") || 
                        e.name().toUpperCase().contains("AL-QA"))
            .map(e -> {
                ScoreBreakdown breakdown = entityScorer.scoreWithBreakdown(query, e);
                return new ScoredEntity(e, breakdown);
            })
            .sorted((a, b) -> Double.compare(b.breakdown.totalWeightedScore(), a.breakdown.totalWeightedScore()))
            .limit(15)
            .forEach(scored -> {
                System.out.printf("Score: %.4f | %-40s | ID: %s%n",
                    scored.breakdown.totalWeightedScore(),
                    scored.entity.name(),
                    scored.entity.sourceId());
            });
    }

    private record ScoredEntity(Entity entity, ScoreBreakdown breakdown) {}
}
