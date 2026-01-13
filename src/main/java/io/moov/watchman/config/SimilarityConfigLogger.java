package io.moov.watchman.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Logs similarity configuration values at application startup.
 * Provides visibility into which tuning parameters are active.
 */
@Component
public class SimilarityConfigLogger implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SimilarityConfigLogger.class);

    private final SimilarityConfig config;

    public SimilarityConfigLogger(SimilarityConfig config) {
        this.config = config;
    }

    @Override
    public void run(String... args) {
        log.info("=== Similarity Algorithm Configuration ===");
        log.info("Jaro-Winkler Parameters:");
        log.info("  boost-threshold: {}", config.getJaroWinklerBoostThreshold());
        log.info("  prefix-size: {}", config.getJaroWinklerPrefixSize());

        log.info("Penalty Weights:");
        log.info("  length-difference-cutoff: {}", config.getLengthDifferenceCutoffFactor());
        log.info("  length-difference-penalty: {}", config.getLengthDifferencePenaltyWeight());
        log.info("  different-letter-penalty: {}", config.getDifferentLetterPenaltyWeight());
        log.info("  unmatched-token-penalty: {}", config.getUnmatchedIndexTokenWeight());
        log.info("  exact-match-favoritism: {}", config.getExactMatchFavoritism());

        log.info("Feature Flags:");
        log.info("  phonetic-filtering-disabled: {}", config.isPhoneticFilteringDisabled());
        log.info("  keep-stopwords: {}", config.isKeepStopwords());
        log.info("  log-stopword-debugging: {}", config.isLogStopwordDebugging());
        log.info("==========================================");
    }
}
