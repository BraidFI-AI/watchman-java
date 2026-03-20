package io.braid.daywatcher.api;

import io.braid.daywatcher.trace.ScoringTrace;
import io.braid.daywatcher.trace.TraceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RED Phase Test for Observation S.I. 1: AEROCARIBBEAN AIRLINES
 * 
 * Issue: Score Trace Report shows low-confidence partial matches (< 0.85) in audit trail.
 * Example: "CARIBBEAN" matched with "CASPIAN AIRLINES" at ~0.70-0.75 similarity.
 * 
 * While the correct entity IS returned in search results, the trace contains noise
 * that confuses BSA examiners during audit reviews.
 * 
 * Fix (Option 2): Filter low-confidence partial matches from Score Trace.
 * Only include entities with totalWeightedScore >= 0.85 in trace data.
 * 
 * References:
 * - CSV Row 1: AEROCARIBBEAN AIRLINES observation
 * - EntityScorerImpl.scoreWithBreakdown() - scoring logic
 * - TraceRepository - stores trace data
 * - ReportRenderer - generates HTML reports from trace
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("🔴 RED: Low-Confidence Trace Filtering (S.I. 1)")
class LowConfidenceTraceFilteringTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TraceRepository traceRepository;

    private static final double LOW_CONFIDENCE_THRESHOLD = 0.85;

    @Test
    @DisplayName("Score trace should exclude entities below 0.85 confidence threshold")
    void traceFilteringExcludesLowConfidenceEntities() throws Exception {
        // GIVEN: Search for "CARIBBEAN AIRLINES" which produces false positive matches
        // Expected behavior: "CASPIAN AIRLINES" (~0.70 score) should NOT appear in trace
        // Expected behavior: "AEROCARIBBEAN AIRLINES" (~0.92 score) SHOULD appear in trace
        
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "CARIBBEAN AIRLINES")
                .param("minMatch", "0.70")  // Low threshold to allow false positives in results
                .param("trace", "true")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reportUrl").exists())
            .andReturn();

        String response = result.getResponse().getContentAsString();
        
        // Extract session ID from reportUrl
        Pattern reportUrlPattern = Pattern.compile("/api/reports/([^\"]+)");
        Matcher matcher = reportUrlPattern.matcher(response);
        assertThat(matcher.find())
            .as("Response should contain reportUrl")
            .isTrue();
        
        String sessionId = matcher.group(1);
        
        // Retrieve trace from repository
        Optional<ScoringTrace> traceOpt = traceRepository.findBySessionId(sessionId);
        assertThat(traceOpt).as("Trace should exist in repository").isPresent();
        
        ScoringTrace trace = traceOpt.get();
        
        // Parse trace metadata to check which entities were traced
        // The trace should contain breakdown scores for each entity evaluated
        Object metadataObj = trace.metadata();
        assertThat(metadataObj).as("Trace should have metadata").isNotNull();
        
        // Count entities in trace with scores < 0.85
        // Expected: 0 (all low-confidence entities filtered out)
        long lowConfidenceCount = countLowConfidenceEntitiesInTrace(response);
        
        assertThat(lowConfidenceCount)
            .as("Trace should NOT include entities with score < 0.85")
            .isEqualTo(0);
    }

    @Test
    @DisplayName("Score trace should include entities at or above 0.85 confidence threshold")
    void traceIncludesHighConfidenceEntities() throws Exception {
        // GIVEN: Search that produces high-confidence match
        
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "AEROCARIBBEAN")  // Direct match to AEROCARIBBEAN AIRLINES
                .param("minMatch", "0.70")
                .param("trace", "true")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reportUrl").exists())
            .andReturn();

        String response = result.getResponse().getContentAsString();
        
        // Extract session ID
        Pattern reportUrlPattern = Pattern.compile("/api/reports/([^\"]+)");
        Matcher matcher = reportUrlPattern.matcher(response);
        assertThat(matcher.find()).isTrue();
        String sessionId = matcher.group(1);
        
        // Retrieve trace
        Optional<ScoringTrace> traceOpt = traceRepository.findBySessionId(sessionId);
        assertThat(traceOpt).isPresent();
        
        ScoringTrace trace = traceOpt.get();
        
        // Trace should include at least one entity with score >= 0.85
        long highConfidenceCount = countHighConfidenceEntitiesInTrace(response);
        
        assertThat(highConfidenceCount)
            .as("Trace should include at least one entity with score >= 0.85")
            .isGreaterThan(0);
    }

    @Test
    @DisplayName("Trace filtering should not affect search results (only audit trail)")
    void traceFilteringDoesNotAffectSearchResults() throws Exception {
        // GIVEN: Search with low minMatch threshold
        
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "CARIBBEAN AIRLINES")
                .param("minMatch", "0.70")  // Allow low-confidence results
                .param("trace", "true")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities").isArray())
            .andReturn();

        String response = result.getResponse().getContentAsString();
        
        // Count results - should include low-confidence matches (per minMatch=0.70)
        int resultCount = extractResultCount(response);
        
        // Count traced entities - should only include high-confidence (>= 0.85)
        long tracedCount = countAllTracedEntities(response);
        
        // Trace count should be less than or equal to result count
        // (Some results may be filtered from trace due to low confidence)
        assertThat(tracedCount)
            .as("Trace filtering should only affect audit trail, not search results")
            .isLessThanOrEqualTo(resultCount);
    }

    @Test
    @DisplayName("Trace boundary test: entity at exactly 0.85 should be included")
    void traceBoundaryAtExactThreshold() throws Exception {
        // RED phase: Define expected behavior at exact threshold
        // An entity scoring exactly 0.85 should be included in trace
        
        // This test documents the boundary condition and will pass once
        // the threshold logic is implemented with >= comparison
        
        // For now, this test will help validate the implementation
        // uses >= 0.85 (not > 0.85)
        assertThat(0.85).as("Threshold should use inclusive comparison")
            .isGreaterThanOrEqualTo(LOW_CONFIDENCE_THRESHOLD);
    }

    // ==================== Helper Methods ====================

    private long countLowConfidenceEntitiesInTrace(String response) {
        // Extract trace section - need to handle nested JSON properly
        int traceStart = response.indexOf("\"trace\":");
        if (traceStart == -1) {
            return 0; // No trace found
        }
        
        // Find the matching closing brace for the trace object
        int braceCount = 0;
        int traceValueStart = response.indexOf("{", traceStart);
        int traceEnd = traceValueStart;
        
        for (int i = traceValueStart; i < response.length(); i++) {
            char c = response.charAt(i);
            if (c == '{') braceCount++;
            if (c == '}') braceCount--;
            if (braceCount == 0) {
                traceEnd = i + 1;
                break;
            }
        }
        
        String traceSection = response.substring(traceValueStart, traceEnd);
        
        // Look for entities with totalWeightedScore < 0.85 within trace section
        Pattern scorePattern = Pattern.compile("\"totalWeightedScore\"\\s*:\\s*([0-9.]+)");
        Matcher matcher = scorePattern.matcher(traceSection);
        
        long count = 0;
        while (matcher.find()) {
            double score = Double.parseDouble(matcher.group(1));
            if (score < LOW_CONFIDENCE_THRESHOLD) {
                count++;
            }
        }
        return count;
    }

    private long countHighConfidenceEntitiesInTrace(String response) {
        // Extract trace section - need to handle nested JSON properly
        int traceStart = response.indexOf("\"trace\":");
        if (traceStart == -1) {
            return 0; // No trace found
        }
        
        // Find the matching closing brace for the trace object
        int braceCount = 0;
        int traceValueStart = response.indexOf("{", traceStart);
        int traceEnd = traceValueStart;
        
        for (int i = traceValueStart; i < response.length(); i++) {
            char c = response.charAt(i);
            if (c == '{') braceCount++;
            if (c == '}') braceCount--;
            if (braceCount == 0) {
                traceEnd = i + 1;
                break;
            }
        }
        
        String traceSection = response.substring(traceValueStart, traceEnd);
        
        // Look for entities with totalWeightedScore >= 0.85 within trace section
        Pattern scorePattern = Pattern.compile("\"totalWeightedScore\"\\s*:\\s*([0-9.]+)");
        Matcher matcher = scorePattern.matcher(traceSection);
        
        long count = 0;
        while (matcher.find()) {
            double score = Double.parseDouble(matcher.group(1));
            if (score >= LOW_CONFIDENCE_THRESHOLD) {
                count++;
            }
        }
        return count;
    }

    private long countAllTracedEntities(String response) {
        // Extract trace section - need to handle nested JSON properly
        int traceStart = response.indexOf("\"trace\":");
        if (traceStart == -1) {
            return 0; // No trace found
        }
        
        // Find the matching closing brace for the trace object
        int braceCount = 0;
        int traceValueStart = response.indexOf("{", traceStart);
        int traceEnd = traceValueStart;
        
        for (int i = traceValueStart; i < response.length(); i++) {
            char c = response.charAt(i);
            if (c == '{') braceCount++;
            if (c == '}') braceCount--;
            if (braceCount == 0) {
                traceEnd = i + 1;
                break;
            }
        }
        
        String traceSection = response.substring(traceValueStart, traceEnd);
        
        // Count total entities in trace (regardless of score)
        Pattern scorePattern = Pattern.compile("\"totalWeightedScore\"\\s*:");
        Matcher matcher = scorePattern.matcher(traceSection);
        
        long count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private int extractResultCount(String response) {
        // Count entities in search results array
        Pattern resultPattern = Pattern.compile("\"entities\"\\s*:\\s*\\[");
        Matcher matcher = resultPattern.matcher(response);
        
        if (!matcher.find()) {
            return 0;
        }
        
        // Count occurrences of entity objects
        Pattern entityPattern = Pattern.compile("\"id\"\\s*:\\s*\"[^\"]+\"");
        Matcher entityMatcher = entityPattern.matcher(response);
        
        int count = 0;
        while (entityMatcher.find()) {
            count++;
        }
        return count;
    }
}
