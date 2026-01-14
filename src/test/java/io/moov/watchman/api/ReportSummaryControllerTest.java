package io.moov.watchman.api;

import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.report.TraceSummaryService;
import io.moov.watchman.report.model.ReportSummary;
import io.moov.watchman.trace.Phase;
import io.moov.watchman.trace.ScoringEvent;
import io.moov.watchman.trace.ScoringTrace;
import io.moov.watchman.trace.TraceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TDD Tests for Report Summary endpoint.
 * RED Phase: These tests define the desired behavior for score trace summaries.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportSummary Endpoint TDD Tests")
class ReportSummaryControllerTest {

    @Mock
    private TraceRepository traceRepository;

    @Mock
    private TraceSummaryService summaryService;

    private ReportController controller;

    @BeforeEach
    void setUp() {
        controller = new ReportController(traceRepository, null, summaryService);
    }

    @Nested
    @DisplayName("GET /api/reports/{sessionId}/summary")
    class GetSummaryTests {

        @Test
        @DisplayName("Should return JSON summary with overall statistics")
        void shouldReturnJsonSummaryWithOverallStatistics() {
            // Given: A trace with multiple entities scored
            String sessionId = "test-session-123";
            ScoringTrace trace = createMultiEntityTrace(sessionId, 5);
            
            ReportSummary expectedSummary = ReportSummary.builder()
                .sessionId(sessionId)
                .totalEntitiesScored(5)
                .averageScore(0.75)
                .highestScore(0.95)
                .lowestScore(0.45)
                .totalDurationMs(123L)
                .build();
            
            when(traceRepository.findBySessionId(sessionId)).thenReturn(Optional.of(trace));
            when(summaryService.generateSummary(trace)).thenReturn(expectedSummary);

            // When: Request summary
            ResponseEntity<ReportSummary> response = controller.getSummary(sessionId);

            // Then: Returns 200 with summary data
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().totalEntitiesScored()).isEqualTo(5);
            assertThat(response.getBody().averageScore()).isEqualTo(0.75);
            assertThat(response.getBody().highestScore()).isEqualTo(0.95);
            assertThat(response.getBody().lowestScore()).isEqualTo(0.45);
        }

        @Test
        @DisplayName("Should return 404 when session ID not found")
        void shouldReturn404WhenSessionIdNotFound() {
            // Given: Session doesn't exist
            String sessionId = "non-existent";
            when(traceRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

            // When: Request summary
            ResponseEntity<ReportSummary> response = controller.getSummary(sessionId);

            // Then: Returns 404
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Should include phase contribution breakdown")
        void shouldIncludePhaseContributionBreakdown() {
            // Given: Trace with varied phase contributions
            String sessionId = "test-session-123";
            ScoringTrace trace = createMultiEntityTrace(sessionId, 3);
            
            Map<Phase, Double> phaseContributions = Map.of(
                Phase.NAME_COMPARISON, 0.45,  // 45% weight
                Phase.ALT_NAME_COMPARISON, 0.30,  // 30% weight
                Phase.ADDRESS_COMPARISON, 0.15,  // 15% weight
                Phase.GOV_ID_COMPARISON, 0.10   // 10% weight
            );
            
            ReportSummary expectedSummary = ReportSummary.builder()
                .sessionId(sessionId)
                .totalEntitiesScored(3)
                .phaseContributions(phaseContributions)
                .build();
            
            when(traceRepository.findBySessionId(sessionId)).thenReturn(Optional.of(trace));
            when(summaryService.generateSummary(trace)).thenReturn(expectedSummary);

            // When: Request summary
            ResponseEntity<ReportSummary> response = controller.getSummary(sessionId);

            // Then: Phase contributions are included
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().phaseContributions()).isNotNull();
            assertThat(response.getBody().phaseContributions().get(Phase.NAME_COMPARISON)).isEqualTo(0.45);
            assertThat(response.getBody().phaseContributions().get(Phase.ALT_NAME_COMPARISON)).isEqualTo(0.30);
        }

        @Test
        @DisplayName("Should include top matches with key factors")
        void shouldIncludeTopMatchesWithKeyFactors() {
            // Given: Trace with scored entities
            String sessionId = "test-session-123";
            ScoringTrace trace = createMultiEntityTrace(sessionId, 5);
            
            List<ReportSummary.EntitySummary> topMatches = List.of(
                new ReportSummary.EntitySummary("6861", "GUZMAN LOERA, Joaquin", 0.95, 
                    "Strong match: Name 92%, Alt Names 95%"),
                new ReportSummary.EntitySummary("23647", "WEI, Zhao", 0.87, 
                    "Good match: Name 85%, Alt Names 88%")
            );
            
            ReportSummary expectedSummary = ReportSummary.builder()
                .sessionId(sessionId)
                .topMatches(topMatches)
                .build();
            
            when(traceRepository.findBySessionId(sessionId)).thenReturn(Optional.of(trace));
            when(summaryService.generateSummary(trace)).thenReturn(expectedSummary);

            // When: Request summary
            ResponseEntity<ReportSummary> response = controller.getSummary(sessionId);

            // Then: Top matches are included with key factors
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().topMatches()).hasSize(2);
            assertThat(response.getBody().topMatches().get(0).entityId()).isEqualTo("6861");
            assertThat(response.getBody().topMatches().get(0).explanation()).contains("Name 92%");
        }

        @Test
        @DisplayName("Should include performance metrics by phase")
        void shouldIncludePerformanceMetricsByPhase() {
            // Given: Trace with timing data
            String sessionId = "test-session-123";
            ScoringTrace trace = createMultiEntityTrace(sessionId, 3);
            
            Map<Phase, Long> phaseTimings = Map.of(
                Phase.NAME_COMPARISON, 45L,
                Phase.ALT_NAME_COMPARISON, 32L,
                Phase.ADDRESS_COMPARISON, 18L,
                Phase.AGGREGATION, 5L
            );
            
            ReportSummary expectedSummary = ReportSummary.builder()
                .sessionId(sessionId)
                .phaseTimings(phaseTimings)
                .build();
            
            when(traceRepository.findBySessionId(sessionId)).thenReturn(Optional.of(trace));
            when(summaryService.generateSummary(trace)).thenReturn(expectedSummary);

            // When: Request summary
            ResponseEntity<ReportSummary> response = controller.getSummary(sessionId);

            // Then: Performance metrics are included
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().phaseTimings()).isNotNull();
            assertThat(response.getBody().phaseTimings().get(Phase.NAME_COMPARISON)).isEqualTo(45L);
        }
    }

    // Helper method to create test trace data
    private ScoringTrace createMultiEntityTrace(String sessionId, int entityCount) {
        List<ScoringEvent> events = List.of(
            new ScoringEvent(Instant.now(), Phase.NAME_COMPARISON, "Compare names", Map.of("durationMs", 10, "success", true)),
            new ScoringEvent(Instant.now(), Phase.ALT_NAME_COMPARISON, "Compare alt names", Map.of("durationMs", 8, "success", true)),
            new ScoringEvent(Instant.now(), Phase.AGGREGATION, "Calculate weighted score", Map.of("durationMs", 2, "success", true))
        );
        
        ScoreBreakdown breakdown = new ScoreBreakdown(0.92, 0.88, 0.0, 0.0, 0.0, 0.0, 0.0, 0.90);
        
        return new ScoringTrace(sessionId, events, breakdown, java.time.Duration.ofMillis(123));
    }
}
