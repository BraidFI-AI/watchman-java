package io.moov.watchman.api;

import io.moov.watchman.report.ReportRenderer;
import io.moov.watchman.report.TraceSummaryService;
import io.moov.watchman.trace.ScoringTrace;
import io.moov.watchman.trace.TraceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Production-readiness tests for error handling scenarios.
 * Tests real-world failure cases that must return proper JSON errors.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerProductionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TraceRepository traceRepository;

    @MockBean
    private ReportRenderer reportRenderer;

    @MockBean
    private TraceSummaryService summaryService;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public TestTimeoutController testTimeoutController() {
            return new TestTimeoutController();
        }
    }

    // Test controller for simulating timeout scenarios
    @RestController
    static class TestTimeoutController {
        
        @GetMapping("/test/timeout")
        public String timeout() throws SQLException {
            throw new SQLException("Connection timed out", "08001");
        }
    }

    @Test
    void testReportNotFoundReturnsJsonError() throws Exception {
        // Mock: trace not found
        when(traceRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        
        // Expect: 404 with JSON error body (not HTML)
        // NOTE: Request with Accept: application/json to ensure JSON response (not HTML)
        mockMvc.perform(get("/api/reports/invalid-session-123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("invalid-session-123")))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/reports/invalid-session-123"))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testBatchValidationReturnsJsonError() throws Exception {
        // Invalid batch request: empty items array
        String emptyBatchRequest = """
            {
                "items": []
            }
            """;
        
        // Expect: 400 with JSON error explaining the problem
        mockMvc.perform(post("/v2/search/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emptyBatchRequest))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("item")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void testBatchSizeLimitReturnsJsonError() throws Exception {
        // Build request with 1001 items (exceeds MAX_BATCH_SIZE of 1000)
        StringBuilder itemsJson = new StringBuilder("[");
        for (int i = 0; i < 1001; i++) {
            if (i > 0) itemsJson.append(",");
            itemsJson.append(String.format("{\"requestId\":\"%d\",\"name\":\"Test %d\"}", i, i));
        }
        itemsJson.append("]");
        
        String oversizedBatchRequest = String.format("""
            {
                "items": %s
            }
            """, itemsJson);
        
        // Expect: 400 with JSON error explaining size limit
        mockMvc.perform(post("/v2/search/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(oversizedBatchRequest))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("1000")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void testDatabaseTimeoutReturns503() throws Exception {
        // Simulate database timeout (SQLException with timeout state code)
        mockMvc.perform(get("/test/timeout")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value(containsString("timed out")))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void testNullPointerInServiceReturns500() throws Exception {
        // Mock: service throws NullPointerException (programming error)
        when(traceRepository.findBySessionId(anyString())).thenThrow(new NullPointerException("Unexpected null value"));
        
        // Expect: 500 with generic error (don't leak internal details)
        mockMvc.perform(get("/api/reports/test-session")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.requestId").exists());
    }
}
