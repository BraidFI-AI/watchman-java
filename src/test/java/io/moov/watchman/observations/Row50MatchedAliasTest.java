package io.moov.watchman.observations;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.search.EntityScorer;
import io.moov.watchman.trace.ScoringContext;
import io.moov.watchman.trace.ScoringTrace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Test to see what matchedAlias is extracted for entity 55451
 */
@SpringBootTest
public class Row50MatchedAliasTest extends EntityDataIngestionTest {

    @Autowired
    private EntityScorer entityScorer;
    
    @Autowired
    private EntityIndex entityIndex;

    @Test
    public void checkMatchedAliasForTarget() {
        String query = "KIM, Yo'ng-chu";
        String targetId = "55451";
        
        System.out.println("\n=== Checking matchedAlias for Entity 55451 ===");
        System.out.println("Query: \"" + query + "\"");
        System.out.println();
        
        // Find target entity
        Entity target = entityIndex.getAll().stream()
            .filter(e -> e.sourceId().equals(targetId))
            .findFirst()
            .orElse(null);
            
        if (target == null) {
            System.out.println("❌ Entity not found");
            return;
        }
        
        System.out.println("Entity: " + target.name());
        System.out.println("Aliases: " + target.altNames());
        System.out.println();
        
        // Score with tracing to extract matchedAlias
        Entity queryEntity = Entity.of(null, query, null, null);
        ScoringContext ctx = ScoringContext.enabled("test-" + System.nanoTime());
        ScoreBreakdown breakdown = entityScorer.scoreWithBreakdown(queryEntity, target, ctx);
        
        System.out.println("Scores:");
        System.out.printf("  Name Score: %.2f%%%n", breakdown.nameScore() * 100);
        System.out.printf("  Alt Names Score: %.2f%%%n", breakdown.altNamesScore() * 100);
        System.out.printf("  Total Score: %.2f%%%n", breakdown.totalWeightedScore() * 100);
        System.out.println();
        
        // Extract matchedAlias from context
        ScoringTrace trace = ctx.toTrace();
        String matchedAlias = null;
        if (trace != null && trace.metadata() != null) {
            Object aliasObj = trace.metadata().get("matchedAlias");
            if (aliasObj instanceof String) {
                matchedAlias = (String) aliasObj;
            }
        }
        
        System.out.println("Extracted matchedAlias: " + 
            (matchedAlias != null ? "\"" + matchedAlias + "\"" : "NULL"));
        
        if (matchedAlias == null) {
            System.out.println("\n⚠️ PROBLEM: matchedAlias is NULL!");
            System.out.println("This means the entity won't get proper tie-breaking priority.");
            System.out.println();
            System.out.println("Debugging altNameScore:");
            System.out.printf("  nameScore = %.2f%%  |  altNamesScore = %.2f%%%n", 
                breakdown.nameScore() * 100, breakdown.altNamesScore() * 100);
            System.out.println("  matchedAlias is set when: altNamesScore > nameScore");
            System.out.println("  Current: " + breakdown.altNamesScore() + " > " + breakdown.nameScore() + " = " + 
                (breakdown.altNamesScore() > breakdown.nameScore()));
        } else {
            System.out.println("\n✅ matchedAlias is correctly set");
        }
    }
}
