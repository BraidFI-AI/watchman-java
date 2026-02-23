package io.moov.watchman.observations;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.search.EntityScorer;
import io.moov.watchman.trace.ScoringContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Debug why 200 entities score 1.0 for "CK ID"
 */
@SpringBootTest
@DisplayName("Row 17 DEBUG: Why do 200 entities score 1.0?")
class Row17DebugMassScoresTest {

    @Autowired
    private EntityIndex entityIndex;

    @Autowired
    private EntityScorer entityScorer;

    @Test
    @DisplayName("Debug scoring breakdown for entities that score 1.0 on 'CK ID'")
    void debugScoringBreakdown() {
        String query = "CK ID";
        Entity queryEntity = Entity.of(null, query, null, null);
        
        List<Entity> allEntities = entityIndex.getAll();
        
        // Get target entity (should score 1.0)
        Entity target = allEntities.stream()
            .filter(e -> "55865".equals(e.sourceId()))
            .findFirst()
            .orElse(null);
        
        // Get a few random entities that also score 1.0
        Entity entity2 = allEntities.stream()
            .filter(e -> "27788".equals(e.sourceId())) // AASI, Sheikh Yusuf
            .findFirst()
            .orElse(null);
        
        Entity entity3 = allEntities.stream()
            .filter(e -> "37416".equals(e.sourceId())) // ABDALLAH, Ahmad Jalal Reda
            .findFirst()
            .orElse(null);
        
        Entity entity4 = allEntities.stream()
            .filter(e -> "8860".equals(e.sourceId())) // AL-FADHLI, Muhsin
            .findFirst()
            .orElse(null);
        
        System.out.println("\n=== Query: 'CK ID' ===\n");
        
        if (target != null) {
            debugEntity(queryEntity, target, "TARGET: CK ID CO. LTD");
        }
        
        if (entity2 != null) {
            debugEntity(queryEntity, entity2, "AASI, Sheikh Yusuf");
        }
        
        if (entity3 != null) {
            debugEntity(queryEntity, entity3, "ABDALLAH, Ahmad Jalal Reda");
        }
        
        if (entity4 != null) {
            debugEntity(queryEntity, entity4, "AL-FADHLI, Muhsin");
        }
    }
    
    private void debugEntity(Entity queryEntity, Entity indexEntity, String label) {
        ScoringContext ctx = ScoringContext.enabled("debug-" + indexEntity.sourceId());
        ScoreBreakdown breakdown = entityScorer.scoreWithBreakdown(queryEntity, indexEntity, ctx);
        
        System.out.println("=== " + label + " ===");
        System.out.println("Name: " + indexEntity.name());
        System.out.println("AltNames: " + indexEntity.altNames());
        System.out.println("\nScores:");
        System.out.println("  nameScore: " + breakdown.nameScore());
        System.out.println("  altNamesScore: " + breakdown.altNamesScore());
        System.out.println("  governmentIdScore: " + breakdown.governmentIdScore());
        System.out.println("  totalWeightedScore: " + breakdown.totalWeightedScore());
        
        // Check if alias boost was applied
        double bestNameScore = Math.max(breakdown.nameScore(), breakdown.altNamesScore());
        boolean gotBoost = breakdown.totalWeightedScore() > bestNameScore + 0.1;
        System.out.println("  Alias boost applied: " + gotBoost);
        
        // Also test String-based scoring (no alias boost)
        double stringScore = entityScorer.score(queryEntity.name(), indexEntity);
        System.out.println("  String-based score (no boost): " + stringScore);
        System.out.println();
    }

    @Test
    @DisplayName("Test direct string scoring for CK ID")
    void testDirectStringScoring() {
        String query = "CK ID";
        List<Entity> allEntities = entityIndex.getAll();
        
        // Get target entity
        Entity target = allEntities.stream()
            .filter(e -> "55865".equals(e.sourceId()))
            .findFirst()
            .orElse(null);
        
        // Get random entities
        Entity unrelated1 = allEntities.stream()
            .filter(e -> "27788".equals(e.sourceId()))
            .findFirst()
            .orElse(null);
        
        Entity unrelated2 = allEntities.stream()
            .filter(e -> "37416".equals(e.sourceId()))
            .findFirst()
            .orElse(null);
        
        System.out.println("\n=== String-based Scoring (No Alias Boost) ===\n");
        
        if (target != null) {
            double score = entityScorer.score(query, target);
            System.out.printf("CK ID CO. LTD: %.4f%n", score);
        }
        
        if (unrelated1 != null) {
            double score = entityScorer.score(query, unrelated1);
            System.out.printf("AASI, Sheikh Yusuf: %.4f%n", score);
        }
        
        if (unrelated2 != null) {
            double score = entityScorer.score(query, unrelated2);
            System.out.printf("ABDALLAH, Ahmad Jalal Reda: %.4f%n", score);
        }
    }
}
