package io.moov.watchman.search;

import io.moov.watchman.config.SimilarityConfig;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.similarity.JaroWinklerSimilarity;
import io.moov.watchman.similarity.PhoneticFilter;
import io.moov.watchman.similarity.TextNormalizer;
import io.moov.watchman.trace.ScoringContext;

import java.io.IOException;
import java.io.Writer;

/**
 * Debug utilities for scoring operations.
 * 
 * Ported from Go: pkg/search/similarity.go debug() and DebugSimilarity()
 * 
 * Phase 16 (January 10, 2026): Complete Zone 1 (Scoring Functions) to 100%
 */
public class DebugScoring {
    
    /**
     * Writes debug output to writer if non-null.
     * Null-safe debug logging helper.
     * 
     * Go equivalent: debug(w io.Writer, pattern string, args ...any)
     * 
     * @param w       Writer to output to (null-safe)
     * @param pattern Format pattern
     * @param args    Format arguments
     */
    public static void debug(Writer w, String pattern, Object... args) {
        if (w != null) {
            try {
                w.write(String.format(pattern, args));
            } catch (IOException e) {
                // Ignore - debug output failure should not break execution
            }
        }
    }
    
    /**
     * Calculates similarity score with debug logging output.
     * Logs entity information, score components, and final result.
     * 
     * Go equivalent: DebugSimilarity(w io.Writer, query Entity[Q], index Entity[I]) float64
     * 
     * @param w     Writer for debug output (null-safe)
     * @param query Query entity
     * @param index Index entity
     * @return Final similarity score
     */
    public static double debugSimilarity(Writer w, Entity query, Entity index) {
        // Get detailed score breakdown using EntityScorerImpl
        // TODO: Inject config via constructor when these utilities become Spring-managed beans
        EntityScorer scorer = new EntityScorerImpl(new JaroWinklerSimilarity(
            new TextNormalizer(),
            new PhoneticFilter(true),
            new SimilarityConfig()
        ));
        ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index, ScoringContext.disabled());
        
        debug(w, "=== Debug Similarity ===\n");
        debug(w, "Query: %s (%s)\n", query.name(), query.type());
        debug(w, "Index: %s (%s)\n", index.name(), index.type());
        debug(w, "\n");
        
        // Log component scores
        debug(w, "Score Components:\n");
        debug(w, "  Name Score:       %.4f\n", breakdown.nameScore());
        debug(w, "  Alt Names:        %.4f\n", breakdown.altNamesScore());
        debug(w, "  Gov ID Score:     %.4f\n", breakdown.governmentIdScore());
        debug(w, "  Crypto Score:     %.4f\n", breakdown.cryptoAddressScore());
        debug(w, "  Address Score:    %.4f\n", breakdown.addressScore());
        debug(w, "  Contact Score:    %.4f\n", breakdown.contactScore());
        debug(w, "  Date Score:       %.4f\n", breakdown.dateScore());
        debug(w, "\n");
        
        double finalScore = breakdown.totalWeightedScore();
        debug(w, "Final Score: %.4f\n", finalScore);
        debug(w, "Match: %s (threshold 0.5)\n", finalScore > 0.5);
        debug(w, "\n");
        
        return finalScore;
    }
}
