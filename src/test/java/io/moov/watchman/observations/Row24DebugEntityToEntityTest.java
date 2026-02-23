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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("Row 24 DEBUG: Entity-to-Entity Scoring Investigation")
public class Row24DebugEntityToEntityTest {

    @Autowired
    private EntityIndex entityIndex;

    @Autowired
    private EntityScorer entityScorer;

    @Test
    @DisplayName("Compare Entity-to-Entity vs String-based scoring for SMARTMET LLC")
    void compareEntityVsStringScoringForSmartmet() {
        // Load entity 47769 - LIMITED LIABILITY COMPANY SMARTMET
        List<Entity> allEntities = entityIndex.getAll();
        Optional<Entity> entityOpt = allEntities.stream()
            .filter(e -> "47769".equals(e.sourceId()))
            .findFirst();
        assertThat(entityOpt).isPresent();
        Entity entity47769 = entityOpt.get();
        
        String query = "SMARTMET LLC";
        
        // Method 1: Entity-to-Entity (what SearchServiceImpl does)
        Entity queryEntity = Entity.of(null, query, null, null);
        ScoringContext ctx1 = ScoringContext.enabled("entity-to-entity");
        ScoreBreakdown breakdown1 = entityScorer.scoreWithBreakdown(queryEntity, entity47769, ctx1);
        
        // Method 2: String-based (the workaround)
        double stringScore = entityScorer.score(query, entity47769);
        
        System.out.println("\n=== SMARTMET LLC Query ===");
        System.out.println("Query Entity properties:");
        System.out.println("  - sourceId: " + queryEntity.sourceId());
        System.out.println("  - name: " + queryEntity.name());
        System.out.println("  - preparedFields: " + queryEntity.preparedFields());
        System.out.println();
        System.out.println("Index Entity 47769 properties:");
        System.out.println("  - sourceId: " + entity47769.sourceId());
        System.out.println("  - name: " + entity47769.name());
        System.out.println("  - preparedFields: " + entity47769.preparedFields());
        System.out.println("  - normalizedPrimaryName: " + 
            (entity47769.preparedFields() != null ? entity47769.preparedFields().normalizedPrimaryName() : "null"));
        System.out.println();
        System.out.println("Scoring comparison:");
        System.out.println("  Entity-to-Entity total: " + breakdown1.totalWeightedScore());
        System.out.println("    - nameScore: " + breakdown1.nameScore());
        System.out.println("    - altNamesScore: " + breakdown1.altNamesScore());
        System.out.println("    - governmentIdScore: " + breakdown1.governmentIdScore());
        System.out.println("    - addressScore: " + breakdown1.addressScore());
        System.out.println("  String-based: " + stringScore);
        
        // This should fail - Entity-to-Entity returns wrong score
        assertThat(breakdown1.totalWeightedScore())
            .describedAs("Entity-to-Entity should match String-based score")
            .isEqualTo(stringScore);
    }

    @Test
    @DisplayName("Test Entity-to-Entity with random unrelated entity")
    void testEntityScoringWithUnrelatedEntity() {
        List<Entity> allEntities = entityIndex.getAll();
        
        // Load entity 47769 - SMARTMET
        Optional<Entity> entity47769Opt = allEntities.stream()
            .filter(e -> "47769".equals(e.sourceId()))
            .findFirst();
        assertThat(entity47769Opt).isPresent();
        Entity entity47769 = entity47769Opt.get();
        
        // Load entity 1 (should be unrelated to SMARTMET)
        Optional<Entity> entity1Opt = allEntities.stream()
            .filter(e -> "1".equals(e.sourceId()))
            .findFirst();
        assertThat(entity1Opt).isPresent();
        Entity entity1 = entity1Opt.get();
        
        String query = "SMARTMET LLC";
        Entity queryEntity = Entity.of(null, query, null, null);
        
        // Score each with Entity-to-Entity
        ScoreBreakdown smartmetBreakdown = entityScorer.scoreWithBreakdown(queryEntity, entity47769, ScoringContext.disabled());
        ScoreBreakdown unrelatedBreakdown = entityScorer.scoreWithBreakdown(queryEntity, entity1, ScoringContext.disabled());
        
        // Also get String-based scores
        double smartmetStringScore = entityScorer.score(query, entity47769);
        double unrelatedStringScore = entityScorer.score(query, entity1);
        
        System.out.println("\n=== Entity Comparison ===");
        System.out.println("Entity 47769 (SMARTMET): " + entity47769.name());
        System.out.println("  Entity-to-Entity: " + smartmetBreakdown.totalWeightedScore());
        System.out.println("  String-based: " + smartmetStringScore);
        System.out.println();
        System.out.println("Entity 1 (unrelated): " + entity1.name());
        System.out.println("  Entity-to-Entity: " + unrelatedBreakdown.totalWeightedScore());
        System.out.println("  String-based: " + unrelatedStringScore);
        
        // Entity-to-Entity should show different scores for related vs unrelated
        assertThat(smartmetBreakdown.totalWeightedScore())
            .describedAs("SMARTMET and unrelated should have different Entity-to-Entity scores")
            .isNotEqualTo(unrelatedBreakdown.totalWeightedScore());
    }

    @Test
    @DisplayName("Check if sourceId equality is causing the issue")
    void checkSourceIdEquality() {
        String query = "SMARTMET LLC";
        Entity queryEntity = Entity.of(null, query, null, null);
        
        List<Entity> allEntities = entityIndex.getAll();
        
        // Load 5 random entities
        Entity entity1 = allEntities.stream().filter(e -> "1".equals(e.sourceId())).findFirst().orElse(null);
        Entity entity100 = allEntities.stream().filter(e -> "100".equals(e.sourceId())).findFirst().orElse(null);
        Entity entity1000 = allEntities.stream().filter(e -> "1000".equals(e.sourceId())).findFirst().orElse(null);
        Entity entity47769 = allEntities.stream().filter(e -> "47769".equals(e.sourceId())).findFirst().orElse(null);
        Entity entity10000 = allEntities.stream().filter(e -> "10000".equals(e.sourceId())).findFirst().orElse(null);
        
        System.out.println("\n=== SourceId Comparison ===");
        System.out.println("Query Entity sourceId: " + queryEntity.sourceId());
        System.out.println();
        System.out.println("Entity 1 sourceId: " + entity1.sourceId());
        System.out.println("Entity 100 sourceId: " + entity100.sourceId());
        System.out.println("Entity 1000 sourceId: " + entity1000.sourceId());
        System.out.println("Entity 47769 sourceId: " + entity47769.sourceId());
        System.out.println("Entity 10000 sourceId: " + entity10000.sourceId());
        
        // Check equality
        System.out.println();
        System.out.println("Check: queryEntity.sourceId() == null: " + (queryEntity.sourceId() == null));
        System.out.println("Check: entity1.sourceId() == null: " + (entity1.sourceId() == null));
        
        // If all entities have sourceId = null and query has sourceId = null,
        // then the sourceId equality check might be causing issues
    }
}
