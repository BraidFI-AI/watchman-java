package io.moov.watchman.observations;

import io.moov.watchman.model.Entity;
import io.moov.watchman.search.EntityScorer;
import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.search.SearchService;
import io.moov.watchman.index.EntityIndex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * BSA FIX DIAGNOSTIC: Understand why AL-QAIDA entities score below threshold
 * 
 * Example issue: "ISLAMIC STATE OF IRAQ AND THE LEVANT" has alias "AL-QAIDA GROUP OF JIHAD IN IRAQ"
 * Should appear when searching "AL QA'IDA" but scores 73.51% (below 88%)
 */
@SpringBootTest
class AlQaidaScoreDiagnosticTest {

    @Autowired
    private EntityScorer scorer;

    @Autowired
    private EntityIndex entityIndex;

    @Autowired
    private SearchService searchService;

    @Test
    void diagnoseAlQaidaJihadScoring() {
        // Find the problematic entity
        List<Entity> entities = entityIndex.getAll().stream()
            .filter(e -> e.name().contains("ISLAMIC STATE OF IRAQ AND THE LEVANT"))
            .filter(e -> e.altNames() != null && e.altNames().stream()
                .anyMatch(alt -> alt.contains("JIHAD") && alt.contains("IRAQ")))
            .toList();

        System.out.println("\n================================================================================");
        System.out.println("AL-QAIDA JIHAD DIAGNOSTIC");
        System.out.println("================================================================================\n");

        for (Entity entity : entities) {
            System.out.println("Entity: " + entity.name());
            System.out.println("ID: " + entity.id());
            System.out.println("Source ID: " + entity.sourceId());
            System.out.println("Alt Names:");
            if (entity.altNames() != null) {
                entity.altNames().forEach(alt -> System.out.println("  - " + alt));
            }
            System.out.println();

            // Score against "AL QA'IDA"
            double score = scorer.score("AL QA'IDA", entity);
            ScoreBreakdown breakdown = scorer.scoreWithBreakdown("AL QA'IDA", entity);

            System.out.println("Score vs 'AL QA'IDA': " + String.format("%.2f%%", score * 100));
            System.out.println("Breakdown:");
            System.out.println("  Name:     " + String.format("%.2f%%", breakdown.nameScore() * 100));
            System.out.println("  AltNames: " + String.format("%.2f%%", breakdown.altNamesScore() * 100));
            System.out.println("  FINAL:    " + String.format("%.2f%%", breakdown.totalWeightedScore() * 100));
            System.out.println();
            
            if (score < 0.88) {
                System.out.println("❌ BELOW 88% THRESHOLD");
                System.out.println("   Even with +15% alias boost: " + String.format("%.2f%%", (score + 0.15) * 100));
            } else {
                System.out.println("✅ PASSES THRESHOLD");
            }
        }

        System.out.println("\nNow checking what actually appears in top 20 for 'AL QA'IDA':");
        var results = searchService.search("AL QA'IDA", 20, 0.88);
        
        boolean foundJihad = false;
        boolean foundSyria = false;
        
        for (int i = 0; i < results.size(); i++) {
            var result = results.get(i);
            System.out.println(String.format("%2d. [%5.1f%%] %s", 
                i + 1, result.score() * 100, result.entity().name()));
            if (result.matchedAlias() != null) {
                System.out.println("    Alias: " + result.matchedAlias());
            }
            
            if (result.entity().name().contains("JIHAD") && result.entity().name().contains("IRAQ")) {
                foundJihad = true;
            }
            if (result.entity().name().contains("SYRIA")) {
                foundSyria = true;
            }
        }
        
        System.out.println("\n'JIHAD IN IRAQ' entity in top 20: " + foundJihad);
        System.out.println("'SYRIA' entity in top 20: " + foundSyria);
    }
}
