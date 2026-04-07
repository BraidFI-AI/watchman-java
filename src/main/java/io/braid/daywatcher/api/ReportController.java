package io.braid.daywatcher.api;

import io.braid.daywatcher.report.ReportRenderer;
import io.braid.daywatcher.report.ScoreNarrative;
import io.braid.daywatcher.report.ScoreNarrativeService;
import io.braid.daywatcher.report.TraceSummaryService;
import io.braid.daywatcher.report.model.ReportSummary;
import io.braid.daywatcher.trace.ScoringTrace;
import io.braid.daywatcher.trace.TraceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final ScoreNarrativeService narrativeService;

    public ReportController(TraceRepository traceRepository, ReportRenderer reportRenderer,
                            TraceSummaryService summaryService, ScoreNarrativeService narrativeService) {
        this.traceRepository = traceRepository;
        this.reportRenderer = reportRenderer;
        this.summaryService = summaryService;
        this.narrativeService = narrativeService;
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
        ScoringTrace trace = traceRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new EntityNotFoundException("Report", sessionId));
        
        // Render as HTML (only format supported in Phase 1)
        String html = reportRenderer.renderHtml(trace);
        
        logger.info("Report generated successfully: sessionId={}, size={} bytes", sessionId, html.length());
        
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(html);
    }
    
    /**
     * AI-generated analysis of a scoring trace.
     *
     * GET /api/reports/{sessionId}/analyze?query=Nicolas+Maduro
     *
     * Feeds the full scoring engine source code, live runtime config, and the trace
     * to Claude Opus. Returns a narrative explaining every scoring decision with
     * specific method names, config fields, and runtime values — plus tuning flags
     * pointing at the exact knobs to adjust.
     *
     * Requires ANTHROPIC_API_KEY. Returns 404 if session not found.
     */
    @GetMapping(value = "/{sessionId}/analyze", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ScoreNarrative> analyze(@PathVariable String sessionId) {

        logger.info("Narrative analysis request: sessionId={}", sessionId);

        ScoringTrace trace = traceRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Report", sessionId));

        ScoreNarrative narrative = narrativeService.analyze(trace);
        return ResponseEntity.ok(narrative);
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
        ScoringTrace trace = traceRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new EntityNotFoundException("Report", sessionId));
        ReportSummary summary = summaryService.generateSummary(trace);
        
        logger.info("Summary generated: sessionId={}, entities={}", sessionId, summary.totalEntitiesScored());
        
        return ResponseEntity.ok(summary);
    }
}
