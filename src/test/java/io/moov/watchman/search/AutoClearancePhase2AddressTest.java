package io.moov.watchman.search;

import io.moov.watchman.model.Address;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SourceList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD Test Suite for Auto-Clearance Phase 2: Address-Based Auto-Clearance
 * 
 * Phase 2 Address Clearance should:
 * - Auto-clear matches when query address differs significantly from entity address (score < 50%)
 * - Keep matches for manual review when addresses are similar (score >= 50%)
 * - Skip clearance when no query address provided
 */
@SpringBootTest
@DisplayName("Auto-Clearance Phase 2: Address-Based Clearance")
class AutoClearancePhase2AddressTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private io.moov.watchman.index.EntityIndex entityIndex;

    private Entity johnSmithNYC;
    private Entity johnSmithLA;
    private Entity johnSmithNoAddress;

    @BeforeEach
    void setUp() {
        entityIndex.clear();

        // Entity 1: John Smith in New York City
        Address nycAddress = new Address("123 Broadway", "Apt 5B", "New York", "NY", "10001", "US");
        johnSmithNYC = new Entity(
            "SDN-101", "SMITH, John Michael", EntityType.PERSON, SourceList.US_OFAC, "SDN-101",
            null, null, null, null, null,
            null, List.of(nycAddress), List.of(), List.of(), List.of(),
            null, List.of(), null, null,
            null, null, null, null, null
        );

        // Entity 2: John Smith in Los Angeles (different address)
        Address laAddress = new Address("456 Sunset Blvd", null, "Los Angeles", "CA", "90028", "US");
        johnSmithLA = new Entity(
            "SDN-102", "SMITH, John Michael", EntityType.PERSON, SourceList.US_OFAC, "SDN-102",
            null, null, null, null, null,
            null, List.of(laAddress), List.of(), List.of(), List.of(),
            null, List.of(), null, null,
            null, null, null, null, null
        );

        // Entity 3: John Smith with no address
        johnSmithNoAddress = Entity.of("SDN-103", "SMITH, John Michael", EntityType.PERSON, SourceList.US_OFAC);

        entityIndex.addAll(List.of(johnSmithNYC, johnSmithLA, johnSmithNoAddress));
    }

    @Test
    @DisplayName("Phase 2: Should auto-clear when address score < 50%")
    void testAutoClearOnAddressMismatch() {
        // Arrange
        String queryName = "John Smith";
        String queryAddress = "123 Broadway, New York, NY 10001";

        // Act
        AutoClearanceResponse response = searchService.searchWithAutoClearance(
            queryName, queryAddress, null, null
        );

        // Assert: SDN-102 (LA address) should be auto-cleared (different city)
        assertThat(response.getResults())
            .anySatisfy(result -> {
                if (result.getEntityId().equals("SDN-102")) {
                    assertThat(result.getAutoClearance()).isNotNull();
                    assertThat(result.getAutoClearance().getStatus()).isEqualTo("AUTO_CLEARED");
                    assertThat(result.getAutoClearance().getReason()).contains("Address mismatch");
                    assertThat(result.getFinalStatus()).isEqualTo("AUTO_CLEARED");
                    
                    // Verify discriminator details
                    assertThat(result.getAutoClearance().getDiscriminators().getAddress()).isNotNull();
                    assertThat(result.getAutoClearance().getDiscriminators().getAddress().getScore())
                        .isLessThan(0.50);
                }
            });

        // Verify summary counts
        assertThat(response.getSummary().getAutoClearedMatches()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Phase 2: Should keep for manual review when address score >= 50%")
    void testKeepForManualReviewOnAddressMatch() {
        // Arrange
        String queryName = "John Smith";
        String queryAddress = "123 Broadway, New York, NY 10001";

        // Act
        AutoClearanceResponse response = searchService.searchWithAutoClearance(
            queryName, queryAddress, null, null
        );

        // Assert: SDN-101 (NYC address) should remain for manual review (same address)
        assertThat(response.getResults())
            .anySatisfy(result -> {
                if (result.getEntityId().equals("SDN-101")) {
                    // Should NOT be auto-cleared
                    if (result.getAutoClearance() != null) {
                        assertThat(result.getAutoClearance().getStatus()).isNotEqualTo("AUTO_CLEARED");
                    }
                    assertThat(result.getFinalStatus()).isEqualTo("REQUIRES_MANUAL_REVIEW");
                    
                    // Address score should be >= 50%
                    if (result.getAutoClearance() != null && 
                        result.getAutoClearance().getDiscriminators().getAddress() != null) {
                        assertThat(result.getAutoClearance().getDiscriminators().getAddress().getScore())
                            .isGreaterThanOrEqualTo(0.50);
                    }
                }
            });

        // Verify some matches require manual review
        assertThat(response.getSummary().getManualReviewRequired()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Phase 2: Should skip clearance when no query address provided")
    void testSkipClearanceWhenNoQueryAddress() {
        // Arrange
        String queryName = "John Smith";
        String queryAddress = null; // No address provided

        // Act
        AutoClearanceResponse response = searchService.searchWithAutoClearance(
            queryName, queryAddress, null, null
        );

        // Assert: All matches should remain for manual review (no clearance data)
        assertThat(response.getResults())
            .allSatisfy(result -> {
                // Without address, cannot auto-clear
                assertThat(result.getFinalStatus()).isEqualTo("REQUIRES_MANUAL_REVIEW");
                
                // If autoClearance exists, reason should indicate no discriminating data
                if (result.getAutoClearance() != null) {
                    assertThat(result.getAutoClearance().getStatus()).isNotEqualTo("AUTO_CLEARED");
                }
            });

        // Summary: No auto-cleared matches when no address provided
        assertThat(response.getSummary().getAutoClearedMatches()).isEqualTo(0);
        assertThat(response.getSummary().getManualReviewRequired())
            .isEqualTo(response.getSummary().getPhase1Matches());
    }
}
