package io.moov.watchman.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
@AutoConfigureMockMvc
public class DayWatcherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testLambdaMetrics() throws Exception {
        mockMvc.perform(get("/api/admin/day-watcher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lambda").exists())
                .andExpect(jsonPath("$.lambda.functionName").exists())
                .andExpect(jsonPath("$.lambda.lastInvocation").exists())
                .andExpect(jsonPath("$.lambda.invocationCount24h").exists())
                .andExpect(jsonPath("$.lambda.averageDuration").exists())
                .andExpect(jsonPath("$.lambda.errorCount24h").exists());
    }

    @Test
    public void testRdsMetrics() throws Exception {
        mockMvc.perform(get("/api/admin/day-watcher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rds").exists())
                .andExpect(jsonPath("$.rds.dbInstanceIdentifier").exists())
                .andExpect(jsonPath("$.rds.cpuUtilization").exists())
                .andExpect(jsonPath("$.rds.databaseConnections").exists())
                .andExpect(jsonPath("$.rds.freeStorageSpace").exists());
    }

    @Test
    public void testEcsTaskMetrics() throws Exception {
        mockMvc.perform(get("/api/admin/day-watcher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ecsTask").exists())
                .andExpect(jsonPath("$.ecsTask.lastTaskStatus").exists())
                .andExpect(jsonPath("$.ecsTask.lastRunTime").exists())
                .andExpect(jsonPath("$.ecsTask.runningTaskCount").exists());
    }

    @Test
    public void testS3Metrics() throws Exception {
        mockMvc.perform(get("/api/admin/day-watcher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s3").exists())
                .andExpect(jsonPath("$.s3.inputObjectCount").exists())
                .andExpect(jsonPath("$.s3.outputObjectCount").exists())
                .andExpect(jsonPath("$.s3.lastModified").exists());
    }

    @Test
    public void testAwsLinks() throws Exception {
        mockMvc.perform(get("/api/admin/day-watcher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links").exists())
                .andExpect(jsonPath("$.links.lambdaFunction").exists())
                .andExpect(jsonPath("$.links.rdsInstance").exists())
                .andExpect(jsonPath("$.links.ecsCluster").exists())
                .andExpect(jsonPath("$.links.s3InputBucket").exists())
                .andExpect(jsonPath("$.links.s3OutputBucket").exists());
    }
}
