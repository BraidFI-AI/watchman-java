package io.moov.watchman.index;

import io.moov.watchman.model.Address;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.GovernmentId;
import io.moov.watchman.model.GovernmentIdType;
import io.moov.watchman.model.SourceList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for InMemoryEntityIndex deduplication functionality.
 */
class InMemoryEntityIndexTest {

    private InMemoryEntityIndex index;

    @BeforeEach
    void setUp() {
        index = new InMemoryEntityIndex();
    }

    // ==================== addAllWithMerge() Tests ====================

    @Test
    void addAllWithMerge_withNoDuplicates_shouldAddAllEntities() {
        // Given: Three unique entities
        List<Entity> entities = List.of(
            Entity.of("1", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN),
            Entity.of("2", "Jane Smith", EntityType.INDIVIDUAL, SourceList.EU_CSL),
            Entity.of("3", "Acme Corp", EntityType.ENTITY, SourceList.UK_CSL)
        );

        // When: Adding with merge
        index.addAllWithMerge(entities);

        // Then: All 3 entities should be in index
        assertThat(index.size()).isEqualTo(3);
    }

    @Test
    void addAllWithMerge_withDuplicatesInBatch_shouldMergeBeforeAdding() {
        // Given: Two duplicate entities in same batch
        List<Entity> entities = List.of(
            Entity.of("ofac-123", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN),
            Entity.of("eu-456", "John Doe", EntityType.INDIVIDUAL, SourceList.EU_CSL)
        );

        // When: Adding with merge
        index.addAllWithMerge(entities);

        // Then: Should have only 1 merged entity
        assertThat(index.size()).isEqualTo(1);

        // And should preserve first entity's ID
        Entity result = index.getAll().get(0);
        assertThat(result.id()).isEqualTo("ofac-123");
    }

    @Test
    void addAllWithMerge_withExistingDuplicates_shouldMergeWithExisting() {
        // Given: Existing entity in index
        Entity existing = new Entity(
            "ofac-1", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN, "ofac-1",
            null, null, null, null, null,
            null,
            List.of(new Address("123 Main St", null, "New York", "NY", "10001", "USA")),
            List.of(),
            List.of("Johnny"),
            List.of(new GovernmentId(GovernmentIdType.PASSPORT, "AB123456", "USA")),
            null, null, null
        );
        index.add(existing);

        // When: Adding duplicate from different source
        Entity newEntity = new Entity(
            "eu-1", "John Doe", EntityType.INDIVIDUAL, SourceList.EU_CSL, "eu-1",
            null, null, null, null, null,
            null,
            List.of(new Address("456 Park Ave", null, "London", "LDN", "SW1", "UK")),
            List.of(),
            List.of("J. Doe"),
            List.of(new GovernmentId(GovernmentIdType.TAX_ID, "987654321", "UK")),
            null, null, null
        );
        index.addAllWithMerge(List.of(newEntity));

        // Then: Should still have 1 entity (merged)
        assertThat(index.size()).isEqualTo(1);

        // And should combine data from both
        Entity result = index.getAll().get(0);
        assertThat(result.addresses()).hasSize(2);      // Both addresses
        assertThat(result.altNames()).hasSize(2);       // Both alt names
        assertThat(result.governmentIds()).hasSize(2);  // Both IDs

        // And should keep first entity's ID
        assertThat(result.id()).isEqualTo("ofac-1");
    }

    @Test
    void addAllWithMerge_withMultipleLoads_shouldContinueMerging() {
        // Given: Empty index

        // When: Loading OFAC entities
        index.addAllWithMerge(List.of(
            Entity.of("ofac-1", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN),
            Entity.of("ofac-2", "Jane Smith", EntityType.INDIVIDUAL, SourceList.OFAC_SDN)
        ));

        // Then: Should have 2 entities
        assertThat(index.size()).isEqualTo(2);

        // When: Loading EU entities (with duplicates)
        index.addAllWithMerge(List.of(
            Entity.of("eu-1", "John Doe", EntityType.INDIVIDUAL, SourceList.EU_CSL),      // Duplicate
            Entity.of("eu-2", "Acme Corp", EntityType.ENTITY, SourceList.EU_CSL)          // New
        ));

        // Then: Should have 3 entities (John merged, Jane + Acme)
        assertThat(index.size()).isEqualTo(3);

        // When: Loading UK entities (more duplicates)
        index.addAllWithMerge(List.of(
            Entity.of("uk-1", "John Doe", EntityType.INDIVIDUAL, SourceList.UK_CSL),      // Duplicate
            Entity.of("uk-2", "Jane Smith", EntityType.INDIVIDUAL, SourceList.UK_CSL)     // Duplicate
        ));

        // Then: Should still have 3 entities (all duplicates merged)
        assertThat(index.size()).isEqualTo(3);
    }

    @Test
    void addAllWithMerge_withCaseVariations_shouldRecognizeAsDuplicates() {
        // Given: Entity with case variation
        index.add(Entity.of("ofac-1", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN));

        // When: Adding same entity with different case
        index.addAllWithMerge(List.of(
            Entity.of("eu-1", "JOHN DOE", EntityType.INDIVIDUAL, SourceList.EU_CSL)
        ));

        // Then: Should merge (case-insensitive)
        assertThat(index.size()).isEqualTo(1);
    }

    @Test
    void addAllWithMerge_withSDNNameFormat_shouldRecognizeAsDuplicate() {
        // Given: Entity with SDN "Last, First" format
        index.add(Entity.of("ofac-1", "Doe, John", EntityType.INDIVIDUAL, SourceList.OFAC_SDN));

        // When: Adding same entity with normal format
        index.addAllWithMerge(List.of(
            Entity.of("eu-1", "John Doe", EntityType.INDIVIDUAL, SourceList.EU_CSL)
        ));

        // Then: Should merge (name normalization handles reordering)
        assertThat(index.size()).isEqualTo(1);
        assertThat(index.getAll().get(0).name()).isEqualTo("Doe, John");  // First format preserved
    }

    @Test
    void addAllWithMerge_withEmptyList_shouldNotModifyIndex() {
        // Given: Index with one entity
        index.add(Entity.of("1", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN));

        // When: Adding empty list
        index.addAllWithMerge(List.of());

        // Then: Index unchanged
        assertThat(index.size()).isEqualTo(1);
    }

    @Test
    void addAllWithMerge_withNullList_shouldNotModifyIndex() {
        // Given: Index with one entity
        index.add(Entity.of("1", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN));

        // When: Adding null list
        index.addAllWithMerge(null);

        // Then: Index unchanged
        assertThat(index.size()).isEqualTo(1);
    }

    @Test
    void addAllWithMerge_realWorldScenario_loadingMultipleSources() {
        // Scenario: Loading sanctions lists from OFAC, EU, and UK

        // Step 1: Load OFAC SDN list
        index.addAllWithMerge(List.of(
            Entity.of("ofac-1", "Vladimir Putin", EntityType.INDIVIDUAL, SourceList.OFAC_SDN),
            Entity.of("ofac-2", "Bank of Evil Holdings", EntityType.ENTITY, SourceList.OFAC_SDN),
            Entity.of("ofac-3", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN)
        ));
        assertThat(index.size()).isEqualTo(3);

        // Step 2: Load EU CSL list (with overlaps)
        index.addAllWithMerge(List.of(
            Entity.of("eu-1", "Vladimir Putin", EntityType.INDIVIDUAL, SourceList.EU_CSL),       // Duplicate
            Entity.of("eu-2", "Bank of Evil Holdings", EntityType.ENTITY, SourceList.EU_CSL),    // Duplicate
            Entity.of("eu-3", "Jane Smith", EntityType.INDIVIDUAL, SourceList.EU_CSL)            // New
        ));
        assertThat(index.size()).isEqualTo(4);  // Putin merged, Bank merged, John + Jane

        // Step 3: Load UK CSL list (more overlaps)
        index.addAllWithMerge(List.of(
            Entity.of("uk-1", "Vladimir Putin", EntityType.INDIVIDUAL, SourceList.UK_CSL),       // Duplicate
            Entity.of("uk-2", "Acme Corp", EntityType.ENTITY, SourceList.UK_CSL)                 // New
        ));
        assertThat(index.size()).isEqualTo(5);  // Putin merged again, Acme added

        // Verify final state
        List<Entity> all = index.getAll();
        assertThat(all).anyMatch(e -> "Vladimir Putin".equals(e.name()));
        assertThat(all).anyMatch(e -> "Bank of Evil Holdings".equals(e.name()));
        assertThat(all).anyMatch(e -> "John Doe".equals(e.name()));
        assertThat(all).anyMatch(e -> "Jane Smith".equals(e.name()));
        assertThat(all).anyMatch(e -> "Acme Corp".equals(e.name()));
    }

    @Test
    void addAllWithMerge_threadSafety_concurrentAdds() throws InterruptedException {
        // Given: Multiple threads adding entities concurrently
        Runnable task1 = () -> {
            for (int i = 0; i < 100; i++) {
                index.addAllWithMerge(List.of(
                    Entity.of("ofac-" + i, "Entity " + i, EntityType.INDIVIDUAL, SourceList.OFAC_SDN)
                ));
            }
        };

        Runnable task2 = () -> {
            for (int i = 0; i < 100; i++) {
                index.addAllWithMerge(List.of(
                    Entity.of("eu-" + i, "Entity " + i, EntityType.INDIVIDUAL, SourceList.EU_CSL)
                ));
            }
        };

        // When: Running concurrently
        Thread thread1 = new Thread(task1);
        Thread thread2 = new Thread(task2);
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // Then: Should have 100 merged entities (each entity merged from 2 sources)
        assertThat(index.size()).isEqualTo(100);
    }

    @Test
    void addAllWithMerge_preservesInsertionOrder() {
        // Given: Entities added in specific order
        index.addAllWithMerge(List.of(
            Entity.of("3", "Zebra Corp", EntityType.ENTITY, SourceList.UK_CSL),
            Entity.of("1", "Alpha Corp", EntityType.ENTITY, SourceList.OFAC_SDN),
            Entity.of("2", "Beta Corp", EntityType.ENTITY, SourceList.EU_CSL)
        ));

        // Then: Should maintain insertion order (not alphabetical)
        List<Entity> all = index.getAll();
        assertThat(all.get(0).name()).isEqualTo("Zebra Corp");
        assertThat(all.get(1).name()).isEqualTo("Alpha Corp");
        assertThat(all.get(2).name()).isEqualTo("Beta Corp");
    }
}
