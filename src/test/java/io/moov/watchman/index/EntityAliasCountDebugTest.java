package io.moov.watchman.index;

import io.moov.watchman.model.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Debug test to check alias counts for specific entities
 */
@SpringBootTest
@DisplayName("Entity Alias Count Debug")
class EntityAliasCountDebugTest {

    @Autowired
    private EntityIndex entityIndex;

    @Test
    @DisplayName("Check alias count for entity 6366 (AL QA'IDA)")
    void checkEntity6366Aliases() {
        Entity entity = entityIndex.getAll().stream()
            .filter(e -> "6366".equals(e.sourceId()))
            .findFirst()
            .orElse(null);

        if (entity == null) {
            System.out.println("\n❌ Entity 6366 NOT FOUND");
            return;
        }

        int aliasCount = entity.altNames() != null ? entity.altNames().size() : 0;
        int totalResults = 1 + aliasCount; // primary name + aliases

        System.out.println("\n=== Entity 6366 (AL QA'IDA) ===");
        System.out.println("Name: " + entity.name());
        System.out.println("Alias count: " + aliasCount);
        System.out.println("Total results (1 primary + N aliases): " + totalResults);
        System.out.println("\nAliases:");
        if (entity.altNames() != null) {
            entity.altNames().forEach(alias -> System.out.println("  - " + alias));
        }
    }

    @Test
    @DisplayName("Check alias count for entity 9598")
    void checkEntity9598Aliases() {
        Entity entity = entityIndex.getAll().stream()
            .filter(e -> "9598".equals(e.sourceId()))
            .findFirst()
            .orElse(null);

        if (entity == null) {
            System.out.println("\n❌ Entity 9598 NOT FOUND");
            return;
        }

        int aliasCount = entity.altNames() != null ? entity.altNames().size() : 0;
        int totalResults = 1 + aliasCount;

        System.out.println("\n=== Entity 9598 ===");
        System.out.println("Name: " + entity.name());
        System.out.println("Alias count: " + aliasCount);
        System.out.println("Total results: " + totalResults);
    }

    @Test
    @DisplayName("Simulate how limit=20 fills up with entity 6366 and 9598")
    void simulateLimitFill() {
        System.out.println("\n=== Simulating limit=20 with alias expansion ===\n");

        int limit = 20;
        int consumed = 0;

        // Entity 6366
        Entity entity6366 = entityIndex.getAll().stream()
            .filter(e -> "6366".equals(e.sourceId()))
            .findFirst()
            .orElse(null);

        if (entity6366 != null) {
            int results6366 = 1 + (entity6366.altNames() != null ? entity6366.altNames().size() : 0);
            consumed += results6366;
            System.out.println("Entity 6366 (AL QA'IDA) consumes: " + results6366 + " results");
            System.out.println("  Running total: " + consumed + "/" + limit);
        }

        // Entity 9598
        Entity entity9598 = entityIndex.getAll().stream()
            .filter(e -> "9598".equals(e.sourceId()))
            .findFirst()
            .orElse(null);

        if (entity9598 != null) {
            int results9598 = 1 + (entity9598.altNames() != null ? entity9598.altNames().size() : 0);
            consumed += results9598;
            System.out.println("Entity 9598 consumes: " + results9598 + " results");
            System.out.println("  Running total: " + consumed + "/" + limit);
        }

        System.out.println("\n📊 Summary:");
        System.out.println("  Total consumed: " + consumed + "/" + limit);
        System.out.println("  Remaining slots: " + (limit - consumed));
        System.out.println("\n" + (consumed >= limit 
            ? "❌ Limit EXCEEDED - entity 13041 gets cut off!" 
            : "✅ Room for more entities"));
    }
}
