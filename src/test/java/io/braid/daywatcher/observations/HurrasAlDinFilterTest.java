package io.braid.daywatcher.observations;

import io.braid.daywatcher.index.EntityIndex;
import io.braid.daywatcher.model.Entity;
import io.braid.daywatcher.model.ScoreBreakdown;
import io.braid.daywatcher.search.EntityScorer;
import io.braid.daywatcher.trace.ScoringContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class HurrasAlDinFilterTest {

    @Autowired
    private EntityIndex entityIndex;

    @Autowired
    private EntityScorer scorer;

    @Test
    void checkIfPassesFilters() {
        System.out.println("\n================================================================");
        System.out.println("HURRAS AL-DIN FILTER SIMULATION");
        System.out.println("================================================================\n");

        Entity entity = entityIndex.getAll().stream()
            .filter(e -> e.name().equals("HURRAS AL-DIN"))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Entity not found"));

        String query = "AL QA'IDA";
        Entity queryEntity = Entity.of(null, query, null, null);

        ScoringContext ctx = ScoringContext.enabled("filter-test");
        ScoreBreakdown breakdown = scorer.scoreWithBreakdown(queryEntity, entity, ctx);
        double score = breakdown.totalWeightedScore();
        
        String matchedAlias = null;
        if (ctx.toTrace() != null && ctx.toTrace().metadata() != null) {
            Object aliasObj = ctx.toTrace().metadata().get("matchedAlias");
            if (aliasObj instanceof String) {
                matchedAlias = (String) aliasObj;
            }
        }

        System.out.println("Entity: " + entity.name());
        System.out.println("Query: " + query);
        System.out.println("Score: " + String.format("%.2f%%", score * 100));
        System.out.println("Matched Alias: " + (matchedAlias != null ? matchedAlias : "NULL"));

        System.out.println("\n--- FILTER 1: Threshold Check ---");
        double effectiveMinMatch = 0.88;  // Default for consultant
        boolean meetsThreshold;
        if (matchedAlias != null) {
            System.out.println("Has matchedAlias → Use 0.75 threshold");
            meetsThreshold = score >= 0.75;
            System.out.println("Score " + String.format("%.2f%%", score * 100) + " >= 75%? " + meetsThreshold);
        } else {
            System.out.println("No matchedAlias → Use " + effectiveMinMatch + " threshold");
            meetsThreshold = score >= effectiveMinMatch;
            System.out.println("Score " + String.format("%.2f%%", score * 100) + " >= " + (effectiveMinMatch * 100) + "%? " + meetsThreshold);
        }
        System.out.println("✓ FILTER 1 RESULT: " + (meetsThreshold ? "PASS" : "FAIL"));

        System.out.println("\n--- FILTER 2: Token Coverage Check ---");
        int queryTokenCount = query.trim().split("\\s+").length;
        System.out.println("Query token count: " + queryTokenCount);
        System.out.println("Coverage filter applies to: 3+ tokens");
        
        if (queryTokenCount >= 3 && score >= 0.95) {
            System.out.println("Coverage filter ACTIVE (query has 3+ tokens and score >= 95%)");
            String matchedName = matchedAlias != null ? matchedAlias : entity.name();
            // Would need to calculate coverage here
            System.out.println("✓ FILTER 2 RESULT: Would check coverage");
        } else {
            System.out.println("Coverage filter INACTIVE (query has <3 tokens or score <95%)");
            System.out.println("✓ FILTER 2 RESULT: PASS (bypassed)");
        }

        System.out.println("\n================================================================");
        if (meetsThreshold && (queryTokenCount < 3 || score < 0.95)) {
            System.out.println("✅ ENTITY SHOULD APPEAR IN SEARCH RESULTS");
        } else {
            System.out.println("❌ ENTITY WOULD BE FILTERED OUT");
        }
        
        System.out.println("\nVerifying: Check if entity exists in actual search...");
        System.out.println("Run: searchService.search(\"AL QA'IDA\", 100, 0.88)");
    }
}
