package io.moov.watchman.report;

import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.report.model.ReportSummary;
import io.moov.watchman.trace.Phase;
import io.moov.watchman.trace.ScoringEvent;
import io.moov.watchman.trace.ScoringTrace;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders scoring traces as human-readable HTML reports.
 * <p>
 * Converts technical trace data into plain-English explanations
 * suitable for non-technical users and compliance review.
 */
@Service
public class ReportRenderer {
    
    private static final DateTimeFormatter FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault());
    
    private final TraceSummaryService summaryService;
    
    public ReportRenderer(TraceSummaryService summaryService) {
        this.summaryService = summaryService;
    }
    
    /**
     * Render a scoring trace as HTML.
     * 
     * @param trace the scoring trace to render
     * @return HTML string
     */
    public String renderHtml(ScoringTrace trace) {
        if (trace == null) {
            return renderEmptyReport();
        }
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>Score Trace Report - ").append(trace.sessionId()).append("</title>\n");
        html.append("  <style>\n");
        html.append(getCss());
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <div class=\"container\">\n");
        
        // Header
        html.append("    <h1>🔍 Score Trace Report</h1>\n");
        html.append("    <div class=\"meta\">\n");
        html.append("      <p><strong>Session ID:</strong> ").append(trace.sessionId()).append("</p>\n");
        html.append("      <p><strong>Duration:</strong> ").append(trace.durationMs()).append(" ms</p>\n");
        html.append("      <p><strong>Generated:</strong> ").append(FORMATTER.format(Instant.now())).append("</p>\n");
        html.append("    </div>\n");
        
        // Executive Summary (NEW - appears first)
        ReportSummary summary = summaryService.generateSummary(trace);
        html.append(renderExecutiveSummary(summary));
        
        // Score Breakdown
        if (trace.breakdown() != null) {
            html.append(renderScoreBreakdown(trace.breakdown()));
        }
        
        // Phase Timeline
        html.append(renderPhaseTimeline(trace.events()));
        
        // Metadata
        if (trace.metadata() != null && !trace.metadata().isEmpty()) {
            html.append(renderMetadata(trace.metadata()));
        }
        
        html.append("  </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    private String renderExecutiveSummary(ReportSummary summary) {
        StringBuilder html = new StringBuilder();
        html.append("    <section class=\"summary\">\n");
        html.append("      <h2>📊 Executive Summary</h2>\n");
        html.append("      <div class=\"summary-grid\">\n");
        
        // Overall Statistics
        html.append("        <div class=\"summary-card\">\n");
        html.append("          <div class=\"card-title\">Entities Scored</div>\n");
        html.append("          <div class=\"card-value\">").append(summary.totalEntitiesScored()).append("</div>\n");
        html.append("          <div class=\"card-subtitle\">Total candidates evaluated</div>\n");
        html.append("        </div>\n");
        
        html.append("        <div class=\"summary-card\">\n");
        html.append("          <div class=\"card-title\">Average Score</div>\n");
        html.append("          <div class=\"card-value ").append(getScoreClass(summary.averageScore()))
            .append("\">").append(String.format("%.1f%%", summary.averageScore() * 100)).append("</div>\n");
        html.append("          <div class=\"card-subtitle\">Mean match confidence</div>\n");
        html.append("        </div>\n");
        
        html.append("        <div class=\"summary-card\">\n");
        html.append("          <div class=\"card-title\">Best Match</div>\n");
        html.append("          <div class=\"card-value score-high\">")
            .append(String.format("%.1f%%", summary.highestScore() * 100)).append("</div>\n");
        html.append("          <div class=\"card-subtitle\">Highest scoring entity</div>\n");
        html.append("        </div>\n");
        
        html.append("        <div class=\"summary-card\">\n");
        html.append("          <div class=\"card-title\">Processing Time</div>\n");
        html.append("          <div class=\"card-value\">").append(summary.totalDurationMs()).append(" ms</div>\n");
        html.append("          <div class=\"card-subtitle\">Total execution time</div>\n");
        html.append("        </div>\n");
        
        html.append("      </div>\n");
        
        // Score Anatomy - Phase Contributions
        if (summary.phaseContributions() != null && !summary.phaseContributions().isEmpty()) {
            html.append("      <div class=\"score-anatomy\">\n");
            html.append("        <h3>🔬 Score Anatomy - What Contributed to Matches</h3>\n");
            html.append("        <p class=\"subtitle\">Understanding the 9 scoring components and their impact</p>\n");
            html.append("        <div class=\"contribution-list\">\n");
            
            summary.phaseContributions().entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(entry -> {
                    Phase phase = entry.getKey();
                    double contribution = entry.getValue();
                    html.append("          <div class=\"contribution-item\">\n");
                    html.append("            <div class=\"contribution-header\">\n");
                    html.append("              <span class=\"phase-name\">")
                        .append(formatPhaseName(phase)).append("</span>\n");
                    html.append("              <span class=\"contribution-value ").append(getScoreClass(contribution))
                        .append("\">").append(String.format("%.0f%%", contribution * 100)).append("</span>\n");
                    html.append("            </div>\n");
                    html.append("            <div class=\"contribution-bar\">\n");
                    html.append("              <div class=\"contribution-fill ").append(getScoreClass(contribution))
                        .append("\" style=\"width: ").append(contribution * 100).append("%\"></div>\n");
                    html.append("            </div>\n");
                    html.append("            <div class=\"phase-explanation\">")
                        .append(explainPhase(phase)).append("</div>\n");
                    html.append("          </div>\n");
                });
            
            html.append("        </div>\n");
            html.append("      </div>\n");
        }
        
        // Performance Insights
        if (summary.phaseTimings() != null && !summary.phaseTimings().isEmpty()) {
            html.append("      <div class=\"performance-insights\">\n");
            html.append("        <h3>⚡ Performance Insights</h3>\n");
            
            if (summary.slowestPhase() != null) {
                html.append("        <p class=\"insight\"><strong>Slowest Phase:</strong> ")
                    .append(formatPhaseName(summary.slowestPhase()))
                    .append(" (").append(summary.phaseTimings().get(summary.slowestPhase())).append(" ms)</p>\n");
            }
            
            html.append("      </div>\n");
        }
        
        html.append("    </section>\n");
        return html.toString();
    }
    
    private String renderScoreBreakdown(ScoreBreakdown breakdown) {
        StringBuilder html = new StringBuilder();
        html.append("    <section class=\"score-breakdown\">\n");
        html.append("      <h2>📊 Score Breakdown</h2>\n");
        
        html.append("      <div class=\"score-item\">\n");
        html.append("        <div class=\"score-label\">Name Matching</div>\n");
        html.append("        <div class=\"score-bar\">\n");
        html.append("          <div class=\"score-fill ").append(getScoreClass(breakdown.nameScore()))
            .append("\" style=\"width: ").append(breakdown.nameScore() * 100).append("%\"></div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"score-value\">").append(String.format("%.2f%%", breakdown.nameScore() * 100)).append("</div>\n");
        html.append("        <div class=\"score-explanation\">")
            .append(explainNameScore(breakdown.nameScore())).append("</div>\n");
        html.append("      </div>\n");
        
        html.append("      <div class=\"score-item\">\n");
        html.append("        <div class=\"score-label\">Alt Names Matching</div>\n");
        html.append("        <div class=\"score-bar\">\n");
        html.append("          <div class=\"score-fill ").append(getScoreClass(breakdown.altNamesScore()))
            .append("\" style=\"width: ").append(breakdown.altNamesScore() * 100).append("%\"></div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"score-value\">").append(String.format("%.2f%%", breakdown.altNamesScore() * 100)).append("</div>\n");
        html.append("      </div>\n");
        
        html.append("      <div class=\"score-item\">\n");
        html.append("        <div class=\"score-label\">Address Matching</div>\n");
        html.append("        <div class=\"score-bar\">\n");
        html.append("          <div class=\"score-fill ").append(getScoreClass(breakdown.addressScore()))
            .append("\" style=\"width: ").append(breakdown.addressScore() * 100).append("%\"></div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"score-value\">").append(String.format("%.2f%%", breakdown.addressScore() * 100)).append("</div>\n");
        html.append("      </div>\n");
        
        html.append("      <div class=\"score-item final\">\n");
        html.append("        <div class=\"score-label\">Total Weighted Score</div>\n");
        html.append("        <div class=\"score-bar\">\n");
        html.append("          <div class=\"score-fill ").append(getScoreClass(breakdown.totalWeightedScore()))
            .append("\" style=\"width: ").append(breakdown.totalWeightedScore() * 100).append("%\"></div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"score-value\">").append(String.format("%.2f%%", breakdown.totalWeightedScore() * 100)).append("</div>\n");
        html.append("        <div class=\"score-explanation\">")
            .append(explainTotalScore(breakdown.totalWeightedScore())).append("</div>\n");
        html.append("      </div>\n");
        
        html.append("    </section>\n");
        return html.toString();
    }
    
    private String renderPhaseTimeline(List<ScoringEvent> events) {
        StringBuilder html = new StringBuilder();
        html.append("    <section class=\"phase-timeline\">\n");
        html.append("      <h2>⏱️ Scoring Timeline</h2>\n");
        html.append("      <p class=\"subtitle\">Step-by-step breakdown of how the score was calculated</p>\n");
        
        // Group events by phase
        Map<Phase, List<ScoringEvent>> eventsByPhase = events.stream()
            .collect(Collectors.groupingBy(ScoringEvent::phase));
        
        // Render phases in order
        for (Phase phase : Phase.values()) {
            List<ScoringEvent> phaseEvents = eventsByPhase.get(phase);
            if (phaseEvents != null && !phaseEvents.isEmpty()) {
                html.append("      <div class=\"phase-group\">\n");
                html.append("        <div class=\"phase-header\">")
                    .append(formatPhaseName(phase)).append("</div>\n");
                
                for (ScoringEvent event : phaseEvents) {
                    html.append("        <div class=\"event\">\n");
                    html.append("          <div class=\"event-time\">")
                        .append(FORMATTER.format(event.timestamp())).append("</div>\n");
                    html.append("          <div class=\"event-description\">").append(event.description()).append("</div>\n");
                    
                    if (event.data() != null && !event.data().isEmpty()) {
                        html.append("          <div class=\"event-data\">\n");
                        event.data().forEach((key, value) -> {
                            html.append("            <span class=\"data-item\"><strong>")
                                .append(key).append(":</strong> ").append(value).append("</span>\n");
                        });
                        html.append("          </div>\n");
                    }
                    html.append("        </div>\n");
                }
                
                html.append("      </div>\n");
            }
        }
        
        html.append("    </section>\n");
        return html.toString();
    }
    
    private String renderMetadata(Map<String, Object> metadata) {
        StringBuilder html = new StringBuilder();
        html.append("    <section class=\"metadata\">\n");
        html.append("      <h2>📋 Additional Information</h2>\n");
        html.append("      <table>\n");
        
        metadata.forEach((key, value) -> {
            html.append("        <tr>\n");
            html.append("          <td class=\"key\">").append(key).append("</td>\n");
            html.append("          <td class=\"value\">").append(value).append("</td>\n");
            html.append("        </tr>\n");
        });
        
        html.append("      </table>\n");
        html.append("    </section>\n");
        return html.toString();
    }
    
    private String renderEmptyReport() {
        return "<!DOCTYPE html>\n<html><head><title>Empty Report</title></head>" +
               "<body><h1>No trace data available</h1></body></html>\n";
    }
    
    private String getScoreClass(double score) {
        if (score >= 0.7) return "high";
        if (score >= 0.3) return "medium";
        return "low";
    }
    
    private String explainNameScore(double score) {
        if (score >= 0.9) return "Very strong name match - names are nearly identical";
        if (score >= 0.7) return "Strong name match - names are very similar";
        if (score >= 0.5) return "Moderate name match - names share significant similarities";
        if (score >= 0.3) return "Weak name match - some similarities but notable differences";
        return "Very weak name match - names are quite different";
    }
    
    private String explainTotalScore(double score) {
        if (score >= 0.9) return "🔴 HIGH RISK - Very strong match, likely the same entity";
        if (score >= 0.7) return "🟡 MEDIUM RISK - Strong match, requires manual review";
        if (score >= 0.5) return "🟡 MEDIUM RISK - Moderate match, further investigation recommended";
        return "🟢 LOW RISK - Weak match, likely a false positive";
    }
    
    private String explainPhase(Phase phase) {
        return switch (phase) {
            case NORMALIZATION -> "Standardize text (remove accents, lowercase, punctuation) for consistent comparison";
            case TOKENIZATION -> "Break names into words and generate combinations for matching";
            case PHONETIC_FILTER -> "Use sound-alike matching (Soundex) to catch spelling variations";
            case NAME_COMPARISON -> "Compare primary names using similarity algorithm (Jaro-Winkler)";
            case ALT_NAME_COMPARISON -> "Check against known aliases and alternate spellings";
            case GOV_ID_COMPARISON -> "Match government IDs (passport, tax ID) for exact identification";
            case CRYPTO_COMPARISON -> "Compare cryptocurrency wallet addresses";
            case CONTACT_COMPARISON -> "Match email addresses, phone numbers, and fax numbers";
            case ADDRESS_COMPARISON -> "Compare physical addresses and locations";
            case DATE_COMPARISON -> "Compare birth dates, death dates, and other temporal data";
            case AGGREGATION -> "Combine all component scores into final weighted score";
            case FILTERING -> "Apply minimum match threshold to filter out low-confidence results";
        };
    }
    
    private String getCss() {
        return """
            body {
              font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
              line-height: 1.6;
              color: #333;
              background: #f5f5f5;
              margin: 0;
              padding: 20px;
            }
            .container {
              max-width: 900px;
              margin: 0 auto;
              background: white;
              padding: 30px;
              border-radius: 8px;
              box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
            h1 {
              color: #2c3e50;
              border-bottom: 3px solid #3498db;
              padding-bottom: 10px;
            }
            h2 {
              color: #34495e;
              margin-top: 30px;
              border-left: 4px solid #3498db;
              padding-left: 15px;
            }
            .meta {
              background: #ecf0f1;
              padding: 15px;
              border-radius: 5px;
              margin: 20px 0;
            }
            .meta p {
              margin: 5px 0;
            }
            .subtitle {
              color: #7f8c8d;
              font-style: italic;
              margin-top: -10px;
            }
            .score-breakdown {
              margin: 30px 0;
            }
            .score-item {
              margin: 20px 0;
              padding: 15px;
              background: #f8f9fa;
              border-radius: 5px;
            }
            .score-item.final {
              background: #e8f4f8;
              border: 2px solid #3498db;
            }
            .score-label {
              font-weight: bold;
              margin-bottom: 8px;
              color: #2c3e50;
            }
            .score-bar {
              height: 30px;
              background: #e0e0e0;
              border-radius: 15px;
              overflow: hidden;
              margin: 10px 0;
            }
            .score-fill {
              height: 100%;
              transition: width 0.3s ease;
              border-radius: 15px;
            }
            .score-fill.high {
              background: linear-gradient(90deg, #e74c3c, #c0392b);
            }
            .score-fill.medium {
              background: linear-gradient(90deg, #f39c12, #d68910);
            }
            .score-fill.low {
              background: linear-gradient(90deg, #27ae60, #229954);
            }
            .score-value {
              font-size: 18px;
              font-weight: bold;
              color: #2c3e50;
              margin: 5px 0;
            }
            .score-explanation {
              color: #7f8c8d;
              font-size: 14px;
              margin-top: 8px;
            }
            .phase-timeline {
              margin: 30px 0;
            }
            .phase-group {
              margin: 20px 0;
              border-left: 3px solid #3498db;
              padding-left: 15px;
            }
            .phase-header {
              font-size: 16px;
              font-weight: bold;
              color: #2c3e50;
              margin-bottom: 10px;
            }
            .event {
              margin: 10px 0;
              padding: 10px;
              background: #f8f9fa;
              border-radius: 5px;
            }
            .event-time {
              font-size: 12px;
              color: #7f8c8d;
            }
            .event-description {
              font-weight: 500;
              margin: 5px 0;
            }
            .event-data {
              margin-top: 8px;
              font-size: 14px;
            }
            .data-item {
              display: inline-block;
              margin-right: 15px;
              color: #555;
            }
            .metadata table {
              width: 100%;
              border-collapse: collapse;
              margin-top: 15px;
            }
            .metadata td {
              padding: 10px;
              border-bottom: 1px solid #ecf0f1;
            }
            .metadata td.key {
              font-weight: bold;
              width: 30%;
              color: #2c3e50;
            }
            .metadata td.value {
              color: #555;
            }
            .summary {
              margin: 30px 0;
              padding: 25px;
              background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
              color: white;
              border-radius: 10px;
            }
            .summary h2, .summary h3 {
              color: white;
              border: none;
              margin-top: 20px;
            }
            .summary-grid {
              display: grid;
              grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
              gap: 20px;
              margin: 20px 0;
            }
            .summary-card {
              background: rgba(255, 255, 255, 0.15);
              backdrop-filter: blur(10px);
              padding: 20px;
              border-radius: 8px;
              text-align: center;
              border: 1px solid rgba(255, 255, 255, 0.2);
            }
            .card-title {
              font-size: 14px;
              text-transform: uppercase;
              letter-spacing: 1px;
              opacity: 0.9;
              margin-bottom: 10px;
            }
            .card-value {
              font-size: 36px;
              font-weight: bold;
              margin: 10px 0;
            }
            .card-subtitle {
              font-size: 12px;
              opacity: 0.8;
            }
            .score-anatomy {
              background: rgba(255, 255, 255, 0.1);
              padding: 20px;
              border-radius: 8px;
              margin: 20px 0;
            }
            .contribution-list {
              margin-top: 15px;
            }
            .contribution-item {
              margin: 15px 0;
              padding: 15px;
              background: rgba(255, 255, 255, 0.1);
              border-radius: 6px;
            }
            .contribution-header {
              display: flex;
              justify-content: space-between;
              align-items: center;
              margin-bottom: 8px;
            }
            .phase-name {
              font-weight: 600;
              font-size: 16px;
            }
            .contribution-value {
              font-size: 24px;
              font-weight: bold;
            }
            .contribution-bar {
              height: 20px;
              background: rgba(255, 255, 255, 0.2);
              border-radius: 10px;
              overflow: hidden;
              margin: 10px 0;
            }
            .contribution-fill {
              height: 100%;
              background: rgba(255, 255, 255, 0.6);
              border-radius: 10px;
            }
            .contribution-fill.high {
              background: linear-gradient(90deg, #ffffff, #f0f0f0);
            }
            .phase-explanation {
              font-size: 13px;
              opacity: 0.9;
              margin-top: 8px;
              line-height: 1.5;
            }
            .performance-insights {
              background: rgba(255, 255, 255, 0.1);
              padding: 15px;
              border-radius: 8px;
              margin: 20px 0;
            }
            .insight {
              margin: 10px 0;
              font-size: 15px;
            }
            """;
    }
    
    /**
     * Format phase name with emoji icon for display.
     */
    private String formatPhaseName(Phase phase) {
        return switch (phase) {
            case NORMALIZATION -> "🔤 Normalization";
            case TOKENIZATION -> "✂️ Tokenization";
            case PHONETIC_FILTER -> "🔊 Phonetic Filter";
            case NAME_COMPARISON -> "📝 Name Comparison";
            case ALT_NAME_COMPARISON -> "📋 Alternate Name Comparison";
            case GOV_ID_COMPARISON -> "🆔 Government ID Comparison";
            case CRYPTO_COMPARISON -> "₿ Crypto Address Comparison";
            case ADDRESS_COMPARISON -> "📍 Address Comparison";
            case CONTACT_COMPARISON -> "📞 Contact Comparison";
            case DATE_COMPARISON -> "📅 Date Comparison";
            case AGGREGATION -> "➕ Aggregation";
            case FILTERING -> "🔍 Filtering";
        };
    }
}
