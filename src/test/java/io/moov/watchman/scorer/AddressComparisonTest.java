package io.moov.watchman.scorer;

import io.moov.watchman.model.Address;
import io.moov.watchman.model.PreparedAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD Phase 7 - RED PHASE
 * Tests for address normalization and comparison
 * 
 * Based on Go implementation:
 * - pkg/search/models.go: normalizeAddress(), normalizeAddresses() (lines 356-391)
 * - pkg/search/similarity_address.go: compareAddress(), findBestAddressMatch() (lines 53-161)
 * 
 * These tests WILL FAIL until we implement:
 * 1. AddressNormalizer.normalizeAddress(Address) → PreparedAddress
 * 2. AddressNormalizer.normalizeAddresses(List<Address>) → List<PreparedAddress>
 * 3. AddressComparer.compareAddress(PreparedAddress, PreparedAddress) → double
 * 4. AddressComparer.findBestAddressMatch(List<PreparedAddress>, List<PreparedAddress>) → double
 */
@DisplayName("Address Normalization and Comparison")
class AddressComparisonTest {
    
    @Nested
    @DisplayName("normalizeAddress() tests")
    class NormalizeAddressTests {
        
        @Test
        @DisplayName("Should lowercase all fields")
        void shouldLowercaseAllFields() {
            // GIVEN: Address with mixed case
            Address addr = new Address(
                "123 MAIN ST",
                "APT 5B",
                "NEW YORK",
                "NY",
                "10001",
                "USA"
            );
            
            // WHEN: Normalized
            PreparedAddress prepared = AddressNormalizer.normalizeAddress(addr);
            
            // THEN: All fields should be lowercase
            assertEquals("123 main st", prepared.line1());
            assertEquals("apt 5b", prepared.line2());
            assertEquals("new york", prepared.city());
            assertEquals("ny", prepared.state());
            assertEquals("10001", prepared.postalCode());
            // Country should be normalized via CountryNormalizer
            
            // EXPECTED TO FAIL: AddressNormalizer doesn't exist
        }
        
        @Test
        @DisplayName("Should remove commas from address lines")
        void shouldRemoveCommas() {
            // GIVEN: Address with commas (common in addresses)
            Address addr = new Address(
                "123 Main St, Suite 100",
                "Building A, Floor 2",
                "New York",
                "NY",
                "10001",
                "USA"
            );
            
            // WHEN: Normalized
            PreparedAddress prepared = AddressNormalizer.normalizeAddress(addr);
            
            // THEN: Commas should be removed (Go uses addressCleaner)
            assertEquals("123 main st suite 100", prepared.line1());
            assertEquals("building a floor 2", prepared.line2());
        }
        
        @Test
        @DisplayName("Should tokenize line1 into line1Fields")
        void shouldTokenizeLine1() {
            // GIVEN: Address with line1
            Address addr = new Address("123 Broadway Suite 500", null, "New York", "NY", "10013", "US");
            
            // WHEN: Normalized
            PreparedAddress prepared = AddressNormalizer.normalizeAddress(addr);
            
            // THEN: line1Fields should contain tokens
            assertNotNull(prepared.line1Fields());
            assertEquals(4, prepared.line1Fields().size());
            assertEquals(List.of("123", "broadway", "suite", "500"), prepared.line1Fields());
        }
        
        @Test
        @DisplayName("Should tokenize line2 into line2Fields")
        void shouldTokenizeLine2() {
            // GIVEN: Address with line2
            Address addr = new Address("123 Main St", "Apt 5B", "New York", "NY", "10001", "US");
            
            // WHEN: Normalized
            PreparedAddress prepared = AddressNormalizer.normalizeAddress(addr);
            
            // THEN: line2Fields should contain tokens
            assertNotNull(prepared.line2Fields());
            assertEquals(2, prepared.line2Fields().size());
            assertEquals(List.of("apt", "5b"), prepared.line2Fields());
        }
        
        @Test
        @DisplayName("Should tokenize city into cityFields")
        void shouldTokenizeCity() {
            // GIVEN: Address with multi-word city
            Address addr = new Address("123 Main St", null, "New York", "NY", "10001", "US");
            
            // WHEN: Normalized
            PreparedAddress prepared = AddressNormalizer.normalizeAddress(addr);
            
            // THEN: cityFields should contain tokens
            assertNotNull(prepared.cityFields());
            assertEquals(2, prepared.cityFields().size());
            assertEquals(List.of("new", "york"), prepared.cityFields());
        }
        
        @Test
        @DisplayName("Should handle empty line1Fields when line1 is empty")
        void shouldHandleEmptyLine1() {
            // GIVEN: Address with null/empty line1
            Address addr = new Address(null, null, "New York", "NY", "10001", "US");
            
            // WHEN: Normalized
            PreparedAddress prepared = AddressNormalizer.normalizeAddress(addr);
            
            // THEN: Phase 17 - line1 stays null, line1Fields should be empty
            assertNull(prepared.line1());
            assertTrue(prepared.line1Fields().isEmpty());
        }
        
        @Test
        @DisplayName("Should handle empty line2Fields when line2 is empty")
        void shouldHandleEmptyLine2() {
            // GIVEN: Address without line2
            Address addr = new Address("123 Main St", null, "New York", "NY", "10001", "US");
            
            // WHEN: Normalized
            PreparedAddress prepared = AddressNormalizer.normalizeAddress(addr);
            
            // THEN: Phase 17 - line2 stays null, line2Fields should be empty
            assertNull(prepared.line2());
            assertTrue(prepared.line2Fields().isEmpty());
        }
        
        @Test
        @DisplayName("Should handle empty cityFields when city is empty")
        void shouldHandleEmptyCity() {
            // GIVEN: Address without city (unusual but possible)
            Address addr = new Address("123 Main St", null, "", "NY", "10001", "US");
            
            // WHEN: Normalized
            PreparedAddress prepared = AddressNormalizer.normalizeAddress(addr);
            
            // THEN: city should be empty, cityFields should be empty
            assertEquals("", prepared.city());
            assertTrue(prepared.cityFields().isEmpty());
        }
        
        @Test
        @DisplayName("Should normalize country code to standard form")
        void shouldNormalizeCountry() {
            // GIVEN: Various country representations
            Address addr1 = new Address("123 Main", null, "City", "NY", "10001", "USA");
            Address addr2 = new Address("456 Oak", null, "City", "CA", "90001", "United States");
            
            // WHEN: Normalized
            PreparedAddress prep1 = AddressNormalizer.normalizeAddress(addr1);
            PreparedAddress prep2 = AddressNormalizer.normalizeAddress(addr2);
            
            // THEN: Phase 17 - Just lowercase, no expansion ("usa" vs "united states")
            assertEquals("usa", prep1.country());
            assertEquals("united states", prep2.country());
        }
        
        @Test
        @DisplayName("Should handle null address fields gracefully")
        void shouldHandleNullFields() {
            // GIVEN: Address with all null fields
            Address addr = new Address(null, null, null, null, null, null);
            
            // WHEN: Normalized
            PreparedAddress prepared = AddressNormalizer.normalizeAddress(addr);
            
            // THEN: Phase 17 - Null fields stay null (not converted to empty strings)
            assertNull(prepared.line1());
            assertNull(prepared.line2());
            assertNull(prepared.city());
            assertNull(prepared.state());
            assertNull(prepared.postalCode());
            assertNull(prepared.country());
        }
    }
    
    @Nested
    @DisplayName("normalizeAddresses() tests")
    class NormalizeAddressesTests {
        
        @Test
        @DisplayName("Should return empty list for null input")
        void shouldHandleNullInput() {
            // WHEN: Normalizing null
            List<PreparedAddress> result = AddressNormalizer.normalizeAddresses(null);
            
            // THEN: Should return empty list (not null)
            assertNotNull(result);
            assertTrue(result.isEmpty());
            
            // EXPECTED TO FAIL: AddressNormalizer doesn't exist
        }
        
        @Test
        @DisplayName("Should return empty list for empty input")
        void shouldHandleEmptyInput() {
            // WHEN: Normalizing empty list
            List<PreparedAddress> result = AddressNormalizer.normalizeAddresses(List.of());
            
            // THEN: Should return empty list
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
        
        @Test
        @DisplayName("Should normalize single address")
        void shouldNormalizeSingleAddress() {
            // GIVEN: Single address
            Address addr = new Address("123 Main St", null, "New York", "NY", "10001", "US");
            
            // WHEN: Normalized
            List<PreparedAddress> result = AddressNormalizer.normalizeAddresses(List.of(addr));
            
            // THEN: Should return one normalized address
            assertEquals(1, result.size());
            assertEquals("123 main st", result.get(0).line1());
        }
        
        @Test
        @DisplayName("Should normalize multiple addresses")
        void shouldNormalizeMultipleAddresses() {
            // GIVEN: Multiple addresses
            Address addr1 = new Address("123 Main St", null, "New York", "NY", "10001", "US");
            Address addr2 = new Address("456 Oak Ave", "Suite 200", "Los Angeles", "CA", "90001", "US");
            
            // WHEN: Normalized
            List<PreparedAddress> result = AddressNormalizer.normalizeAddresses(List.of(addr1, addr2));
            
            // THEN: Should return two normalized addresses
            assertEquals(2, result.size());
            assertEquals("123 main st", result.get(0).line1());
            assertEquals("456 oak ave", result.get(1).line1());
            assertEquals("suite 200", result.get(1).line2());
        }
    }
    
    @Nested
    @DisplayName("compareAddress() tests")
    class CompareAddressTests {
        
        @Test
        @DisplayName("Should return 1.0 for identical addresses")
        void shouldReturnPerfectScoreForIdentical() {
            // GIVEN: Identical addresses
            PreparedAddress addr1 = AddressNormalizer.normalizeAddress(
                new Address("123 Main St", "Apt 5", "New York", "NY", "10001", "US")
            );
            PreparedAddress addr2 = AddressNormalizer.normalizeAddress(
                new Address("123 Main St", "Apt 5", "New York", "NY", "10001", "US")
            );
            
            // WHEN: Compared
            double score = AddressComparer.compareAddress(addr1, addr2);
            
            // THEN: Should return perfect score
            assertEquals(1.0, score, 0.001);
            
            // EXPECTED TO FAIL: AddressComparer doesn't exist
        }
        
        @Test
        @DisplayName("Should return low score when no fields match well")
        void shouldReturnLowScoreForNoMatch() {
            // GIVEN: Completely different addresses (but same country)
            PreparedAddress addr1 = AddressNormalizer.normalizeAddress(
                new Address("123 Main St", null, "New York", "NY", "10001", "US")
            );
            PreparedAddress addr2 = AddressNormalizer.normalizeAddress(
                new Address("456 Oak Ave", null, "Los Angeles", "CA", "90001", "US")
            );
            
            // WHEN: Compared
            double score = AddressComparer.compareAddress(addr1, addr2);
            
            // THEN: Should return low score
            // Note: They share same country (US → united states), so score won't be 0.0
            // But line1, city, state, postalCode are all different
            assertTrue(score < 0.5, "Score should be low for mostly different addresses");
        }
        
        @Test
        @DisplayName("Should weight line1 most heavily (weight=5.0)")
        void shouldWeightLine1Heavily() {
            // GIVEN: Two addresses that only match on line1
            PreparedAddress addr1 = new PreparedAddress(
                "123 main st", List.of("123", "main", "st"),
                "", List.of(),
                "", List.of(),
                "", "", ""
            );
            PreparedAddress addr2 = new PreparedAddress(
                "123 main st", List.of("123", "main", "st"),
                "", List.of(),
                "", List.of(),
                "", "", ""
            );
            
            // WHEN: Compared
            double score = AddressComparer.compareAddress(addr1, addr2);
            
            // THEN: Should return perfect score (only field compared)
            assertEquals(1.0, score, 0.001);
        }
        
        @Test
        @DisplayName("Should compare line1 using BestPairCombinationJaroWinkler")
        void shouldUseBestPairForLine1() {
            // GIVEN: Similar but not identical line1
            PreparedAddress addr1 = new PreparedAddress(
                "123 main street", List.of("123", "main", "street"),
                "", List.of(),
                "", List.of(),
                "", "", ""
            );
            PreparedAddress addr2 = new PreparedAddress(
                "123 main st", List.of("123", "main", "st"),
                "", List.of(),
                "", List.of(),
                "", "", ""
            );
            
            // WHEN: Compared
            double score = AddressComparer.compareAddress(addr1, addr2);
            
            // THEN: Should return high score (street ≈ st via JaroWinkler)
            assertTrue(score > 0.90, "Should use JaroWinkler for fuzzy line1 matching");
        }
        
        @Test
        @DisplayName("Should compare city using BestPairCombinationJaroWinkler")
        void shouldUseBestPairForCity() {
            // GIVEN: Addresses with similar cities
            PreparedAddress addr1 = new PreparedAddress(
                "", List.of(),
                "", List.of(),
                "new york", List.of("new", "york"),
                "", "", ""
            );
            PreparedAddress addr2 = new PreparedAddress(
                "", List.of(),
                "", List.of(),
                "new york", List.of("new", "york"),
                "", "", ""
            );
            
            // WHEN: Compared
            double score = AddressComparer.compareAddress(addr1, addr2);
            
            // THEN: Should return perfect score (only field compared)
            assertEquals(1.0, score, 0.001);
        }
        
        @Test
        @DisplayName("Should compare state using case-insensitive exact match")
        void shouldCompareStateCaseInsensitive() {
            // GIVEN: Addresses with matching states (different case)
            PreparedAddress addr1 = new PreparedAddress(
                "", List.of(),
                "", List.of(),
                "", List.of(),
                "ny", "", ""
            );
            PreparedAddress addr2 = new PreparedAddress(
                "", List.of(),
                "", List.of(),
                "", List.of(),
                "NY", "", ""
            );
            
            // WHEN: Compared
            double score = AddressComparer.compareAddress(addr1, addr2);
            
            // THEN: Should return perfect score (case-insensitive state match)
            assertEquals(1.0, score, 0.001);
        }
        
        @Test
        @DisplayName("Should compare postalCode using case-insensitive exact match")
        void shouldComparePostalCodeCaseInsensitive() {
            // GIVEN: Addresses with matching postal codes
            PreparedAddress addr1 = new PreparedAddress(
                "", List.of(),
                "", List.of(),
                "", List.of(),
                "", "10001", ""
            );
            PreparedAddress addr2 = new PreparedAddress(
                "", List.of(),
                "", List.of(),
                "", List.of(),
                "", "10001", ""
            );
            
            // WHEN: Compared
            double score = AddressComparer.compareAddress(addr1, addr2);
            
            // THEN: Should return perfect score
            assertEquals(1.0, score, 0.001);
        }
        
        @Test
        @DisplayName("Should compare country using case-insensitive exact match")
        void shouldCompareCountryCaseInsensitive() {
            // GIVEN: Addresses with matching countries (assumes already normalized)
            PreparedAddress addr1 = new PreparedAddress(
                "", List.of(),
                "", List.of(),
                "", List.of(),
                "", "", "united states"
            );
            PreparedAddress addr2 = new PreparedAddress(
                "", List.of(),
                "", List.of(),
                "", List.of(),
                "", "", "united states"
            );
            
            // WHEN: Compared
            double score = AddressComparer.compareAddress(addr1, addr2);
            
            // THEN: Should return perfect score
            assertEquals(1.0, score, 0.001);
        }
        
        @Test
        @DisplayName("Should weight city heavily (weight=4.0)")
        void shouldWeightCityHeavily() {
            // GIVEN: Addresses matching on city and state
            PreparedAddress addr1 = new PreparedAddress(
                "", List.of(),
                "", List.of(),
                "new york", List.of("new", "york"),
                "ny", "", ""
            );
            PreparedAddress addr2 = new PreparedAddress(
                "", List.of(),
                "", List.of(),
                "new york", List.of("new", "york"),
                "ny", "", ""
            );
            
            // WHEN: Compared
            double score = AddressComparer.compareAddress(addr1, addr2);
            
            // THEN: Should return perfect score (city weight=4.0, state weight=2.0)
            assertEquals(1.0, score, 0.001);
        }
        
        @Test
        @DisplayName("Should return 0.0 when both addresses have no comparable fields")
        void shouldReturnZeroForEmptyAddresses() {
            // GIVEN: Two empty addresses
            PreparedAddress addr1 = PreparedAddress.empty();
            PreparedAddress addr2 = PreparedAddress.empty();
            
            // WHEN: Compared
            double score = AddressComparer.compareAddress(addr1, addr2);
            
            // THEN: Should return 0.0 (no fields to compare)
            assertEquals(0.0, score, 0.001);
        }
        
        @Test
        @DisplayName("Should skip fields that are empty in query")
        void shouldSkipEmptyQueryFields() {
            // GIVEN: Query with empty line1, index with line1
            PreparedAddress query = new PreparedAddress(
                "", List.of(),
                "", List.of(),
                "new york", List.of("new", "york"),
                "", "", ""
            );
            PreparedAddress index = new PreparedAddress(
                "123 main st", List.of("123", "main", "st"),
                "", List.of(),
                "new york", List.of("new", "york"),
                "", "", ""
            );
            
            // WHEN: Compared
            double score = AddressComparer.compareAddress(query, index);
            
            // THEN: Should only compare city (line1 skipped because query is empty)
            assertEquals(1.0, score, 0.001, "Should ignore line1 when query is empty");
        }
        
        @Test
        @DisplayName("Should skip fields that are empty in index")
        void shouldSkipEmptyIndexFields() {
            // GIVEN: Query with line1, index without line1
            PreparedAddress query = new PreparedAddress(
                "123 main st", List.of("123", "main", "st"),
                "", List.of(),
                "new york", List.of("new", "york"),
                "", "", ""
            );
            PreparedAddress index = new PreparedAddress(
                "", List.of(),
                "", List.of(),
                "new york", List.of("new", "york"),
                "", "", ""
            );
            
            // WHEN: Compared
            double score = AddressComparer.compareAddress(query, index);
            
            // THEN: Should only compare city (line1 skipped because index is empty)
            assertEquals(1.0, score, 0.001, "Should ignore line1 when index is empty");
        }
        
        @Test
        @DisplayName("Real-world example: Similar but not identical address")
        void realWorldExample() {
            // GIVEN: Real-world address variations (from Go tests)
            PreparedAddress query = AddressNormalizer.normalizeAddress(
                new Address("St 1/A Block 2 Gulshan-e-Iqbal", null, "Karachi", null, "75300", "Pakistan")
            );
            PreparedAddress index = AddressNormalizer.normalizeAddress(
                new Address("ST 1/A, Block 2, Gulshan-e-Iqbal", null, "Karachi", null, "75300", "Pakistan")
            );
            
            // WHEN: Compared
            double score = AddressComparer.compareAddress(query, index);
            
            // THEN: Should return high score despite punctuation differences
            assertTrue(score > 0.95, "Should handle real-world address variations");
        }
    }
    
    @Nested
    @DisplayName("findBestAddressMatch() tests")
    class FindBestAddressMatchTests {
        
        @Test
        @DisplayName("Should return 0.0 for empty query list")
        void shouldReturnZeroForEmptyQuery() {
            // GIVEN: Empty query, non-empty index
            List<PreparedAddress> query = List.of();
            List<PreparedAddress> index = List.of(
                AddressNormalizer.normalizeAddress(new Address("123 Main", null, "NYC", "NY", "10001", "US"))
            );
            
            // WHEN: Finding best match
            double score = AddressComparer.findBestAddressMatch(query, index);
            
            // THEN: Should return 0.0
            assertEquals(0.0, score, 0.001);
            
            // EXPECTED TO FAIL: AddressComparer doesn't exist
        }
        
        @Test
        @DisplayName("Should return 0.0 for empty index list")
        void shouldReturnZeroForEmptyIndex() {
            // GIVEN: Non-empty query, empty index
            List<PreparedAddress> query = List.of(
                AddressNormalizer.normalizeAddress(new Address("123 Main", null, "NYC", "NY", "10001", "US"))
            );
            List<PreparedAddress> index = List.of();
            
            // WHEN: Finding best match
            double score = AddressComparer.findBestAddressMatch(query, index);
            
            // THEN: Should return 0.0
            assertEquals(0.0, score, 0.001);
        }
        
        @Test
        @DisplayName("Should find best match from single query and single index")
        void shouldFindBestMatchSingleToSingle() {
            // GIVEN: One query address, one index address
            List<PreparedAddress> query = List.of(
                AddressNormalizer.normalizeAddress(new Address("123 Main St", null, "New York", "NY", "10001", "US"))
            );
            List<PreparedAddress> index = List.of(
                AddressNormalizer.normalizeAddress(new Address("123 Main St", null, "New York", "NY", "10001", "US"))
            );
            
            // WHEN: Finding best match
            double score = AddressComparer.findBestAddressMatch(query, index);
            
            // THEN: Should return perfect score
            assertEquals(1.0, score, 0.001);
        }
        
        @Test
        @DisplayName("Should find best match from single query and multiple index addresses")
        void shouldFindBestMatchFromMultipleIndex() {
            // GIVEN: One query, three index addresses (one perfect match)
            List<PreparedAddress> query = List.of(
                AddressNormalizer.normalizeAddress(new Address("123 Main St", null, "New York", "NY", "10001", "US"))
            );
            List<PreparedAddress> index = List.of(
                AddressNormalizer.normalizeAddress(new Address("456 Oak Ave", null, "Los Angeles", "CA", "90001", "US")),
                AddressNormalizer.normalizeAddress(new Address("123 Main St", null, "New York", "NY", "10001", "US")),  // Perfect match
                AddressNormalizer.normalizeAddress(new Address("789 Elm St", null, "Chicago", "IL", "60601", "US"))
            );
            
            // WHEN: Finding best match
            double score = AddressComparer.findBestAddressMatch(query, index);
            
            // THEN: Should return perfect score (found the matching address)
            assertEquals(1.0, score, 0.001);
        }
        
        @Test
        @DisplayName("Should find best match from multiple query addresses")
        void shouldFindBestMatchFromMultipleQuery() {
            // GIVEN: Multiple query addresses, single index (one matches)
            List<PreparedAddress> query = List.of(
                AddressNormalizer.normalizeAddress(new Address("456 Oak Ave", null, "Los Angeles", "CA", "90001", "US")),
                AddressNormalizer.normalizeAddress(new Address("123 Main St", null, "New York", "NY", "10001", "US"))  // Will match
            );
            List<PreparedAddress> index = List.of(
                AddressNormalizer.normalizeAddress(new Address("123 Main St", null, "New York", "NY", "10001", "US"))
            );
            
            // WHEN: Finding best match
            double score = AddressComparer.findBestAddressMatch(query, index);
            
            // THEN: Should return perfect score (found the matching query address)
            assertEquals(1.0, score, 0.001);
        }
        
        @Test
        @DisplayName("Should try all combinations and return highest score")
        void shouldReturnHighestScoreFromAllCombinations() {
            // GIVEN: Multiple queries and indices (one best pair)
            List<PreparedAddress> query = List.of(
                AddressNormalizer.normalizeAddress(new Address("123 Main St", null, "New York", "NY", "10001", "US")),  // Low match
                AddressNormalizer.normalizeAddress(new Address("789 Elm St", null, "Chicago", "IL", "60601", "US"))     // High match
            );
            List<PreparedAddress> index = List.of(
                AddressNormalizer.normalizeAddress(new Address("456 Oak Ave", null, "Los Angeles", "CA", "90001", "US")),  // Low match
                AddressNormalizer.normalizeAddress(new Address("789 Elm Street", null, "Chicago", "IL", "60601", "US"))    // High match
            );
            
            // WHEN: Finding best match
            double score = AddressComparer.findBestAddressMatch(query, index);
            
            // THEN: Should return high score (found the best pair: Elm St ↔ Elm Street)
            assertTrue(score > 0.95, "Should find best match across all query-index pairs");
        }
        
        @Test
        @DisplayName("Should early-exit when finding high confidence match (>0.92)")
        void shouldEarlyExitOnHighConfidence() {
            // GIVEN: Multiple index addresses with one perfect match early in list
            List<PreparedAddress> query = List.of(
                AddressNormalizer.normalizeAddress(new Address("123 Main St", null, "New York", "NY", "10001", "US"))
            );
            List<PreparedAddress> index = List.of(
                AddressNormalizer.normalizeAddress(new Address("123 Main St", null, "New York", "NY", "10001", "US")),  // Perfect match - should stop here
                AddressNormalizer.normalizeAddress(new Address("456 Oak Ave", null, "Los Angeles", "CA", "90001", "US")),
                AddressNormalizer.normalizeAddress(new Address("789 Elm St", null, "Chicago", "IL", "60601", "US"))
            );
            
            // WHEN: Finding best match
            double score = AddressComparer.findBestAddressMatch(query, index);
            
            // THEN: Should return perfect score (early exit optimization)
            assertEquals(1.0, score, 0.001);
            // Note: In production, this should stop comparing after first address
        }
        
        @Test
        @DisplayName("Should handle no good matches (all low scores)")
        void shouldHandleNoGoodMatches() {
            // GIVEN: Completely different addresses
            List<PreparedAddress> query = List.of(
                AddressNormalizer.normalizeAddress(new Address("123 Main St", null, "New York", "NY", "10001", "US"))
            );
            List<PreparedAddress> index = List.of(
                AddressNormalizer.normalizeAddress(new Address("999 Distant Rd", null, "Remote City", "ZZ", "99999", "XX"))
            );
            
            // WHEN: Finding best match
            double score = AddressComparer.findBestAddressMatch(query, index);
            
            // THEN: Should return low score (still best available)
            assertTrue(score < 0.3, "Should return low score when no good matches exist");
        }
        
        @Test
        @DisplayName("Real-world integration: Multiple addresses with best match")
        void realWorldIntegration() {
            // GIVEN: Real-world scenario with multiple entity addresses
            List<PreparedAddress> query = List.of(
                AddressNormalizer.normalizeAddress(new Address("1234 Broadway Suite 500", null, "New York", "NY", "10013", "USA")),
                AddressNormalizer.normalizeAddress(new Address("PO Box 789", null, "New York", "NY", "10001", "USA"))
            );
            List<PreparedAddress> index = List.of(
                // Phase 17: Use same country format since expansion was removed
                AddressNormalizer.normalizeAddress(new Address("1234 Broadway, Suite 500", null, "New York", "NY", "10013", "USA")),  // Should match first query
                AddressNormalizer.normalizeAddress(new Address("456 Different St", null, "Los Angeles", "CA", "90001", "USA"))
            );
            
            // WHEN: Finding best match
            double score = AddressComparer.findBestAddressMatch(query, index);
            
            // THEN: Should return high score (found the matching Broadway address despite comma difference)
            assertTrue(score > 0.95, "Should handle real-world address variations");
        }
    }
    
    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        
        @Test
        @DisplayName("End-to-end: Normalize and compare workflow")
        void endToEndWorkflow() {
            // GIVEN: Raw addresses
            // Phase 17: Use same country format since expansion was removed
            Address query = new Address("123 MAIN STREET", "SUITE 100", "NEW YORK", "NY", "10001", "USA");
            Address index = new Address("123 Main St", "Suite 100", "New York", "NY", "10001", "USA");
            
            // WHEN: Normalize
            PreparedAddress prepQuery = AddressNormalizer.normalizeAddress(query);
            PreparedAddress prepIndex = AddressNormalizer.normalizeAddress(index);
            
            // AND: Compare
            double score = AddressComparer.compareAddress(prepQuery, prepIndex);
            
            // THEN: Should match despite case and abbreviation differences
            assertTrue(score > 0.95, "End-to-end workflow should handle normalization and comparison");
        }
        
        @Test
        @DisplayName("Batch processing: Multiple addresses end-to-end")
        void batchProcessingWorkflow() {
            // GIVEN: Multiple raw addresses
            // Phase 17: Use same country format
            List<Address> queryAddrs = List.of(
                new Address("123 Main St", null, "New York", "NY", "10001", "US"),
                new Address("456 Oak Ave", null, "Los Angeles", "CA", "90001", "US")
            );
            List<Address> indexAddrs = List.of(
                new Address("123 Main Street", null, "New York", "NY", "10001", "US"),
                new Address("789 Elm St", null, "Chicago", "IL", "60601", "US")
            );
            
            // WHEN: Batch normalize
            List<PreparedAddress> prepQuery = AddressNormalizer.normalizeAddresses(queryAddrs);
            List<PreparedAddress> prepIndex = AddressNormalizer.normalizeAddresses(indexAddrs);
            
            // AND: Find best match
            double score = AddressComparer.findBestAddressMatch(prepQuery, prepIndex);
            
            // THEN: Should find the best match (Main St vs Main Street)
            assertTrue(score > 0.95, "Batch processing should find best match across all addresses");
        }
    }
}
