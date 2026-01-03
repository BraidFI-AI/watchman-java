package io.moov.watchman.index;

import io.moov.watchman.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for concurrent access to the entity index.
 * 
 * The index must be thread-safe since:
 * - Multiple search requests can read concurrently
 * - Background refresh can write while searches are in progress
 */
class ConcurrentAccessTest {

    private EntityIndex index;

    @BeforeEach
    void setUp() {
        index = new InMemoryEntityIndex();
    }

    @Test
    @DisplayName("Should handle concurrent reads")
    void shouldHandleConcurrentReads() throws InterruptedException {
        // Setup with some entities
        List<Entity> entities = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            entities.add(Entity.of(String.valueOf(i), "Entity " + i, EntityType.PERSON, SourceList.US_OFAC));
        }
        index.addAll(entities);
        
        // Concurrent reads
        int numReaders = 10;
        CountDownLatch latch = new CountDownLatch(numReaders);
        ExecutorService executor = Executors.newFixedThreadPool(numReaders);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < numReaders; i++) {
            executor.submit(() -> {
                try {
                    List<Entity> result = index.getAll();
                    if (result.size() == 1000) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertThat(successCount.get()).isEqualTo(numReaders);
    }

    @Test
    @DisplayName("Should handle concurrent reads during write")
    void shouldHandleConcurrentReadsDuringWrite() throws InterruptedException {
        // Initial entities
        List<Entity> initialEntities = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            initialEntities.add(Entity.of(String.valueOf(i), "Initial " + i, EntityType.PERSON, SourceList.US_OFAC));
        }
        index.addAll(initialEntities);
        
        // New entities for replacement
        List<Entity> newEntities = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            newEntities.add(Entity.of(String.valueOf(i), "New " + i, EntityType.PERSON, SourceList.US_OFAC));
        }
        
        int numReaders = 5;
        CountDownLatch readersStarted = new CountDownLatch(numReaders);
        CountDownLatch writerDone = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(numReaders + 1);
        
        // Start readers
        for (int i = 0; i < numReaders; i++) {
            executor.submit(() -> {
                readersStarted.countDown();
                try {
                    // Keep reading during write
                    for (int j = 0; j < 100; j++) {
                        List<Entity> result = index.getAll();
                        // Should get either old or new, not a mix
                        assertThat(result.size()).isIn(500, 1000);
                        Thread.sleep(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        // Wait for readers to start, then write
        readersStarted.await();
        executor.submit(() -> {
            try {
                index.replaceAll(newEntities);
            } finally {
                writerDone.countDown();
            }
        });
        
        writerDone.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        // Final state should be new entities
        assertThat(index.size()).isEqualTo(1000);
    }

    @RepeatedTest(5)
    @DisplayName("Should not lose entities during concurrent adds")
    void shouldNotLoseEntitiesDuringConcurrentAdds() throws InterruptedException {
        int numWriters = 5;
        int entitiesPerWriter = 100;
        CountDownLatch latch = new CountDownLatch(numWriters);
        ExecutorService executor = Executors.newFixedThreadPool(numWriters);
        
        for (int w = 0; w < numWriters; w++) {
            final int writerId = w;
            executor.submit(() -> {
                try {
                    List<Entity> batch = new ArrayList<>();
                    for (int i = 0; i < entitiesPerWriter; i++) {
                        batch.add(Entity.of(
                            writerId + "-" + i,
                            "Entity " + writerId + "-" + i,
                            EntityType.PERSON,
                            SourceList.US_OFAC
                        ));
                    }
                    index.addAll(batch);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertThat(index.size()).isEqualTo(numWriters * entitiesPerWriter);
    }

    @Test
    @DisplayName("Should handle rapid clear and add cycles")
    void shouldHandleRapidClearAndAddCycles() throws InterruptedException {
        int numCycles = 50;
        
        for (int cycle = 0; cycle < numCycles; cycle++) {
            List<Entity> entities = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                entities.add(Entity.of(String.valueOf(i), "Entity " + i, EntityType.PERSON, SourceList.US_OFAC));
            }
            
            index.replaceAll(entities);
            assertThat(index.size()).isEqualTo(100);
            
            index.clear();
            assertThat(index.size()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Filter operations should be thread-safe")
    void filterOperationsShouldBeThreadSafe() throws InterruptedException {
        // Add entities of different types and sources
        List<Entity> entities = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            EntityType type = i % 2 == 0 ? EntityType.PERSON : EntityType.BUSINESS;
            SourceList source = i % 3 == 0 ? SourceList.US_OFAC : SourceList.US_CSL;
            entities.add(Entity.of(String.valueOf(i), "Entity " + i, type, source));
        }
        index.addAll(entities);
        
        int numReaders = 10;
        CountDownLatch latch = new CountDownLatch(numReaders);
        ExecutorService executor = Executors.newFixedThreadPool(numReaders);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < numReaders; i++) {
            final int readerId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 50; j++) {
                        // Alternate between different filter types
                        if (readerId % 2 == 0) {
                            index.getByType(EntityType.PERSON);
                        } else {
                            index.getBySource(SourceList.US_OFAC);
                        }
                    }
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertThat(successCount.get()).isEqualTo(numReaders);
    }
}
