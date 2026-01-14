package io.moov.watchman.trace;

import io.moov.watchman.model.ScoreBreakdown;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD tests for TraceSummary - summarizes complex trace data for non-technical users.
 */
@DisplayName("TraceSummary Tests")
class TraceSummaryTest {

    @Test
    @DisplayName("Should calculate total entities scored from trace events")
    void shouldCalculateTotalEntitiesScored() {
        // Given: A trace with events for 3 entities (each has 9 phases)
        List<ScoringEvent> events = createEventsForEntities(3);
        ScoringTrace trace = new ScoringTrace("session-1", events, Map.of(), null, 100L);
        
        // When: We generate a summary
        TraceSummary summary = TraceSummary.from(trace);
        
        // Then: Should detect 3 entities
        assertThat(summary.totalEntitiesScored()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should identify top contributing phases")
    void shouldIdentifyTopContributingPhases() {
        // Given: A trace with frequent NAME_COMPARISON and ALT_NAME_COMPARISON events
        List<ScoringEvent> events = List.of(
            event(Phase.NAME_COMPARISON, "Compare names", Map.of("similarity", 0.95)),
            event(Phase.NAME_COMPARISON, "Compare names", Map.of("similarity", 0.88)),
            event(Phase.ALT_NAME_COMPARISON, "Compare alt names", Map.of("similarity", 0.92)),
            event(Phase.ADDRESS_COMPARISON, "Compare addresses", Map.of("similarity", 0.0)),
            event(Phase.AGGREGATION, "Calculate score", Map.of())
        );
        ScoringTrace trace = new ScoringTrace("session-1", events, Map.of(), null, 50L);
        
        // When: We generate a summary
        TraceSummary summary = TraceSummary.from(trace);
        
        // Then: NAME_COMPARISON should be the top phase (2 occurrences)
        assertThat(summary.topPhases()).hasSize(3);
        assertThat(summary.topPhases().get(0).phase()).isEqualTo(Phase.NAME_COMPARISON);
        assertThat(summary.topPhases().get(0).count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should calculate average processing time per phase")
    void shouldCalculateAverageProcessingTimePerPhase() {
        // Given: Events with timing data
        List<ScoringEvent> events = List.of(
            event(Phase.NAME_COMPARISON, "Compare", Map.of("durationMs", 10)),
            event(Phase.NAME_COMPARISON, "Compare", Map.of("durationMs", 20)),
            event(Phase.ADDRESS_COMPARISON, "Compare", Map.of("durationMs", 100))
        );
        ScoringTrace trace = new ScoringTrace("session-1", events, Map.of(), null, 130L);
        
        // When: We generate a summary
        TraceSummary summary = TraceSummary.from(trace);
        
        // Then: ADDRESS_COMPARISON should be identified as slowest
        assertThat(summary.performanceInsights()).contains("ADDRESS_COMPARISON");
    }

    @Test
    @DisplayName("Should explain score breakdown in plain English")
    void shouldExplainScoreBreakdownInPlainEnglish() {
        // Given: A trace with score breakdown
        ScoreBreakdown breakdown = new ScoreBreakdown(0.95, 0.88, 0.0, 0.0, 0.0, 0.0, 0.0, 0.92);
        ScoringTrace trace = new ScoringTrace("session-1", List.of(), breakdown, java.time.Duration.ofMillis(50));
        
        // When: We generate a summary
        TraceSummary summary = TraceSummary.from(trace);
        
        // Then: Should have plain English explanations
        assertThat(summary.scoreExplanation()).isNotEmpty();
        assertThat(summary.scoreExplanation()).contains("name");
        assertThat(summary.scoreExplanation()).contains("95%");
    }

    @Test
    @DisplayName("Should provide key insights for operators")
    void shouldProvideKeyInsightsForOperators() {
        // Given: A trace with high name match but no other matches
        ScoreBreakdown breakdown = new ScoreBreakdown(0.95, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.88);
        List<ScoringEvent> events = createEventsForEntities(1);
        ScoringTrace trace = new ScoringTrace("session-1", events, breakdown, java.time.Duration.ofMillis(50));
        
        // When: We generate a summary
        TraceSummary summary = TraceSummary.from(trace);
        
        // Then: Should provide actionable insights
        assertThat(summary.keyInsights()).isNotEmpty();
        assertThat(summary.keyInsights().size()).isGreaterThanOrEqualTo(1);
    }

    // Helper methods
    private List<ScoringEvent> createEventsForEntities(int count) {
        var events = new java.util.ArrayList<ScoringEvent>();
        for (int i = 0; i < count; i++) {
            events.add(event(Phase.NORMALIZATION, "Normalize", Map.of()));
            events.add(event(Phase.NAME_COMPARISON, "Compare names", Map.of("durationMs", 5)));
            events.add(event(Phase.ALT_NAME_COMPARISON, "Compare alt names", Map.of("durationMs", 3)));
            events.add(event(Phase.GOV_ID_COMPARISON, "Compare IDs", Map.of("durationMs", 1)));
            events.add(event(Phase.CRYPTO_COMPARISON, "Compare crypto", Map.of("durationMs", 1)));
            events.add(event(Phase.ADDRESS_COMPARISON, "Compare addresses", Map.of("durationMs", 2)));
            events.add(event(Phase.CONTACT_COMPARISON, "Compare contacts", Map.of("durationMs", 1)));
            events.add(event(Phase.DATE_COMPARISON, "Compare dates", Map.of("durationMs", 1)));
            events.add(event(Phase.AGGREGATION, "Aggregate", Map.of("durationMs", 1)));
        }
        return events;
    }

    private ScoringEvent event(Phase phase, String description, Map<String, Object> data) {
        return new ScoringEvent(java.time.Instant.now(), phase, description, data);
    }
}
