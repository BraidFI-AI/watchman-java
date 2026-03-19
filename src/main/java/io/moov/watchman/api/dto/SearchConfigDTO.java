package io.moov.watchman.api.dto;

import io.moov.watchman.config.SearchConfig;

/**
 * DTO for SearchConfig - 7 search filtering parameters.
 */
public record SearchConfigDTO(
    double aliasMatchThreshold,
    double highScoreThreshold,
    double tokenCoverageMinimum,
    int multiTokenQueryThreshold,
    double normalThresholdMax,
    double normalThresholdMin,
    int shortQueryTokenThreshold
) {
    public static SearchConfigDTO from(SearchConfig config) {
        return new SearchConfigDTO(
            config.getAliasMatchThreshold(),
            config.getHighScoreThreshold(),
            config.getTokenCoverageMinimum(),
            config.getMultiTokenQueryThreshold(),
            config.getNormalThresholdMax(),
            config.getNormalThresholdMin(),
            config.getShortQueryTokenThreshold()
        );
    }
}
