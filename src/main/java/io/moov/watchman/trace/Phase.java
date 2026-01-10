package io.moov.watchman.trace;

/**
 * Represents the lifecycle phases of scoring a candidate entity.
 * Used to categorize trace events by the stage in the scoring pipeline.
 */
public enum Phase {
    /**
     * Text normalization phase - removing diacritics, lowercasing, punctuation handling
     */
    NORMALIZATION,

    /**
     * Tokenization phase - splitting text into words and generating combinations
     */
    TOKENIZATION,

    /**
     * Phonetic filtering phase - Soundex-based pre-filtering
     */
    PHONETIC_FILTER,

    /**
     * Primary name comparison phase
     */
    NAME_COMPARISON,

    /**
     * Alternate names comparison phase
     */
    ALT_NAME_COMPARISON,

    /**
     * Government ID comparison phase
     */
    GOV_ID_COMPARISON,

    /**
     * Crypto address comparison phase
     */
    CRYPTO_COMPARISON,

    /**
     * Contact (email/phone) comparison phase
     */
    CONTACT_COMPARISON,

    /**
     * Address comparison phase
     */
    ADDRESS_COMPARISON,

    /**
     * Date comparison phase (birth dates, etc.)
     */
    DATE_COMPARISON,

    /**
     * Score aggregation and weighting phase
     */
    AGGREGATION,

    /**
     * Filtering phase - applying minMatch threshold
     */
    FILTERING
}
