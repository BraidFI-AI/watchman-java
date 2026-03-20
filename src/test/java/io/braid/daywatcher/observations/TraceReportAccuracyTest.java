package io.braid.daywatcher.observations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BSA Group 6: Row 45 - P-672 Aircraft Trace Report Accuracy
 * <p>
 * Observation: "Score Trace Report matched the name with another aircraft (P-632) 
 * though P-672 was listed in search results."
 * <p>
 * Issue: When searching for P-672, the trace report shows the wrong aircraft name (P-632).
 * P-672 appears in search results but the trace report entity details show P-632 instead.
 * <p>
 * Root Cause: SearchController always uses the first high-confidence result's entity name
 * for the trace report metadata, regardless of whether it matches the search query. When 
 * multiple entities score 1.0, the first result may not be the requested entity.
 * <p>
 * Expected: Trace report should display P-672 (the searched entity) in Entity Information section
 * Actual: Trace report may display P-632 or another high-scoring entity if it appears first
 * <p>
 * Fix Strategy: Use the best matching entity for trace report, preferring exact/near-exact 
 * name matches over the first high-confidence result by position.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("BSA Row 45: P-672 Trace Report Accuracy")
class TraceReportAccuracyTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * BSA Row 45 - Direct Test
     * Search for P-672 and verify trace report shows P-672, not P-632 or other aircraft.
     */
    @Test
    @DisplayName("Row 45: P-672 trace report should show P-672, not P-632")
    void testP672TraceReportShowsCorrectAircraft() throws Exception {
        // Search for P-672 with trace enabled
        String searchResponse = mockMvc.perform(get("/v1/search")
                        .param("name", "P-672")
                        .param("limit", "5")
                        .param("trace", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities").isArray())
                .andExpect(jsonPath("$.reportUrl").value(notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Extract trace report URL
        Pattern reportUrlPattern = Pattern.compile("/api/reports/([a-f0-9-]+)");
        Matcher matcher = reportUrlPattern.matcher(searchResponse);
        assertThat(matcher.find()).as("Response should contain reportUrl").isTrue();
        String reportUrl = matcher.group(0);
        
        // Fetch trace report HTML
        String reportHtml = mockMvc.perform(get(reportUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify report contains P-672 in Entity Information section
        assertThat(reportHtml)
                .as("Trace report should display P-672 in Entity Information")
                .contains("P-672");
        
        // Verify report does NOT incorrectly show P-632
        Pattern entityNamePattern = Pattern.compile("Primary Name:</span>\\s*<span class=\"detail-value\">([^<]+)</span>");
        Matcher entityNameMatcher = entityNamePattern.matcher(reportHtml);
        assertThat(entityNameMatcher.find())
                .as("Report should contain Primary Name field")
                .isTrue();
        
        String displayedEntityName = entityNameMatcher.group(1).trim();
        assertThat(displayedEntityName)
                .as("Trace report Primary Name should be P-672, not P-632 or other aircraft")
                .isEqualTo("P-672");
    }

    /**
     * Verification Test: P-632 trace report should correctly show P-632
     */
    @Test
    @DisplayName("Verify: P-632 trace report shows P-632 (not confused with P-672)")
    void testP632TraceReportShowsCorrectAircraft() throws Exception {
        // Search for P-632 with trace enabled
        String searchResponse = mockMvc.perform(get("/v1/search")
                        .param("name", "P-632")
                        .param("limit", "5")
                        .param("trace", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities").isArray())
                .andExpect(jsonPath("$.reportUrl").value(notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Extract trace report URL
        Pattern reportUrlPattern = Pattern.compile("/api/reports/([a-f0-9-]+)");
        Matcher matcher = reportUrlPattern.matcher(searchResponse);
        assertThat(matcher.find()).as("Response should contain reportUrl").isTrue();
        String reportUrl = matcher.group(0);
        
        // Fetch trace report HTML
        String reportHtml = mockMvc.perform(get(reportUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify report shows P-632
        Pattern entityNamePattern = Pattern.compile("Primary Name:</span>\\s*<span class=\"detail-value\">([^<]+)</span>");
        Matcher entityNameMatcher = entityNamePattern.matcher(reportHtml);
        assertThat(entityNameMatcher.find())
                .as("Report should contain Primary Name field")
                .isTrue();
        
        String displayedEntityName = entityNameMatcher.group(1).trim();
        assertThat(displayedEntityName)
                .as("Trace report Primary Name should be P-632")
                .isEqualTo("P-632");
    }

    /**
     * Edge Case: Multiple high-confidence results - report should show best match
     */
    @Test
    @DisplayName("Edge Case: Report shows best match when multiple entities score 1.0")
    void testTraceReportShowsBestMatchWithMultipleHighConfidence() throws Exception {
        // This tests scenarios where query matches multiple entities with score 1.0
        // Report should prefer the entity that best matches the query, not just first result
        
        String searchResponse = mockMvc.perform(get("/v1/search")
                        .param("name", "P-537")  // Another aircraft
                        .param("limit", "10")
                        .param("trace", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities").isArray())
                .andExpect(jsonPath("$.reportUrl").value(notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        Pattern reportUrlPattern = Pattern.compile("/api/reports/([a-f0-9-]+)");
        Matcher matcher = reportUrlPattern.matcher(searchResponse);
        assertThat(matcher.find()).as("Response should contain reportUrl").isTrue();
        String reportUrl = matcher.group(0);
        
        String reportHtml = mockMvc.perform(get(reportUrl))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        Pattern entityNamePattern = Pattern.compile("Primary Name:</span>\\s*<span class=\"detail-value\">([^<]+)</span>");
        Matcher entityNameMatcher = entityNamePattern.matcher(reportHtml);
        assertThat(entityNameMatcher.find())
                .as("Report should contain Primary Name field")
                .isTrue();
        
        String displayedEntityName = entityNameMatcher.group(1).trim();
        assertThat(displayedEntityName)
                .as("Trace report should show P-537 (the queried entity)")
                .isEqualTo("P-537");
    }
}
