package io.moov.watchman.api;

import io.moov.watchman.report.TraceSummaryService;
import io.moov.watchman.report.model.ReportSummary;
import io.moov.watchman.trace.ScoringTrace;
import io.moov.watchman.trace.TraceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for trace filtering - ensures trace only includes entities
 * above minMatch threshold, not all candidates evaluated.
 * 
 * Bug: Current implementation traces all 18,590 entities even when only 1-5 match.
 * Fix: Trace should only include filtered results that will be returned to user.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TraceFilteringIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TraceRepository traceRepository;

    @Autowired
    private TraceSummaryService traceSummaryService;

    @Test
    void traceOnlyIncludesEntitiesAboveMinMatchThreshold() throws Exception {
        // Search with high threshold - should return very few results
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "Putin")
                .param("minMatch", "0.9")
                .param("trace", "true")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reportUrl").exists())
            .andReturn();

        // Extract trace from response and verify it only includes filtered results
        String response = result.getResponse().getContentAsString();
        
        // Parse the trace events count - should only have events for entities that passed threshold
        // Each entity traced generates 10 phase events (NORMALIZATION + 9 comparison phases)
        int eventCount = response.split("\"phase\":").length - 1;
        int entitiesTraced = eventCount / 10; // 10 events per entity
        
        // Should only trace entities that passed minMatch threshold
        assertThat(entitiesTraced)
            .as("Trace should only include entities >= 0.9 threshold")
            .isLessThan(10); // Definitely not 18,590!
    }

    @Test
    void traceStatisticsReflectFilteredResultsOnly() throws Exception {
        // Search for known entity with good match
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "PUTIN, Vladimir Vladimirovich")
                .param("minMatch", "0.88")
                .param("trace", "true")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[0].score").exists())
            .andReturn();

        String response = result.getResponse().getContentAsString();
        double topScore = extractTopScore(response);
        
        // Parse highest score from trace breakdown
        double traceHighestScore = extractTraceHighestScore(response);
        
        // Highest score in trace should match top result score
        assertThat(traceHighestScore)
            .as("Trace highest score should match top search result")
            .isGreaterThan(0.88) // Should be ~0.9+, not 0.39
            .isCloseTo(topScore, org.assertj.core.data.Offset.offset(0.01));
        
        // Count entities traced (10 events per entity)
        int eventCount = response.split("\"phase\":").length - 1;
        int entitiesTraced = eventCount / 10;
        
        // Should only trace the filtered results
        assertThat(entitiesTraced)
            .as("Should only trace entities that passed threshold")
            .isLessThanOrEqualTo(5);
    }

    @Test
    void traceWithNoMatchesAboveThreshold() throws Exception {
        // Search for nonsense that won't match anything above threshold
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "XYZABC123NOMATCH")
                .param("minMatch", "0.9")
                .param("trace", "true")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities").isEmpty())
            .andReturn();

        String response = result.getResponse().getContentAsString();
        
        // When no results match, trace might still be created but empty
        // Just verify no entities were traced
        boolean hasPhaseEvents = response.contains("\"phase\":");
        if (hasPhaseEvents) {
            int eventCount = response.split("\"phase\":").length - 1;
            int entitiesTraced = eventCount / 10; // 10 events per entity
            assertThat(entitiesTraced)
                .as("No entities should be traced when none match")
                .isEqualTo(0);
        } else {
            // No trace at all is also acceptable
            assertThat(hasPhaseEvents).isFalse();
        }
    }

    @Test
    void traceWithMultipleMatchesShowsOnlyFiltered() throws Exception {
        // Search with lower threshold to get multiple results
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "MADURO")
                .param("minMatch", "0.7")
                .param("trace", "true")
                .param("limit", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities").isArray())
            .andReturn();

        String response = result.getResponse().getContentAsString();
        int resultCount = extractResultCount(response);
        
        // Count unique entityIds in trace breakdown
        Set<String> tracedEntityIds = new HashSet<>();
        Pattern entityIdPattern = Pattern.compile("\"entityId\":\"([^\"]+)\"");
        Matcher matcher = entityIdPattern.matcher(response);
        while (matcher.find()) {
            tracedEntityIds.add(matcher.group(1));
        }
        int entitiesTraced = tracedEntityIds.size();
        
        // Trace should show same number of entities as search results
        assertThat(entitiesTraced)
            .as("Trace count should match result count")
            .isEqualTo(resultCount)
            .isLessThanOrEqualTo(3);
    }

    @Test
    void traceBreakdownMatchesEntityScore() throws Exception {
        // Search and verify breakdown scores match
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "Nicolas Maduro")
                .param("minMatch", "0.7")
                .param("trace", "true")
                .param("limit", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[0].score").exists())
            .andExpect(jsonPath("$.entities[0].breakdown.totalWeightedScore").exists())
            .andReturn();

        String response = result.getResponse().getContentAsString();
        double entityScore = extractTopScore(response);
        double breakdownScore = extractBreakdownScore(response);
        
        // Entity score should match breakdown score
        assertThat(entityScore)
            .as("Entity score should equal breakdown.totalWeightedScore")
            .isCloseTo(breakdownScore, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void traceLimitRespected() throws Exception {
        // Search with low threshold and small limit
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "AL-QAIDA")
                .param("minMatch", "0.5")
                .param("trace", "true")
                .param("limit", "3"))
            .andExpect(status().isOk())
            .andReturn();

        String response = result.getResponse().getContentAsString();
        
        // Count entities traced (10 events per entity)
        int eventCount = response.split("\"phase\":").length - 1;
        int entitiesTraced = eventCount / 10;
        
        // Should respect limit even if more entities match
        assertThat(entitiesTraced)
            .as("Trace should respect limit parameter")
            .isLessThanOrEqualTo(3);
    }

    @Test
    void traceAverageScoreReflectsOnlyFilteredEntities() throws Exception {
        // Search for entity with good match
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "PUTIN")
                .param("minMatch", "0.88")
                .param("trace", "true")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andReturn();

        String response = result.getResponse().getContentAsString();
        
        // Extract all entity scores from results
        String[] scoreMatches = response.split("\"score\":");
        double totalScore = 0;
        int scoreCount = 0;
        for (int i = 1; i < scoreMatches.length; i++) {
            String scoreStr = scoreMatches[i].split(",")[0].trim();
            if (scoreStr.matches("[0-9.]+")) {
                totalScore += Double.parseDouble(scoreStr);
                scoreCount++;
            }
        }
        
        double avgScore = totalScore / scoreCount;
        
        // Average should be high (only good matches traced)
        assertThat(avgScore)
            .as("Average score should reflect only filtered entities (>0.88)")
            .isGreaterThan(0.88); // Not 0.39 from averaging all 18,590!
    }

    @Test
    void nonTraceSearchUnaffectedByTraceChanges() throws Exception {
        // Search without trace - ensure normal path still works
        mockMvc.perform(get("/v1/search")
                .param("name", "Putin")
                .param("minMatch", "0.88")
                .param("trace", "false")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities").isArray())
            .andExpect(jsonPath("$.trace").doesNotExist())
            .andExpect(jsonPath("$.reportUrl").doesNotExist());
    }

    // Helper methods to extract data from JSON responses
    
    private String extractSessionId(String jsonResponse) {
        try {
            int start = jsonResponse.indexOf("\"reportUrl\":\"/api/reports/") + 27;
            int end = jsonResponse.indexOf("\"", start);
            return jsonResponse.substring(start, end);
        } catch (Exception e) {
            throw new RuntimeException("Could not extract session ID from response", e);
        }
    }

    private double extractTopScore(String jsonResponse) {
        try {
            String scoreStr = jsonResponse.split("\"score\":")[1].split(",")[0];
            return Double.parseDouble(scoreStr);
        } catch (Exception e) {
            throw new RuntimeException("Could not extract top score from response", e);
        }
    }

    private double extractBreakdownScore(String jsonResponse) {
        try {
            String scoreStr = jsonResponse.split("\"totalWeightedScore\":")[1].split(",|\\}")[0];
            return Double.parseDouble(scoreStr);
        } catch (Exception e) {
            throw new RuntimeException("Could not extract breakdown score from response", e);
        }
    }

    private int extractResultCount(String jsonResponse) {
        try {
            // Use totalResults field from response
            String totalResultsStr = jsonResponse.split("\"totalResults\":")[1].split(",")[0];
            return Integer.parseInt(totalResultsStr.trim());
        } catch (Exception e) {
            // Fallback: count "id": occurrences in entities array
            try {
                String entitiesSection = jsonResponse.split("\"entities\":\\[")[1].split("],")[0];
                int count = 0;
                int index = 0;
                while ((index = entitiesSection.indexOf("\"id\":", index)) != -1) {
                    count++;
                    index += 5;
                }
                return count;
            } catch (Exception ex) {
                throw new RuntimeException("Could not extract result count from response", ex);
            }
        }
    }
    
    private double extractTraceHighestScore(String jsonResponse) {
        try {
            // Extract the breakdown score from trace data
            String traceSection = jsonResponse.split("\"trace\":")[1];
            String scoreStr = traceSection.split("\"totalWeightedScore\":")[1].split(",|\\}")[0];
            return Double.parseDouble(scoreStr);
        } catch (Exception e) {
            throw new RuntimeException("Could not extract trace highest score from response", e);
        }
    }
}
