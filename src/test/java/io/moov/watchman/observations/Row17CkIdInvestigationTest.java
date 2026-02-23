package io.moov.watchman.observations;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Investigation: Row 17 - CK ID CO. LTD
 * 
 * BSA Observation: Partial name "CK ID" didn't return any results
 * 
 * Investigation Phase: Determine if entity exists in OFAC data
 */
@SpringBootTest
@DisplayName("Row 17 Investigation - CK ID CO. LTD Entity Search")
class Row17CkIdInvestigationTest {

    @Autowired
    private EntityIndex entityIndex;

    @Test
    @DisplayName("Find all entities containing 'CK ID' in name")
    void findCkIdEntities() {
        List<Entity> allEntities = entityIndex.getAll();
        
        System.out.println("\n=== Searching for entities with 'CK ID' ===");
        
        List<Entity> ckIdEntities = allEntities.stream()
            .filter(e -> e.name().toUpperCase().contains("CK ID"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + ckIdEntities.size() + " entities:");
        for (Entity entity : ckIdEntities) {
            System.out.println("  ID: " + entity.sourceId() + " | Name: " + entity.name());
            if (!entity.altNames().isEmpty()) {
                System.out.println("    Aliases: " + String.join(", ", 
                    entity.altNames().stream().limit(3).collect(Collectors.toList())));
            }
        }
        
        // Also search for variations
        System.out.println("\n=== Searching for entities with 'CKID' (no space) ===");
        List<Entity> ckidNoSpace = allEntities.stream()
            .filter(e -> e.name().toUpperCase().contains("CKID"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + ckidNoSpace.size() + " entities:");
        for (Entity entity : ckidNoSpace) {
            System.out.println("  ID: " + entity.sourceId() + " | Name: " + entity.name());
        }
        
        // Search in aliases too
        System.out.println("\n=== Searching aliases for 'CK ID' ===");
        List<Entity> aliasMatches = allEntities.stream()
            .filter(e -> e.altNames().stream()
                .anyMatch(alias -> alias.toUpperCase().contains("CK ID")))
            .collect(Collectors.toList());
        
        System.out.println("Found " + aliasMatches.size() + " entities with 'CK ID' in aliases:");
        for (Entity entity : aliasMatches) {
            System.out.println("  ID: " + entity.sourceId() + " | Name: " + entity.name());
            System.out.println("    Matching aliases: " + entity.altNames().stream()
                .filter(alias -> alias.toUpperCase().contains("CK ID"))
                .collect(Collectors.joining(", ")));
        }
    }
}
