package io.moov.watchman.search;

import io.moov.watchman.model.Entity;
import io.moov.watchman.index.EntityIndex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verify how many entities in the index have NULL preparedFields.
 * 
 * Root Cause Investigation for Row 31 (T.E.G. LIMITED) Regression:
 * - T.E.G. LIMITED exists in index but preparedFields is NULL
 * - This means Entity.normalize() was never called during data loading
 * - This test checks if ALL entities have this problem or just some
 */
@SpringBootTest
public class AllEntitiesNormalizationCheck {

    @Autowired
    private EntityIndex entityIndex;

    @Test
    void checkHowManyEntitiesAreNormalized() {
        List<Entity> allEntities = entityIndex.getAll();
        
        long totalCount = allEntities.size();
        long normalizedCount = allEntities.stream()
            .filter(e -> e.preparedFields() != null)
            .count();
        long unnormalizedCount = allEntities.stream()
            .filter(e -> e.preparedFields() == null)
            .count();
        
        System.out.println("===== Entity Normalization Check =====");
        System.out.println("Total entities: " + totalCount);
        System.out.println("Normalized (preparedFields != null): " + normalizedCount);
        System.out.println("Unnormalized (preparedFields == null): " + unnormalizedCount);
        System.out.println("Percentage unnormalized: " + String.format("%.2f%%", 
            (unnormalizedCount * 100.0 / totalCount)));
        
        // Show first 10 unnormalized entities as examples
        System.out.println("\nFirst 10 unnormalized entities:");
        allEntities.stream()
            .filter(e -> e.preparedFields() == null)
            .limit(10)
            .forEach(e -> System.out.println("  - ID: " + e.id() + ", Name: " + e.name() + ", Type: " + e.type()));
        
        // Check if T.E.G. LIMITED specifically is affected
        Entity tegEntity = allEntities.stream()
            .filter(e -> "T.E.G. LIMITED".equals(e.name()))
            .findFirst()
            .orElse(null);
        
        if (tegEntity != null) {
            System.out.println("\nT.E.G. LIMITED found:");
            System.out.println("  - ID: " + tegEntity.id());
            System.out.println("  - Name: " + tegEntity.name());
            System.out.println("  - preparedFields: " + (tegEntity.preparedFields() == null ? "NULL" : "NOT NULL"));
        } else {
            System.out.println("\nT.E.G. LIMITED not found in index!");
        }
        
        // ASSERTION: This will likely fail, proving ALL entities are unnormalized
        // assertThat(normalizedCount).isGreaterThan(0);
        
        // Just log the data for now
        System.out.println("\n**CRITICAL FINDING**: " + 
            (unnormalizedCount == totalCount ? 
                "ALL entities are unnormalized! This is a systemic issue." : 
                "Some entities normalized, some not - investigate why."));
    }
}
