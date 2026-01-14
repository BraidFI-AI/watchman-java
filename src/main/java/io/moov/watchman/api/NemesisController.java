package io.moov.watchman.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST API for triggering Nemesis parity testing runs.
 * 
 * Endpoints:
 * - POST /v2/nemesis/trigger - Start a Nemesis run (async)
 * - GET /v2/nemesis/status/{jobId} - Check job status
 * - GET /v2/nemesis/reports - List recent reports
 */
@RestController
@RequestMapping("/v2/nemesis")
public class NemesisController {

    private static final Logger log = LoggerFactory.getLogger(NemesisController.class);
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static final Map<String, NemesisJob> jobs = new ConcurrentHashMap<>();
    
    private final ObjectMapper objectMapper;

    public NemesisController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Trigger a new Nemesis run.
     * POST /v2/nemesis/trigger
     * 
     * @param request Configuration for the Nemesis run
     * @return Job ID and status URL
     */
    @PostMapping("/trigger")
    public ResponseEntity<TriggerResponse> trigger(@RequestBody(required = false) TriggerRequest request) {
        if (request == null) {
            request = new TriggerRequest(100, false, true, true, false, false);  // Default: Java-only, async
        }

        String jobId = generateJobId();
        NemesisJob job = new NemesisJob(jobId, request);
        jobs.put(jobId, job);

        log.info("Nemesis job created: {} (queries={}, java={}, go={}, braid={}, ofac={})", 
            jobId, request.queries(), request.javaEnabled(), request.goEnabled(), 
            request.braidEnabled(), request.includeOfacApi());

        // Execute async
        if (request.async()) {
            executor.submit(() -> executeNemesis(job));
            return ResponseEntity.accepted().body(new TriggerResponse(
                jobId,
                "running",
                "/v2/nemesis/status/" + jobId,
                null,
                null,
                "Nemesis run started asynchronously"
            ));
        } else {
            // Execute synchronously
            executeNemesis(job);
            return ResponseEntity.ok(new TriggerResponse(
                jobId,
                job.status,
                "/v2/nemesis/status/" + jobId,
                job.reportPath,
                job.executionTimeSeconds,
                job.message
            ));
        }
    }

    /**
     * Check status of a Nemesis job.
     * GET /v2/nemesis/status/{jobId}
     */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<StatusResponse> status(@PathVariable String jobId) {
        NemesisJob job = jobs.get(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new StatusResponse(
            jobId,
            job.status,
            job.request.queries(),
            job.request.includeOfacApi(),
            job.startTime,
            job.endTime,
            job.executionTimeSeconds,
            job.reportPath,
            job.message,
            job.logs
        ));
    }

    /**
     * List recent Nemesis reports.
     * GET /v2/nemesis/reports
     */
    @GetMapping("/reports")
    public ResponseEntity<List<ReportInfo>> reports() {
        try {
            Path reportsDir = Paths.get("/data/reports");
            if (!Files.exists(reportsDir)) {
                reportsDir = Paths.get("reports"); // Local dev fallback
            }

            if (!Files.exists(reportsDir)) {
                return ResponseEntity.ok(List.of());
            }

            List<ReportInfo> reports = new ArrayList<>();
            Files.list(reportsDir)
                .filter(p -> p.getFileName().toString().startsWith("nemesis-"))
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .sorted((a, b) -> b.toFile().lastModified() > a.toFile().lastModified() ? 1 : -1)
                .limit(10)
                .forEach(p -> {
                    File file = p.toFile();
                    reports.add(new ReportInfo(
                        file.getName(),
                        file.length(),
                        file.lastModified(),
                        "/v2/nemesis/reports/" + file.getName()
                    ));
                });

            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            log.error("Failed to list reports", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void executeNemesis(NemesisJob job) {
        job.status = "running";
        job.startTime = LocalDateTime.now();
        List<String> logs = new ArrayList<>();

        try {
            // Build command
            List<String> command = new ArrayList<>();
            command.add("python3");
            command.add("scripts/nemesis/run_nemesis.py");

            // Set environment variables
            ProcessBuilder pb = new ProcessBuilder(command);
            Map<String, String> env = pb.environment();
            env.put("PYTHONPATH", "scripts");
            env.put("QUERIES_PER_RUN", String.valueOf(job.request.queries()));
            env.put("COMPARE_IMPLEMENTATIONS", "true"); // Always compare Go
            env.put("COMPARE_EXTERNAL", String.valueOf(job.request.includeOfacApi()));
            
            // Set API URLs (use environment or default to localhost for local dev)
            String javaApiUrl = System.getenv("WATCHMAN_JAVA_API_URL");
            if (javaApiUrl == null || javaApiUrl.isEmpty()) {
                javaApiUrl = "http://localhost:8084";  // Default to local instance
            }
            env.put("WATCHMAN_JAVA_API_URL", javaApiUrl);
            
            String goApiUrl = System.getenv("WATCHMAN_GO_API_URL");
            if (goApiUrl == null || goApiUrl.isEmpty()) {
                goApiUrl = "https://watchman-go.fly.dev";  // Default to production Go instance
            }
            env.put("WATCHMAN_GO_API_URL", goApiUrl);
            
            // Pass repair pipeline configuration
            String repairEnabled = System.getenv("REPAIR_PIPELINE_ENABLED");
            if (repairEnabled != null && !repairEnabled.isEmpty()) {
                env.put("REPAIR_PIPELINE_ENABLED", repairEnabled);
            }
            
            String aiProvider = System.getenv("AI_PROVIDER");
            if (aiProvider != null && !aiProvider.isEmpty()) {
                env.put("AI_PROVIDER", aiProvider);
            }
            
            String aiModel = System.getenv("AI_MODEL");
            if (aiModel != null && !aiModel.isEmpty()) {
                env.put("AI_MODEL", aiModel);
            }
            
            String openaiKey = System.getenv("OPENAI_API_KEY");
            if (openaiKey != null && !openaiKey.isEmpty()) {
                env.put("OPENAI_API_KEY", openaiKey);
            }
            
            String anthropicKey = System.getenv("ANTHROPIC_API_KEY");
            if (anthropicKey != null && !anthropicKey.isEmpty()) {
                env.put("ANTHROPIC_API_KEY", anthropicKey);
            }
            
            String githubToken = System.getenv("GITHUB_TOKEN");
            if (githubToken != null && !githubToken.isEmpty()) {
                env.put("GITHUB_TOKEN", githubToken);
            }

            if (job.request.includeOfacApi()) {
                String ofacApiKey = System.getenv("OFAC_API_KEY");
                if (ofacApiKey != null) {
                    env.put("OFAC_API_KEY", ofacApiKey);
                } else {
                    job.status = "failed";
                    job.message = "OFAC_API_KEY environment variable not set";
                    job.endTime = LocalDateTime.now();
                    return;
                }
            }

            // Set working directory
            File projectRoot = new File(System.getProperty("user.dir"));
            pb.directory(projectRoot);
            pb.redirectErrorStream(true);

            log.info("Executing Nemesis: {}", String.join(" ", command));
            logs.add("Starting Nemesis run...");
            logs.add("Queries: " + job.request.queries());
            logs.add("Include OFAC-API: " + job.request.includeOfacApi());

            Process process = pb.start();

            // Capture output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logs.add(line);
                    log.debug("Nemesis output: {}", line);
                    
                    // Parse report path from output
                    if (line.contains("Report saved to:")) {
                        String path = line.substring(line.indexOf("Report saved to:") + 16).trim();
                        job.reportPath = path;
                    }
                }
            }

            int exitCode = process.waitFor();
            job.endTime = LocalDateTime.now();
            job.executionTimeSeconds = java.time.Duration.between(job.startTime, job.endTime).getSeconds();

            if (exitCode == 0) {
                job.status = "completed";
                job.message = "Nemesis run completed successfully";
                logs.add("✓ Nemesis run completed");
            } else {
                job.status = "failed";
                job.message = "Nemesis run failed with exit code: " + exitCode;
                logs.add("✗ Nemesis run failed");
            }

        } catch (Exception e) {
            log.error("Nemesis execution failed", e);
            job.status = "failed";
            job.message = "Execution error: " + e.getMessage();
            job.endTime = LocalDateTime.now();
            logs.add("ERROR: " + e.getMessage());
        } finally {
            job.logs = logs;
        }
    }

    private String generateJobId() {
        return "nemesis-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    // DTOs
    public record TriggerRequest(
        int queries,
        boolean includeOfacApi,
        boolean async,
        boolean javaEnabled,
        boolean goEnabled,
        boolean braidEnabled
    ) {
        public TriggerRequest {
            if (queries <= 0 || queries > 1000) {
                throw new IllegalArgumentException("queries must be between 1 and 1000");
            }
            // Validate at least one comparison target is enabled
            if (!javaEnabled && !goEnabled && !braidEnabled) {
                throw new IllegalArgumentException("At least one comparison target must be enabled (javaEnabled, goEnabled, or braidEnabled)");
            }
        }
    }

    public record TriggerResponse(
        String jobId,
        String status,
        String statusUrl,
        String reportPath,
        Long executionTimeSeconds,
        String message
    ) {}

    public record StatusResponse(
        String jobId,
        String status,
        int queries,
        boolean includeOfacApi,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long executionTimeSeconds,
        String reportPath,
        String message,
        List<String> logs
    ) {}

    public record ReportInfo(
        String filename,
        long sizeBytes,
        long lastModified,
        String downloadUrl
    ) {}

    private static class NemesisJob {
        final String jobId;
        final TriggerRequest request;
        String status = "pending";
        LocalDateTime startTime;
        LocalDateTime endTime;
        Long executionTimeSeconds;
        String reportPath;
        String message;
        List<String> logs = new ArrayList<>();

        NemesisJob(String jobId, TriggerRequest request) {
            this.jobId = jobId;
            this.request = request;
        }
    }
}
