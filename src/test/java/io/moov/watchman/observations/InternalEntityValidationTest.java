package io.moov.watchman.observations;

import io.braid.daywatcher.WatchmanApplication;
import io.braid.daywatcher.driftguard.DriftGuardResult;
import io.braid.daywatcher.driftguard.DriftGuardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

/**
 * DriftGuard — Internal Entity Validation
 *
 * Day Watcher's self-policing model validation suite for organizations, vessels, and aircraft.
 * Catches scoring regressions and false positives before they reach production.
 *
 * Test cases defined in: observations/internal-entities.csv
 * Delegates to DriftGuardService — same logic used by the admin UI.
 */
@SpringBootTest(classes = WatchmanApplication.class)
@DisplayName("DriftGuard — Entity Validation")
class InternalEntityValidationTest {

    @Autowired
    private DriftGuardService driftGuardService;

    @Test
    @DisplayName("DriftGuard Entities — All Rows")
    void validateAllEntityRows() throws IOException {
        List<DriftGuardResult> results = driftGuardService.runSuite("entities", "observations/internal-entities.csv");

        System.out.println("\n" + "=".repeat(100));
        System.out.println("DRIFTGUARD — ENTITY VALIDATION");
        System.out.println("Source: observations/internal-entities.csv");
        System.out.println("=".repeat(100));

        int passed = 0;
        for (DriftGuardResult r : results) {
            String matchType = r.shouldMatch() ? "[POSITIVE]" : "[NEGATIVE]";
            String status = r.passed() ? "PASS" : "FAIL";
            System.out.printf("Row %2d %s %-14s | %-40s | %s | %s%n",
                    r.row(), matchType, r.variantType(),
                    truncate(r.query(), 40), status, r.detail());
            if (!r.passed()) {
                System.out.println("         Notes: " + r.notes());
            }
            if (r.passed()) passed++;
        }

        int total = results.size();
        int failed = total - passed;
        System.out.println("\n" + "=".repeat(100));
        System.out.printf("FINAL RESULTS: %d/%d PASSING (%.1f%%)%n", passed, total, (passed * 100.0) / total);
        System.out.printf("  PASS: %d  |  FAIL: %d%n", passed, failed);
        System.out.println("=".repeat(100));
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
