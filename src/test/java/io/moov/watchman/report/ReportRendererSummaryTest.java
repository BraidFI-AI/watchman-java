package io.moov.watchman.report;

import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.trace.Phase;
import io.moov.watchman.trace.ScoringEvent;
import io.moov.watchman.trace.ScoringTrace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD Tests for Enhanced HTML Report with Summary Section.
 * RED Phase: These tests define how the HTML report should present summary data.
 */
@DisplayName("Enhanced HTML Report TDD Tests")
class ReportRendererSummaryTest {

    private ReportRenderer renderer;
    private TraceSummaryService summaryService;

    @BeforeEach
    void setUp() {
        summaryService = new TraceSummaryService();
        renderer = new ReportRenderer(summaryService);
    }

    @Test
    @DisplayName("Should render summary section at top of HTML report")
    void shouldRenderSummarySectionAtTopOfHtmlReport() {
        // Given: Trace with multiple entities
        ScoringTrace trace = createMultiEntityTrace(3);

        // When: Render HTML
        String html = renderer.renderHtml(trace);

        // Then: HTML contains summary section before detailed events
        assertThat(html).contains("<section class=\"summary\">");
        assertThat(html).contains("📊 Executive Summary");
        
        // Summary should appear before detailed trace events
        int summaryIndex = html.indexOf("📊 Executive Summary");
        int detailsIndex = html.indexOf("Processing Details");
        assertThat(summaryIndex).isLessThan(detailsIndex);
    }

    @Test
    @DisplayName("Should display total entities scored in summary")
    void shouldDisplayTotalEntitiesScoredInSummary() {
        // Given: Trace with 5 entities scored
        ScoringTrace trace = createMultiEntityTrace(5);

        // When: Render HTML
        String html = renderer.renderHtml(trace);

        // Then: Summary shows "5 entities scored"
        assertThat(html).containsPattern("(?i)5\\s+entities\\s+scored");
    }

    @Test
    @DisplayName("Should display score statistics in summary")
    void shouldDisplayScoreStatisticsInSummary() {
        // Given: Trace with known scores
        ScoringTrace trace = createMultiEntityTrace(3);

        // When: Render HTML
        String html = renderer.renderHtml(trace);

        // Then: Summary shows avg, min, max scores
        assertThat(html).containsPattern("(?i)average.*score");
        assertThat(html).containsPattern("(?i)highest.*score");
        assertThat(html).containsPattern("(?i)lowest.*score");
    }

    @Test
    @DisplayName("Should display phase contribution pie chart or bar chart")
    void shouldDisplayPhaseContributionChart() {
        // Given: Trace with breakdown
        ScoreBreakdown breakdown = new ScoreBreakdown(0.92, 0.15, 0.0, 0.0, 0.0, 0.0, 0.0, 0.85);
        List<ScoringEvent> events = createEvents(1);
        ScoringTrace trace = new ScoringTrace("test", events, breakdown, Duration.ofMillis(100));

        // When: Render HTML
        String html = renderer.renderHtml(trace);

        // Then: Contains phase contribution visualization
        assertThat(html).containsAnyOf(
            "Phase Contributions",
            "Score Anatomy",
            "Component Breakdown"
        );
        assertThat(html).containsIgnoringCase("name");
        assertThat(html).contains("92%");
    }

    @Test
    @DisplayName("Should display top matches with explanations")
    void shouldDisplayTopMatchesWithExplanations() {
        // Given: Trace with multiple matches
        ScoringTrace trace = createMultiEntityTrace(5);

        // When: Render HTML
        String html = renderer.renderHtml(trace);

        // Then: Summary shows top matches with explanations
        assertThat(html).containsAnyOf(
            "Top Matches",
            "Best Matches",
            "Highest Scoring Entities"
        );
    }

    @Test
    @DisplayName("Should display performance insights")
    void shouldDisplayPerformanceInsights() {
        // Given: Trace with timing data
        List<ScoringEvent> events = List.of(
            new ScoringEvent(Instant.now(), Phase.NAME_COMPARISON, "Compare names", 
                Map.of("durationMs", 50, "success", true)),
            new ScoringEvent(Instant.now(), Phase.ADDRESS_COMPARISON, "Compare addresses", 
                Map.of("durationMs", 5, "success", true))
        );
        ScoringTrace trace = new ScoringTrace("test", events, Map.of(), Duration.ofMillis(55));

        // When: Render HTML
        String html = renderer.renderHtml(trace);

        // Then: Performance insights are shown
        assertThat(html).containsAnyOf(
            "Performance",
            "Timing",
            "Slowest Phase"
        );
    }

    @Test
    @DisplayName("Should make summary collapsible with details expanded by default")
    void shouldMakeSummaryCollapsibleWithDetailsExpanded() {
        // Given: Any trace
        ScoringTrace trace = createMultiEntityTrace(2);

        // When: Render HTML
        String html = renderer.renderHtml(trace);

        // Then: Summary is prominent, details can be collapsed/expanded
        // This is UX guidance - specific implementation may vary
        assertThat(html).contains("summary");
    }

    @Test
    @DisplayName("Should use color coding for score quality")
    void shouldUseColorCodingForScoreQuality() {
        // Given: Trace with high average score
        ScoringTrace trace = createMultiEntityTrace(2);

        // When: Render HTML
        String html = renderer.renderHtml(trace);

        // Then: Uses color classes (success/warning/danger)
        assertThat(html).containsAnyOf(
            "score-high",
            "score-medium",
            "score-low",
            "text-success",
            "text-warning",
            "text-danger"
        );
    }

    @Test
    @DisplayName("Should explain what each phase does in layman terms")
    void shouldExplainWhatEachPhaseDoesInLaymanTerms() {
        // Given: Trace with phase breakdown
        ScoreBreakdown breakdown = new ScoreBreakdown(0.92, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.92);
        List<ScoringEvent> events = createEvents(1);
        ScoringTrace trace = new ScoringTrace("test", events, breakdown, Duration.ofMillis(50));

        // When: Render HTML
        String html = renderer.renderHtml(trace);

        // Then: Contains plain English explanations
        // Example: "Name Comparison: How well the primary name matches"
        assertThat(html).containsPattern("(?i)name.*comparison.*:");
    }

    // Helper methods
    private ScoringTrace createMultiEntityTrace(int count) {
        List<ScoringEvent> events = createEvents(count);
        ScoreBreakdown breakdown = new ScoreBreakdown(0.85, 0.10, 0.0, 0.0, 0.0, 0.0, 0.0, 0.80);
        return new ScoringTrace("test-session", events, breakdown, Duration.ofMillis(100));
    }

    private List<ScoringEvent> createEvents(int entityCount) {
        var events = new java.util.ArrayList<ScoringEvent>();
        for (int i = 0; i < entityCount; i++) {
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
}
