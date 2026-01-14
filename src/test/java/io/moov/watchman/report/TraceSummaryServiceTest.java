package io.moov.watchman.report;

import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.report.model.ReportSummary;
import io.moov.watchman.trace.Phase;
import io.moov.watchman.trace.ScoringEvent;
import io.moov.watchman.trace.ScoringTrace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD Tests for TraceSummaryService.
 * RED Phase: These tests define how trace data should be analyzed and summarized.
 */
@DisplayName("TraceSummaryService TDD Tests")
class TraceSummaryServiceTest {

    private TraceSummaryService service;

    @BeforeEach
    void setUp() {
        service = new TraceSummaryService();
    }

    @Test
    @DisplayName("Should calculate total entities scored from trace events")
    void shouldCalculateTotalEntitiesScored() {
        // Given: Trace with 3 complete scoring cycles (NORMALIZATION → AGGREGATION)
        ScoringTrace trace = createTraceWithEntities(3);

        // When: Generate summary
        ReportSummary summary = service.generateSummary(trace);

        // Then: Total entities is 3
        assertThat(summary.totalEntitiesScored()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should calculate average score from all breakdowns")
    void shouldCalculateAverageScoreFromAllBreakdowns() {
        // Given: Trace with known scores (0.95, 0.85, 0.75)
        List<ScoringEvent> events = createEventsForEntities(3);
        ScoreBreakdown breakdown = new ScoreBreakdown(0.92, 0.88, 0.0, 0.0, 0.0, 0.0, 0.0, 0.85);
        ScoringTrace trace = new ScoringTrace("test", events, breakdown, Duration.ofMillis(100));

        // When: Generate summary
        ReportSummary summary = service.generateSummary(trace);

        // Then: Average is calculated (will be refined based on actual breakdown data)
        assertThat(summary.averageScore()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should identify highest and lowest scores")
    void shouldIdentifyHighestAndLowestScores() {
        // Given: Trace with varied scores
        ScoringTrace trace = createTraceWithVariedScores();

        // When: Generate summary
        ReportSummary summary = service.generateSummary(trace);

        // Then: Highest and lowest are identified
        assertThat(summary.highestScore()).isGreaterThanOrEqualTo(summary.averageScore());
        assertThat(summary.lowestScore()).isLessThanOrEqualTo(summary.averageScore());
    }

    @Test
    @DisplayName("Should calculate phase contribution percentages")
    void shouldCalculatePhaseContributionPercentages() {
        // Given: Trace with breakdown showing which phases contributed
        ScoreBreakdown breakdown = new ScoreBreakdown(
            0.92,  // nameScore - 92% contribution
            0.15,  // altNamesScore - 15% contribution
            0.0,   // addressScore - no contribution
            0.0,   // govIdScore - no contribution
            0.0,   // cryptoScore - no contribution
            0.0,   // contactScore - no contribution
            0.0,   // dateScore - no contribution
            0.85   // totalWeightedScore
        );
        List<ScoringEvent> events = createEventsForEntities(1);
        ScoringTrace trace = new ScoringTrace("test", events, breakdown, Duration.ofMillis(50));

        // When: Generate summary
        ReportSummary summary = service.generateSummary(trace);

        // Then: Phase contributions show NAME_COMPARISON dominated
        assertThat(summary.phaseContributions()).isNotNull();
        assertThat(summary.phaseContributions().get(Phase.NAME_COMPARISON)).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("Should calculate phase timing statistics")
    void shouldCalculatePhaseTimingStatistics() {
        // Given: Trace with timing data
        List<ScoringEvent> events = List.of(
            new ScoringEvent(Instant.now(), Phase.NAME_COMPARISON, "Compare names", 
                Map.of("durationMs", 25, "success", true)),
            new ScoringEvent(Instant.now(), Phase.ALT_NAME_COMPARISON, "Compare alt names", 
                Map.of("durationMs", 15, "success", true)),
            new ScoringEvent(Instant.now(), Phase.ADDRESS_COMPARISON, "Compare addresses", 
                Map.of("durationMs", 5, "success", true)),
            new ScoringEvent(Instant.now(), Phase.AGGREGATION, "Aggregate", 
                Map.of("durationMs", 2, "success", true))
        );
        ScoringTrace trace = new ScoringTrace("test", events, null, Duration.ofMillis(47));

        // When: Generate summary
        ReportSummary summary = service.generateSummary(trace);

        // Then: Phase timings are calculated
        assertThat(summary.phaseTimings()).isNotNull();
        assertThat(summary.phaseTimings().get(Phase.NAME_COMPARISON)).isEqualTo(25L);
        assertThat(summary.phaseTimings().get(Phase.ALT_NAME_COMPARISON)).isEqualTo(15L);
    }

    @Test
    @DisplayName("Should identify slowest phases")
    void shouldIdentifySlowestPhases() {
        // Given: Trace where NAME_COMPARISON is slowest
        List<ScoringEvent> events = List.of(
            new ScoringEvent(Instant.now(), Phase.NAME_COMPARISON, "Compare names", 
                Map.of("durationMs", 100, "success", true)),
            new ScoringEvent(Instant.now(), Phase.ALT_NAME_COMPARISON, "Compare alt names", 
                Map.of("durationMs", 10, "success", true)),
            new ScoringEvent(Instant.now(), Phase.AGGREGATION, "Aggregate", 
                Map.of("durationMs", 2, "success", true))
        );
        ScoringTrace trace = new ScoringTrace("test", events, null, Duration.ofMillis(112));

        // When: Generate summary
        ReportSummary summary = service.generateSummary(trace);

        // Then: NAME_COMPARISON is identified as slowest
        assertThat(summary.slowestPhase()).isEqualTo(Phase.NAME_COMPARISON);
    }

    @Test
    @DisplayName("Should generate top 5 matches with explanations")
    void shouldGenerateTop5MatchesWithExplanations() {
        // Given: Trace with multiple entities scored
        ScoringTrace trace = createTraceWithMultipleScores();

        // When: Generate summary
        ReportSummary summary = service.generateSummary(trace);

        // Then: Top matches are generated with explanations
        assertThat(summary.topMatches()).isNotNull();
        assertThat(summary.topMatches()).hasSizeLessThanOrEqualTo(5);
        
        // Each match should have an explanation
        summary.topMatches().forEach(match -> {
            assertThat(match.explanation()).isNotBlank();
            assertThat(match.score()).isGreaterThan(0.0);
        });
    }

    @Test
    @DisplayName("Should generate human-readable explanation for entity score")
    void shouldGenerateHumanReadableExplanationForEntityScore() {
        // Given: Score breakdown with dominant name match
        ScoreBreakdown breakdown = new ScoreBreakdown(0.95, 0.20, 0.0, 0.0, 0.0, 0.0, 0.0, 0.89);

        // When: Generate explanation
        String explanation = service.explainScore(breakdown);

        // Then: Explanation highlights key contributors
        assertThat(explanation).containsIgnoringCase("name");
        assertThat(explanation).contains("95%");
        assertThat(explanation).containsAnyOf("strong", "high", "good");
    }

    @Test
    @DisplayName("Should handle empty trace gracefully")
    void shouldHandleEmptyTraceGracefully() {
        // Given: Empty trace
        ScoringTrace trace = new ScoringTrace("test", List.of(), Map.of(), null, 0L);

        // When: Generate summary
        ReportSummary summary = service.generateSummary(trace);

        // Then: Returns valid summary with zeros
        assertThat(summary.totalEntitiesScored()).isEqualTo(0);
        assertThat(summary.averageScore()).isEqualTo(0.0);
    }

    // Helper methods
    private ScoringTrace createTraceWithEntities(int count) {
        List<ScoringEvent> events = createEventsForEntities(count);
        return new ScoringTrace("test", events, null, Duration.ofMillis(100));
    }

    private List<ScoringEvent> createEventsForEntities(int count) {
        List<ScoringEvent> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(new ScoringEvent(Instant.now(), Phase.NORMALIZATION, "Normalize", Map.of()));
            events.add(new ScoringEvent(Instant.now(), Phase.NAME_COMPARISON, "Compare names", 
                Map.of("durationMs", 10, "success", true)));
            events.add(new ScoringEvent(Instant.now(), Phase.ALT_NAME_COMPARISON, "Compare alt names", 
                Map.of("durationMs", 5, "success", true)));
            events.add(new ScoringEvent(Instant.now(), Phase.AGGREGATION, "Aggregate", 
                Map.of("durationMs", 2, "success", true)));
        }
        return events;
    }

    private ScoringTrace createTraceWithVariedScores() {
        // Simulate trace with high, medium, and low scores
        List<ScoringEvent> events = createEventsForEntities(3);
        return new ScoringTrace("test", events, null, Duration.ofMillis(100));
    }

    private ScoringTrace createTraceWithMultipleScores() {
        // Simulate trace with 5+ entities
        List<ScoringEvent> events = createEventsForEntities(5);
        return new ScoringTrace("test", events, null, Duration.ofMillis(200));
    }
}
