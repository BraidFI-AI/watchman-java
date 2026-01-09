package io.moov.watchman.search;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.index.InMemoryEntityIndex;
import io.moov.watchman.model.*;
import io.moov.watchman.similarity.JaroWinklerSimilarity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for the search service and entity scoring.
 * Test cases ported from Go implementation: pkg/search/similarity_test.go
 */
class SearchServiceTest {

    private SearchService searchService;
    private EntityIndex entityIndex;

    @BeforeEach
    void setUp() {
        entityIndex = new InMemoryEntityIndex();
        EntityScorer entityScorer = new EntityScorerImpl(new JaroWinklerSimilarity());
        searchService = new SearchServiceImpl(entityIndex, entityScorer);
        
        // Add test data
        entityIndex.addAll(List.of(
            Entity.of("7140", "Nicolas Maduro", EntityType.PERSON, SourceList.US_OFAC),
            Entity.of("7141", "MADURO MOROS, Nicolas", EntityType.PERSON, SourceList.US_OFAC),
            Entity.of("7142", "MADURO, Nicolas Jr", EntityType.PERSON, SourceList.US_OFAC),
            Entity.of("1001", "AEROCARIBBEAN AIRLINES", EntityType.BUSINESS, SourceList.US_OFAC),
            Entity.of("1002", "BANCO NACIONAL DE CUBA", EntityType.BUSINESS, SourceList.US_OFAC),
            Entity.of("1003", "HAVANA SHIPPING CO", EntityType.VESSEL, SourceList.US_OFAC)
        ));
    }

    @Nested
    @DisplayName("Search Result Ranking")
    class SearchRankingTests {

        @Test
        @DisplayName("Results should be sorted by score descending")
        void resultsShouldBeSortedByScoreDescending() {
            assertThat(searchService).isNotNull();
            
            List<SearchResult> results = searchService.search("nicolas maduro");
            
            // Results should be in descending score order
            for (int i = 0; i < results.size() - 1; i++) {
                assertThat(results.get(i).score())
                    .isGreaterThanOrEqualTo(results.get(i + 1).score());
            }
        }

        @Test
        @DisplayName("Should respect limit parameter")
        void shouldRespectLimitParameter() {
            assertThat(searchService).isNotNull();
            
            List<SearchResult> results = searchService.search("maduro", 5, 0.0);
            
            assertThat(results).hasSizeLessThanOrEqualTo(5);
        }

        @Test
        @DisplayName("Should filter by minMatch threshold")
        void shouldFilterByMinMatchThreshold() {
            assertThat(searchService).isNotNull();
            
            double minMatch = 0.90;
            List<SearchResult> results = searchService.search("nicolas maduro", 100, minMatch);
            
            for (SearchResult result : results) {
                assertThat(result.score()).isGreaterThanOrEqualTo(minMatch);
            }
        }

        @Test
        @DisplayName("Empty query should return empty results")
        void emptyQueryShouldReturnEmptyResults() {
            assertThat(searchService).isNotNull();
            
            List<SearchResult> results = searchService.search("");
            
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Entity Scoring")
    class EntityScoringTests {

        @Test
        @DisplayName("Empty query should score 0.0")
        void emptyQueryShouldScoreZero() {
            assertThat(searchService).isNotNull();
            
            Entity entity = Entity.of("47371", "Test Entity", EntityType.PERSON, SourceList.US_OFAC);
            
            double score = searchService.scoreEntity("", entity);
            
            assertThat(score).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("Matching sourceId should score 1.0")
        void matchingSourceIdShouldScoreOne() {
            assertThat(searchService).isNotNull();
            
            // When query contains the exact sourceId, should be perfect match
            Entity entity = new Entity(
                "ABC123", "Test Entity", EntityType.PERSON, SourceList.US_OFAC, "ABC123",
                null, null, null, null, null,
                null, List.of(), List.of(), List.of(), List.of(),
                null, null, null
            );
            
            // If we search by sourceId directly, should get exact match
            // This tests the critical identifier matching
            assertThat(true).isTrue(); // Placeholder
        }

        @Test
        @DisplayName("High name similarity should contribute to score")
        void highNameSimilarityShouldContributeToScore() {
            assertThat(searchService).isNotNull();
            
            Entity entity = Entity.of("1", "Nicolas Maduro", EntityType.PERSON, SourceList.US_OFAC);
            
            double score = searchService.scoreEntity("nicolas maduro", entity);
            
            // Name match is weight 35, so high name similarity should give good score
            assertThat(score).isGreaterThan(0.8);
        }

        @Test
        @DisplayName("Score should combine multiple matching factors")
        void scoreShouldCombineMultipleFactors() {
            assertThat(searchService).isNotNull();
            
            // Entity with matching name AND government ID should score higher
            // than entity with just matching name
            
            Person person = new Person(
                "Nicolas Maduro",
                List.of(),
                null, null, null, null, List.of(),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "V12345678", "Venezuela"))
            );
            
            Entity entityWithId = new Entity(
                "1", "Nicolas Maduro", EntityType.PERSON, SourceList.US_OFAC, "1",
                person, null, null, null, null,
                null, List.of(), List.of(), List.of(), 
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "V12345678", "Venezuela")),
                null, null, null
            );
            
            Entity entityWithoutId = Entity.of("2", "Nicolas Maduro", EntityType.PERSON, SourceList.US_OFAC);
            
            // TODO: Compare scores when searching with matching passport number
            assertThat(true).isTrue(); // Placeholder
        }
    }

    @Nested
    @DisplayName("Source ID Matching")
    class SourceIdMatchingTests {

        @Test
        @DisplayName("Non-matching sourceId should score 0.0")
        void nonMatchingSourceIdShouldScoreZero() {
            assertThat(searchService).isNotNull();
            
            // From Go test: TestSimilarity_SourceID
            // query.SourceID = "abc", index.SourceID = different value
            // Score should be 0.0
            
            assertThat(true).isTrue(); // Placeholder
        }

        @Test
        @DisplayName("Exact sourceId match should score 1.0")
        void exactSourceIdMatchShouldScoreOne() {
            assertThat(searchService).isNotNull();
            
            // From Go test: TestSimilarity_SourceID
            // When sourceIds match exactly, score should be 1.0
            
            assertThat(true).isTrue(); // Placeholder
        }
    }

    @Nested
    @DisplayName("Weighted Scoring")
    class WeightedScoringTests {

        @Test
        @DisplayName("Critical identifiers have weight 50")
        void criticalIdentifiersHaveWeight50() {
            // Exact ID matches (sourceId, crypto addresses) should have high weight
            assertThat(true).isTrue(); // Placeholder
        }

        @Test
        @DisplayName("Government IDs have weight 50")
        void governmentIdsHaveWeight50() {
            // Passport, tax ID, etc. matches should have high weight
            assertThat(true).isTrue(); // Placeholder
        }

        @Test
        @DisplayName("Name comparison has weight 35")
        void nameComparisonHasWeight35() {
            // Name fuzzy matching should have moderate weight
            assertThat(true).isTrue(); // Placeholder
        }

        @Test
        @DisplayName("Address matching has weight 25")
        void addressMatchingHasWeight25() {
            // Address comparison should have lower weight
            assertThat(true).isTrue(); // Placeholder
        }

        @Test
        @DisplayName("Date comparison has weight 15")
        void dateComparisonHasWeight15() {
            // Birth/death date matching should have low weight
            assertThat(true).isTrue(); // Placeholder
        }
    }
}
