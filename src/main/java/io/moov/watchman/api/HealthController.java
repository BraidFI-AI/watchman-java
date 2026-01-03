package io.moov.watchman.api;

import io.moov.watchman.index.EntityIndex;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Root-level health check endpoint for load balancers and orchestrators.
 */
@RestController
public class HealthController {

    private final EntityIndex entityIndex;

    public HealthController(EntityIndex entityIndex) {
        this.entityIndex = entityIndex;
    }

    /**
     * Health check endpoint at root level.
     * GET /health
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        int totalEntities = entityIndex.size();
        String status = totalEntities > 0 ? "healthy" : "starting";
        return ResponseEntity.ok(new HealthResponse(status, totalEntities));
    }

    public record HealthResponse(String status, int entityCount) {}
}
