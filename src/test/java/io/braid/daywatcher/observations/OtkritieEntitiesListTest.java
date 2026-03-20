package io.braid.daywatcher.observations;

import io.braid.daywatcher.index.EntityIndex;
import io.braid.daywatcher.model.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class OtkritieEntitiesListTest {

    @Autowired
    private EntityIndex entityIndex;

    @Test
    void listAllOtkritieEntities() {
        System.out.println("\n================================================================");
        System.out.println("ALL OTKRITIE ENTITIES IN OFAC DATA");
        System.out.println("================================================================\n");

        List<Entity> entities = entityIndex.getAll().stream()
            .filter(e -> e.name().toUpperCase().contains("OTKRITIE") || 
                        e.name().toUpperCase().contains("OTKRYTIE"))
            .toList();

        System.out.println("Found " + entities.size() + " entities\n");

        for (Entity entity : entities) {
            System.out.println("Entity: " + entity.name());
            System.out.println("  ID: " + entity.id());
            System.out.println("  Type: " + entity.type());
            System.out.println("  Source: " + entity.source());
            
            if (entity.altNames() != null && !entity.altNames().isEmpty()) {
                System.out.println("  Aliases:");
                entity.altNames().forEach(alt -> System.out.println("    - " + alt));
            }
            System.out.println();
        }

        System.out.println("================================================================");
        System.out.println("Total: " + entities.size() + " OTKRITIE entities");
    }
}
