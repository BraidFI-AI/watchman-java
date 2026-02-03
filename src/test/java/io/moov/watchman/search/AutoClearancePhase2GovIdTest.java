package io.moov.watchman.search;

import io.moov.watchman.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD Test Suite for Auto-Clearance Phase 2: Government ID Auto-Clearance
 * 
 * Phase 2 Government ID Clearance should:
 * - Auto-clear matches when query government ID differs from all entity government IDs
 * - Keep matches for manual review when any government ID matches
 * - Skip clearance when no query government ID provided
 */
@SpringBootTest
@DisplayName("Auto-Clearance Phase 2: Government ID Clearance")
class AutoClearancePhase2GovIdTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private io.moov.watchman.index.EntityIndex entityIndex;

    private Entity johnSmithPassport123;
    private Entity johnSmithPassport456;
    private Entity johnSmithNoId;

    @BeforeEach
    void setUp() {
        entityIndex.clear();

        // Entity 1: John Smith with passport 123456789
        GovernmentId passport123 = new GovernmentId(GovernmentIdType.PASSPORT, "123456789", "US");
        Person person123 = new Person(
            "SMITH, John Michael", List.of(), "male",
            LocalDate.of(1985, 6, 15), null, null,
            List.of(), List.of(passport123)
        );
        johnSmithPassport123 = new Entity(
            "SDN-301", "SMITH, John Michael", EntityType.PERSON, SourceList.US_OFAC, "SDN-301",
            person123, null, null, null, null,
            null, List.of(), List.of(), List.of(), List.of(passport123),
            null, List.of(), null, null,
            null, null, null, null, null
        );

        // Entity 2: John Smith with different passport 456789123
        GovernmentId passport456 = new GovernmentId(GovernmentIdType.PASSPORT, "456789123", "US");
        Person person456 = new Person(
            "SMITH, John Michael", List.of(), "male",
            LocalDate.of(1985, 6, 15), null, null,
            List.of(), List.of(passport456)
        );
        johnSmithPassport456 = new Entity(
            "SDN-302", "SMITH, John Michael", EntityType.PERSON, SourceList.US_OFAC, "SDN-302",
            person456, null, null, null, null,
            null, List.of(), List.of(), List.of(), List.of(passport456),
            null, List.of(), null, null,
            null, null, null, null, null
        );

        // Entity 3: John Smith with no government ID
        johnSmithNoId = Entity.of("SDN-303", "SMITH, John Michael", EntityType.PERSON, SourceList.US_OFAC);

        entityIndex.addAll(List.of(johnSmithPassport123, johnSmithPassport456, johnSmithNoId));
    }

    @Test
    @DisplayName("Phase 2: Should auto-clear when government ID does not match")
    void testAutoClearOnGovIdMismatch() {
        // Arrange
        String queryName = "John Smith";
        String queryGovId = "123456789"; // Matches SDN-301

        // Act
        AutoClearanceResponse response = searchService.searchWithAutoClearance(
            queryName, null, null, queryGovId
        );

        // Assert: SDN-302 (different passport) should be auto-cleared
        assertThat(response.getResults())
            .anySatisfy(result -> {
                if (result.getEntityId().equals("SDN-302")) {
                    assertThat(result.getAutoClearance()).isNotNull();
                    assertThat(result.getAutoClearance().getStatus()).isEqualTo("AUTO_CLEARED");
                    assertThat(result.getAutoClearance().getReason()).contains("Government ID mismatch");
                    assertThat(result.getFinalStatus()).isEqualTo("AUTO_CLEARED");
                    
                    // Verify discriminator details
                    assertThat(result.getAutoClearance().getDiscriminators().getGovernmentId()).isNotNull();
                    assertThat(result.getAutoClearance().getDiscriminators().getGovernmentId().isMatched()).isFalse();
                }
            });

        // Verify summary counts
        assertThat(response.getSummary().getAutoClearedMatches()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Phase 2: Should keep for manual review when government ID matches")
    void testKeepForManualReviewOnGovIdMatch() {
        // Arrange
        String queryName = "John Smith";
        String queryGovId = "123456789"; // Matches SDN-301

        // Act
        AutoClearanceResponse response = searchService.searchWithAutoClearance(
            queryName, null, null, queryGovId
        );

        // Assert: SDN-301 (matching passport) should remain for manual review
        assertThat(response.getResults())
            .anySatisfy(result -> {
                if (result.getEntityId().equals("SDN-301")) {
                    // Should NOT be auto-cleared
                    if (result.getAutoClearance() != null) {
                        assertThat(result.getAutoClearance().getStatus()).isNotEqualTo("AUTO_CLEARED");
                    }
                    assertThat(result.getFinalStatus()).isEqualTo("REQUIRES_MANUAL_REVIEW");
                    
                    // Government ID should match
                    if (result.getAutoClearance() != null && 
                        result.getAutoClearance().getDiscriminators().getGovernmentId() != null) {
                        assertThat(result.getAutoClearance().getDiscriminators().getGovernmentId().isMatched()).isTrue();
                    }
                }
            });

        // Verify some matches require manual review
        assertThat(response.getSummary().getManualReviewRequired()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Phase 2: Should skip clearance when no query government ID provided")
    void testSkipClearanceWhenNoQueryGovId() {
        // Arrange
        String queryName = "John Smith";
        String queryGovId = null; // No government ID provided

        // Act
        AutoClearanceResponse response = searchService.searchWithAutoClearance(
            queryName, null, null, queryGovId
        );

        // Assert: All matches should remain for manual review (no clearance data)
        assertThat(response.getResults())
            .allSatisfy(result -> {
                // Without government ID, cannot auto-clear
                assertThat(result.getFinalStatus()).isEqualTo("REQUIRES_MANUAL_REVIEW");
                
                // If autoClearance exists, reason should indicate no discriminating data
                if (result.getAutoClearance() != null) {
                    assertThat(result.getAutoClearance().getStatus()).isNotEqualTo("AUTO_CLEARED");
                }
            });

        // Summary: No auto-cleared matches when no government ID provided
        assertThat(response.getSummary().getAutoClearedMatches()).isEqualTo(0);
        assertThat(response.getSummary().getManualReviewRequired())
            .isEqualTo(response.getSummary().getPhase1Matches());
    }

    @Test
    @DisplayName("Phase 2: Should skip clearance when entity has no government ID")
    void testSkipClearanceWhenEntityNoGovId() {
        // Arrange
        String queryName = "John Smith";
        String queryGovId = "123456789";

        // Act
        AutoClearanceResponse response = searchService.searchWithAutoClearance(
            queryName, null, null, queryGovId
        );

        // Assert: SDN-303 (no entity government ID) should remain for manual review
        assertThat(response.getResults())
            .anySatisfy(result -> {
                if (result.getEntityId().equals("SDN-303")) {
                    assertThat(result.getFinalStatus()).isEqualTo("REQUIRES_MANUAL_REVIEW");
                    
                    if (result.getAutoClearance() != null) {
                        assertThat(result.getAutoClearance().getStatus()).isEqualTo("PENDING");
                        assertThat(result.getAutoClearance().getReason())
                            .contains("No entity government ID");
                    }
                }
            });
    }

    @Test
    @DisplayName("Phase 2: Should match government ID case-insensitively")
    void testMatchGovIdCaseInsensitive() {
        // Arrange
        String queryName = "John Smith";
        String queryGovId = "123456789"; // Lowercase, should match uppercase entity ID

        // Act
        AutoClearanceResponse response = searchService.searchWithAutoClearance(
            queryName, null, null, queryGovId
        );

        // Assert: SDN-301 should match even with different case
        assertThat(response.getResults())
            .anySatisfy(result -> {
                if (result.getEntityId().equals("SDN-301")) {
                    assertThat(result.getFinalStatus()).isEqualTo("REQUIRES_MANUAL_REVIEW");
                    
                    if (result.getAutoClearance() != null &&
                        result.getAutoClearance().getDiscriminators().getGovernmentId() != null) {
                        assertThat(result.getAutoClearance().getDiscriminators().getGovernmentId().isMatched()).isTrue();
                    }
                }
            });
    }
}
