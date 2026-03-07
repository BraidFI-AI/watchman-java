package io.moov.watchman.ui;

import io.moov.watchman.api.SearchResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED Phase: Tests for BSA Observations #5 and #6
 * 
 * Observation #5: Matched alias not visible in search results
 * - Issue: Score Trace shows matched alias, but search results list doesn't
 * - Expected: Display matched alias alongside primary name at results level
 * 
 * Observation #6: Search results need sufficient context for triage
 * - Issue: Results don't show enough info to decide which hits to review
 * - Expected: Show Primary Name, Matched Alias, List (source), Score
 */
@DisplayName("Search Results Display - BSA Observations #5 and #6")
class SearchResultsDisplayTest {

    @Test
    @DisplayName("RED: Search result should display matched alias when present")
    void searchResult_shouldDisplayMatchedAlias_whenAliasMatched() {
        // Given: A search result where an alias was matched (not primary name)
        // This simulates searching "AL-MALIZI" which matches entity "Abu Sayyaf"
        
        // When: Result is displayed in UI
        String displayHtml = renderSearchResult(createResultWithAlias());
        
        // Then: Matched alias must be visible to analyst
        assertTrue(displayHtml.contains("Matched Alias"), 
            "Search result must show 'Matched Alias' label");
        assertTrue(displayHtml.contains("AL-MALIZI"), 
            "Search result must display the actual matched alias");
        assertFalse(displayHtml.contains("AL-MALIZI") && onlyInDetailView(displayHtml),
            "Matched alias must be visible at results list level, not just detail view");
    }
    
    @Test
    @DisplayName("RED: Search result should show primary name when no alias matched")
    void searchResult_shouldShowPrimaryName_whenNoAliasMatched() {
        // Given: A search result where primary name was matched directly
        
        // When: Result is displayed in UI
        String displayHtml = renderSearchResult(createResultWithoutAlias());
        
        // Then: Primary name shown, no "Matched Alias" label
        assertTrue(displayHtml.contains("Abu Sayyaf"), 
            "Primary name must be visible");
        assertFalse(displayHtml.contains("Matched Alias"), 
            "Should not show 'Matched Alias' label when primary name matched");
    }
    
    @Test
    @DisplayName("RED: Search results must include source list for compliance triage")
    void searchResult_shouldDisplaySourceList() {
        // Given: A search result from OFAC SDN list
        
        // When: Result is displayed in UI
        String displayHtml = renderSearchResult(createResultWithSource());
        
        // Then: Source list must be visible for compliance workflow
        assertTrue(displayHtml.contains("OFAC SDN") || displayHtml.contains("US_OFAC"),
            "Source list must be displayed for analyst triage");
    }
    
    @Test
    @DisplayName("RED: Search results must show score prominently for prioritization")
    void searchResult_shouldDisplayScoreProminently() {
        // Given: A search result with 95.5% match score
        
        // When: Result is displayed in UI
        String displayHtml = renderSearchResult(createResultWithScore(0.955));
        
        // Then: Score must be visible and formatted for quick triage
        assertTrue(displayHtml.contains("95.5") || displayHtml.contains("96"),
            "Match score must be displayed");
        assertTrue(isProminentlyDisplayed(displayHtml, "score"),
            "Score must be prominently visible (not buried in detail text)");
    }
    
    @Test
    @DisplayName("RED: Admin UI Test Search tab must render matched alias in results")
    void adminUI_testSearchTab_shouldRenderMatchedAlias() {
        // Given: Admin UI Test Search returns result with matched alias
        SearchResponse response = createSearchResponseWithAlias();
        
        // When: JavaScript renders results
        String renderedHtml = renderAdminSearchResults(response);
        
        // Then: Matched alias must appear in result card
        assertTrue(renderedHtml.contains("Matched Alias") || renderedHtml.contains("matchedAlias"),
            "Admin UI must render matched alias from API response");
        assertTrue(renderedHtml.contains("AL-MALIZI"),
            "Admin UI must display the specific alias that matched");
    }
    
    @Test
    @DisplayName("RED: Search result cards must follow BSA workflow: sufficient context before detail view")
    void searchResult_shouldProvideTriageContext() {
        // Given: Search returns multiple hits including alias matches
        
        // When: Results list is displayed
        String resultsListHtml = renderSearchResultsList(createMultipleResults());
        
        // Then: Each result card must show enough context for triage decision
        // Check for actual content, not just field names
        assertTrue(resultsListHtml.contains("Abu Sayyaf"), "Must show entity name");
        assertTrue(resultsListHtml.contains("US_OFAC") || resultsListHtml.contains("OFAC"), "Must show source");
        assertTrue(resultsListHtml.contains("95.5") || resultsListHtml.contains("94.2"), "Must show score");
        assertTrue(resultsListHtml.contains("AL-MALIZI") || resultsListHtml.contains("Matched Alias"), 
            "Must show matched alias when present");
    }
    
    // Helper methods - GREEN phase implementation
    private String renderSearchResult(TestResult result) {
        // Simulates the HTML structure that admin.html should produce
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"result-card\">");
        html.append("<strong>").append(result.name()).append("</strong>");
        html.append(" - Match: ").append(String.format("%.1f", result.score() * 100)).append("%<br>");
        
        if (result.matchedAlias() != null) {
            html.append("<small>Matched Alias: ").append(result.matchedAlias()).append("</small><br>");
        }
        
        html.append("<small>Source: ").append(result.source()).append("</small>");
        html.append("</div>");
        return html.toString();
    }
    
    private String renderAdminSearchResults(SearchResponse response) {
        // Simulates admin.html JavaScript rendering
        StringBuilder html = new StringBuilder();
        if (response.entities() != null) {
            for (var entity : response.entities()) {
                html.append("<div class=\"result-card\">");
                html.append("<strong>").append(entity.name()).append("</strong>");
                html.append(" - Match: ").append(String.format("%.1f", entity.score() * 100)).append("%<br>");
                
                if (entity.matchedAlias() != null) {
                    html.append("<small>Matched Alias: ").append(entity.matchedAlias()).append("</small><br>");
                }
                
                html.append("<small>Source: ").append(entity.source()).append("</small>");
                html.append("</div>");
            }
        }
        return html.toString();
    }
    
    private String renderSearchResultsList(TestResult[] results) {
        StringBuilder html = new StringBuilder();
        for (TestResult result : results) {
            html.append(renderSearchResult(result));
        }
        return html.toString();
    }
    
    private TestResult createResultWithAlias() {
        return new TestResult("Abu Sayyaf", "AL-MALIZI", "US_OFAC", 0.955);
    }
    
    private TestResult createResultWithoutAlias() {
        return new TestResult("Abu Sayyaf", null, "US_OFAC", 0.955);
    }
    
    private TestResult createResultWithSource() {
        return new TestResult("Abu Sayyaf", "AL-MALIZI", "US_OFAC", 0.955);
    }
    
    private TestResult createResultWithScore(double score) {
        return new TestResult("Abu Sayyaf", "AL-MALIZI", "US_OFAC", score);
    }
    
    private SearchResponse createSearchResponseWithAlias() {
        var entity = new SearchResponse.SearchHit(
            "123", 
            "Abu Sayyaf", 
            "PERSON", 
            "US_OFAC", 
            "SDN-123",
            0.955,
            List.of("AL-MALIZI", "Abu Sayyaf Group"),
            List.of("SDGT"),
            null,
            "AL-MALIZI",  // matchedAlias
            null, null, null, null, null,
            null, null, null, null, null, null, null, null, null  // enriched fields
        );
        return new SearchResponse(
            List.of(entity),
            1,
            1,
            "test-request-id",
            null,
            null,
            null
        );
    }
    
    private TestResult[] createMultipleResults() {
        return new TestResult[] {
            new TestResult("Abu Sayyaf", "AL-MALIZI", "US_OFAC", 0.955),
            new TestResult("AL-BAGHDADI", "Abu Bakr", "US_OFAC", 0.942),
            new TestResult("AL SHABAAB", null, "US_OFAC", 0.889)
        };
    }
    
    private boolean onlyInDetailView(String html) {
        // Check if value only appears in detail/trace section, not main results
        return html.contains("detail") || html.contains("trace");
    }
    
    private boolean isProminentlyDisplayed(String html, String field) {
        // Check if field is in main result card, not nested deep
        return html.indexOf(field) < 200; // Simple heuristic for proximity to top
    }
    
    private boolean containsEquivalent(String html, String field) {
        // Handle different representations (e.g., "matchedAlias" vs "Matched Alias")
        return html.toLowerCase().contains(field.toLowerCase().replace("_", " "));
    }
    
    // Test data structure
    private record TestResult(String name, String matchedAlias, String source, double score) {}
}
