package io.braid.daywatcher.driftguard;

/**
 * Result of a single DriftGuard test case validation.
 */
public record DriftGuardResult(
        String suite,
        int row,
        String query,
        String expectedName,
        boolean shouldMatch,
        double scoreFloor,
        double scoreCeiling,
        String variantType,
        String notes,
        boolean passed,
        double actualScore,
        String detail
) {}
