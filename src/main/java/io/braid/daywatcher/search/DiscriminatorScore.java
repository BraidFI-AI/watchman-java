package io.braid.daywatcher.search;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Score and threshold for a specific discriminator.
 * For fuzzy discriminators (address), score represents similarity.
 * For exact discriminators (DOB, ID), matched represents boolean result.
 */
public record DiscriminatorScore(
    Double score,
    Double threshold,
    Boolean matched
) {
    /**
     * Create a fuzzy discriminator with score (e.g., address).
     */
    public static DiscriminatorScore fuzzy(double score, double threshold) {
        return new DiscriminatorScore(score, threshold, score >= threshold);
    }

    /**
     * Create an exact discriminator (e.g., DOB, government ID).
     */
    public static DiscriminatorScore exact(boolean matched) {
        return new DiscriminatorScore(null, null, matched);
    }

    @JsonProperty("score")
    public Double getScore() {
        return score;
    }

    @JsonProperty("threshold")
    public Double getThreshold() {
        return threshold;
    }

    @JsonProperty("matched")
    public Boolean isMatched() {
        return matched;
    }
}
