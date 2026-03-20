package io.braid.daywatcher.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TDD Test for Infrastructure Observability API.
 * Tests AWS infrastructure metrics and application health.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InfrastructureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/admin/infrastructure returns application metrics")
    void testApplicationMetrics() throws Exception {
        mockMvc.perform(get("/api/admin/infrastructure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").exists())
                .andExpect(jsonPath("$.application.entityCount").isNumber())
                .andExpect(jsonPath("$.application.jvmHeapUsed").isNumber())
                .andExpect(jsonPath("$.application.jvmHeapMax").isNumber())
                .andExpect(jsonPath("$.application.jvmUptime").isNumber())
                .andExpect(jsonPath("$.application.threadCount").isNumber());
    }

    @Test
    @DisplayName("GET /api/admin/infrastructure returns AWS links")
    void testAwsLinks() throws Exception {
        mockMvc.perform(get("/api/admin/infrastructure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links").exists())
                .andExpect(jsonPath("$.links.cloudWatchLogs").isString())
                .andExpect(jsonPath("$.links.ecsService").isString())
                .andExpect(jsonPath("$.links.alb").isString());
    }

    @Test
    @DisplayName("GET /api/admin/infrastructure returns ECS metrics when running in AWS")
    void testEcsMetrics() throws Exception {
        mockMvc.perform(get("/api/admin/infrastructure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ecs").exists());
        // ECS metrics may be null if not running in AWS
        // Test passes if field exists (null is acceptable outside AWS)
    }

    @Test
    @DisplayName("GET /api/admin/infrastructure returns CloudWatch metrics when running in AWS")
    void testCloudWatchMetrics() throws Exception {
        mockMvc.perform(get("/api/admin/infrastructure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cloudwatch").exists());
        // CloudWatch metrics may be null if not running in AWS
        // Test passes if field exists (null is acceptable outside AWS)
    }
}
