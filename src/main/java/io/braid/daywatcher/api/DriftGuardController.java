package io.braid.daywatcher.api;

import io.braid.daywatcher.driftguard.DriftGuardReport;
import io.braid.daywatcher.driftguard.DriftGuardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DriftGuard API — exposes model validation results to the admin UI.
 */
@RestController
@RequestMapping("/api/admin/driftguard")
public class DriftGuardController {

    private final DriftGuardService driftGuardService;

    public DriftGuardController(DriftGuardService driftGuardService) {
        this.driftGuardService = driftGuardService;
    }

    /**
     * Run all DriftGuard test cases and return the report.
     * GET /api/admin/driftguard/run
     */
    @GetMapping("/run")
    public ResponseEntity<DriftGuardReport> run() {
        DriftGuardReport report = driftGuardService.runAll();
        return ResponseEntity.ok(report);
    }
}
