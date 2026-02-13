package io.moov.watchman.index;

import io.moov.watchman.model.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Debug test to check if specific entities exist in the index
 */
@SpringBootTest
@DisplayName("Entity Index Debug")
class EntityIndexDebugTest {

    @Autowired
    private EntityIndex entityIndex;

    @Test
    @DisplayName("Check if AL-QA'IDA KURDISH BATTALIONS (13041) is in index")
    void checkAlQaidaKurdishBattalions() {
        long count = entityIndex.getAll().stream()
            .filter(e -> e.name().contains("KURDISH BATTALIONS"))
            .count();
        
        System.out.println("\n=== Entities containing 'KURDISH BATTALIONS' ===");
        System.out.println("Count: " + count);
        
        entityIndex.getAll().stream()
            .filter(e -> e.name().contains("KURDISH BATTALIONS"))
            .forEach(e -> {
                System.out.println("\nEntity ID: " + e.id());
                System.out.println("Source ID: " + e.sourceId());
                System.out.println("Name: " + e.name());
                System.out.println("Type: " + e.type());
                if (e.altNames() != null) {
                    System.out.println("Alt Names: " + e.altNames());
                }
            });
    }

    @Test
    @DisplayName("Check for 'KURDISH TALIBAN' entity")
    void checkKurdishTaliban() {
        long count = entityIndex.getAll().stream()
            .filter(e -> e.name().contains("KURDISH TAL"))
            .count();
        
        System.out.println("\n=== Entities containing 'KURDISH TAL' ===");
        System.out.println("Count: " + count);
        
        entityIndex.getAll().stream()
            .filter(e -> e.name().contains("KURDISH TAL"))
            .forEach(e -> {
                System.out.println("\nEntity ID: " + e.id());
                System.out.println("Source ID: " + e.sourceId());
                System.out.println("Name: " + e.name());
                System.out.println("Type: " + e.type());
            });
    }

    @Test
    @DisplayName("Check entities containing 'AL QA' or 'AL-QA'")
    void checkAlQaidaEntities() {
        long count = entityIndex.getAll().stream()
            .filter(e -> e.name().toUpperCase().contains("AL QA") || 
                        e.name().toUpperCase().contains("AL-QA"))
            .count();
        
        System.out.println("\n=== Entities containing 'AL QA' or 'AL-QA' ===");
        System.out.println("Count: " + count);
        
        entityIndex.getAll().stream()
            .filter(e -> e.name().toUpperCase().contains("AL QA") || 
                        e.name().toUpperCase().contains("AL-QA"))
            .limit(10)  // Just first 10 to avoid too much output
            .forEach(e -> {
                System.out.println("\nEntity ID: " + e.id());
                System.out.println("Source ID: " + e.sourceId());
                System.out.println("Name: " + e.name());
            });
    }
}
