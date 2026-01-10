package io.moov.watchman.similarity;

import io.moov.watchman.model.*;
import io.moov.watchman.search.ContactFieldAdapter;
import io.moov.watchman.search.ContactFieldMatch;
import io.moov.watchman.search.DebugScoring;
import io.moov.watchman.search.EntityScorer;
import io.moov.watchman.search.EntityScorerImpl;
import io.moov.watchman.search.ScorePiece;
import io.moov.watchman.trace.ScoringContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 16: Complete Zone 1 (Scoring Functions) to 100%
 * 
 * Tests for the final 6 functions in Scoring Functions category:
 * 1. compareEntityTitlesFuzzy() - Type dispatcher for title comparison
 * 2. debug() - Debug output helper
 * 3. DebugSimilarity() - Debug scoring with logging
 * 4. compareGovernmentIDs() - Generic government ID dispatcher
 * 5. compareCryptoWallets() - Alias for compareCryptoAddresses
 * 6. compareContactField() - Contact field list comparison (compatibility adapter)
 * 
 * Target: Zone 1 completion 63/69 (91%) → 69/69 (100%)
 * Overall: 93/177 (53%) → 99/177 (56%)
 * Tests: 918 → 938 (+20 tests)
 */
@DisplayName("Phase 16: Zone 1 Completion (Scoring Functions)")
class Phase16ZoneOneCompletionTest {

    @Nested
    @DisplayName("compareEntityTitlesFuzzy Tests")
    class CompareEntityTitlesFuzzyTests {

        @Test
        @DisplayName("Person with matching titles should score high")
        void personTitles_exactMatch_scoresHigh() {
            Person queryPerson = new Person(
                    "John Doe",
                    List.of(),
                    "male",
                    null,
                    null,
                    "USA",
                    List.of("CEO", "President"),
                    List.of()
            );
            Entity query = new Entity(
                    "id1",
                    EntityType.PERSON,
                    "John Doe",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    queryPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            Person indexPerson = new Person(
                    "id2",
                    "John Doe",
                    List.of(),
                    "male",
                    null,
                    null,
                    "USA",
                    List.of("CEO"),
                    List.of()
            );
            Entity index = new Entity(
                    "id2",
                    EntityType.PERSON,
                    "John Doe",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    indexPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            ScorePiece result = EntityTitleComparer.compareEntityTitlesFuzzy(query, index, 35.0);

            assertNotNull(result);
            assertTrue(result.getScore() > 0.9, "Matching titles should score > 0.9, got: " + result.getScore());
            assertTrue(result.isMatched(), "Should be marked as matched");
            assertEquals("title-fuzzy", result.getPieceType());
            assertEquals(35.0, result.getWeight());
            assertEquals(1, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Business names with similar match should score medium")
        void businessName_fuzzyMatch_scoresMedium() {
            Business queryBusiness = new Business(
                    "Acme Corporation",
                    List.of(),
                    null,
                    null,
                    List.of()
            );
            Entity query = new Entity(
                    "id1",
                    "Acme Corporation",
                    EntityType.BUSINESS,
                    SourceList.US_OFAC,
                    "id1",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    queryBusiness,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            Business indexBusiness = new Business(
                    "Acme Corp",
                    List.of(),
                    null,
                    null,
                    List.of()
            );
            Entity index = new Entity(
                    "id2",
                    EntityType.BUSINESS,
                    "Acme Corp",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    indexBusiness,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            ScorePiece result = EntityTitleComparer.compareEntityTitlesFuzzy(query, index, 35.0);

            assertNotNull(result);
            assertTrue(result.getScore() > 0.7, "Similar business names should score > 0.7, got: " + result.getScore());
            assertTrue(result.isMatched(), "Should be marked as matched");
        }

        @Test
        @DisplayName("Different titles should score zero")
        void personTitles_noMatch_scoresZero() {
            Person queryPerson = new Person(
                    "id1",
                    "John Doe",
                    List.of(),
                    "male",
                    null,
                    null,
                    "USA",
                    List.of("Engineer"),
                    List.of()
            );
            Entity query = new Entity(
                    "id1",
                    EntityType.PERSON,
                    "John Doe",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    queryPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            Person indexPerson = new Person(
                    "id2",
                    "Jane Smith",
                    List.of(),
                    "female",
                    null,
                    null,
                    "USA",
                    List.of("CEO", "President"),
                    List.of()
            );
            Entity index = new Entity(
                    "id2",
                    EntityType.PERSON,
                    "Jane Smith",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    indexPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            ScorePiece result = EntityTitleComparer.compareEntityTitlesFuzzy(query, index, 35.0);

            assertNotNull(result);
            assertTrue(result.getScore() < 0.5, "Different titles should score < 0.5, got: " + result.getScore());
            assertFalse(result.isMatched(), "Should not be marked as matched");
        }

        @Test
        @DisplayName("Empty titles should return zero score")
        void emptyTitles_returnsZeroScore() {
            Person queryPerson = new Person(
                    "id1",
                    "John Doe",
                    List.of(),
                    "male",
                    null,
                    null,
                    "USA",
                    List.of(),  // Empty titles
                    List.of()
            );
            Entity query = new Entity(
                    "id1",
                    EntityType.PERSON,
                    "John Doe",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    queryPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            Person indexPerson = new Person(
                    "Jane Smith",
                    List.of(),
                    "female",
                    null,
                    null,
                    "USA",
                    List.of("CEO"),
                    List.of()
            );
            Entity index = new Entity(
                    "id2",
                    EntityType.PERSON,
                    "Jane Smith",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    indexPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            ScorePiece result = EntityTitleComparer.compareEntityTitlesFuzzy(query, index, 35.0);

            assertNotNull(result);
            assertEquals(0.0, result.getScore());
            assertFalse(result.isMatched());
            assertEquals(0, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Aircraft type matching should work")
        void aircraftType_exactMatch_scoresHigh() {
            Aircraft queryAircraft = new Aircraft(
                    "id1",
                    "Boeing 737",
                    List.of(),
                    "Passenger",
                    "USA",
                    "12345",
                    "737-800",
                    null,
                    null
            );
            Entity query = new Entity(
                    "id1",
                    EntityType.AIRCRAFT,
                    "Boeing 737",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    queryAircraft,
                    null,
                    null,
                    null,
                    null
            );

            Aircraft indexAircraft = new Aircraft(
                    "id2",
                    "Boeing 737",
                    List.of(),
                    "Passenger",
                    "USA",
                    "67890",
                    "737-900",
                    null,
                    null
            );
            Entity index = new Entity(
                    "id2",
                    EntityType.AIRCRAFT,
                    "Boeing 737",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    indexAircraft,
                    null,
                    null,
                    null,
                    null
            );

            ScorePiece result = EntityTitleComparer.compareEntityTitlesFuzzy(query, index, 35.0);

            assertNotNull(result);
            assertTrue(result.getScore() > 0.9, "Matching aircraft types should score > 0.9, got: " + result.getScore());
            assertTrue(result.isMatched());
            assertTrue(result.isExact());
        }

        @Test
        @DisplayName("Matched threshold should be > 0.5")
        void matched_thresholdAbove05() {
            // Create entities with titles that score exactly at threshold
            Person queryPerson = new Person(
                    "id1",
                    "John",
                    List.of(),
                    "male",
                    null,
                    null,
                    "USA",
                    List.of("Director"),
                    List.of()
            );
            Entity query = new Entity(
                    "id1",
                    EntityType.PERSON,
                    "John",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    queryPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            Person indexPerson = new Person(
                    "id2",
                    "Jane",
                    List.of(),
                    "female",
                    null,
                    null,
                    "USA",
                    List.of("Dir"),  // Abbreviated, should score medium
                    List.of()
            );
            Entity index = new Entity(
                    "id2",
                    EntityType.PERSON,
                    "Jane",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    indexPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            ScorePiece result = EntityTitleComparer.compareEntityTitlesFuzzy(query, index, 35.0);

            assertNotNull(result);
            if (result.getScore() > 0.5) {
                assertTrue(result.isMatched(), "Score > 0.5 should be marked as matched");
            } else {
                assertFalse(result.isMatched(), "Score <= 0.5 should not be marked as matched");
            }
        }

        @Test
        @DisplayName("Exact threshold should be > 0.99")
        void exact_thresholdAbove099() {
            Person queryPerson = new Person(
                    "id1",
                    "John Doe",
                    List.of(),
                    "male",
                    null,
                    null,
                    "USA",
                    List.of("Chief Executive Officer"),
                    List.of()
            );
            Entity query = new Entity(
                    "id1",
                    EntityType.PERSON,
                    "John Doe",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    queryPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            Person indexPerson = new Person(
                    "id2",
                    "Jane Smith",
                    List.of(),
                    "female",
                    null,
                    null,
                    "USA",
                    List.of("Chief Executive Officer"),  // Exact match
                    List.of()
            );
            Entity index = new Entity(
                    "id2",
                    EntityType.PERSON,
                    "Jane Smith",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    indexPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            ScorePiece result = EntityTitleComparer.compareEntityTitlesFuzzy(query, index, 35.0);

            assertNotNull(result);
            if (result.getScore() > 0.99) {
                assertTrue(result.isExact(), "Score > 0.99 should be marked as exact");
            }
        }
    }

    @Nested
    @DisplayName("Debug Functions Tests")
    class DebugFunctionsTests {

        @Test
        @DisplayName("debug() with null writer should not throw")
        void debug_withNullWriter_doesNotThrow() {
            assertDoesNotThrow(() -> 
                DebugScoring.debug(null, "Test message: %s", "value")
            );
        }

        @Test
        @DisplayName("debug() with writer should write output")
        void debug_withWriter_writesOutput() throws Exception {
            StringWriter writer = new StringWriter();
            
            DebugScoring.debug(writer, "Hello %s!\n", "World");
            
            String output = writer.toString();
            assertTrue(output.contains("Hello World!"), "Output should contain formatted message");
        }

        @Test
        @DisplayName("debug() with formatting should apply correctly")
        void debug_withFormatting_appliesCorrectly() throws Exception {
            StringWriter writer = new StringWriter();
            
            DebugScoring.debug(writer, "Score: %.4f, Count: %d\n", 0.8523, 42);
            
            String output = writer.toString();
            assertTrue(output.contains("0.8523"), "Should contain formatted double");
            assertTrue(output.contains("42"), "Should contain formatted integer");
        }

        @Test
        @DisplayName("debugSimilarity() should log entity info")
        void debugSimilarity_logsEntityInfo() throws Exception {
            Person queryPerson = new Person(
                    "id1",
                    "John Doe",
                    List.of(),
                    "male",
                    null,
                    null,
                    "USA",
                    List.of("CEO"),
                    List.of()
            );
            Entity query = new Entity(
                    "id1",
                    EntityType.PERSON,
                    "John Doe",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    queryPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            Person indexPerson = new Person(
                    "id2",
                    "John Doe",
                    List.of(),
                    "male",
                    null,
                    null,
                    "USA",
                    List.of("CEO"),
                    List.of()
            );
            Entity index = new Entity(
                    "id2",
                    EntityType.PERSON,
                    "John Doe",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    indexPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            StringWriter writer = new StringWriter();
            
            double score = DebugScoring.debugSimilarity(writer, query, index);
            
            String output = writer.toString();
            assertTrue(output.contains("Debug Similarity"), "Should contain debug header");
            assertTrue(output.contains("John Doe"), "Should contain entity name");
            assertTrue(output.contains("PERSON"), "Should contain entity type");
            assertTrue(score > 0, "Should return a valid score");
        }

        @Test
        @DisplayName("debugSimilarity() should log all score components")
        void debugSimilarity_logsAllScorePieces() throws Exception {
            Person queryPerson = new Person(
                    "id1",
                    "John Doe",
                    List.of(),
                    "male",
                    null,
                    null,
                    "USA",
                    List.of("CEO"),
                    List.of()
            );
            Entity query = new Entity(
                    "id1",
                    EntityType.PERSON,
                    "John Doe",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    queryPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            Person indexPerson = new Person(
                    "id2",
                    "John Doe",
                    List.of(),
                    "male",
                    null,
                    null,
                    "USA",
                    List.of("CEO"),
                    List.of()
            );
            Entity index = new Entity(
                    "id2",
                    EntityType.PERSON,
                    "John Doe",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    indexPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            StringWriter writer = new StringWriter();
            
            DebugScoring.debugSimilarity(writer, query, index);
            
            String output = writer.toString();
            assertTrue(output.contains("Name Score") || output.contains("score"), 
                "Should contain score component information");
            assertTrue(output.contains("Final Score"), "Should contain final score");
        }

        @Test
        @DisplayName("debugSimilarity() should return same score as normal scoring")
        void debugSimilarity_returnsSameScoreAsNormal() {
            Person queryPerson = new Person(
                    "id1",
                    "John Doe",
                    List.of(),
                    "male",
                    null,
                    null,
                    "USA",
                    List.of("CEO"),
                    List.of()
            );
            Entity query = new Entity(
                    "id1",
                    EntityType.PERSON,
                    "John Doe",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    queryPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            Person indexPerson = new Person(
                    "id2",
                    "John Doe",
                    List.of(),
                    "male",
                    null,
                    null,
                    "USA",
                    List.of("CEO"),
                    List.of()
            );
            Entity index = new Entity(
                    "id2",
                    EntityType.PERSON,
                    "John Doe",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    indexPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            StringWriter writer = new StringWriter();
            double debugScore = DebugScoring.debugSimilarity(writer, query, index);
            
            EntityScorer scorer = new EntityScorerImpl(new JaroWinklerSimilarity());
            double normalScore = scorer.scoreWithBreakdown(query, index, ScoringContext.disabled())
                    .totalWeightedScore();
            
            assertEquals(normalScore, debugScore, 0.001, 
                "Debug similarity should return same score as normal scoring");
        }
    }

    @Nested
    @DisplayName("Generic Dispatchers Tests")
    class GenericDispatchersTests {

        @Test
        @DisplayName("compareGovernmentIDs() for Person should delegate correctly")
        void compareGovernmentIDs_personType_delegatesToPersonMatcher() {
            GovernmentId queryId = new GovernmentId("USA", "SSN", "123-45-6789");
            Person queryPerson = new Person(
                    "id1",
                    "John Doe",
                    List.of(),
                    "male",
                    null,
                    null,
                    "USA",
                    List.of(),
                    List.of(queryId)
            );
            Entity query = new Entity(
                    "id1",
                    EntityType.PERSON,
                    "John Doe",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    queryPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            GovernmentId indexId = new GovernmentId("USA", "SSN", "123-45-6789");
            Person indexPerson = new Person(
                    "id2",
                    "John Doe",
                    List.of(),
                    "male",
                    null,
                    null,
                    "USA",
                    List.of(),
                    List.of(indexId)
            );
            Entity index = new Entity(
                    "id2",
                    EntityType.PERSON,
                    "John Doe",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    indexPerson,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            IdMatchResult result = ExactIdMatcher.compareGovernmentIDs(query, index, 15.0);

            assertNotNull(result);
            assertTrue(result.getScore() > 0, "Matching government IDs should score > 0");
            assertTrue(result.isMatched());
            assertEquals(15.0, result.getWeight());
        }

        @Test
        @DisplayName("compareGovernmentIDs() for Business should delegate correctly")
        void compareGovernmentIDs_businessType_delegatesToBusinessMatcher() {
            GovernmentId queryId = new GovernmentId("USA", "EIN", "12-3456789");
            Business queryBusiness = new Business(
                    "id1",
                    "Acme Corp",
                    List.of(),
                    null,
                    null,
                    List.of(queryId)
            );
            Entity query = new Entity(
                    "id1",
                    EntityType.BUSINESS,
                    "Acme Corp",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    queryBusiness,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            GovernmentId indexId = new GovernmentId("USA", "EIN", "12-3456789");
            Business indexBusiness = new Business(
                    "id2",
                    "Acme Corp",
                    List.of(),
                    null,
                    null,
                    List.of(indexId)
            );
            Entity index = new Entity(
                    "id2",
                    EntityType.BUSINESS,
                    "Acme Corp",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    indexBusiness,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            IdMatchResult result = ExactIdMatcher.compareGovernmentIDs(query, index, 15.0);

            assertNotNull(result);
            assertTrue(result.getScore() > 0, "Matching government IDs should score > 0");
            assertEquals(15.0, result.getWeight());
        }

        @Test
        @DisplayName("compareGovernmentIDs() for unknown type should return zero")
        void compareGovernmentIDs_unknownType_returnsZero() {
            Entity query = new Entity(
                    "id1",
                    EntityType.UNKNOWN,
                    "Unknown Entity",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            Entity index = new Entity(
                    "id2",
                    EntityType.UNKNOWN,
                    "Unknown Entity",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            IdMatchResult result = ExactIdMatcher.compareGovernmentIDs(query, index, 15.0);

            assertNotNull(result);
            assertEquals(0.0, result.getScore());
            assertFalse(result.isMatched());
            assertEquals(0, result.getFieldsCompared());
        }

        @Test
        @DisplayName("compareCryptoWallets() should delegate to compareCryptoAddresses()")
        void compareCryptoWallets_delegatesToCryptoAddresses() {
            CryptoAddress queryAddress = new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa");
            Entity query = new Entity(
                    "id1",
                    EntityType.PERSON,
                    "Satoshi Nakamoto",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    List.of(queryAddress),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            CryptoAddress indexAddress = new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa");
            Entity index = new Entity(
                    "id2",
                    EntityType.PERSON,
                    "Satoshi Nakamoto",
                    "ofac-sdn",
                    List.of(),
                    List.of(),
                    List.of(indexAddress),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            IdMatchResult result = ExactIdMatcher.compareCryptoWallets(query, index, 15.0);

            assertNotNull(result);
            assertTrue(result.getScore() > 0, "Matching crypto addresses should score > 0");
            assertTrue(result.isExact(), "Exact crypto match should be marked as exact");
            assertEquals(15.0, result.getWeight());
        }
    }

    @Nested
    @DisplayName("ContactFieldAdapter Tests (Optional)")
    class ContactFieldAdapterTests {

        @Test
        @DisplayName("compareContactField() with exact matches should score 1.0")
        void compareContactField_exactMatches_scoresOne() {
            List<String> queryValues = List.of("test@example.com", "admin@example.com");
            List<String> indexValues = List.of("test@example.com", "admin@example.com");

            ContactFieldMatch result = ContactFieldAdapter.compareContactField(queryValues, indexValues);

            assertNotNull(result);
            assertEquals(2, result.matches());
            assertEquals(2, result.totalQuery());
            assertEquals(1.0, result.getScore(), 0.001);
        }

        @Test
        @DisplayName("compareContactField() with partial matches should score correctly")
        void compareContactField_partialMatches_scoresCorrectly() {
            List<String> queryValues = List.of("test@example.com", "admin@example.com", "user@example.com");
            List<String> indexValues = List.of("test@example.com", "other@example.com");

            ContactFieldMatch result = ContactFieldAdapter.compareContactField(queryValues, indexValues);

            assertNotNull(result);
            assertEquals(1, result.matches());
            assertEquals(3, result.totalQuery());
            assertEquals(1.0 / 3.0, result.getScore(), 0.001);
        }

        @Test
        @DisplayName("compareContactField() with no matches should score 0.0")
        void compareContactField_noMatches_scoresZero() {
            List<String> queryValues = List.of("test@example.com");
            List<String> indexValues = List.of("other@example.com");

            ContactFieldMatch result = ContactFieldAdapter.compareContactField(queryValues, indexValues);

            assertNotNull(result);
            assertEquals(0, result.matches());
            assertEquals(1, result.totalQuery());
            assertEquals(0.0, result.getScore(), 0.001);
        }

        @Test
        @DisplayName("compareContactField() should be case-insensitive")
        void compareContactField_caseInsensitive() {
            List<String> queryValues = List.of("TEST@EXAMPLE.COM");
            List<String> indexValues = List.of("test@example.com");

            ContactFieldMatch result = ContactFieldAdapter.compareContactField(queryValues, indexValues);

            assertNotNull(result);
            assertEquals(1, result.matches());
            assertEquals(1.0, result.getScore(), 0.001);
        }
    }
}
