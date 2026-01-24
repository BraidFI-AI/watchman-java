package io.moov.watchman.trace;

/**
 * Represents the lifecycle phases of scoring a candidate entity.
 * Used to categorize trace events by the stage in the scoring pipeline.
 * 
 * <p><b>Total Phases: 12</b></p>
 * <p><b>Traced Phases: 10</b> - These phases call ctx.record() or ctx.traced() in EntityScorerImpl</p>
 * <p><b>Not Traced: 3</b> - These phases execute but don't write trace entries</p>
 * 
 * <h3>Why Some Phases Aren't Traced:</h3>
 * <ul>
 *   <li><b>TOKENIZATION, PHONETIC_FILTER</b> - Child processes of NAME_COMPARISON and ALT_NAME_COMPARISON.
 *       They execute inside JaroWinklerSimilarity as implementation details.</li>
 *   <li><b>FILTERING</b> - Post-processing step that happens in SearchController after all scoring completes.
 *       Applies minMatch threshold to filter results.</li>
 * </ul>
 * 
 * <p>All 12 phases execute during scoring. Tracing affects observability, not functionality.</p>
 * 
 * @see io.moov.watchman.search.EntityScorerImpl#scoreWithBreakdown
 * @see io.moov.watchman.trace.ScoringContext
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
