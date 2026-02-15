package io.moov.watchman.observations;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.search.EntityScorer;
import io.moov.watchman.trace.ScoringContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HurrasAlDinBreakdownTest {

    @Autowired
    private EntityIndex entityIndex;

    @Autowired
    private EntityScorer scorer;

    @Test
    void showScoringBreakdown() {
        System.out.println("\n================================================================");
        System.out.println("HURRAS AL-DIN SCORE BREAKDOWN");
        System.out.println("================================================================\n");

        Entity entity = entityIndex.getAll().stream()
            .filter(e -> e.name().equals("HURRAS AL-DIN"))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Entity not found"));

        String query = "AL QA'IDA";
        Entity queryEntity = Entity.of(null, query, null, null);

        ScoringContext ctx = ScoringContext.enabled("hurras-debug");
        ScoreBreakdown breakdown = scorer.scoreWithBreakdown(queryEntity, entity, ctx);

        System.out.println("Query: " + query);
        System.out.println("Entity: " + entity.name());
        System.out.println("\nScore Breakdown:");
        System.out.println("  Name Score:     " + String.format("%.2f%%", breakdown.nameScore() * 100));
        System.out.println("  Alt Names Score: " + String.format("%.2f%%", breakdown.altNamesScore() * 100));
        System.out.println("  Total Score:    " + String.format("%.2f%%", breakdown.totalWeightedScore() * 100));

        System.out.println("\nBoost Conditions:");
        System.out.println("  altNamesScore > nameScore? " + (breakdown.altNamesScore() > breakdown.nameScore()));
        System.out.println("  altNamesScore > 0.65? " + (breakdown.altNamesScore() > 0.65));
        System.out.println("  finalScore < 0.88? " + (breakdown.totalWeightedScore() < 0.88));

        boolean matchedViaAlias = breakdown.altNamesScore() > breakdown.nameScore() && breakdown.altNamesScore() > 0.65;
        System.out.println("  matchedViaAlias? " + matchedViaAlias);

        System.out.println("\nAliases:");
        if (entity.altNames() != null) {
            entity.altNames().forEach(alt -> {
                if (alt.toUpperCase().contains("AL-QAIDA") || alt.toUpperCase().contains("SYRIA")) {
                    System.out.println("  ⭐ " + alt);
                } else {
                    System.out.println("  - " + alt);
                }
            });
        }
    }
}
