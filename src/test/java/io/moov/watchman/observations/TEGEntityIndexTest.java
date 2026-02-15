package io.moov.watchman.observations;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

@SpringBootTest
class TEGEntityIndexTest {

    @Autowired
    private EntityIndex entityIndex;

    @Test
    void checkIfTEGIsInIndex() {
        System.out.println("\n=== T.E.G. LIMITED Index Check ===");
        
        List<Entity> allEntities = entityIndex.getAll();
        System.out.println("Total entities in index: " + allEntities.size());
        
        // Search for T.E.G. LIMITED
        Optional<Entity> tegEntity = allEntities.stream()
            .filter(e -> e.name().contains("T.E.G."))
            .findFirst();
        
        if (tegEntity.isPresent()) {
            Entity entity = tegEntity.get();
            System.out.println("\n✅ T.E.G. LIMITED FOUND IN INDEX!");
            System.out.println("ID: " + entity.id());
            System.out.println("Name: " + entity.name());
            System.out.println("Type: " + entity.type());
            System.out.println("Source: " + entity.source());
            
            if (entity.preparedFields() != null) {
                System.out.println("\nPrepared Fields:");
                System.out.println("Normalized Primary: " + entity.preparedFields().normalizedPrimaryName());
                System.out.println("Normalized Alts: " + entity.preparedFields().normalizedAltNames());
            }
        } else {
            System.out.println("\n❌ T.E.G. LIMITED NOT FOUND IN INDEX!");
            System.out.println("Searching for similar entities with 'LIMITED':");
            
            allEntities.stream()
                .filter(e -> e.name().contains("LIMITED"))
                .filter(e -> e.name().length() < 20)
                .limit(10)
                .forEach(e -> System.out.println("  - " + e.name()));
        }
    }
}
