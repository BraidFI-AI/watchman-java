package io.moov.watchman.search;

/**
 * Represents field coverage information for entity matching.
 * Tracks what percentage of available fields were actually compared.
 */
public class Coverage {
    private final double ratio;
    private final double criticalRatio;

    public Coverage(double ratio, double criticalRatio) {
        this.ratio = ratio;
        this.criticalRatio = criticalRatio;
    }

    public double getRatio() {
        return ratio;
    }

    public double getCriticalRatio() {
        return criticalRatio;
    }
}
