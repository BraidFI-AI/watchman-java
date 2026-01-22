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
     * Jaro-Winkler boost threshold (required)
     * Only apply prefix boost if base Jaro score >= this threshold
     */
    private double jaroWinklerBoostThreshold;

    /**
     * Jaro-Winkler prefix size (required)
     * Number of characters to check for common prefix
     */
    private int jaroWinklerPrefixSize;

    /**
     * Length difference cutoff factor (required)
     * If shorter string < (longer string * cutoff), return 0.0
     */
    private double lengthDifferenceCutoffFactor;

    /**
     * Length difference penalty weight (required)
     * Penalty applied based on length difference
     */
    private double lengthDifferencePenaltyWeight;

    /**
     * Different letter penalty weight (required)
     * Penalty for mismatched characters in Jaro-Winkler
     */
    private double differentLetterPenaltyWeight;

    /**
     * Exact match favoritism (required)
     * Boost applied to exact matches (0.0 = disabled)
     */
    private double exactMatchFavoritism;

    /**
     * Unmatched index token weight (required)
     * Penalty for tokens in index that don't match query
     */
    private double unmatchedIndexTokenWeight;

    /**
     * Disable phonetic filtering (required)
     * If true, skip Soundex pre-filter
     */
    private boolean phoneticFilteringDisabled;

    /**
     * Keep stopwords (required)
     * If true, don't remove stopwords during normalization
     */
    private boolean keepStopwords;

    /**
     * Log stopword debugging (required)
     * If true, log stopword removal details
     */
    private boolean logStopwordDebugging;

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
