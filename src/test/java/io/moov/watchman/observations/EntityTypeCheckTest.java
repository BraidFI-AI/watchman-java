package io.moov.watchman.observations;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EntityTypeCheckTest {

    @Autowired
    private EntityIndex entityIndex;

    @Test
    void checkIslamicStateEntityType() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("ENTITY TYPE CHECK");
        System.out.println("=".repeat(80) + "\n");
        
        Entity entity = entityIndex.getAll().stream()
            .filter(e -> e.name().equals("ISLAMIC STATE OF IRAQ AND THE LEVANT"))
            .findFirst()
            .orElse(null);
        
        if (entity != null) {
            System.out.println("Entity: " + entity.name());
            System.out.println("Type: " + entity.type());
            System.out.println("Source: " + entity.source());
            System.out.println("Id: " + entity.id());
        } else {
            System.out.println("NOT FOUND");
        }
    }
}
