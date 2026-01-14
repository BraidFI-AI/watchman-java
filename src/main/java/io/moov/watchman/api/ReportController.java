package io.moov.watchman.api;

import io.moov.watchman.report.ReportRenderer;
import io.moov.watchman.report.TraceSummaryService;
import io.moov.watchman.report.model.ReportSummary;
import io.moov.watchman.trace.ScoringTrace;
import io.moov.watchman.trace.TraceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * REST controller for serving human-readable scoring reports.
 * <p>
 * Retrieves trace data by session ID and renders it as HTML for
 * non-technical users and compliance review.
 */
@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportController {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);
    
    private final TraceRepository traceRepository;
    private final ReportRenderer reportRenderer;
    private final TraceSummaryService summaryService;
    
    public ReportController(TraceRepository traceRepository, ReportRenderer reportRenderer, TraceSummaryService summaryService) {
        this.traceRepository = traceRepository;
        this.reportRenderer = reportRenderer;
        this.summaryService = summaryService;
    }
    
    /**
     * Get a human-readable HTML report for a scoring trace.
     * 
     * GET /api/reports/{sessionId}?format=html
     * 
     * @param sessionId the trace session ID
     * @param format the report format (default: html)
     * @return HTML report or 404 if not found
     */
    @GetMapping(value = "/{sessionId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getReport(
            @PathVariable String sessionId,
            @RequestParam(required = false, defaultValue = "html") String format) {
        
        logger.info("Report request: sessionId={}, format={}", sessionId, format);
        
        // Retrieve trace from repository
        Optional<ScoringTrace> traceOpt = traceRepository.findBySessionId(sessionId);
        
        if (traceOpt.isEmpty()) {
            logger.warn("Trace not found: sessionId={}", sessionId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("<html><body><h1>404 - Report Not Found</h1>" +
                      "<p>No trace data found for session: " + sessionId + "</p></body></html>");
        }
        
        ScoringTrace trace = traceOpt.get();
        
        // Render as HTML (only format supported in Phase 1)
        String html = reportRenderer.renderHtml(trace);
        
        logger.info("Report generated successfully: sessionId={}, size={} bytes", sessionId, html.length());
        
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(html);
    }
    
    /**
     * Get a JSON summary of a scoring trace for programmatic access.
     * 
     * GET /api/reports/{sessionId}/summary
     * 
     * @param sessionId the trace session ID
     * @return JSON summary or 404 if not found
     */
    @GetMapping(value = "/{sessionId}/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReportSummary> getSummary(@PathVariable String sessionId) {
        logger.info("Summary request: sessionId={}", sessionId);
        
        // Retrieve trace from repository
        Optional<ScoringTrace> traceOpt = traceRepository.findBySessionId(sessionId);
        
        if (traceOpt.isEmpty()) {
            logger.warn("Trace not found for summary: sessionId={}", sessionId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        
        ScoringTrace trace = traceOpt.get();
        ReportSummary summary = summaryService.generateSummary(trace);
        
        logger.info("Summary generated: sessionId={}, entities={}", sessionId, summary.totalEntitiesScored());
        
        return ResponseEntity.ok(summary);
    }
}
