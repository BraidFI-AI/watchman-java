package io.moov.watchman.performance;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CRITICAL PERFORMANCE VERIFICATION: Check if PreparedFields are actually populated.
 * 
 * Context: Performance regression analysis identified PreparedFields as potential 40% win.
 * EntityScorerImpl checks for PreparedFields before falling back to on-the-fly normalization.
 * If PreparedFields=null, every search does redundant normalization (massive overhead).
 * 
 * This test verifies entities loaded into index have PreparedFields populated correctly.
 */
@SpringBootTest
class PreparedFieldsVerificationTest {

    @Autowired
    private EntityIndex entityIndex;

    @Test
    void verifyPreparedFieldsPopulated() {
        List<Entity> entities = entityIndex.getAll();
        
        assertFalse(entities.isEmpty(), "EntityIndex should contain entities");
        
        System.out.println("\n=== PREPARED FIELDS VERIFICATION ===");
        System.out.println("Total entities: " + entities.size());
        
        int withPreparedFields = 0;
        int withNormalizedPrimary = 0;
        int withNormalizedAlts = 0;
        int withWordCombinations = 0;
        
        // Check first 100 entities for PreparedFields population
        List<Entity> sample = entities.subList(0, Math.min(100, entities.size()));
        
        for (Entity entity : sample) {
            if (entity.preparedFields() != null) {
                withPreparedFields++;
                
                if (entity.preparedFields().normalizedPrimaryName() != null 
                        && !entity.preparedFields().normalizedPrimaryName().isEmpty()) {
                    withNormalizedPrimary++;
                }
                
                if (entity.preparedFields().normalizedAltNames() != null 
                        && !entity.preparedFields().normalizedAltNames().isEmpty()) {
                    withNormalizedAlts++;
                }
                
                if (entity.preparedFields().wordCombinations() != null 
                        && !entity.preparedFields().wordCombinations().isEmpty()) {
                    withWordCombinations++;
                }
            }
        }
        
        double preparedFieldsRate = (double) withPreparedFields / sample.size() * 100;
        double normalizedPrimaryRate = (double) withNormalizedPrimary / sample.size() * 100;
        double normalizedAltsRate = (double) withNormalizedAlts / sample.size() * 100;
        double wordCombinationsRate = (double) withWordCombinations / sample.size() * 100;
        
        System.out.println("\nSample size: " + sample.size() + " entities");
        System.out.println("PreparedFields populated: " + withPreparedFields + " (" + preparedFieldsRate + "%)");
        System.out.println("  - normalizedPrimaryName: " + withNormalizedPrimary + " (" + normalizedPrimaryRate + "%)");
        System.out.println("  - normalizedAltNames: " + withNormalizedAlts + " (" + normalizedAltsRate + "%)");
        System.out.println("  - wordCombinations: " + withWordCombinations + " (" + wordCombinationsRate + "%)");
        
        // Print sample entity details
        if (withPreparedFields > 0) {
            Entity sampleEntity = sample.stream()
                .filter(e -> e.preparedFields() != null)
                .findFirst()
                .orElse(null);
            
            if (sampleEntity != null) {
                System.out.println("\n=== SAMPLE ENTITY ===");
                System.out.println("ID: " + sampleEntity.id());
                System.out.println("Name: " + sampleEntity.name());
                System.out.println("PreparedFields:");
                System.out.println("  - normalizedPrimaryName: " + sampleEntity.preparedFields().normalizedPrimaryName());
                System.out.println("  - normalizedAltNames count: " + sampleEntity.preparedFields().normalizedAltNames().size());
                System.out.println("  - wordCombinations count: " + sampleEntity.preparedFields().wordCombinations().size());
                
                if (!sampleEntity.altNames().isEmpty()) {
                    System.out.println("Original altNames count: " + sampleEntity.altNames().size());
                }
            }
        }
        
        System.out.println("\n=== VERDICT ===");
        if (preparedFieldsRate >= 95) {
            System.out.println("✅ PreparedFields working correctly (" + preparedFieldsRate + "% populated)");
            System.out.println("   Performance optimization via PreparedFields is ACTIVE");
        } else if (preparedFieldsRate > 0) {
            System.out.println("⚠️  PreparedFields partially populated (" + preparedFieldsRate + "%)");
            System.out.println("   Some entities falling back to on-the-fly normalization");
        } else {
            System.out.println("❌ CRITICAL: PreparedFields NOT populated (0%)");
            System.out.println("   ALL searches doing redundant normalization - 40% performance loss!");
            System.out.println("   FIX: Ensure Entity.normalize() is called during data load");
        }
        System.out.println("=====================================\n");
        
        // Assert at least 95% have PreparedFields populated
        assertTrue(preparedFieldsRate >= 95, 
            "PreparedFields should be populated for ≥95% of entities. Current: " + preparedFieldsRate + "%");
    }
}
