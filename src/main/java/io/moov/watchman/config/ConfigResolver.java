package io.moov.watchman.config;

import io.moov.watchman.api.ConfigOverride;
import io.moov.watchman.api.ScoringConfigOverride;
import io.moov.watchman.api.SearchConfigOverride;
import io.moov.watchman.api.SimilarityConfigOverride;
import org.springframework.stereotype.Service;

/**
 * Resolves configuration by merging defaults with optional overrides.
 * Used by POST /v2/search to support per-request config tuning.
 */
@Service
public class ConfigResolver {

    private final SimilarityConfig defaultSimilarityConfig;
    private final ScoringConfig defaultScoringConfig;

    public ConfigResolver(SimilarityConfig defaultSimilarityConfig, ScoringConfig defaultScoringConfig) {
        this.defaultSimilarityConfig = defaultSimilarityConfig;
        this.defaultScoringConfig = defaultScoringConfig;
    }

    /**
     * Resolve configuration by merging defaults with overrides.
     * Null overrides return defaults unchanged.
     */
    public ResolvedConfig resolve(ConfigOverride override) {
        if (override == null) {
            return new ResolvedConfig(
                defaultSimilarityConfig,
                defaultScoringConfig,
                ResolvedConfig.SearchParams.defaults()
            );
        }

        SimilarityConfig similarity = mergeSimilarity(override.similarity());
        ScoringConfig scoring = mergeScoring(override.scoring());
        ResolvedConfig.SearchParams search = mergeSearch(override.search());

        return new ResolvedConfig(similarity, scoring, search);
    }

    /**
     * Merge similarity config with overrides.
     */
    private SimilarityConfig mergeSimilarity(SimilarityConfigOverride override) {
        if (override == null) {
            return defaultSimilarityConfig;
        }

        SimilarityConfig merged = new SimilarityConfig();
        merged.setJaroWinklerBoostThreshold(
            override.jaroWinklerBoostThreshold() != null
                ? override.jaroWinklerBoostThreshold()
                : defaultSimilarityConfig.getJaroWinklerBoostThreshold()
        );
        merged.setJaroWinklerPrefixSize(
            override.jaroWinklerPrefixSize() != null
                ? override.jaroWinklerPrefixSize()
                : defaultSimilarityConfig.getJaroWinklerPrefixSize()
        );
        merged.setLengthDifferenceCutoffFactor(
            override.lengthDifferenceCutoffFactor() != null
                ? override.lengthDifferenceCutoffFactor()
                : defaultSimilarityConfig.getLengthDifferenceCutoffFactor()
        );
        merged.setLengthDifferencePenaltyWeight(
            override.lengthDifferencePenaltyWeight() != null
                ? override.lengthDifferencePenaltyWeight()
                : defaultSimilarityConfig.getLengthDifferencePenaltyWeight()
        );
        merged.setDifferentLetterPenaltyWeight(
            override.differentLetterPenaltyWeight() != null
                ? override.differentLetterPenaltyWeight()
                : defaultSimilarityConfig.getDifferentLetterPenaltyWeight()
        );
        merged.setUnmatchedIndexTokenWeight(
            override.unmatchedIndexTokenWeight() != null
                ? override.unmatchedIndexTokenWeight()
                : defaultSimilarityConfig.getUnmatchedIndexTokenWeight()
        );
        merged.setExactMatchFavoritism(
            override.exactMatchFavoritism() != null
                ? override.exactMatchFavoritism()
                : defaultSimilarityConfig.getExactMatchFavoritism()
        );
        merged.setPhoneticFilteringDisabled(
            override.phoneticFilteringDisabled() != null
                ? override.phoneticFilteringDisabled()
                : defaultSimilarityConfig.isPhoneticFilteringDisabled()
        );
        merged.setKeepStopwords(
            override.keepStopwords() != null
                ? override.keepStopwords()
                : defaultSimilarityConfig.isKeepStopwords()
        );
        merged.setLogStopwordDebugging(
            override.logStopwordDebugging() != null
                ? override.logStopwordDebugging()
                : defaultSimilarityConfig.isLogStopwordDebugging()
        );

        return merged;
    }

    /**
     * Merge scoring config with overrides.
     */
    private ScoringConfig mergeScoring(ScoringConfigOverride override) {
        if (override == null) {
            return defaultScoringConfig;
        }

        ScoringConfig merged = new ScoringConfig();
        merged.setNameWeight(
            override.nameWeight() != null
                ? override.nameWeight()
                : defaultScoringConfig.getNameWeight()
        );
        merged.setAddressWeight(
            override.addressWeight() != null
                ? override.addressWeight()
                : defaultScoringConfig.getAddressWeight()
        );
        merged.setCriticalIdWeight(
            override.criticalIdWeight() != null
                ? override.criticalIdWeight()
                : defaultScoringConfig.getCriticalIdWeight()
        );
        merged.setSupportingInfoWeight(
            override.supportingInfoWeight() != null
                ? override.supportingInfoWeight()
                : defaultScoringConfig.getSupportingInfoWeight()
        );
        merged.setNameEnabled(
            override.nameEnabled() != null
                ? override.nameEnabled()
                : defaultScoringConfig.isNameEnabled()
        );
        merged.setAltNamesEnabled(
            override.altNamesEnabled() != null
                ? override.altNamesEnabled()
                : defaultScoringConfig.isAltNamesEnabled()
        );
        merged.setGovernmentIdEnabled(
            override.governmentIdEnabled() != null
                ? override.governmentIdEnabled()
                : defaultScoringConfig.isGovernmentIdEnabled()
        );
        merged.setCryptoEnabled(
            override.cryptoEnabled() != null
                ? override.cryptoEnabled()
                : defaultScoringConfig.isCryptoEnabled()
        );
        merged.setContactEnabled(
            override.contactEnabled() != null
                ? override.contactEnabled()
                : defaultScoringConfig.isContactEnabled()
        );
        merged.setAddressEnabled(
            override.addressEnabled() != null
                ? override.addressEnabled()
                : defaultScoringConfig.isAddressEnabled()
        );
        merged.setDateEnabled(
            override.dateEnabled() != null
                ? override.dateEnabled()
                : defaultScoringConfig.isDateEnabled()
        );

        return merged;
    }

    /**
     * Merge search params with overrides.
     */
    private ResolvedConfig.SearchParams mergeSearch(SearchConfigOverride override) {
        ResolvedConfig.SearchParams defaults = ResolvedConfig.SearchParams.defaults();

        if (override == null) {
            return defaults;
        }

        return new ResolvedConfig.SearchParams(
            override.minMatch() != null ? override.minMatch() : defaults.minMatch(),
            override.limit() != null ? override.limit() : defaults.limit()
        );
    }
}
