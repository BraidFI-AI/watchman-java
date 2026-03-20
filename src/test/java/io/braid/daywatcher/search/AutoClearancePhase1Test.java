package io.braid.daywatcher.search;

import io.braid.daywatcher.model.Entity;
import io.braid.daywatcher.model.EntityType;
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
 * TDD Test Suite for Auto-Clearance Phase 1: Name-Based Detection
 * 
 * Phase 1 should:
 * - Return entities with nameScore ≥ 85% (configurable threshold)
 * - Use name-only scoring (ignore address/DOB/ID in Phase 1)
 * - Mark results with status "NAME_MATCH"
 */
@SpringBootTest
@DisplayName("Auto-Clearance Phase 1: Name-Based Detection")
class AutoClearancePhase1Test {

    @Autowired
    private SearchService searchService;

    @Autowired
    private io.braid.daywatcher.index.EntityIndex entityIndex;

    private Entity johnSmithEntity;
    private Entity johnDoeEntity;
    private Entity muhammadAliEntity;

    @BeforeEach
    void setUp() {
        // Clear index before each test
        entityIndex.clear();

        // Index test entities with known names (Phase 1 only needs names)
        johnSmithEntity = Entity.of("SDN-001", "SMITH, John Michael", EntityType.PERSON, SourceList.US_OFAC);
        johnDoeEntity = Entity.of("SDN-002", "DOE, John", EntityType.PERSON, SourceList.US_OFAC);
        muhammadAliEntity = Entity.of("SDN-003", "ALI, Muhammad Hassan", EntityType.PERSON, SourceList.US_OFAC);

        entityIndex.addAll(List.of(johnSmithEntity, johnDoeEntity, muhammadAliEntity));
    }

    @Test
    @DisplayName("Phase 1: Should return entities with nameScore ≥ 85%")
    void testPhase1IncludesHighScoringMatches() {
        // Arrange
        String queryName = "John Smith";

        // Act
        AutoClearanceResponse response = searchService.searchWithAutoClearance(queryName);

        // Assert: "John Smith" should match "SMITH, John Michael" with nameScore ≥ 85%
        assertThat(response.getResults())
            .isNotEmpty()
            .anySatisfy(result -> {
                assertThat(result.getEntityId()).isEqualTo("SDN-001");
                assertThat(result.getPhase1().getNameScore()).isGreaterThanOrEqualTo(0.85);
                assertThat(result.getPhase1().getStatus()).isEqualTo("NAME_MATCH");
            });
    }

    @Test
    @DisplayName("Phase 1: Should exclude entities with nameScore < 85%")
    void testPhase1ExcludesLowScoringMatches() {
        // Arrange
        String queryName = "John Smith";

        // Act
        AutoClearanceResponse response = searchService.searchWithAutoClearance(queryName);

        // Assert: "Muhammad Ali" should NOT match "John Smith" (nameScore < 85%)
        assertThat(response.getResults())
            .noneSatisfy(result -> 
                assertThat(result.getEntityId()).isEqualTo("SDN-003")
            );

        // Verify summary counts
        assertThat(response.getSummary().getPhase1Matches()).isLessThan(3);
    }

    @Test
    @DisplayName("Phase 1: Should ignore address/DOB/ID in name-only detection")
    void testPhase1IgnoresNonNameFactors() {
        // Arrange
        String queryName = "John Smith";
        String queryAddress = "999 Wrong Street, Wrong City, XX 00000"; // Completely different
        LocalDate queryDob = LocalDate.of(1990, 1, 1); // Different from entity (1975-06-15)

        // Act
        AutoClearanceResponse response = searchService.searchWithAutoClearance(
            queryName, 
            queryAddress, 
            queryDob, 
            null // no gov ID
        );

        // Assert: "SMITH, John Michael" should still match based on name alone
        // Address/DOB mismatch should NOT affect Phase 1 scoring
        assertThat(response.getResults())
            .isNotEmpty()
            .anySatisfy(result -> {
                assertThat(result.getEntityId()).isEqualTo("SDN-001");
                assertThat(result.getPhase1().getNameScore()).isGreaterThanOrEqualTo(0.85);
                assertThat(result.getPhase1().getStatus()).isEqualTo("NAME_MATCH");
                
                // Phase 2 auto-clearance is now active, but entity has no address
                // So it returns PENDING with reason about entity address not available
                assertThat(result.getAutoClearance()).isNotNull();
                assertThat(result.getAutoClearance().getStatus()).isEqualTo("PENDING");
                assertThat(result.getAutoClearance().getReason()).contains("No entity address");
            });
    }
}
