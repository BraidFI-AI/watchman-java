package io.moov.watchman.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for search filtering and threshold parameters.
 * 
 * <p>All values can be overridden via environment variables or application.yml.
 * 
 * <p>BSA/AML Context: These thresholds control match sensitivity and precision.
 * Previously hardcoded in SearchServiceImpl.java:
 * - 0.75: Alias match threshold (BSA critical sensitivity)
 * - 0.95: High score threshold for precision filter
 * - 0.40: Minimum token coverage (40%)
 * - 3: Query token count for coverage filter
 * - 0.88: "Normal max" threshold for adjustment
 * - 0.75: "Normal min" threshold for adjustment
 * 
 * <p>Making these configurable allows:
 * - Audit trail for threshold changes
 * - Risk-based adjustments (high-risk jurisdictions, PEPs, etc.)
 * - A/B testing different thresholds
 * - Compliance with evolving BSA/AML guidance
 */
@Configuration
@ConfigurationProperties(prefix = "watchman.search")
public class SearchConfig {

    /**
     * Alias match threshold (BSA critical).
     * 
     * <p>Entities matched via alias use this lower threshold for better sensitivity.
     * 
     * <p>BSA Context (AL-QAIDA SYRIA case):
     * - Problem: "HURRAS AL-DIN" with alias "AL-QAIDA IN SYRIA" scored 81.8% (below 88%)
     * - Solution: Alias matches use 0.75 threshold
     * - Rationale: False positives acceptable (analyst reviews), false negatives are not
     * 
     * Default: 0.75
     */
    private double aliasMatchThreshold = 0.75;

    /**
     * High score threshold for precision filter.
     * 
     * <p>Queries with 3+ tokens scoring above this threshold require minimum token coverage.
     * 
     * <p>BSA Context (Precision Fix - Feb 14, 2026):
     * - Problem: "Randy San Nicolas" returned 20x 100% matches from single token "San"
     * - Solution: Scores >= 0.95 with 3+ tokens require 40% coverage
     * - Rationale: Single-token matches (33% coverage) too weak for customer name searches
     * 
     * Default: 0.95
     */
    private double highScoreThreshold = 0.95;

    /**
     * Minimum token coverage for high-scoring multi-token queries.
     * 
     * <p>Requires at least this fraction of query tokens to match in result.
     * 
     * <p>Examples:
     * - "Randy San Nicolas" (3 tokens): Requires 2/3 tokens = 67% (passes 40% threshold)
     * - "Hassan" matching "Randy San Nicolas": 1/3 = 33% (fails 40% threshold)
     * 
     * Default: 0.40 (40%)
     */
    private double tokenCoverageMinimum = 0.40;

    /**
     * Query token count threshold for coverage filter.
     * 
     * <p>Only apply token coverage filter if query has at least this many tokens.
     * 
     * <p>Rationale: Short queries (1-2 tokens) like "Muhammad Ali" are common names
     * that need permissive matching. Longer queries are more specific customer names.
     * 
     * Default: 3
     */
    private int multiTokenQueryThreshold = 3;

    /**
     * Maximum "normal" threshold for adjustment.
     * 
     * <p>If caller provides threshold in range [normalThresholdMin, normalThresholdMax],
     * it may be adjusted down for short queries (OFAC parity).
     * 
     * <p>BSA Context: OFAC.gov portal is very permissive with short common names.
     * Auditors compare our results against OFAC, so we need similar sensitivity.
     * 
     * Default: 0.88
     */
    private double normalThresholdMax = 0.88;

    /**
     * Minimum "normal" threshold for adjustment.
     * 
     * <p>If caller provides threshold in range [normalThresholdMin, normalThresholdMax],
     * it may be adjusted down for short queries (OFAC parity).
     * 
     * Default: 0.75
     */
    private double normalThresholdMin = 0.75;

    /**
     * Short query token count threshold.
     * 
     * <p>Queries with <= this many tokens are considered "short" and may use
     * adjusted threshold for OFAC parity.
     * 
     * <p>Examples: "Muhammad Ali" (2 tokens), "Li Wei" (2 tokens)
     * 
     * Default: 2
     */
    private int shortQueryTokenThreshold = 2;

    // Getters and setters

    public double getAliasMatchThreshold() {
        return aliasMatchThreshold;
    }

    public void setAliasMatchThreshold(double aliasMatchThreshold) {
        this.aliasMatchThreshold = aliasMatchThreshold;
    }

    public double getHighScoreThreshold() {
        return highScoreThreshold;
    }

    public void setHighScoreThreshold(double highScoreThreshold) {
        this.highScoreThreshold = highScoreThreshold;
    }

    public double getTokenCoverageMinimum() {
        return tokenCoverageMinimum;
    }

    public void setTokenCoverageMinimum(double tokenCoverageMinimum) {
        this.tokenCoverageMinimum = tokenCoverageMinimum;
    }

    public int getMultiTokenQueryThreshold() {
        return multiTokenQueryThreshold;
    }

    public void setMultiTokenQueryThreshold(int multiTokenQueryThreshold) {
        this.multiTokenQueryThreshold = multiTokenQueryThreshold;
    }

    public double getNormalThresholdMax() {
        return normalThresholdMax;
    }

    public void setNormalThresholdMax(double normalThresholdMax) {
        this.normalThresholdMax = normalThresholdMax;
    }

    public double getNormalThresholdMin() {
        return normalThresholdMin;
    }

    public void setNormalThresholdMin(double normalThresholdMin) {
        this.normalThresholdMin = normalThresholdMin;
    }

    public int getShortQueryTokenThreshold() {
        return shortQueryTokenThreshold;
    }

    public void setShortQueryTokenThreshold(int shortQueryTokenThreshold) {
        this.shortQueryTokenThreshold = shortQueryTokenThreshold;
    }
}
