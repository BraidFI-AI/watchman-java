package io.braid.daywatcher.observations;

import io.braid.daywatcher.index.EntityIndex;
import io.braid.daywatcher.model.Entity;
import io.braid.daywatcher.model.ScoreBreakdown;
import io.braid.daywatcher.search.EntityScorer;
import io.braid.daywatcher.trace.ScoringContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HurrasAlDinMetadataTest {

    @Autowired
    private EntityIndex entityIndex;

    @Autowired
    private EntityScorer scorer;

    @Test
    void checkMatchedAliasMetadata() {
        System.out.println("\n================================================================");
        System.out.println("HURRAS AL-DIN MATCHED ALIAS METADATA CHECK");
        System.out.println("================================================================\n");

        Entity entity = entityIndex.getAll().stream()
            .filter(e -> e.name().equals("HURRAS AL-DIN"))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Entity not found"));

        String query = "AL QA'IDA";
        Entity queryEntity = Entity.of(null, query, null, null);

        ScoringContext ctx = ScoringContext.enabled("hurras-metadata-check");
        ScoreBreakdown breakdown = scorer.scoreWithBreakdown(queryEntity, entity, ctx);

        System.out.println("Query: " + query);
        System.out.println("Entity: " + entity.name());
        System.out.println("\nScoring:");
        System.out.println("  Name Score: " + String.format("%.2f%%", breakdown.nameScore() * 100));
        System.out.println("  Alt Names Score: " + String.format("%.2f%%", breakdown.altNamesScore() * 100));
        System.out.println("  Total (after boost): " + String.format("%.2f%%", breakdown.totalWeightedScore() * 100));

        System.out.println("\nContext Metadata:");
        if (ctx.toTrace() != null && ctx.toTrace().metadata() != null) {
            System.out.println("  Metadata keys: " + ctx.toTrace().metadata().keySet());
            Object matchedAlias = ctx.toTrace().metadata().get("matchedAlias");
            if (matchedAlias != null) {
                System.out.println("  ✓ matchedAlias: " + matchedAlias);
            } else {
                System.out.println("  ❌ matchedAlias: NULL");
            }
        } else {
            System.out.println("  ❌ No metadata in context");
        }

        System.out.println("\nCondition Check:");
        System.out.println("  altNamesScore > nameScore? " + (breakdown.altNamesScore() > breakdown.nameScore()));
        System.out.println("  altNamesScore > 0? " + (breakdown.altNamesScore() > 0));
        System.out.println("  Should set matchedAlias? " + 
            (breakdown.altNamesScore() > breakdown.nameScore() && breakdown.altNamesScore() > 0));
    }
}
