package io.moov.watchman.integration;

import io.moov.watchman.index.InMemoryEntityIndex;
import io.moov.watchman.model.*;
import io.moov.watchman.parser.OFACParser;
import io.moov.watchman.search.SearchService;
import io.moov.watchman.similarity.TextNormalizer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests that verify the complete pipeline:
 * Parse OFAC data -> Index entities -> Search with scoring
 */
@SpringBootTest
@TestPropertySource(properties = {
    "watchman.download.enabled=false",
    "watchman.download.on-startup=false"
})
@DisplayName("End-to-End Pipeline Integration Tests")
class PipelineIntegrationTest {

    @Autowired
    private OFACParser ofacParser;

    @Autowired
    private InMemoryEntityIndex entityIndex;

    @Autowired
    private SearchService searchService;

    @Autowired
    private TextNormalizer textNormalizer;

    @BeforeEach
    void setUp() {
        entityIndex.clear();
    }

    @Nested
    @DisplayName("Full Pipeline Tests")
    class FullPipelineTests {

        @Test
        @DisplayName("Parse -> Index -> Search flow works end to end")
        void parseIndexSearchFlowWorks() {
            // 1. Create test OFAC data
            String sdnCsv = """
                ent_num,SDN_Name,SDN_Type,Program,Title,Call_Sign,Vess_type,Tonnage,GRT,Vess_flag,Vess_owner,Remarks
                12345,"MADURO MOROS, Nicolas",individual,VENEZUELA,,,,,,,,DOB 23 Nov 1962
                67890,"TERROR CORP LTD",-,SDGT,,,,,,,,
                """;
            String addCsv = """
                ent_num,Add_num,Address,City/State/Province/Postal Code,Country,Add_remarks
                12345,1,"Miraflores Palace","Caracas",Venezuela,
                """;
            String altCsv = """
                ent_num,alt_num,alt_type,alt_name,alt_remarks
                12345,1,a.k.a.,"Nicolas Maduro Moros",
                """;

            // 2. Parse the data
            List<Entity> entities = ofacParser.parse(
                toInputStream(sdnCsv),
                toInputStream(addCsv),
                toInputStream(altCsv)
            );

            assertThat(entities).hasSize(2);

            // 3. Index the entities
            entityIndex.addAll(entities);
            assertThat(entityIndex.size()).isEqualTo(2);

            // 4. Search for entities
            Entity maduro = entityIndex.getAll().stream()
                .filter(e -> e.name().contains("MADURO"))
                .findFirst()
                .orElseThrow();

            double score = searchService.scoreEntity("Nicolas Maduro", maduro);
            assertThat(score).isGreaterThan(0.8);
        }

        @Test
        @DisplayName("Search finds entity by partial name")
        void searchFindsEntityByPartialName() {
            // Setup: Add entities
            entityIndex.add(Entity.of("1", "VLADIMIR PUTIN", EntityType.PERSON, SourceList.US_OFAC));
            entityIndex.add(Entity.of("2", "SERGEI LAVROV", EntityType.PERSON, SourceList.US_OFAC));
            entityIndex.add(Entity.of("3", "TEST COMPANY LLC", EntityType.BUSINESS, SourceList.US_OFAC));

            // Search for partial name
            Entity putin = entityIndex.findById("1").orElseThrow();
            double score = searchService.scoreEntity("Putin", putin);

            // Should have decent match score for partial name
            assertThat(score).isGreaterThan(0.5);
        }

        @Test
        @DisplayName("Search handles normalized text correctly")
        void searchHandlesNormalizedText() {
            entityIndex.add(Entity.of("1", "José García López", EntityType.PERSON, SourceList.US_OFAC));

            // Search with different case and diacritics
            Entity entity = entityIndex.findById("1").orElseThrow();
            double score = searchService.scoreEntity("JOSE GARCIA LOPEZ", entity);

            assertThat(score).isGreaterThan(0.9);
        }
    }

    @Nested
    @DisplayName("Component Wiring Tests")
    class ComponentWiringTests {

        @Test
        @DisplayName("All components are properly autowired")
        void allComponentsAreAutowired() {
            assertThat(ofacParser).isNotNull();
            assertThat(entityIndex).isNotNull();
            assertThat(searchService).isNotNull();
            assertThat(textNormalizer).isNotNull();
        }

        @Test
        @DisplayName("TextNormalizer is shared across components")
        void textNormalizerIsShared() {
            // Verify text normalizer works consistently
            String input = "  JOHN   DOE  ";
            String normalized = textNormalizer.lowerAndRemovePunctuation(input);
            assertThat(normalized).isEqualTo("john doe");
        }
    }

    @Nested
    @DisplayName("Entity Index Persistence Tests")
    class EntityIndexPersistenceTests {

        @Test
        @DisplayName("Entities persist in index across operations")
        void entitiesPersistInIndex() {
            // Add entities
            entityIndex.add(Entity.of("1", "Entity One", EntityType.PERSON, SourceList.US_OFAC));
            entityIndex.add(Entity.of("2", "Entity Two", EntityType.BUSINESS, SourceList.US_CSL));

            // Verify they're retrievable
            assertThat(entityIndex.findById("1")).isPresent();
            assertThat(entityIndex.findById("2")).isPresent();
            assertThat(entityIndex.size()).isEqualTo(2);

            // Clear and verify empty
            entityIndex.clear();
            assertThat(entityIndex.size()).isEqualTo(0);
            assertThat(entityIndex.findById("1")).isEmpty();
        }

        @Test
        @DisplayName("Entity filtering works correctly")
        void entityFilteringWorks() {
            entityIndex.add(Entity.of("1", "Person One", EntityType.PERSON, SourceList.US_OFAC));
            entityIndex.add(Entity.of("2", "Person Two", EntityType.PERSON, SourceList.US_CSL));
            entityIndex.add(Entity.of("3", "Business One", EntityType.BUSINESS, SourceList.US_OFAC));

            // Filter by type
            List<Entity> persons = entityIndex.getByType(EntityType.PERSON);
            assertThat(persons).hasSize(2);

            // Filter by source
            List<Entity> ofacEntities = entityIndex.getBySource(SourceList.US_OFAC);
            assertThat(ofacEntities).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Search Scoring Integration Tests")
    class SearchScoringIntegrationTests {

        @Test
        @DisplayName("Exact match scores highest")
        void exactMatchScoresHighest() {
            Entity entity = Entity.of("1", "John Smith", EntityType.PERSON, SourceList.US_OFAC);
            entityIndex.add(entity);

            double exactScore = searchService.scoreEntity("John Smith", entity);
            double partialScore = searchService.scoreEntity("John", entity);
            double differentScore = searchService.scoreEntity("Jane Doe", entity);

            assertThat(exactScore).isGreaterThan(partialScore);
            assertThat(partialScore).isGreaterThan(differentScore);
        }

        @Test
        @DisplayName("Alt names improve match scores")
        void altNamesImproveMatchScores() {
            // Entity with alt names
            Entity entityWithAlt = new Entity(
                "1", "MADURO MOROS, Nicolas", EntityType.PERSON, SourceList.US_OFAC,
                "1", null, null, null, null, null, null,
                List.of(), List.of(),
                List.of("Nicolas Maduro", "El Presidente"),
                List.of(), null, List.of(), null, null
            );
            entityIndex.add(entityWithAlt);

            double scoreWithAlt = searchService.scoreEntity("Nicolas Maduro", entityWithAlt);
            
            // Should have good match because of alt name
            assertThat(scoreWithAlt).isGreaterThan(0.7);
        }

        @Test
        @DisplayName("Case insensitive matching works")
        void caseInsensitiveMatchingWorks() {
            Entity entity = Entity.of("1", "JOHN SMITH", EntityType.PERSON, SourceList.US_OFAC);
            entityIndex.add(entity);

            double upperScore = searchService.scoreEntity("JOHN SMITH", entity);
            double lowerScore = searchService.scoreEntity("john smith", entity);
            double mixedScore = searchService.scoreEntity("John Smith", entity);

            // All should be very close (within rounding)
            assertThat(upperScore).isCloseTo(lowerScore, within(0.01));
            assertThat(lowerScore).isCloseTo(mixedScore, within(0.01));
        }
    }

    // Helper method
    private InputStream toInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
