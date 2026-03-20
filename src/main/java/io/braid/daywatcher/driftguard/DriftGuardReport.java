package io.braid.daywatcher.driftguard;

import java.util.List;

/**
 * DriftGuard run report — returned by the API and displayed in the admin UI.
 */
public record DriftGuardReport(
        String ranAt,
        int total,
        int passed,
        int failed,
        List<DriftGuardResult> individuals,
        List<DriftGuardResult> entities
) {
    public double passRate() {
        return total == 0 ? 0.0 : (passed * 100.0) / total;
    }
}
