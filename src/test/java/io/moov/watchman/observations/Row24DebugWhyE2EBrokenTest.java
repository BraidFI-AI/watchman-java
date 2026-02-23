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
 * Deep dive into why Entity-to-Entity scoring returns 1.0 for everything
 */
@SpringBootTest
@DisplayName("Row 24 DEBUG: Why E2E Returns 1.0 For All")
public class Row24DebugWhyE2EBrokenTest {

    @Autowired
    private EntityIndex entityIndex;

    @Autowired
    private EntityScorer entityScorer;

    @Test
    @DisplayName("Debug E2E scoring internals for unrelated entity")
    void debugE2EScoringForUnrelatedEntity() {
        String query = "SMARTMET LLC";
        Entity queryEntity = Entity.of(null, query, null, null);
        
        List<Entity> allEntities = entityIndex.getAll();
        
        // Get an unrelated entity (ACCENTURE)
        Entity unrelatedEntity = allEntities.stream()
            .filter(e -> "ACCENTURE BUILDING MATERIALS".equals(e.name()))
            .findFirst()
            .orElse(null);
        
        // Get the target entity (SMARTMET)
        Entity targetEntity = allEntities.stream()
            .filter(e -> "47769".equals(e.sourceId()))
            .findFirst()
            .orElse(null);
        
        System.out.println("\n=== Query Entity ===");
        System.out.println("query.sourceId(): " + queryEntity.sourceId());
        System.out.println("query.name(): " + queryEntity.name());
        System.out.println("query.governmentIds(): " + queryEntity.governmentIds());
        System.out.println("query.addresses(): " + queryEntity.addresses());
        System.out.println("query.altNames(): " + queryEntity.altNames());
        
        // Test with unrelated entity
        System.out.println("\n=== UNRELATED Entity (ACCENTURE) ===");
        if (unrelatedEntity != null) {
            System.out.println("Name: " + unrelatedEntity.name());
            System.out.println("sourceId: " + unrelatedEntity.sourceId());
            
            ScoringContext ctx = ScoringContext.enabled("debug-unrelated");
            ScoreBreakdown breakdown = entityScorer.scoreWithBreakdown(queryEntity, unrelatedEntity, ctx);
            
            System.out.println("\nBreakdown:");
            System.out.println("  nameScore: " + breakdown.nameScore());
            System.out.println("  altNamesScore: " + breakdown.altNamesScore());
            System.out.println("  governmentIdScore: " + breakdown.governmentIdScore());
            System.out.println("  addressScore: " + breakdown.addressScore());
            System.out.println("  cryptoAddressScore: " + breakdown.cryptoAddressScore());
            System.out.println("  contactScore: " + breakdown.contactScore());
            System.out.println("  dateScore: " + breakdown.dateScore());
            System.out.println("  totalWeightedScore: " + breakdown.totalWeightedScore());
            
            // Compare to string-based
            double stringScore = entityScorer.score(query, unrelatedEntity);
            System.out.println("\nString-based score: " + stringScore);
            System.out.println("MISMATCH: E2E=" + breakdown.totalWeightedScore() + " vs String=" + stringScore);
        }
        
        // Test with target entity
        System.out.println("\n\n=== TARGET Entity (SMARTMET 47769) ===");
        if (targetEntity != null) {
            System.out.println("Name: " + targetEntity.name());
            System.out.println("sourceId: " + targetEntity.sourceId());
            
            ScoringContext ctx = ScoringContext.enabled("debug-target");
            ScoreBreakdown breakdown = entityScorer.scoreWithBreakdown(queryEntity, targetEntity, ctx);
            
            System.out.println("\nBreakdown:");
            System.out.println("  nameScore: " + breakdown.nameScore());
            System.out.println("  altNamesScore: " + breakdown.altNamesScore());
            System.out.println("  governmentIdScore: " + breakdown.governmentIdScore());
            System.out.println("  addressScore: " + breakdown.addressScore());
            System.out.println("  cryptoAddressScore: " + breakdown.cryptoAddressScore());
            System.out.println("  contactScore: " + breakdown.contactScore());
            System.out.println("  dateScore: " + breakdown.dateScore());
            System.out.println("  totalWeightedScore: " + breakdown.totalWeightedScore());
            
            // Compare to string-based
            double stringScore = entityScorer.score(query, targetEntity);
            System.out.println("\nString-based score: " + stringScore);
            System.out.println("MATCH: E2E=" + breakdown.totalWeightedScore() + " vs String=" + stringScore);
        }

        // Check if it's the sourceId match causing the issue
        System.out.println("\n\n=== SourceId Equality Check ===");
        System.out.println("query.sourceId() == null: " + (queryEntity.sourceId() == null));
        System.out.println("query.sourceId() != null && !query.sourceId().isBlank(): " + 
            (queryEntity.sourceId() != null && !queryEntity.sourceId().isBlank()));
        
        if (unrelatedEntity != null) {
            System.out.println("\nunrelated.sourceId() == null: " + (unrelatedEntity.sourceId() == null));
            System.out.println("unrelated.sourceId() != null && !unrelatedEntity.sourceId().isBlank(): " + 
                (unrelatedEntity.sourceId() != null && !unrelatedEntity.sourceId().isBlank()));
            System.out.println("Would trigger sourceId match: " + 
                (queryEntity.sourceId() != null && !queryEntity.sourceId().isBlank() 
                && unrelatedEntity.sourceId() != null && !unrelatedEntity.sourceId().isBlank()
                && queryEntity.sourceId().equals(unrelatedEntity.sourceId())));
        }
    }
}
