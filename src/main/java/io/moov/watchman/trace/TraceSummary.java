package io.moov.watchman.trace;

import io.moov.watchman.model.ScoreBreakdown;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Executive summary of a scoring trace for non-technical users.
 * Provides high-level insights and plain English explanations.
 */
public record TraceSummary(
    String sessionId,
    int totalEntitiesScored,
    long totalDurationMs,
    List<PhaseContribution> topPhases,
    String scoreExplanation,
    String performanceInsights,
    List<String> keyInsights
) {
    
    /**
     * Generate a summary from a full trace.
     */
    public static TraceSummary from(ScoringTrace trace) {
        int entitiesScored = calculateEntitiesScored(trace.events());
        List<PhaseContribution> topPhases = calculateTopPhases(trace.events());
        String performance = analyzePerformance(trace.events());
        String scoreExplanation = explainScore(trace.breakdown());
        List<String> insights = generateKeyInsights(trace);
        
        return new TraceSummary(
            trace.sessionId(),
            entitiesScored,
            trace.durationMs(),
            topPhases,
            scoreExplanation,
            performance,
            insights
        );
    }
    
    private static int calculateEntitiesScored(List<ScoringEvent> events) {
        // Each entity goes through all phases, ending with AGGREGATION
        return (int) events.stream()
            .filter(e -> e.phase() == Phase.AGGREGATION)
            .count();
    }
    
    private static List<PhaseContribution> calculateTopPhases(List<ScoringEvent> events) {
        // Count occurrences of each phase
        Map<Phase, Long> phaseCounts = events.stream()
            .collect(Collectors.groupingBy(ScoringEvent::phase, Collectors.counting()));
        
        // Sort by count descending, take top 3
        return phaseCounts.entrySet().stream()
            .map(entry -> new PhaseContribution(entry.getKey(), entry.getValue().intValue()))
            .sorted((a, b) -> Integer.compare(b.count(), a.count()))
            .limit(3)
            .toList();
    }
    
    private static String analyzePerformance(List<ScoringEvent> events) {
        // Find phases with longest average duration
        Map<Phase, List<Long>> phaseDurations = new HashMap<>();
        
        for (ScoringEvent event : events) {
            if (event.data().containsKey("durationMs")) {
                phaseDurations.computeIfAbsent(event.phase(), k -> new ArrayList<>())
                    .add(((Number) event.data().get("durationMs")).longValue());
            }
        }
        
        // Calculate averages and find slowest
        var slowest = phaseDurations.entrySet().stream()
            .map(entry -> {
                double avg = entry.getValue().stream().mapToLong(Long::longValue).average().orElse(0);
                return Map.entry(entry.getKey(), avg);
            })
            .max(Comparator.comparingDouble(Map.Entry::getValue));
        
        if (slowest.isPresent() && slowest.get().getValue() > 10) {
            return slowest.get().getKey() + " is the slowest phase (avg " + 
                   String.format("%.1f", slowest.get().getValue()) + "ms)";
        }
        
        return "All phases performing within normal range";
    }
    
    private static String explainScore(ScoreBreakdown breakdown) {
        if (breakdown == null) {
            return "No score breakdown available";
        }
        
        List<String> contributions = new ArrayList<>();
        
        if (breakdown.nameScore() > 0) {
            contributions.add(String.format("Primary name match: %d%%", (int)(breakdown.nameScore() * 100)));
        }
        if (breakdown.altNamesScore() > 0) {
            contributions.add(String.format("Alternative names: %d%%", (int)(breakdown.altNamesScore() * 100)));
        }
        if (breakdown.addressScore() > 0) {
            contributions.add(String.format("Address match: %d%%", (int)(breakdown.addressScore() * 100)));
        }
        if (breakdown.governmentIdScore() > 0) {
            contributions.add(String.format("Government ID: %d%%", (int)(breakdown.governmentIdScore() * 100)));
        }
        if (breakdown.cryptoAddressScore() > 0) {
            contributions.add(String.format("Cryptocurrency: %d%%", (int)(breakdown.cryptoAddressScore() * 100)));
        }
        if (breakdown.contactScore() > 0) {
            contributions.add(String.format("Contact info: %d%%", (int)(breakdown.contactScore() * 100)));
        }
        if (breakdown.dateScore() > 0) {
            contributions.add(String.format("Date of birth: %d%%", (int)(breakdown.dateScore() * 100)));
        }
        
        if (contributions.isEmpty()) {
            return "No significant matches found";
        }
        
        return "Score factors: " + String.join(", ", contributions) + 
               ". Final weighted score: " + (int)(breakdown.totalWeightedScore() * 100) + "%";
    }
    
    private static List<String> generateKeyInsights(ScoringTrace trace) {
        List<String> insights = new ArrayList<>();
        
        if (trace.breakdown() != null) {
            ScoreBreakdown b = trace.breakdown();
            
            // High name match but low overall score
            if (b.nameScore() > 0.9 && b.totalWeightedScore() < 0.85) {
                insights.add("Strong name match, but limited supporting evidence from other fields");
            }
            
            // Multiple strong indicators
            int strongMatches = 0;
            if (b.nameScore() > 0.85) strongMatches++;
            if (b.altNamesScore() > 0.85) strongMatches++;
            if (b.addressScore() > 0.85) strongMatches++;
            if (b.governmentIdScore() > 0.85) strongMatches++;
            
            if (strongMatches >= 3) {
                insights.add("High confidence match with multiple strong indicators");
            }
            
            // Name-only match
            if (b.nameScore() > 0.8 && b.altNamesScore() == 0 && b.addressScore() == 0 && b.governmentIdScore() == 0) {
                insights.add("Match based solely on name similarity - consider requesting additional identifying information");
            }
        }
        
        // Performance insight
        if (trace.durationMs() > 100) {
            insights.add("Processing took longer than usual (" + trace.durationMs() + "ms) - consider optimizing or reviewing data quality");
        }
        
        return insights;
    }
    
    /**
     * Represents a phase's contribution to the overall scoring process.
     */
    public record PhaseContribution(Phase phase, int count) {}
}
