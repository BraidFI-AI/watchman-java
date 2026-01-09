package io.moov.watchman.search;

/**
 * Represents the result of a name comparison operation.
 * Tracks the similarity score, number of matching terms, and match characteristics.
 * 
 * Used for quality-based score adjustments - matches with insufficient
 * matching terms receive penalties even if the similarity score is high.
 * 
 * Ported from Go: pkg/search/similarity_fuzzy.go nameMatch struct
 */
public class NameMatch {
    private final double score;
    private final int matchingTerms;
    private final int totalTerms;
    private final boolean isExact;
    private final boolean isHistorical;

    public NameMatch(double score, int matchingTerms, int totalTerms, boolean isExact, boolean isHistorical) {
        this.score = score;
        this.matchingTerms = matchingTerms;
        this.totalTerms = totalTerms;
        this.isExact = isExact;
        this.isHistorical = isHistorical;
    }

    /**
     * Similarity score between 0.0 and 1.0
     */
    public double getScore() {
        return score;
    }

    /**
     * Number of query terms that matched
     */
    public int getMatchingTerms() {
        return matchingTerms;
    }

    /**
     * Total number of terms in the query
     */
    public int getTotalTerms() {
        return totalTerms;
    }

    /**
     * True if this was an exact string match
     */
    public boolean isExact() {
        return isExact;
    }

    /**
     * True if this matched a historical name (already has historical penalty applied)
     */
    public boolean isHistorical() {
        return isHistorical;
    }

    @Override
    public String toString() {
        return String.format("NameMatch{score=%.2f, matching=%d/%d, exact=%s, historical=%s}",
                score, matchingTerms, totalTerms, isExact, isHistorical);
    }
}
