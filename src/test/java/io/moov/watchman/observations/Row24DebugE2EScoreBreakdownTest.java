package io.moov.watchman.observations;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.search.EntityScorer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Debug score breakdown for Entity-to-Entity scoring.
 */
@SpringBootTest
@DisplayName("Row 24 - Debug Entity-to-Entity Score Breakdown")
public class Row24DebugE2EScoreBreakdownTest {

    @Autowired
    private EntityScorer entityScorer;

    @Test
    @DisplayName("Check score breakdown for E2E scoring")
    void checkScoreBreakdown() {
        String query = "SMARTMET LLC";
        Entity queryEntity = Entity.of(null, query, null, null);
        
        System.out.println("=== Entity-to-Entity Score Breakdown ===");
        System.out.println("Query: '" + query + "'");
        System.out.println();
        
        // Test two entities: one that should match (47769) and one that shouldn't (30971)
        String[] testIds = {"47769", "30971"};
        String[] expectedNames = {"LIMITED LIABILITY COMPANY SMARTMET", "ACCENTURE BUILDING MATERIALS"};
        
        for (int i = 0; i < testIds.length; i++) {
            Entity testEntity = Entity.of(testIds[i], expectedNames[i], null, null).normalize();
            
            ScoreBreakdown breakdown = entityScorer.scoreWithBreakdown(queryEntity, testEntity);
            
            System.out.println("Entity: " + expectedNames[i]);
            System.out.println("  ID: " + testIds[i]);
            System.out.println("  Name Score: " + String.format("%.4f", breakdown.nameScore()));
            System.out.println("  Alt Names Score: " + String.format("%.4f", breakdown.altNamesScore()));
            System.out.println("  Address Score: " + String.format("%.4f", breakdown.addressScore()));
            System.out.println("  Gov ID Score: " + String.format("%.4f", breakdown.governmentIdScore()));
            System.out.println("  Crypto Score: " + String.format("%.4f", breakdown.cryptoAddressScore()));
            System.out.println("  Contact Score: " + String.format("%.4f", breakdown.contactScore()));
            System.out.println("  Date Score: " + String.format("%.4f", breakdown.dateScore()));
            System.out.println("  FINAL SCORE: " + String.format("%.4f", breakdown.totalWeightedScore()));
            System.out.println();
        }
    }
}
