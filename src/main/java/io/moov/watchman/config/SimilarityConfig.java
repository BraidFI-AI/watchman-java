package io.moov.watchman.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for similarity/fuzzy matching parameters.
 * 
 * All values can be overridden via environment variables or application.properties.
 * 
 * Ported from Go environment variables in:
 * - internal/stringscore/jaro_winkler.go
 * - internal/prepare/pipeline_stopwords.go
 * 
 * Environment variable mapping:
 * - JARO_WINKLER_BOOST_THRESHOLD → watchman.similarity.jaro-winkler-boost-threshold
 * - LENGTH_DIFFERENCE_PENALTY_WEIGHT → watchman.similarity.length-difference-penalty-weight
 * - etc.
 */
@Configuration
@ConfigurationProperties(prefix = "watchman.similarity")
public class SimilarityConfig {

    /**
     * Jaro-Winkler boost threshold (default: 0.7)
     * Only apply prefix boost if base Jaro score >= this threshold
     */
    private double jaroWinklerBoostThreshold = 0.7;

    /**
     * Jaro-Winkler prefix size (default: 4)
     * Number of characters to check for common prefix
     */
    private int jaroWinklerPrefixSize = 4;

    /**
     * Length difference cutoff factor (default: 0.9)
     * If shorter string < (longer string * cutoff), return 0.0
     */
    private double lengthDifferenceCutoffFactor = 0.9;

    /**
     * Length difference penalty weight (default: 0.3)
     * Penalty applied based on length difference
     * Go default: 0.3, Current Java: 0.1
     */
    private double lengthDifferencePenaltyWeight = 0.3;

    /**
     * Different letter penalty weight (default: 0.9)
     * Penalty for mismatched characters in Jaro-Winkler
     */
    private double differentLetterPenaltyWeight = 0.9;

    /**
     * Exact match favoritism (default: 0.0 = disabled)
     * Boost applied to exact matches
     */
    private double exactMatchFavoritism = 0.0;

    /**
     * Unmatched index token weight (default: 0.15)
     * Penalty for tokens in index that don't match query
     */
    private double unmatchedIndexTokenWeight = 0.15;

    /**
     * Disable phonetic filtering (default: false)
     * If true, skip Soundex pre-filter
     */
    private boolean phoneticFilteringDisabled = false;

    /**
     * Keep stopwords (default: false)
     * If true, don't remove stopwords during normalization
     */
    private boolean keepStopwords = false;

    /**
     * Log stopword debugging (default: false)
     * If true, log stopword removal details
     */
    private boolean logStopwordDebugging = false;

    // Getters and setters

    public double getJaroWinklerBoostThreshold() {
        return jaroWinklerBoostThreshold;
    }

    public void setJaroWinklerBoostThreshold(double jaroWinklerBoostThreshold) {
        this.jaroWinklerBoostThreshold = jaroWinklerBoostThreshold;
    }

    public int getJaroWinklerPrefixSize() {
        return jaroWinklerPrefixSize;
    }

    public void setJaroWinklerPrefixSize(int jaroWinklerPrefixSize) {
        this.jaroWinklerPrefixSize = jaroWinklerPrefixSize;
    }

    public double getLengthDifferenceCutoffFactor() {
        return lengthDifferenceCutoffFactor;
    }

    public void setLengthDifferenceCutoffFactor(double lengthDifferenceCutoffFactor) {
        this.lengthDifferenceCutoffFactor = lengthDifferenceCutoffFactor;
    }

    public double getLengthDifferencePenaltyWeight() {
        return lengthDifferencePenaltyWeight;
    }

    public void setLengthDifferencePenaltyWeight(double lengthDifferencePenaltyWeight) {
        this.lengthDifferencePenaltyWeight = lengthDifferencePenaltyWeight;
    }

    public double getDifferentLetterPenaltyWeight() {
        return differentLetterPenaltyWeight;
    }

    public void setDifferentLetterPenaltyWeight(double differentLetterPenaltyWeight) {
        this.differentLetterPenaltyWeight = differentLetterPenaltyWeight;
    }

    public double getExactMatchFavoritism() {
        return exactMatchFavoritism;
    }

    public void setExactMatchFavoritism(double exactMatchFavoritism) {
        this.exactMatchFavoritism = exactMatchFavoritism;
    }

    public double getUnmatchedIndexTokenWeight() {
        return unmatchedIndexTokenWeight;
    }

    public void setUnmatchedIndexTokenWeight(double unmatchedIndexTokenWeight) {
        this.unmatchedIndexTokenWeight = unmatchedIndexTokenWeight;
    }

    public boolean isPhoneticFilteringDisabled() {
        return phoneticFilteringDisabled;
    }

    public void setPhoneticFilteringDisabled(boolean phoneticFilteringDisabled) {
        this.phoneticFilteringDisabled = phoneticFilteringDisabled;
    }

    public boolean isKeepStopwords() {
        return keepStopwords;
    }

    public void setKeepStopwords(boolean keepStopwords) {
        this.keepStopwords = keepStopwords;
    }

    public boolean isLogStopwordDebugging() {
        return logStopwordDebugging;
    }

    public void setLogStopwordDebugging(boolean logStopwordDebugging) {
        this.logStopwordDebugging = logStopwordDebugging;
    }
}
