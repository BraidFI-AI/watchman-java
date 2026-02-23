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
 * Investigation: Row 24 - LIMITED LIABILITY COMPANY SMARTMET
 * 
 * BSA Observation: "SMARTMET LLC" & "LLC SMARTMET" didn't return the main entity
 * 
 * Investigation Phase: Determine if entity exists in OFAC data and its exact name
 */
@SpringBootTest
@DisplayName("Row 24 Investigation - SMARTMET Entity Search")
class Row24SmartmetInvestigationTest {

    @Autowired
    private EntityIndex entityIndex;

    @Test
    @DisplayName("Find all entities containing 'SMARTMET' in name")
    void findSmartmetEntities() {
        List<Entity> allEntities = entityIndex.getAll();
        
        System.out.println("\n=== Searching for entities with 'SMARTMET' ===");
        
        List<Entity> smartmetEntities = allEntities.stream()
            .filter(e -> e.name().toUpperCase().contains("SMARTMET"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + smartmetEntities.size() + " entities:");
        for (Entity entity : smartmetEntities) {
            System.out.println("  ID: " + entity.sourceId() + " | Name: " + entity.name());
            System.out.println("    Type: " + entity.type());
            if (!entity.altNames().isEmpty()) {
                System.out.println("    Aliases: " + String.join(", ", entity.altNames()));
            }
        }
        
        // Also search in aliases
        System.out.println("\n=== Searching aliases for 'SMARTMET' ===");
        List<Entity> aliasMatches = allEntities.stream()
            .filter(e -> e.altNames().stream()
                .anyMatch(alias -> alias.toUpperCase().contains("SMARTMET")))
            .collect(Collectors.toList());
        
        System.out.println("Found " + aliasMatches.size() + " entities with 'SMARTMET' in aliases:");
        for (Entity entity : aliasMatches) {
            System.out.println("  ID: " + entity.sourceId() + " | Name: " + entity.name());
            System.out.println("    Matching aliases: " + entity.altNames().stream()
                .filter(alias -> alias.toUpperCase().contains("SMARTMET"))
                .collect(Collectors.joining(", ")));
        }
        
        // Search for LLC variations
        System.out.println("\n=== Searching for 'LLC' entities ===");
        List<Entity> llcEntities = allEntities.stream()
            .filter(e -> e.name().toUpperCase().contains("LLC") 
                && e.name().toUpperCase().contains("SMART"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + llcEntities.size() + " entities with both 'LLC' and 'SMART':");
        for (Entity entity : llcEntities) {
            System.out.println("  ID: " + entity.sourceId() + " | Name: " + entity.name());
        }
    }
}
