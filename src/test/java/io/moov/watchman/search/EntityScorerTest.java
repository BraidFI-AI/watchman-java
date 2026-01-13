package io.moov.watchman.search;

import io.moov.watchman.model.*;
import io.moov.watchman.similarity.JaroWinklerSimilarity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for entity scoring logic.
 * Test cases ported from Go implementation: pkg/search/similarity_test.go
 * 
 * Scoring weights from Go implementation:
 * - Critical identifiers (sourceId, crypto): 50
 * - Government IDs: 50
 * - Contact info (email, phone): 50
 * - Name comparison: 35
 * - Title matching: 35
 * - Date comparison: 15
 * - Address matching: 25
 * - Supporting info: 15
 */
class EntityScorerTest {

    private EntityScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new EntityScorerImpl(new JaroWinklerSimilarity());
    }

    @Nested
    @DisplayName("Critical Identifier Matching (Weight 50)")
    class CriticalIdentifierTests {

        @Test
        @DisplayName("Exact sourceId match should score 1.0")
        void exactSourceIdMatchShouldScoreOne() {
            assertThat(scorer).isNotNull();
            
            Entity query = createEntity("ABC123", "Test Name", "ABC123");
            Entity index = createEntity("ABC123", "Different Name", "ABC123");
            
            ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index);
            
            // Matching sourceId is a critical identifier - should be perfect match
            assertThat(breakdown.totalWeightedScore()).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("Non-matching sourceId should not score on critical identifiers")
        void nonMatchingSourceIdShouldNotScore() {
            assertThat(scorer).isNotNull();
            
            Entity query = createEntity("1", "Test Name", "ABC123");
            Entity index = createEntity("2", "Test Name", "XYZ789");
            
            ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index);
            
            // Names match but sourceIds don't - critical identifier score should be 0
            // Total score should only come from name matching
            assertThat(breakdown.totalWeightedScore()).isLessThan(1.0);
        }
    }

    @Nested
    @DisplayName("Crypto Address Matching (Weight 50)")
    class CryptoAddressTests {

        @Test
        @DisplayName("Exact crypto address match should contribute high score")
        void exactCryptoAddressMatch() {
            assertThat(scorer).isNotNull();
            
            Entity query = createEntityWithCrypto("1", "Test", 
                List.of(CryptoAddress.of("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")));
            Entity index = createEntityWithCrypto("2", "Different Name", 
                List.of(CryptoAddress.of("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")));
            
            ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index);
            
            assertThat(breakdown.cryptoAddressScore()).isCloseTo(1.0, within(0.01));
        }

        @Test
        @DisplayName("Different crypto addresses should not match")
        void differentCryptoAddresses() {
            assertThat(scorer).isNotNull();
            
            Entity query = createEntityWithCrypto("1", "Test", 
                List.of(CryptoAddress.of("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")));
            Entity index = createEntityWithCrypto("2", "Test", 
                List.of(CryptoAddress.of("BTC", "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy")));
            
            ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index);
            
            assertThat(breakdown.cryptoAddressScore()).isCloseTo(0.0, within(0.01));
        }
    }

    @Nested
    @DisplayName("Government ID Matching (Weight 50)")
    class GovernmentIdTests {

        @Test
        @DisplayName("Tax ID with different formatting should match")
        void taxIdWithDifferentFormatting() {
            assertThat(scorer).isNotNull();
            
            // From Go test: TestCompareBusinessExactIDs
            Entity query = createEntityWithGovId("1", "Test Corp",
                new GovernmentId(GovernmentIdType.TAX_ID, "522083095", "United States"));
            Entity index = createEntityWithGovId("2", "Test Corp",
                new GovernmentId(GovernmentIdType.TAX_ID, "52-2083095", "United States"));
            
            ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index);
            
            // IDs should match after normalization
            assertThat(breakdown.governmentIdScore()).isCloseTo(1.0, within(0.01));
        }

        @Test
        @DisplayName("Passport number match should score high")
        void passportNumberMatch() {
            assertThat(scorer).isNotNull();
            
            Entity query = createEntityWithGovId("1", "John Smith",
                new GovernmentId(GovernmentIdType.PASSPORT, "E12345678", "Venezuela"));
            Entity index = createEntityWithGovId("2", "SMITH, John",
                new GovernmentId(GovernmentIdType.PASSPORT, "E12345678", "Venezuela"));
            
            ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index);
            
            assertThat(breakdown.governmentIdScore()).isCloseTo(1.0, within(0.01));
        }

        @Test
        @DisplayName("Different ID types should not match")
        void differentIdTypesShouldNotMatch() {
            assertThat(scorer).isNotNull();
            
            Entity query = createEntityWithGovId("1", "Test",
                new GovernmentId(GovernmentIdType.PASSPORT, "123456", "US"));
            Entity index = createEntityWithGovId("2", "Test",
                new GovernmentId(GovernmentIdType.TAX_ID, "123456", "US"));
            
            ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index);
            
            // Same number but different type - should not be a strong match
            assertThat(breakdown.governmentIdScore()).isLessThan(1.0);
        }
    }

    @Nested
    @DisplayName("Name Comparison (Weight 35)")
    class NameComparisonTests {

        @ParameterizedTest(name = "{0} vs {1} should score ~{2}")
        @CsvSource({
            "'Nicolas Maduro', 'MADURO MOROS, Nicolas', 0.85",
            "'AEROCARIBBEAN AIRLINES', 'AEROCARIBBEAN AIRLINES', 1.0",
            "'aerocaribbean airlines', 'AEROCARIBBEAN AIRLINES', 1.0",
            "'ANGLO CARIBBEAN CO LTD', 'ANGLO-CARIBBEAN CO., LTD.', 1.0",
            "'AEROCARRIBEAN AIRLINES', 'AEROCARIBBEAN AIRLINES', 0.95",
        })
        void shouldScoreNameSimilarity(String queryName, String indexName, double expectedScore) {
            assertThat(scorer).isNotNull();
            
            Entity query = Entity.of("1", queryName, EntityType.PERSON, SourceList.US_OFAC);
            Entity index = Entity.of("2", indexName, EntityType.PERSON, SourceList.US_OFAC);
            
            ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index);
            
            assertThat(breakdown.nameScore()).isCloseTo(expectedScore, within(0.1));
        }

        @Test
        @DisplayName("Completely different names should score low")
        void completelyDifferentNamesShouldScoreLow() {
            assertThat(scorer).isNotNull();
            
            Entity query = Entity.of("1", "AEROCARIBBEAN AIRLINES", EntityType.BUSINESS, SourceList.US_OFAC);
            Entity index = Entity.of("2", "BANCO NACIONAL DE CUBA", EntityType.BUSINESS, SourceList.US_OFAC);
            
            ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index);
            
            // With sourceId mismatch penalty, score is reduced below name-only score
            assertThat(breakdown.nameScore()).isLessThan(0.6);
        }
    }

    @Nested
    @DisplayName("Address Matching (Weight 25)")
    class AddressMatchingTests {

        @Test
        @DisplayName("Exact address match should score high")
        void exactAddressMatch() {
            assertThat(scorer).isNotNull();
            
            Address address = new Address("123 Main St", null, "Havana", null, null, "Cuba");
            
            Entity query = createEntityWithAddress("1", "Test", address);
            Entity index = createEntityWithAddress("2", "Test", address);
            
            ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index);
            
            assertThat(breakdown.addressScore()).isCloseTo(1.0, within(0.1));
        }

        @Test
        @DisplayName("Partial address match should score proportionally")
        void partialAddressMatch() {
            assertThat(scorer).isNotNull();
            
            // Different streets in same city/country
            Address queryAddress = new Address("123 Main St", null, "Havana", null, null, "Cuba");
            Address indexAddress = new Address("999 Ocean Drive", null, "Havana", null, null, "Cuba");
            
            Entity query = createEntityWithAddress("1", "Test", queryAddress);
            Entity index = createEntityWithAddress("2", "Test", indexAddress);
            
            ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index);
            
            // City and country match, street completely differs
            assertThat(breakdown.addressScore()).isBetween(0.3, 0.85);
        }
    }

    @Nested
    @DisplayName("Date Comparison (Weight 15)")
    class DateComparisonTests {

        @Test
        @DisplayName("Exact birth date match should score high")
        void exactBirthDateMatch() {
            assertThat(scorer).isNotNull();
            
            Entity query = createPersonWithDob("1", "John Smith", LocalDate.of(1965, 3, 15));
            Entity index = createPersonWithDob("2", "SMITH, John", LocalDate.of(1965, 3, 15));
            
            ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index);
            
            assertThat(breakdown.dateScore()).isCloseTo(1.0, within(0.01));
        }

        @Test
        @DisplayName("Different birth dates should score zero")
        void differentBirthDatesShouldScoreZero() {
            assertThat(scorer).isNotNull();
            
            Entity query = createPersonWithDob("1", "John Smith", LocalDate.of(1965, 3, 15));
            Entity index = createPersonWithDob("2", "John Smith", LocalDate.of(1970, 6, 20));
            
            ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index);
            
            assertThat(breakdown.dateScore()).isCloseTo(0.0, within(0.01));
        }
    }

    @Nested
    @DisplayName("Combined Scoring")
    class CombinedScoringTests {

        @Test
        @DisplayName("Multiple matching factors should combine scores")
        void multipleMatchingFactors() {
            assertThat(scorer).isNotNull();
            
            // Entity with matching: name, government ID, address
            Entity query = new Entity(
                "1", "Nicolas Maduro", EntityType.PERSON, SourceList.US_OFAC, "Q1",
                new Person("Nicolas Maduro", List.of(), "male", LocalDate.of(1962, 11, 23), 
                    null, "Caracas", List.of(), 
                    List.of(new GovernmentId(GovernmentIdType.PASSPORT, "V123456", "Venezuela"))),
                null, null, null, null, null,
                List.of(new Address("Palacio de Miraflores", null, "Caracas", null, null, "Venezuela")),
                List.of(), List.of(), 
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "V123456", "Venezuela")),
                null, List.of(), null, null
            );
            
            Entity index = new Entity(
                "7140", "MADURO MOROS, Nicolas", EntityType.PERSON, SourceList.US_OFAC, "7140",
                new Person("MADURO MOROS, Nicolas", List.of("Nicolas Maduro"), "male", 
                    LocalDate.of(1962, 11, 23), null, "Caracas, Venezuela", List.of("President"),
                    List.of(new GovernmentId(GovernmentIdType.PASSPORT, "V123456", "Venezuela"))),
                null, null, null, null, null,
                List.of(new Address("Palacio de Miraflores", null, "Caracas", null, null, "Venezuela")),
                List.of(), List.of("Nicolas Maduro", "El Presidente"),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "V123456", "Venezuela")),
                SanctionsInfo.of(List.of("VENEZUELA")), List.of(), null, null
            );
            
            ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index);
            
            // Should have high scores across multiple factors
            assertThat(breakdown.nameScore()).isGreaterThan(0.8);
            assertThat(breakdown.governmentIdScore()).isGreaterThan(0.8);
            assertThat(breakdown.dateScore()).isGreaterThan(0.8);
            assertThat(breakdown.totalWeightedScore()).isGreaterThan(0.9);
        }
    }

    // Helper methods to create test entities
    
    private Entity createEntity(String id, String name, String sourceId) {
        return new Entity(id, name, EntityType.PERSON, SourceList.US_OFAC, sourceId,
            null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), null, List.of(), null, null);
    }

    private Entity createEntityWithCrypto(String id, String name, List<CryptoAddress> cryptoAddresses) {
        return new Entity(id, name, EntityType.PERSON, SourceList.US_OFAC, id,
            null, null, null, null, null, null, List.of(), cryptoAddresses, List.of(), List.of(), null, List.of(), null, null);
    }

    private Entity createEntityWithGovId(String id, String name, GovernmentId govId) {
        return new Entity(id, name, EntityType.PERSON, SourceList.US_OFAC, id,
            null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(govId), null, List.of(), null, null);
    }

    private Entity createEntityWithAddress(String id, String name, Address address) {
        return new Entity(id, name, EntityType.PERSON, SourceList.US_OFAC, id,
            null, null, null, null, null, null, List.of(address), List.of(), List.of(), List.of(), null, List.of(), null, null);
    }

    private Entity createPersonWithDob(String id, String name, LocalDate dob) {
        Person person = new Person(name, List.of(), null, dob, null, null, List.of(), List.of());
        return new Entity(id, name, EntityType.PERSON, SourceList.US_OFAC, id,
            person, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), null, List.of(), null, null);
    }
}
