package io.braid.daywatcher.search;

import io.braid.daywatcher.model.Entity;
import io.braid.daywatcher.model.EntityType;
import io.braid.daywatcher.model.Person;
import io.braid.daywatcher.model.SourceList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD Test Suite for Auto-Clearance Phase 2: Date of Birth Auto-Clearance
 * 
 * Phase 2 DOB Clearance should:
 * - Auto-clear matches when query DOB differs by more than 1 year from entity DOB
 * - Keep matches for manual review when DOBs match or differ by ≤ 1 year
 * - Skip clearance when no query DOB provided
 */
@SpringBootTest
@DisplayName("Auto-Clearance Phase 2: Date of Birth Clearance")
class AutoClearancePhase2DobTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private io.braid.daywatcher.index.EntityIndex entityIndex;

    private Entity johnSmith1985;
    private Entity johnSmith1990;
    private Entity johnSmithNoDob;

    @BeforeEach
    void setUp() {
        entityIndex.clear();

        // Entity 1: John Smith born June 15, 1985
        Person person1985 = new Person(
            "SMITH, John Michael", List.of(), "male", 
            LocalDate.of(1985, 6, 15), null, null,
            List.of(), List.of()
        );
        johnSmith1985 = new Entity(
            "SDN-201", "SMITH, John Michael", EntityType.PERSON, SourceList.US_OFAC, "SDN-201",
            person1985, null, null, null, null,
            null, List.of(), List.of(), List.of(), List.of(),
            null, List.of(), null, null,
            null, null, null, null, null
        );

        // Entity 2: John Smith born March 20, 1990 (5 years different)
        Person person1990 = new Person(
            "SMITH, John Michael", List.of(), "male",
            LocalDate.of(1990, 3, 20), null, null,
            List.of(), List.of()
        );
        johnSmith1990 = new Entity(
            "SDN-202", "SMITH, John Michael", EntityType.PERSON, SourceList.US_OFAC, "SDN-202",
            person1990, null, null, null, null,
            null, List.of(), List.of(), List.of(), List.of(),
            null, List.of(), null, null,
            null, null, null, null, null
        );

        // Entity 3: John Smith with no DOB
        johnSmithNoDob = Entity.of("SDN-203", "SMITH, John Michael", EntityType.PERSON, SourceList.US_OFAC);

        entityIndex.addAll(List.of(johnSmith1985, johnSmith1990, johnSmithNoDob));
    }

    @Test
    @DisplayName("Phase 2: Should auto-clear when DOB differs by > 1 year")
    void testAutoClearOnDobMismatch() {
        // Arrange
        String queryName = "John Smith";
        LocalDate queryDob = LocalDate.of(1985, 6, 15);

        // Act
        AutoClearanceResponse response = searchService.searchWithAutoClearance(
            queryName, null, queryDob, null
        );

        // Assert: SDN-202 (1990 DOB) should be auto-cleared (5 years different)
        assertThat(response.getResults())
            .anySatisfy(result -> {
                if (result.getEntityId().equals("SDN-202")) {
                    assertThat(result.getAutoClearance()).isNotNull();
                    assertThat(result.getAutoClearance().getStatus()).isEqualTo("AUTO_CLEARED");
                    assertThat(result.getAutoClearance().getReason()).contains("Date of birth mismatch");
                    assertThat(result.getFinalStatus()).isEqualTo("AUTO_CLEARED");
                    
                    // Verify discriminator details
                    assertThat(result.getAutoClearance().getDiscriminators().getDob()).isNotNull();
                    assertThat(result.getAutoClearance().getDiscriminators().getDob().isMatched()).isFalse();
                }
            });

        // Verify summary counts
        assertThat(response.getSummary().getAutoClearedMatches()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Phase 2: Should keep for manual review when DOB matches exactly")
    void testKeepForManualReviewOnDobMatch() {
        // Arrange
        String queryName = "John Smith";
        LocalDate queryDob = LocalDate.of(1985, 6, 15);

        // Act
        AutoClearanceResponse response = searchService.searchWithAutoClearance(
            queryName, null, queryDob, null
        );

        // Assert: SDN-201 (exact DOB match) should remain for manual review
        assertThat(response.getResults())
            .anySatisfy(result -> {
                if (result.getEntityId().equals("SDN-201")) {
                    // Should NOT be auto-cleared
                    if (result.getAutoClearance() != null) {
                        assertThat(result.getAutoClearance().getStatus()).isNotEqualTo("AUTO_CLEARED");
                    }
                    assertThat(result.getFinalStatus()).isEqualTo("REQUIRES_MANUAL_REVIEW");
                    
                    // DOB should match
                    if (result.getAutoClearance() != null && 
                        result.getAutoClearance().getDiscriminators().getDob() != null) {
                        assertThat(result.getAutoClearance().getDiscriminators().getDob().isMatched()).isTrue();
                    }
                }
            });

        // Verify some matches require manual review
        assertThat(response.getSummary().getManualReviewRequired()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Phase 2: Should keep for manual review when DOB differs by ≤ 1 year")
    void testKeepForManualReviewOnCloseMatch() {
        // Arrange
        String queryName = "John Smith";
        LocalDate queryDob = LocalDate.of(1985, 9, 20); // 3 months off from SDN-201

        // Act
        AutoClearanceResponse response = searchService.searchWithAutoClearance(
            queryName, null, queryDob, null
        );

        // Assert: SDN-201 (within 1 year) should remain for manual review
        assertThat(response.getResults())
            .anySatisfy(result -> {
                if (result.getEntityId().equals("SDN-201")) {
                    // Should NOT be auto-cleared (within threshold)
                    if (result.getAutoClearance() != null) {
                        assertThat(result.getAutoClearance().getStatus()).isNotEqualTo("AUTO_CLEARED");
                    }
                    assertThat(result.getFinalStatus()).isEqualTo("REQUIRES_MANUAL_REVIEW");
                }
            });
    }

    @Test
    @DisplayName("Phase 2: Should skip clearance when no query DOB provided")
    void testSkipClearanceWhenNoQueryDob() {
        // Arrange
        String queryName = "John Smith";
        LocalDate queryDob = null; // No DOB provided

        // Act
        AutoClearanceResponse response = searchService.searchWithAutoClearance(
            queryName, null, queryDob, null
        );

        // Assert: All matches should remain for manual review (no clearance data)
        assertThat(response.getResults())
            .allSatisfy(result -> {
                // Without DOB, cannot auto-clear
                assertThat(result.getFinalStatus()).isEqualTo("REQUIRES_MANUAL_REVIEW");
                
                // If autoClearance exists, reason should indicate no discriminating data
                if (result.getAutoClearance() != null) {
                    assertThat(result.getAutoClearance().getStatus()).isNotEqualTo("AUTO_CLEARED");
                }
            });

        // Summary: No auto-cleared matches when no DOB provided
        assertThat(response.getSummary().getAutoClearedMatches()).isEqualTo(0);
        assertThat(response.getSummary().getManualReviewRequired())
            .isEqualTo(response.getSummary().getPhase1Matches());
    }

    @Test
    @DisplayName("Phase 2: Should skip clearance when entity has no DOB")
    void testSkipClearanceWhenEntityNoDob() {
        // Arrange
        String queryName = "John Smith";
        LocalDate queryDob = LocalDate.of(1985, 6, 15);

        // Act
        AutoClearanceResponse response = searchService.searchWithAutoClearance(
            queryName, null, queryDob, null
        );

        // Assert: SDN-203 (no entity DOB) should remain for manual review
        assertThat(response.getResults())
            .anySatisfy(result -> {
                if (result.getEntityId().equals("SDN-203")) {
                    assertThat(result.getFinalStatus()).isEqualTo("REQUIRES_MANUAL_REVIEW");
                    
                    if (result.getAutoClearance() != null) {
                        assertThat(result.getAutoClearance().getStatus()).isEqualTo("PENDING");
                        assertThat(result.getAutoClearance().getReason())
                            .contains("No entity date of birth");
                    }
                }
            });
    }
}
