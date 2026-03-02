package io.moov.watchman.observations;

import io.moov.watchman.model.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Debug what Entity.normalize() does to query "SMARTMET LLC".
 */
@DisplayName("Row 24 - Debug Query Entity Normalization")
public class Row24DebugQueryNormalizationTest {

    @Test
    @DisplayName("Check what happens when query entity is normalized")
    void checkQueryNormalization() {
        String query = "SMARTMET LLC";
        
        System.out.println("=== Query: '" + query + "' ===");
        System.out.println();
        
        // Create entity without normalization
        Entity unnormalizedEntity = Entity.of(null, query, null, null);
        System.out.println("Unnormalized entity:");
        System.out.println("  name(): '" + unnormalizedEntity.name() + "'");
        System.out.println("  preparedFields(): " + unnormalizedEntity.preparedFields());
        System.out.println();
        
        // Create entity with normalization
        Entity normalizedEntity = Entity.of(null, query, null, null).normalize();
        System.out.println("Normalized entity:");
        System.out.println("  name(): '" + normalizedEntity.name() + "'");
        if (normalizedEntity.preparedFields() != null) {
            System.out.println("  preparedFields().normalizedPrimaryName(): '" + 
                normalizedEntity.preparedFields().normalizedPrimaryName() + "'");
        } else {
            System.out.println("  preparedFields(): null");
        }
    }
}
