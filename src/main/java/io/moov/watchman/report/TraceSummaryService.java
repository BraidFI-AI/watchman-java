package io.moov.watchman.report;

import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.report.model.ReportSummary;
import io.moov.watchman.trace.Phase;
import io.moov.watchman.trace.ScoringEvent;
import io.moov.watchman.trace.ScoringTrace;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating high-level summaries from detailed scoring traces.
 * Analyzes trace data to provide actionable insights for non-technical operators.
 */
@Service
public class TraceSummaryService {

    /**
     * Generate a comprehensive summary from a scoring trace.
     */
    public ReportSummary generateSummary(ScoringTrace trace) {
        if (trace == null || trace.events().isEmpty()) {
            return createEmptySummary(trace != null ? trace.sessionId() : "unknown");
        }

        int totalEntities = calculateTotalEntities(trace);
        Map<Phase, Long> phaseTimings = calculatePhaseTimings(trace);
        Phase slowestPhase = findSlowestPhase(phaseTimings);
        Map<Phase, Double> phaseContributions = calculatePhaseContributions(trace);
        
        return ReportSummary.builder()
            .sessionId(trace.sessionId())
            .totalEntitiesScored(totalEntities)
            .averageScore(calculateAverageScore(trace))
            .highestScore(calculateHighestScore(trace))
            .lowestScore(calculateLowestScore(trace))
            .totalDurationMs(trace.durationMs())
            .phaseContributions(phaseContributions)
            .phaseTimings(phaseTimings)
            .slowestPhase(slowestPhase)
            .topMatches(generateTopMatches(trace))
            .build();
    }

    /**
     * Generate human-readable explanation for a score breakdown.
     */
    public String explainScore(ScoreBreakdown breakdown) {
        if (breakdown == null) {
            return "No scoring data available";
        }

        List<String> contributors = new ArrayList<>();
        
        if (breakdown.nameScore() > 0.0) {
            contributors.add(String.format("Name %d%%", (int)(breakdown.nameScore() * 100)));
        }
        if (breakdown.altNamesScore() > 0.0) {
            contributors.add(String.format("Alt Names %d%%", (int)(breakdown.altNamesScore() * 100)));
        }
        if (breakdown.addressScore() > 0.0) {
            contributors.add(String.format("Address %d%%", (int)(breakdown.addressScore() * 100)));
        }
        if (breakdown.governmentIdScore() > 0.0) {
            contributors.add(String.format("Gov ID %d%%", (int)(breakdown.governmentIdScore() * 100)));
        }
        
        String quality = getScoreQuality(breakdown.totalWeightedScore());
        
        if (contributors.isEmpty()) {
            return String.format("%s match (%.0f%%)", quality, breakdown.totalWeightedScore() * 100);
        }
        
        return String.format("%s match: %s", quality, String.join(", ", contributors));
    }

    // Private helper methods

    private ReportSummary createEmptySummary(String sessionId) {
        return ReportSummary.builder()
            .sessionId(sessionId)
            .totalEntitiesScored(0)
            .averageScore(0.0)
            .highestScore(0.0)
            .lowestScore(0.0)
            .totalDurationMs(0L)
            .phaseContributions(Map.of())
            .phaseTimings(Map.of())
            .slowestPhase(null)
            .topMatches(List.of())
            .build();
    }

    private int calculateTotalEntities(ScoringTrace trace) {
        // Count AGGREGATION events (one per entity scored)
        return (int) trace.events().stream()
            .filter(e -> e.phase() == Phase.AGGREGATION)
            .count();
    }

    private double calculateAverageScore(ScoringTrace trace) {
        ScoreBreakdown breakdown = trace.breakdown();
        if (breakdown == null) {
            return 0.0;
        }
        // For single-entity traces, use the breakdown score
        // For multi-entity, this is simplified (would need per-entity breakdowns)
        return breakdown.totalWeightedScore();
    }

    private double calculateHighestScore(ScoringTrace trace) {
        ScoreBreakdown breakdown = trace.breakdown();
        return breakdown != null ? breakdown.totalWeightedScore() : 0.0;
    }

    private double calculateLowestScore(ScoringTrace trace) {
        ScoreBreakdown breakdown = trace.breakdown();
        return breakdown != null ? breakdown.totalWeightedScore() : 0.0;
    }

    private Map<Phase, Long> calculatePhaseTimings(ScoringTrace trace) {
        Map<Phase, Long> timings = new HashMap<>();
        
        for (ScoringEvent event : trace.events()) {
            Object durationObj = event.data().get("durationMs");
            if (durationObj instanceof Number) {
                long duration = ((Number) durationObj).longValue();
                timings.merge(event.phase(), duration, Long::sum);
            }
        }
        
        return timings;
    }

    private Phase findSlowestPhase(Map<Phase, Long> phaseTimings) {
        return phaseTimings.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    private Map<Phase, Double> calculatePhaseContributions(ScoringTrace trace) {
        ScoreBreakdown breakdown = trace.breakdown();
        if (breakdown == null) {
            return Map.of();
        }

        Map<Phase, Double> contributions = new HashMap<>();
        
        // Map breakdown scores to phases
        if (breakdown.nameScore() > 0.0) {
            contributions.put(Phase.NAME_COMPARISON, breakdown.nameScore());
        }
        if (breakdown.altNamesScore() > 0.0) {
            contributions.put(Phase.ALT_NAME_COMPARISON, breakdown.altNamesScore());
        }
        if (breakdown.addressScore() > 0.0) {
            contributions.put(Phase.ADDRESS_COMPARISON, breakdown.addressScore());
        }
        if (breakdown.governmentIdScore() > 0) {
            contributions.put(Phase.GOV_ID_COMPARISON, breakdown.governmentIdScore());
        }
        if (breakdown.cryptoAddressScore() > 0) {
            contributions.put(Phase.CRYPTO_COMPARISON, breakdown.cryptoAddressScore());
        }
        if (breakdown.contactScore() > 0.0) {
            contributions.put(Phase.CONTACT_COMPARISON, breakdown.contactScore());
        }
        if (breakdown.dateScore() > 0.0) {
            contributions.put(Phase.DATE_COMPARISON, breakdown.dateScore());
        }
        
        return contributions;
    }

    private List<ReportSummary.EntitySummary> generateTopMatches(ScoringTrace trace) {
        // For now, return empty list
        // In full implementation, would parse entity data from trace metadata
        return List.of();
    }

    private String getScoreQuality(double score) {
        if (score >= 0.90) return "Strong";
        if (score >= 0.75) return "Good";
        if (score >= 0.60) return "Moderate";
        return "Weak";
    }
}
