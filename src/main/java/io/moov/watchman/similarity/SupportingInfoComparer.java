package io.moov.watchman.similarity;

import io.moov.watchman.model.HistoricalInfo;
import io.moov.watchman.model.SanctionsInfo;

import java.util.List;

/**
 * Phase 12: Supporting Information Comparison Functions
 * Compares sanctions programs and historical data between entities.
 */
public class SupportingInfoComparer {

    /**
     * Compares sanctions programs between two entities.
     * Returns a score based on program overlap and secondary sanctions status.
     *
     * @param query Query sanctions info
     * @param index Index sanctions info
     * @return Score 0.0-1.0, with 0.8x penalty if secondary status differs
     */
    public static double compareSanctionsPrograms(SanctionsInfo query, SanctionsInfo index) {
        // TODO: Implement
        return 0.0;
    }

    /**
     * Compares historical information (former names, previous flags, etc).
     * Uses JaroWinkler similarity on values where types match.
     *
     * @param queryHist Query historical info list
     * @param indexHist Index historical info list
     * @return Best JaroWinkler score for matching types
     */
    public static double compareHistoricalValues(List<HistoricalInfo> queryHist, List<HistoricalInfo> indexHist) {
        // TODO: Implement
        return 0.0;
    }
}
