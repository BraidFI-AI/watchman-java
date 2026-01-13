package io.moov.watchman.api;

import io.moov.watchman.report.ReportRenderer;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * TDD Tests for ReportController.
 * Tests the /api/reports/{sessionId} endpoint for retrieving formatted scoring reports.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportController TDD Tests")
class ReportControllerTest {

    @Mock
    private TraceRepository traceRepository;

    @Mock
    private ReportRenderer reportRenderer;

    private ReportController controller;

    @BeforeEach
    void setUp() {
        controller = new ReportController(traceRepository, reportRenderer);
    }

    @Nested
    @DisplayName("GET /api/reports/{sessionId}")
    class GetReportTests {

        @Test
        @DisplayName("Should return HTML report for valid session ID")
        void shouldReturnHtmlReportForValidSessionId() {
            // Given
            String sessionId = "test-session-123";
            ScoringTrace trace = new ScoringTrace(sessionId, List.of(), Map.of(), null, 100L);
            
            when(traceRepository.findBySessionId(sessionId)).thenReturn(Optional.of(trace));
            when(reportRenderer.renderHtml(trace)).thenReturn("<html>Test Report</html>");

            // When
            ResponseEntity<String> response = controller.getReport(sessionId, "html");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
            assertThat(response.getBody()).contains("Test Report");
        }

        @Test
        @DisplayName("Should return 404 for non-existent session ID")
        void shouldReturn404ForNonExistentSessionId() {
            // Given
            String sessionId = "non-existent";
            when(traceRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

            // When
            ResponseEntity<String> response = controller.getReport(sessionId, "html");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Should default to HTML format when not specified")
        void shouldDefaultToHtmlFormat() {
            // Given
            String sessionId = "test-session";
            ScoringTrace trace = new ScoringTrace(sessionId, List.of(), Map.of(), null, 50L);
            
            when(traceRepository.findBySessionId(sessionId)).thenReturn(Optional.of(trace));
            when(reportRenderer.renderHtml(trace)).thenReturn("<html>Report</html>");

            // When
            ResponseEntity<String> response = controller.getReport(sessionId, null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
        }

        @Test
        @DisplayName("Should include score breakdown in HTML report")
        void shouldIncludeScoreBreakdownInHtmlReport() {
            // Given
            String sessionId = "test-session";
            var breakdown = new io.moov.watchman.model.ScoreBreakdown(0.85, 0.92, 0.0, 0.0, 0.0, 0.0, 0.0, 0.89);
            ScoringTrace trace = new ScoringTrace(sessionId, List.of(), Map.of(), breakdown, 50L);
            
            when(traceRepository.findBySessionId(sessionId)).thenReturn(Optional.of(trace));
            when(reportRenderer.renderHtml(trace)).thenReturn(
                "<html><div class=\"score\">nameScore: 0.85</div></html>"
            );

            // When
            ResponseEntity<String> response = controller.getReport(sessionId, "html");

            // Then
            assertThat(response.getBody()).contains("nameScore");
            assertThat(response.getBody()).contains("0.85");
        }

        @Test
        @DisplayName("Should handle empty trace data gracefully")
        void shouldHandleEmptyTraceDataGracefully() {
            // Given
            String sessionId = "empty-trace";
            ScoringTrace trace = new ScoringTrace(sessionId, List.of(), Map.of(), null, 0L);
            
            when(traceRepository.findBySessionId(sessionId)).thenReturn(Optional.of(trace));
            when(reportRenderer.renderHtml(trace)).thenReturn("<html>No data</html>");

            // When
            ResponseEntity<String> response = controller.getReport(sessionId, "html");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
