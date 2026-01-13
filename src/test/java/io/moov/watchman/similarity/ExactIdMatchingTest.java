package io.moov.watchman.similarity;

import io.moov.watchman.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for exact identifier matching functions.
 * 
 * Phase 9 - TDD RED: Exact ID Matching (11 functions)
 * Tests based on Go implementation: pkg/search/similarity_exact.go
 * 
 * Functions under test:
 * 1. comparePersonExactIDs() - Person-specific identifier matching
 * 2. compareBusinessExactIDs() - Business-specific identifier matching
 * 3. compareOrgExactIDs() - Organization-specific identifier matching
 * 4. compareVesselExactIDs() - Vessel-specific identifier matching (IMO, CallSign, MMSI)
 * 5. compareAircraftExactIDs() - Aircraft-specific identifier matching (SerialNumber, ICAO)
 * 6. comparePersonGovernmentIDs() - Person government ID comparison with country matching
 * 7. compareBusinessGovernmentIDs() - Business government ID comparison
 * 8. compareOrgGovernmentIDs() - Organization government ID comparison
 * 9. compareCryptoAddresses() - Cryptocurrency address exact matching
 * 10. normalizeIdentifier() - Hyphen removal for ID normalization
 * 11. compareIdentifiers() - Core ID comparison logic with country validation
 */
@DisplayName("Phase 9: Exact ID Matching")
class ExactIdMatchingTest {

    @Nested
    @DisplayName("comparePersonExactIDs() - Person identifier matching")
    class ComparePersonExactIDsTests {
        
        @Test
        @DisplayName("should return score 1.0 when government IDs match exactly")
        void shouldReturnPerfectScoreForExactMatch() {
            Person query = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "A123456", "US")));
            Person index = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "A123456", "US")));
            
            IdMatchResult result = ExactIdMatcher.comparePersonExactIDs(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
            assertTrue(result.exact());
            assertEquals(1, result.fieldsCompared());
        }
        
        @Test
        @DisplayName("should return 0 when no government IDs present")
        void shouldReturnZeroWhenNoIDs() {
            Person query = new Person("John Doe", List.of(), null, null, null, null, List.of(), List.of());
            Person index = new Person("Jane Doe", List.of(), null, null, null, null, List.of(), List.of());
            
            IdMatchResult result = ExactIdMatcher.comparePersonExactIDs(query, index, 2.0);
            
            assertEquals(0.0, result.score());
            assertFalse(result.matched());
            assertEquals(0, result.fieldsCompared());
        }
        
        @Test
        @DisplayName("should return 0 when government IDs don't match")
        void shouldReturnZeroWhenIDsDontMatch() {
            Person query = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "A123456", "US")));
            Person index = new Person("Jane Doe", List.of(), null, null, null, null, List.of(),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "B999999", "UK")));
            
            IdMatchResult result = ExactIdMatcher.comparePersonExactIDs(query, index, 2.0);
            
            assertEquals(0.0, result.score());
            assertFalse(result.matched());
            assertEquals(1, result.fieldsCompared());
        }
        
        @Test
        @DisplayName("should match first matching ID when multiple IDs present")
        void shouldMatchFirstWhenMultipleIDs() {
            Person query = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(
                    new GovernmentId(GovernmentIdType.PASSPORT, "A123456", "US"),
                    new GovernmentId(GovernmentIdType.NATIONAL_ID, "SSN-12345", "US")
                ));
            Person index = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(
                    new GovernmentId(GovernmentIdType.DRIVERS_LICENSE, "DL-999", "US"),
                    new GovernmentId(GovernmentIdType.NATIONAL_ID, "SSN-12345", "US")
                ));
            
            IdMatchResult result = ExactIdMatcher.comparePersonExactIDs(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
            assertTrue(result.exact());
        }
        
        @Test
        @DisplayName("should handle null Person objects")
        void shouldHandleNullPersons() {
            IdMatchResult result1 = ExactIdMatcher.comparePersonExactIDs(null, new Person("Test", List.of(), null, null, null, null, List.of(), List.of()), 2.0);
            IdMatchResult result2 = ExactIdMatcher.comparePersonExactIDs(new Person("Test", List.of(), null, null, null, null, List.of(), List.of()), null, 2.0);
            
            assertEquals(0.0, result1.score());
            assertEquals(0.0, result2.score());
        }
        
        @Test
        @DisplayName("should compare case-insensitively")
        void shouldCompareCaseInsensitively() {
            Person query = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "a123456", "us")));
            Person index = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "A123456", "US")));
            
            IdMatchResult result = ExactIdMatcher.comparePersonExactIDs(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
        }
    }
    
    @Nested
    @DisplayName("compareBusinessExactIDs() - Business identifier matching")
    class CompareBusinessExactIDsTests {
        
        @Test
        @DisplayName("should return score 1.0 for exact business ID match")
        void shouldReturnPerfectScoreForExactMatch() {
            Business query = new Business("Acme Corp", List.of(), null, null,
                List.of(new GovernmentId(GovernmentIdType.TAX_ID, "12-3456789", "US")));
            Business index = new Business("Acme Corp", List.of(), null, null,
                List.of(new GovernmentId(GovernmentIdType.TAX_ID, "12-3456789", "US")));
            
            IdMatchResult result = ExactIdMatcher.compareBusinessExactIDs(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
            assertTrue(result.exact());
        }
        
        @Test
        @DisplayName("should return 0 when no business IDs present")
        void shouldReturnZeroWhenNoIDs() {
            Business query = new Business("Acme Corp", List.of(), null, null, List.of());
            Business index = new Business("Widgets Inc", List.of(), null, null, List.of());
            
            IdMatchResult result = ExactIdMatcher.compareBusinessExactIDs(query, index, 2.0);
            
            assertEquals(0.0, result.score());
            assertFalse(result.matched());
        }
        
        @Test
        @DisplayName("should handle null Business objects")
        void shouldHandleNullBusinesses() {
            Business business = new Business("Test", List.of(), null, null, List.of());
            
            IdMatchResult result1 = ExactIdMatcher.compareBusinessExactIDs(null, business, 2.0);
            IdMatchResult result2 = ExactIdMatcher.compareBusinessExactIDs(business, null, 2.0);
            
            assertEquals(0.0, result1.score());
            assertEquals(0.0, result2.score());
        }
    }
    
    @Nested
    @DisplayName("compareOrgExactIDs() - Organization identifier matching")
    class CompareOrgExactIDsTests {
        
        @Test
        @DisplayName("should return score 1.0 for exact org ID match")
        void shouldReturnPerfectScoreForExactMatch() {
            Organization query = new Organization("UN", List.of(), null, null,
                List.of(new GovernmentId(GovernmentIdType.TAX_ID, "ORG-12345", "US")));
            Organization index = new Organization("UN", List.of(), null, null,
                List.of(new GovernmentId(GovernmentIdType.TAX_ID, "ORG-12345", "US")));
            
            IdMatchResult result = ExactIdMatcher.compareOrgExactIDs(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
            assertTrue(result.exact());
        }
        
        @Test
        @DisplayName("should return 0 when no org IDs present")
        void shouldReturnZeroWhenNoIDs() {
            Organization query = new Organization("UN", List.of(), null, null, List.of());
            Organization index = new Organization("NATO", List.of(), null, null, List.of());
            
            IdMatchResult result = ExactIdMatcher.compareOrgExactIDs(query, index, 2.0);
            
            assertEquals(0.0, result.score());
            assertFalse(result.matched());
        }
        
        @Test
        @DisplayName("should handle null Organization objects")
        void shouldHandleNullOrganizations() {
            Organization org = new Organization("Test", List.of(), null, null, List.of());
            
            IdMatchResult result1 = ExactIdMatcher.compareOrgExactIDs(null, org, 2.0);
            IdMatchResult result2 = ExactIdMatcher.compareOrgExactIDs(org, null, 2.0);
            
            assertEquals(0.0, result1.score());
            assertEquals(0.0, result2.score());
        }
    }
    
    @Nested
    @DisplayName("compareVesselExactIDs() - Vessel identifier matching")
    class CompareVesselExactIDsTests {
        
        @Test
        @DisplayName("should return score 1.0 for exact IMO match")
        void shouldMatchIMONumber() {
            Vessel query = new Vessel("Ship Alpha", List.of(), "IMO1234567", null, null, null, null, null, null, null);
            Vessel index = new Vessel("Ship Alpha", List.of(), "IMO1234567", null, null, null, null, null, null, null);
            
            IdMatchResult result = ExactIdMatcher.compareVesselExactIDs(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
            assertTrue(result.exact());
        }
        
        @Test
        @DisplayName("should return score 1.0 for exact CallSign match")
        void shouldMatchCallSign() {
            Vessel query = new Vessel("Ship Beta", List.of(), null, null, null, null, null, "CALL123", null, null);
            Vessel index = new Vessel("Ship Beta", List.of(), null, null, null, null, null, "CALL123", null, null);
            
            IdMatchResult result = ExactIdMatcher.compareVesselExactIDs(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
        }
        
        @Test
        @DisplayName("should return score 1.0 for exact MMSI match")
        void shouldMatchMMSI() {
            Vessel query = new Vessel("Ship Gamma", List.of(), null, null, null, null, "123456789", null, null, null);
            Vessel index = new Vessel("Ship Gamma", List.of(), null, null, null, null, "123456789", null, null, null);
            
            IdMatchResult result = ExactIdMatcher.compareVesselExactIDs(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
        }
        
        @Test
        @DisplayName("should compute weighted average for multiple matching fields")
        void shouldComputeWeightedAverageForMultipleFields() {
            // All three fields match: IMO (15.0), CallSign (12.0), MMSI (12.0)
            // Total weight: 39.0, Total score: 39.0, Final: 1.0
            Vessel query = new Vessel("Ship Delta", List.of(), "IMO1234567", null, null, null, "123456789", "CALL123", null, null);
            Vessel index = new Vessel("Ship Delta", List.of(), "IMO1234567", null, null, null, "123456789", "CALL123", null, null);
            
            IdMatchResult result = ExactIdMatcher.compareVesselExactIDs(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
        }
        
        @Test
        @DisplayName("should compute partial score when only some fields match")
        void shouldComputePartialScore() {
            // Only IMO matches: weight 15.0, score 15.0
            // CallSign doesn't match: weight 12.0, score 0.0
            // Total: 15.0/27.0 = 0.556
            Vessel query = new Vessel("Ship Epsilon", List.of(), "IMO1234567", null, null, null, null, "CALL123", null, null);
            Vessel index = new Vessel("Ship Epsilon", List.of(), "IMO1234567", null, null, null, null, "CALL999", null, null);
            
            IdMatchResult result = ExactIdMatcher.compareVesselExactIDs(query, index, 2.0);
            
            assertEquals(0.556, result.score(), 0.01);
            assertTrue(result.matched());
            assertFalse(result.exact());
        }
        
        @Test
        @DisplayName("should return 0 when no vessel IDs present")
        void shouldReturnZeroWhenNoIDs() {
            Vessel query = new Vessel("Ship Zeta", List.of(), null, null, null, null, null, null, null, null);
            Vessel index = new Vessel("Ship Zeta", List.of(), null, null, null, null, null, null, null, null);
            
            IdMatchResult result = ExactIdMatcher.compareVesselExactIDs(query, index, 2.0);
            
            assertEquals(0.0, result.score());
            assertFalse(result.matched());
            assertEquals(0, result.fieldsCompared());
        }
        
        @Test
        @DisplayName("should handle null Vessel objects")
        void shouldHandleNullVessels() {
            Vessel vessel = new Vessel("Test", List.of(), null, null, null, null, null, null, null, null);
            
            IdMatchResult result1 = ExactIdMatcher.compareVesselExactIDs(null, vessel, 2.0);
            IdMatchResult result2 = ExactIdMatcher.compareVesselExactIDs(vessel, null, 2.0);
            
            assertEquals(0.0, result1.score());
            assertEquals(0.0, result2.score());
        }
    }
    
    @Nested
    @DisplayName("compareAircraftExactIDs() - Aircraft identifier matching")
    class CompareAircraftExactIDsTests {
        
        @Test
        @DisplayName("should return score 1.0 for exact SerialNumber match")
        void shouldMatchSerialNumber() {
            Aircraft query = new Aircraft("Plane Alpha", List.of(), null, null, null, null, null, "SN-12345");
            Aircraft index = new Aircraft("Plane Alpha", List.of(), null, null, null, null, null, "SN-12345");
            
            IdMatchResult result = ExactIdMatcher.compareAircraftExactIDs(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
            assertTrue(result.exact());
        }
        
        @Test
        @DisplayName("should return score 1.0 for exact ICAO Code match")
        void shouldMatchICAOCode() {
            Aircraft query = new Aircraft("Plane Beta", List.of(), null, null, null, "ICAO123", null, null);
            Aircraft index = new Aircraft("Plane Beta", List.of(), null, null, null, "ICAO123", null, null);
            
            IdMatchResult result = ExactIdMatcher.compareAircraftExactIDs(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
        }
        
        @Test
        @DisplayName("should compute weighted average for both fields matching")
        void shouldComputeWeightedAverageForBothFields() {
            // SerialNumber (15.0) + ICAO (12.0) = 27.0 total weight, all match
            Aircraft query = new Aircraft("Plane Gamma", List.of(), null, null, null, "ICAO123", null, "SN-12345");
            Aircraft index = new Aircraft("Plane Gamma", List.of(), null, null, null, "ICAO123", null, "SN-12345");
            
            IdMatchResult result = ExactIdMatcher.compareAircraftExactIDs(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
        }
        
        @Test
        @DisplayName("should compute partial score when only SerialNumber matches")
        void shouldComputePartialScoreSerialOnly() {
            // SerialNumber matches (15.0), ICAO doesn't (12.0)
            // Score: 15.0/27.0 = 0.556
            Aircraft query = new Aircraft("Plane Delta", List.of(), null, null, null, "ICAO123", null, "SN-12345");
            Aircraft index = new Aircraft("Plane Delta", List.of(), null, null, null, "ICAO999", null, "SN-12345");
            
            IdMatchResult result = ExactIdMatcher.compareAircraftExactIDs(query, index, 2.0);
            
            assertEquals(0.556, result.score(), 0.01);
            assertTrue(result.matched());
        }
        
        @Test
        @DisplayName("should return 0 when no aircraft IDs present")
        void shouldReturnZeroWhenNoIDs() {
            Aircraft query = new Aircraft("Plane Zeta", List.of(), null, null, null, null, null, null);
            Aircraft index = new Aircraft("Plane Zeta", List.of(), null, null, null, null, null, null);
            
            IdMatchResult result = ExactIdMatcher.compareAircraftExactIDs(query, index, 2.0);
            
            assertEquals(0.0, result.score());
            assertFalse(result.matched());
        }
        
        @Test
        @DisplayName("should handle null Aircraft objects")
        void shouldHandleNullAircraft() {
            Aircraft aircraft = new Aircraft("Test", List.of(), null, null, null, null, null, null);
            
            IdMatchResult result1 = ExactIdMatcher.compareAircraftExactIDs(null, aircraft, 2.0);
            IdMatchResult result2 = ExactIdMatcher.compareAircraftExactIDs(aircraft, null, 2.0);
            
            assertEquals(0.0, result1.score());
            assertEquals(0.0, result2.score());
        }
    }
    
    @Nested
    @DisplayName("comparePersonGovernmentIDs() - Person government ID with country validation")
    class ComparePersonGovernmentIDsTests {
        
        @Test
        @DisplayName("should return 1.0 for exact match with same identifier and country")
        void shouldReturnPerfectScoreForExactMatch() {
            Person query = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "A123456", "US")));
            Person index = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "A123456", "US")));
            
            IdMatchResult result = ExactIdMatcher.comparePersonGovernmentIDs(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
            assertTrue(result.exact());
        }
        
        @Test
        @DisplayName("should return 0.9 when identifier matches but one country missing")
        void shouldPenalizePartialCountryMatch() {
            Person query = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "A123456", null)));
            Person index = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "A123456", "US")));
            
            IdMatchResult result = ExactIdMatcher.comparePersonGovernmentIDs(query, index, 2.0);
            
            assertEquals(0.9, result.score(), 0.001);
            assertTrue(result.matched());
            assertFalse(result.exact());
        }
        
        @Test
        @DisplayName("should return 0.7 when identifier matches but countries differ")
        void shouldPenalizeDifferentCountries() {
            Person query = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "A123456", "US")));
            Person index = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "A123456", "UK")));
            
            IdMatchResult result = ExactIdMatcher.comparePersonGovernmentIDs(query, index, 2.0);
            
            assertEquals(0.7, result.score(), 0.001);
            assertTrue(result.matched());
            assertFalse(result.exact());
        }
        
        @Test
        @DisplayName("should return 1.0 when identifier matches and both have no country")
        void shouldMatchWhenBothHaveNoCountry() {
            Person query = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "A123456", null)));
            Person index = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "A123456", null)));
            
            IdMatchResult result = ExactIdMatcher.comparePersonGovernmentIDs(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
            assertTrue(result.exact());
        }
        
        @Test
        @DisplayName("should return best match when multiple IDs present")
        void shouldReturnBestMatchFromMultipleIDs() {
            Person query = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(
                    new GovernmentId(GovernmentIdType.PASSPORT, "A123456", "US"),
                    new GovernmentId(GovernmentIdType.NATIONAL_ID, "SSN-12345", "US")
                ));
            Person index = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(
                    new GovernmentId(GovernmentIdType.PASSPORT, "A123456", "UK"),  // 0.7 score
                    new GovernmentId(GovernmentIdType.NATIONAL_ID, "SSN-12345", "US")  // 1.0 score (best)
                ));
            
            IdMatchResult result = ExactIdMatcher.comparePersonGovernmentIDs(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);  // Should use best match
            assertTrue(result.matched());
        }
        
        @Test
        @DisplayName("should return 0 when identifiers don't match")
        void shouldReturnZeroWhenIdentifiersDontMatch() {
            Person query = new Person("John Doe", List.of(), null, null, null, null, List.of(),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "A123456", "US")));
            Person index = new Person("Jane Doe", List.of(), null, null, null, null, List.of(),
                List.of(new GovernmentId(GovernmentIdType.PASSPORT, "B999999", "US")));
            
            IdMatchResult result = ExactIdMatcher.comparePersonGovernmentIDs(query, index, 2.0);
            
            assertEquals(0.0, result.score());
            assertFalse(result.matched());
        }
    }
    
    @Nested
    @DisplayName("compareBusinessGovernmentIDs() - Business government ID matching")
    class CompareBusinessGovernmentIDsTests {
        
        @Test
        @DisplayName("should return 1.0 for exact business ID and country match")
        void shouldReturnPerfectScoreForExactMatch() {
            Business query = new Business("Acme Corp", List.of(), null, null,
                List.of(new GovernmentId(GovernmentIdType.TAX_ID, "12-3456789", "US")));
            Business index = new Business("Acme Corp", List.of(), null, null,
                List.of(new GovernmentId(GovernmentIdType.TAX_ID, "12-3456789", "US")));
            
            IdMatchResult result = ExactIdMatcher.compareBusinessGovernmentIDs(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
            assertTrue(result.exact());
        }
        
        @Test
        @DisplayName("should apply country penalties same as Person")
        void shouldApplyCountryPenalties() {
            // Different countries: 0.7 score
            Business query = new Business("Acme Corp", List.of(), null, null,
                List.of(new GovernmentId(GovernmentIdType.TAX_ID, "12-3456789", "US")));
            Business index = new Business("Acme Corp", List.of(), null, null,
                List.of(new GovernmentId(GovernmentIdType.TAX_ID, "12-3456789", "UK")));
            
            IdMatchResult result = ExactIdMatcher.compareBusinessGovernmentIDs(query, index, 2.0);
            
            assertEquals(0.7, result.score(), 0.001);
            assertTrue(result.matched());
        }
    }
    
    @Nested
    @DisplayName("compareOrgGovernmentIDs() - Organization government ID matching")
    class CompareOrgGovernmentIDsTests {
        
        @Test
        @DisplayName("should return 1.0 for exact org ID and country match")
        void shouldReturnPerfectScoreForExactMatch() {
            Organization query = new Organization("UN", List.of(), null, null,
                List.of(new GovernmentId(GovernmentIdType.TAX_ID, "ORG-12345", "US")));
            Organization index = new Organization("UN", List.of(), null, null,
                List.of(new GovernmentId(GovernmentIdType.TAX_ID, "ORG-12345", "US")));
            
            IdMatchResult result = ExactIdMatcher.compareOrgGovernmentIDs(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
            assertTrue(result.exact());
        }
        
        @Test
        @DisplayName("should apply country penalties same as Person and Business")
        void shouldApplyCountryPenalties() {
            // One country missing: 0.9 score
            Organization query = new Organization("UN", List.of(), null, null,
                List.of(new GovernmentId(GovernmentIdType.TAX_ID, "ORG-12345", null)));
            Organization index = new Organization("UN", List.of(), null, null,
                List.of(new GovernmentId(GovernmentIdType.TAX_ID, "ORG-12345", "US")));
            
            IdMatchResult result = ExactIdMatcher.compareOrgGovernmentIDs(query, index, 2.0);
            
            assertEquals(0.9, result.score(), 0.001);
            assertTrue(result.matched());
        }
    }
    
    @Nested
    @DisplayName("compareCryptoAddresses() - Cryptocurrency address exact matching")
    class CompareCryptoAddressesTests {
        
        @Test
        @DisplayName("should return 1.0 for exact crypto address match with same currency")
        void shouldMatchExactAddressAndCurrency() {
            List<CryptoAddress> query = List.of(new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"));
            List<CryptoAddress> index = List.of(new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"));
            
            IdMatchResult result = ExactIdMatcher.compareCryptoAddresses(query, index, 2.0);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
            assertTrue(result.exact());
        }
        
        @Test
        @DisplayName("should match address when currency matches")
        void shouldMatchWhenCurrencyMatches() {
            List<CryptoAddress> query = List.of(new CryptoAddress("ETH", "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb"));
            List<CryptoAddress> index = List.of(new CryptoAddress("ETH", "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb"));
            
            IdMatchResult result = ExactIdMatcher.compareCryptoAddresses(query, index, 2.0);
            
            assertTrue(result.matched());
            assertEquals(1.0, result.score(), 0.001);
        }
        
        @Test
        @DisplayName("should NOT match when address matches but currency differs")
        void shouldNotMatchWhenCurrencyDiffers() {
            List<CryptoAddress> query = List.of(new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"));
            List<CryptoAddress> index = List.of(new CryptoAddress("ETH", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"));
            
            IdMatchResult result = ExactIdMatcher.compareCryptoAddresses(query, index, 2.0);
            
            assertEquals(0.0, result.score());
            assertFalse(result.matched());
        }
        
        @Test
        @DisplayName("should match address when one currency is empty")
        void shouldMatchWhenOneCurrencyEmpty() {
            List<CryptoAddress> query = List.of(new CryptoAddress("", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"));
            List<CryptoAddress> index = List.of(new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"));
            
            IdMatchResult result = ExactIdMatcher.compareCryptoAddresses(query, index, 2.0);
            
            assertTrue(result.matched());
            assertEquals(1.0, result.score(), 0.001);
        }
        
        @Test
        @DisplayName("should match address when both currencies are empty")
        void shouldMatchWhenBothCurrenciesEmpty() {
            List<CryptoAddress> query = List.of(new CryptoAddress("", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"));
            List<CryptoAddress> index = List.of(new CryptoAddress("", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"));
            
            IdMatchResult result = ExactIdMatcher.compareCryptoAddresses(query, index, 2.0);
            
            assertTrue(result.matched());
            assertEquals(1.0, result.score(), 0.001);
        }
        
        @Test
        @DisplayName("should return 0 when no crypto addresses present")
        void shouldReturnZeroWhenNoAddresses() {
            List<CryptoAddress> query = List.of();
            List<CryptoAddress> index = List.of();
            
            IdMatchResult result = ExactIdMatcher.compareCryptoAddresses(query, index, 2.0);
            
            assertEquals(0.0, result.score());
            assertFalse(result.matched());
            assertEquals(0, result.fieldsCompared());
        }
        
        @Test
        @DisplayName("should skip empty addresses")
        void shouldSkipEmptyAddresses() {
            List<CryptoAddress> query = List.of(
                new CryptoAddress("BTC", ""),
                new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
            );
            List<CryptoAddress> index = List.of(
                new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
            );
            
            IdMatchResult result = ExactIdMatcher.compareCryptoAddresses(query, index, 2.0);
            
            assertTrue(result.matched());
            assertEquals(1.0, result.score(), 0.001);
        }
        
        @Test
        @DisplayName("should compare case-insensitively")
        void shouldCompareCaseInsensitively() {
            List<CryptoAddress> query = List.of(new CryptoAddress("btc", "1a1zp1ep5qgefi2dmptftl5slmv7divfna"));
            List<CryptoAddress> index = List.of(new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"));
            
            IdMatchResult result = ExactIdMatcher.compareCryptoAddresses(query, index, 2.0);
            
            assertTrue(result.matched());
            assertEquals(1.0, result.score(), 0.001);
        }
    }
    
    @Nested
    @DisplayName("normalizeIdentifier() - Hyphen removal for ID normalization")
    class NormalizeIdentifierTests {
        
        @Test
        @DisplayName("should remove hyphens from identifier")
        void shouldRemoveHyphens() {
            String normalized = ExactIdMatcher.normalizeIdentifier("12-34-56");
            assertEquals("123456", normalized);
        }
        
        @Test
        @DisplayName("should handle identifier without hyphens")
        void shouldHandleNoHyphens() {
            String normalized = ExactIdMatcher.normalizeIdentifier("123456");
            assertEquals("123456", normalized);
        }
        
        @Test
        @DisplayName("should handle empty string")
        void shouldHandleEmptyString() {
            String normalized = ExactIdMatcher.normalizeIdentifier("");
            assertEquals("", normalized);
        }
        
        @Test
        @DisplayName("should remove multiple consecutive hyphens")
        void shouldRemoveMultipleHyphens() {
            String normalized = ExactIdMatcher.normalizeIdentifier("12--34--56");
            assertEquals("123456", normalized);
        }
    }
    
    @Nested
    @DisplayName("compareIdentifiers() - Core ID comparison with country validation")
    class CompareIdentifiersTests {
        
        @Test
        @DisplayName("should return score 1.0 and exact=true for perfect match")
        void shouldReturnPerfectMatchForExactMatch() {
            IdComparison result = ExactIdMatcher.compareIdentifiers("A123456", "A123456", "US", "US");
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.found());
            assertTrue(result.exact());
            assertTrue(result.hasCountry());
        }
        
        @Test
        @DisplayName("should return 0 when identifiers don't match")
        void shouldReturnZeroWhenNoMatch() {
            IdComparison result = ExactIdMatcher.compareIdentifiers("A123456", "B999999", "US", "US");
            
            assertEquals(0.0, result.score());
            assertFalse(result.found());
            assertFalse(result.exact());
        }
        
        @Test
        @DisplayName("should return score 0.9 when one country is missing")
        void shouldPenalizeOneCountryMissing() {
            IdComparison result1 = ExactIdMatcher.compareIdentifiers("A123456", "A123456", "", "US");
            IdComparison result2 = ExactIdMatcher.compareIdentifiers("A123456", "A123456", "US", "");
            
            assertEquals(0.9, result1.score(), 0.001);
            assertTrue(result1.found());
            assertFalse(result1.exact());
            assertTrue(result1.hasCountry());
            
            assertEquals(0.9, result2.score(), 0.001);
        }
        
        @Test
        @DisplayName("should return score 0.7 when countries differ")
        void shouldPenalizeDifferentCountries() {
            IdComparison result = ExactIdMatcher.compareIdentifiers("A123456", "A123456", "US", "UK");
            
            assertEquals(0.7, result.score(), 0.001);
            assertTrue(result.found());
            assertFalse(result.exact());
            assertTrue(result.hasCountry());
        }
        
        @Test
        @DisplayName("should return score 1.0 with hasCountry=false when both countries empty")
        void shouldMatchWithoutCountries() {
            IdComparison result = ExactIdMatcher.compareIdentifiers("A123456", "A123456", "", "");
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.found());
            assertTrue(result.exact());
            assertFalse(result.hasCountry());
        }
        
        @Test
        @DisplayName("should compare identifiers case-insensitively")
        void shouldCompareCaseInsensitively() {
            IdComparison result = ExactIdMatcher.compareIdentifiers("a123456", "A123456", "us", "US");
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.found());
            assertTrue(result.exact());
        }
        
        @Test
        @DisplayName("should handle null countries as empty strings")
        void shouldHandleNullCountries() {
            IdComparison result = ExactIdMatcher.compareIdentifiers("A123456", "A123456", null, null);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.found());
            assertFalse(result.hasCountry());
        }
    }
}
