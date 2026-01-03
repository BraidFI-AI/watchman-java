package io.moov.watchman.index;

import io.moov.watchman.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the in-memory entity index.
 */
class EntityIndexTest {

    private EntityIndex index;

    @BeforeEach
    void setUp() {
        index = new InMemoryEntityIndex();
    }

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperationsTests {

        @Test
        @DisplayName("Should start empty")
        void shouldStartEmpty() {
            assertThat(index.size()).isEqualTo(0);
            assertThat(index.getAll()).isEmpty();
        }

        @Test
        @DisplayName("Should add single entity")
        void shouldAddSingleEntity() {
            Entity entity = Entity.of("1", "Test Entity", EntityType.PERSON, SourceList.US_OFAC);
            
            index.addAll(List.of(entity));
            
            assertThat(index.size()).isEqualTo(1);
            assertThat(index.getAll()).containsExactly(entity);
        }

        @Test
        @DisplayName("Should add multiple entities")
        void shouldAddMultipleEntities() {
            List<Entity> entities = List.of(
                Entity.of("1", "Entity One", EntityType.PERSON, SourceList.US_OFAC),
                Entity.of("2", "Entity Two", EntityType.BUSINESS, SourceList.US_OFAC),
                Entity.of("3", "Entity Three", EntityType.VESSEL, SourceList.US_CSL)
            );
            
            index.addAll(entities);
            
            assertThat(index.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should clear all entities")
        void shouldClearAllEntities() {
            index.addAll(List.of(
                Entity.of("1", "Entity One", EntityType.PERSON, SourceList.US_OFAC),
                Entity.of("2", "Entity Two", EntityType.BUSINESS, SourceList.US_OFAC)
            ));
            
            index.clear();
            
            assertThat(index.size()).isEqualTo(0);
            assertThat(index.getAll()).isEmpty();
        }

        @Test
        @DisplayName("Should replace all entities atomically")
        void shouldReplaceAllEntities() {
            index.addAll(List.of(
                Entity.of("1", "Old Entity", EntityType.PERSON, SourceList.US_OFAC)
            ));
            
            index.replaceAll(List.of(
                Entity.of("2", "New Entity One", EntityType.PERSON, SourceList.US_OFAC),
                Entity.of("3", "New Entity Two", EntityType.BUSINESS, SourceList.US_OFAC)
            ));
            
            assertThat(index.size()).isEqualTo(2);
            assertThat(index.getAll().stream().map(Entity::name))
                .containsExactlyInAnyOrder("New Entity One", "New Entity Two");
        }
    }

    @Nested
    @DisplayName("Filtering by Source")
    class FilterBySourceTests {

        @BeforeEach
        void setUpEntities() {
            index.addAll(List.of(
                Entity.of("1", "OFAC Entity 1", EntityType.PERSON, SourceList.US_OFAC),
                Entity.of("2", "OFAC Entity 2", EntityType.BUSINESS, SourceList.US_OFAC),
                Entity.of("3", "CSL Entity 1", EntityType.PERSON, SourceList.US_CSL),
                Entity.of("4", "EU Entity 1", EntityType.BUSINESS, SourceList.EU_CSL)
            ));
        }

        @Test
        @DisplayName("Should filter by US_OFAC")
        void shouldFilterByUsOfac() {
            List<Entity> ofacEntities = index.getBySource(SourceList.US_OFAC);
            
            assertThat(ofacEntities).hasSize(2);
            assertThat(ofacEntities.stream().allMatch(e -> e.source() == SourceList.US_OFAC)).isTrue();
        }

        @Test
        @DisplayName("Should filter by US_CSL")
        void shouldFilterByUsCsl() {
            List<Entity> cslEntities = index.getBySource(SourceList.US_CSL);
            
            assertThat(cslEntities).hasSize(1);
            assertThat(cslEntities.get(0).name()).isEqualTo("CSL Entity 1");
        }

        @Test
        @DisplayName("Should return empty for source with no entities")
        void shouldReturnEmptyForNoEntities() {
            List<Entity> ukEntities = index.getBySource(SourceList.UK_CSL);
            
            assertThat(ukEntities).isEmpty();
        }
    }

    @Nested
    @DisplayName("Filtering by Type")
    class FilterByTypeTests {

        @BeforeEach
        void setUpEntities() {
            index.addAll(List.of(
                Entity.of("1", "Person 1", EntityType.PERSON, SourceList.US_OFAC),
                Entity.of("2", "Person 2", EntityType.PERSON, SourceList.US_OFAC),
                Entity.of("3", "Business 1", EntityType.BUSINESS, SourceList.US_OFAC),
                Entity.of("4", "Vessel 1", EntityType.VESSEL, SourceList.US_OFAC),
                Entity.of("5", "Aircraft 1", EntityType.AIRCRAFT, SourceList.US_OFAC)
            ));
        }

        @Test
        @DisplayName("Should filter by PERSON")
        void shouldFilterByPerson() {
            List<Entity> persons = index.getByType(EntityType.PERSON);
            
            assertThat(persons).hasSize(2);
            assertThat(persons.stream().allMatch(e -> e.type() == EntityType.PERSON)).isTrue();
        }

        @Test
        @DisplayName("Should filter by BUSINESS")
        void shouldFilterByBusiness() {
            List<Entity> businesses = index.getByType(EntityType.BUSINESS);
            
            assertThat(businesses).hasSize(1);
            assertThat(businesses.get(0).name()).isEqualTo("Business 1");
        }

        @Test
        @DisplayName("Should filter by VESSEL")
        void shouldFilterByVessel() {
            List<Entity> vessels = index.getByType(EntityType.VESSEL);
            
            assertThat(vessels).hasSize(1);
        }

        @Test
        @DisplayName("Should filter by AIRCRAFT")
        void shouldFilterByAircraft() {
            List<Entity> aircraft = index.getByType(EntityType.AIRCRAFT);
            
            assertThat(aircraft).hasSize(1);
        }

        @Test
        @DisplayName("Should return empty for type with no entities")
        void shouldReturnEmptyForNoEntities() {
            List<Entity> orgs = index.getByType(EntityType.ORGANIZATION);
            
            assertThat(orgs).isEmpty();
        }
    }

    @Nested
    @DisplayName("Combined Filtering")
    class CombinedFilteringTests {

        @BeforeEach
        void setUpEntities() {
            index.addAll(List.of(
                Entity.of("1", "OFAC Person", EntityType.PERSON, SourceList.US_OFAC),
                Entity.of("2", "OFAC Business", EntityType.BUSINESS, SourceList.US_OFAC),
                Entity.of("3", "CSL Person", EntityType.PERSON, SourceList.US_CSL),
                Entity.of("4", "CSL Business", EntityType.BUSINESS, SourceList.US_CSL)
            ));
        }

        @Test
        @DisplayName("Should filter by source and type")
        void shouldFilterBySourceAndType() {
            List<Entity> ofacPersons = index.getBySource(SourceList.US_OFAC).stream()
                .filter(e -> e.type() == EntityType.PERSON)
                .toList();
            
            assertThat(ofacPersons).hasSize(1);
            assertThat(ofacPersons.get(0).name()).isEqualTo("OFAC Person");
        }
    }
}
