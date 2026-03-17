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
    private double jaroWinklerBoostThreshold = 0.7;

    /**
     * Jaro-Winkler prefix size (required)
     * Number of characters to check for common prefix
     */
    private int jaroWinklerPrefixSize = 4;

    /**
     * Length difference cutoff factor (required)
     * If shorter string < (longer string * cutoff), return 0.0
     */
    private double lengthDifferenceCutoffFactor = 0.9;

    /**
     * Length difference penalty weight (required)
     * Penalty applied based on length difference
     */
    private double lengthDifferencePenaltyWeight = 0.3;

    /**
     * Different letter penalty weight (required)
     * Penalty for mismatched characters in Jaro-Winkler
     */
    private double differentLetterPenaltyWeight = 0.9;

    /**
     * Exact match favoritism (required)
     * Boost applied to exact matches (0.0 = disabled)
     */
    private double exactMatchFavoritism = 0.0;

    /**
     * Unmatched index token weight (required)
     * Penalty for tokens in index that don't match query
     */
    private double unmatchedIndexTokenWeight = 0.15;

    /**
     * Disable phonetic filtering (required)
     * If true, skip Soundex pre-filter
     */
    private boolean phoneticFilteringDisabled = false;

    /**
     * Keep stopwords (required)
     * If true, don't remove stopwords during normalization
     */
    private boolean keepStopwords = false;

    /**
     * Log stopword debugging (required)
     * If true, log stopword removal details
     */
    private boolean logStopwordDebugging = false;

    /**
     * Winkler prefix weight (required).
     * 
     * <p>Weight applied to common prefix in Jaro-Winkler algorithm.
     * Formula: jaro + (prefix * weight * (1 - jaro))
     * 
     * <p>Previously hardcoded as WINKLER_PREFIX_WEIGHT = 0.1 in JaroWinklerSimilarity.java.
     * 
     * Default: 0.1
     */
    private double winklerPrefixWeight = 0.1;

    /**
     * Minimum token length (required).
     * 
     * <p>BSA FIX (Row 17): Prevents matching on ultra-short tokens like "AL-", "ABU-"
     * that appear as standalone aliases.
     * 
     * <p>Aligned with Go implementation which combines short tokens (<=3 chars) with neighbors.
     * 
     * <p>Previously hardcoded as MIN_TOKEN_LENGTH = 3 in JaroWinklerSimilarity.java.
     * 
     * Default: 3
     */
    private int minimumTokenLength = 3;

    /**
     * Phonetic length difference threshold (required).
     * 
     * <p>BSA CRITICAL FIX (Rows 13, 16, 18, 24): Tightened from 30% to 10%.
     * 
     * <p>Maximum length difference ratio allowed for phonetic matching.
     * If (maxLen - minLen) / maxLen > threshold, phonetic match is rejected.
     * 
     * <p>Cases blocked:
     * - SHINRIKYO (9) vs SUNRISE (7) = 22% diff → REJECT
     * - SHINRIKYO (9) vs SOMERSET (8) = 11% diff → REJECT
     * - CECOEX (6) vs CHACHAJEE (9) = 33% diff → REJECT
     * 
     * <p>Previously hardcoded as 0.10 at JaroWinklerSimilarity:358.
     * 
     * Default: 0.10
     */
    private double phoneticLengthDifferenceThreshold = 0.10;

    /**
     * Short token ratio threshold (required).
     * 
     * <p>BSA: Detects short-code entities like "CK ID CO", "LLC".
     * 
     * <p>If this fraction or more of tokens are short (< minimumTokenLength),
     * keeps ALL tokens to allow matching. Otherwise, filters short tokens.
     * 
     * <p>Example: "CK ID CO" has 3/3 = 100% short tokens → keeps all
     * Example: "SMARTMET LLC" has 1/2 = 50% short tokens → filters "LLC"
     * 
     * <p>Previously hardcoded as 0.60 at JaroWinklerSimilarity:465.
     * 
     * Default: 0.60
     */
    private double shortTokenRatioThreshold = 0.60;

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

    public double getWinklerPrefixWeight() {
        return winklerPrefixWeight;
    }

    public void setWinklerPrefixWeight(double winklerPrefixWeight) {
        this.winklerPrefixWeight = winklerPrefixWeight;
    }

    public int getMinimumTokenLength() {
        return minimumTokenLength;
    }

    public void setMinimumTokenLength(int minimumTokenLength) {
        this.minimumTokenLength = minimumTokenLength;
    }

    public double getPhoneticLengthDifferenceThreshold() {
        return phoneticLengthDifferenceThreshold;
    }

    public void setPhoneticLengthDifferenceThreshold(double phoneticLengthDifferenceThreshold) {
        this.phoneticLengthDifferenceThreshold = phoneticLengthDifferenceThreshold;
    }

    public double getShortTokenRatioThreshold() {
        return shortTokenRatioThreshold;
    }

    public void setShortTokenRatioThreshold(double shortTokenRatioThreshold) {
        this.shortTokenRatioThreshold = shortTokenRatioThreshold;
    }

    // Phase 6: JaroWinklerSimilarity query-coverage boost + LanguageDetector (migrated Mar 16, 2026)

    /**
     * Minimum token average score to trigger query-coverage boost.
     * Previously hardcoded as 0.95 at JaroWinklerSimilarity.java:726.
     * Default: 0.95
     */
    private double queryCoverageQualityThreshold = 0.95;

    /**
     * Multiplier applied when all tokens match with high quality (query-coverage boost).
     * Previously hardcoded as 1.08 at JaroWinklerSimilarity.java:733.
     * Default: 1.08
     */
    private double queryCoverageBoostMultiplier = 1.08;

    /**
     * Weight for token-based score in the token/full-string blend.
     * Full-string weight = 1.0 - tokenBlendWeight.
     * Previously hardcoded as 0.6 at JaroWinklerSimilarity.java:741.
     * Default: 0.6
     */
    private double tokenBlendWeight = 0.6;

    /**
     * Minimum confidence score for language detection to be trusted.
     * Below this threshold, defaults to English.
     * Previously hardcoded as 0.5 at LanguageDetector.java:20.
     * Default: 0.5
     */
    private double languageDetectionMinConfidence = 0.5;

    public double getQueryCoverageQualityThreshold() {
        return queryCoverageQualityThreshold;
    }

    public void setQueryCoverageQualityThreshold(double queryCoverageQualityThreshold) {
        this.queryCoverageQualityThreshold = queryCoverageQualityThreshold;
    }

    public double getQueryCoverageBoostMultiplier() {
        return queryCoverageBoostMultiplier;
    }

    public void setQueryCoverageBoostMultiplier(double queryCoverageBoostMultiplier) {
        this.queryCoverageBoostMultiplier = queryCoverageBoostMultiplier;
    }

    public double getTokenBlendWeight() {
        return tokenBlendWeight;
    }

    public void setTokenBlendWeight(double tokenBlendWeight) {
        this.tokenBlendWeight = tokenBlendWeight;
    }

    public double getLanguageDetectionMinConfidence() {
        return languageDetectionMinConfidence;
    }

    public void setLanguageDetectionMinConfidence(double languageDetectionMinConfidence) {
        this.languageDetectionMinConfidence = languageDetectionMinConfidence;
    }
}
