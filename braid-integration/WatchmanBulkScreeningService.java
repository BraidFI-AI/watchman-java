package io.ropechain.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Example Braid integration for AWS Batch bulk screening.
 * 
 * Minimal changes required from Braid perspective:
 * 1. Submit bulk job with customer list
 * 2. Poll for completion
 * 3. Process matches from response
 * 
 * NO changes needed to existing real-time OFAC checks.
 */
@Service
@Slf4j
public class WatchmanBulkScreeningService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${watchman.url:http://localhost:8084}")
    private String watchmanUrl;

    /**
     * Nightly batch screening - runs at 1am EST.
     * Replaces Go Watchman sequential processing with Java batch processing.
     */
    @Scheduled(cron = "0 1 * * *", zone = "America/New_York")
    public void runNightlyBatch() {
        log.info("Starting nightly OFAC batch screening via Watchman Java");
        
        try {
            // Step 1: Get customers to screen from database
            JsonNode customers = getCustomersForScreening();
            if (customers.size() == 0) {
                log.info("No customers to screen");
                return;
            }
            
            // Step 2: Submit bulk job to Watchman Java
            String jobId = submitBulkJob(customers);
            log.info("Submitted bulk job: jobId={}, customers={}", jobId, customers.size());
            
            // Step 3: Poll for completion (max 2 hours)
            boolean completed = waitForCompletion(jobId, 120);
            
            if (!completed) {
                log.error("Bulk job did not complete within 2 hours: jobId={}", jobId);
                // Fallback to Go Watchman or alert ops team
                return;
            }
            
            // Step 4: Process matches
            processMatches(jobId);
            
            log.info("Nightly batch screening complete: jobId={}", jobId);
            
        } catch (Exception e) {
            log.error("Nightly batch screening failed", e);
            // Alert ops team
        }
    }

    /**
     * Submit bulk job to Watchman Java.
     * Returns job ID for tracking.
     */
    private String submitBulkJob(JsonNode customers) {
        try {
            // Build request
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jobName", "nightly-batch-" + java.time.LocalDate.now());
            request.put("minMatch", 0.88);
            request.put("limit", 10);
            
            ArrayNode items = request.putArray("items");
            for (JsonNode customer : customers) {
                ObjectNode item = items.addObject();
                item.put("requestId", customer.get("customerId").asText());
                item.put("name", customer.get("name").asText());
                item.put("entityType", customer.get("type").asText()); // INDIVIDUAL or BUSINESS
                item.putNull("source"); // Screen against all sources
            }
            
            // Submit to Watchman
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);
            
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                watchmanUrl + "/v2/batch/bulk-job",
                entity,
                JsonNode.class
            );
            
            return response.getBody().get("jobId").asText();
            
        } catch (Exception e) {
            log.error("Failed to submit bulk job", e);
            throw new RuntimeException("Failed to submit bulk job", e);
        }
    }

    /**
     * Wait for bulk job to complete.
     * Polls every 30 seconds.
     */
    private boolean waitForCompletion(String jobId, int maxMinutes) {
        int attempts = maxMinutes * 2; // Poll every 30 seconds
        
        for (int i = 0; i < attempts; i++) {
            try {
                ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                    watchmanUrl + "/v2/batch/bulk-job/" + jobId,
                    JsonNode.class
                );
                
                JsonNode status = response.getBody();
                String state = status.get("status").asText();
                int percentComplete = status.get("percentComplete").asInt();
                
                log.info("Bulk job status: jobId={}, state={}, progress={}%", 
                    jobId, state, percentComplete);
                
                if ("COMPLETED".equals(state)) {
                    return true;
                }
                
                if ("FAILED".equals(state)) {
                    log.error("Bulk job failed: jobId={}", jobId);
                    return false;
                }
                
                // Wait 30 seconds before next poll
                Thread.sleep(30_000);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                log.error("Error polling bulk job status: jobId={}", jobId, e);
                return false;
            }
        }
        
        return false;
    }

    /**
     * Process matches from completed bulk job.
     * Creates alerts for any OFAC matches found.
     */
    private void processMatches(String jobId) {
        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                watchmanUrl + "/v2/batch/bulk-job/" + jobId,
                JsonNode.class
            );
            
            JsonNode result = response.getBody();
            int matchedItems = result.get("matchedItems").asInt();
            
            if (matchedItems == 0) {
                log.info("No OFAC matches found in bulk job: jobId={}", jobId);
                return;
            }
            
            log.info("Processing {} OFAC matches: jobId={}", matchedItems, jobId);
            
            JsonNode matches = result.get("matches");
            for (JsonNode match : matches) {
                String customerId = match.get("customerId").asText();
                String name = match.get("name").asText();
                String entityId = match.get("entityId").asText();
                double score = match.get("matchScore").asDouble();
                String source = match.get("source").asText();
                
                // Create OFAC alert in Braid
                createOfacAlert(customerId, name, entityId, score, source);
            }
            
        } catch (Exception e) {
            log.error("Error processing matches: jobId={}", jobId, e);
        }
    }

    /**
     * Create OFAC alert in Braid system.
     * This is existing Braid code - no changes needed.
     */
    private void createOfacAlert(String customerId, String name, String entityId, 
                                 double score, String source) {
        // TODO: Use existing Braid alert service
        log.info("Creating OFAC alert: customerId={}, name={}, entityId={}, score={}, source={}", 
            customerId, name, entityId, score, source);
        
        // Example: ofacService.createAlert(customerId, name, entityId, score, source);
    }

    /**
     * Get customers from database for screening.
     * This is existing Braid code - no changes needed.
     */
    private JsonNode getCustomersForScreening() {
        // TODO: Query Braid database for active customers
        // For POC, return sample data
        ArrayNode customers = objectMapper.createArrayNode();
        
        // Example customers
        ObjectNode customer1 = customers.addObject();
        customer1.put("customerId", "cust_001");
        customer1.put("name", "John Smith");
        customer1.put("type", "INDIVIDUAL");
        
        ObjectNode customer2 = customers.addObject();
        customer2.put("customerId", "cust_002");
        customer2.put("name", "ACME Corporation");
        customer2.put("type", "BUSINESS");
        
        return customers;
    }
}
