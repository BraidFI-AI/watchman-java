package io.braid.daywatcher.observations;

import io.braid.daywatcher.index.EntityIndex;
import io.braid.daywatcher.model.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class IslamicStateAliasCheckTest {

    @Autowired
    private EntityIndex entityIndex;

    @Test
    void showAllIslamicStateAliases() {
        System.out.println("\n================================================================");
        System.out.println("ISLAMIC STATE OF IRAQ AND THE LEVANT - ALL ALIASES");
        System.out.println("================================================================\n");

        Entity entity = entityIndex.getAll().stream()
            .filter(e -> e.name().equals("ISLAMIC STATE OF IRAQ AND THE LEVANT"))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Entity not found"));

        System.out.println("Entity ID: " + entity.id());
        System.out.println("Name: " + entity.name());
        System.out.println("Type: " + entity.type());
        System.out.println("Source: " + entity.source());
        System.out.println("\nTotal Aliases: " + (entity.altNames() != null ? entity.altNames().size() : 0));
        System.out.println("\nAll Aliases:");
        System.out.println("----------------------------------------------------------------");
        
        if (entity.altNames() != null) {
            for (int i = 0; i < entity.altNames().size(); i++) {
                String alias = entity.altNames().get(i);
                System.out.println(String.format("%3d. %s", i + 1, alias));
                
                // Highlight if contains JIHAD and IRAQ
                if (alias.toUpperCase().contains("JIHAD") && alias.toUpperCase().contains("IRAQ")) {
                    System.out.println("     ⭐ Contains 'JIHAD' and 'IRAQ'");
                }
            }
        }
    }
}
