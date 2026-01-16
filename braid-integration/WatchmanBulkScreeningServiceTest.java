package io.ropechain.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ropechain.api.data.Customer;
import io.ropechain.api.data.ofac.OFACResult;
import io.ropechain.api.enums.AlertEnums;
import io.ropechain.api.enums.OFACEnums;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TDD tests for WatchmanBulkScreeningService S3 workflow.
 * 
 * Test scenarios:
 * 1. Export customers to NDJSON format
 * 2. Upload NDJSON to S3
 * 3. Submit bulk job with s3InputPath
 * 4. Poll for completion
 * 5. Download matches from S3
 * 6. Transform matches to OFACResult
 * 7. Create alerts via existing service
 */
class WatchmanBulkScreeningServiceTest {

    private WatchmanBulkScreeningService service;
    
    @Mock
    private RestTemplate restTemplate;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @Mock
    private S3Client s3Client;
    
    @Mock
    private AlertCreationService alertCreationService;
    
    @Mock
    private CustomerService customerService;
    
    @Mock
    private OFACService ofacService;
    
    private final String watchmanUrl = "http://localhost:8084";
    private final String inputBucket = "watchman-input";
    private final String resultsBucket = "watchman-results";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new WatchmanBulkScreeningService(
            restTemplate, 
            new ObjectMapper(), 
            s3Client,
            alertCreationService,
            customerService,
            ofacService,
            watchmanUrl,
            inputBucket,
            resultsBucket
        );
    }

    @Test
    void testExportCustomersToNdjson() throws Exception {
        // RED: Test that customers export to NDJSON format
        List<Customer> customers = List.of(
            createCustomer("cust_001", "John Doe", "INDIVIDUAL"),
            createCustomer("cust_002", "ACME Corp", "BUSINESS")
        );
        
        String ndjson = service.exportToNdjson(customers);
        
        String[] lines = ndjson.split("\n");
        assertEquals(2, lines.length);
        
        // Verify first line is valid JSON with correct transformation
        ObjectMapper mapper = new ObjectMapper();
        JsonNode line1 = mapper.readTree(lines[0]);
        assertEquals("cust_001", line1.get("requestId").asText());
        assertEquals("John Doe", line1.get("name").asText());
        assertEquals("INDIVIDUAL", line1.get("entityType").asText());
        assertTrue(line1.get("source").isNull());
    }

    @Test
    void testUploadNdjsonToS3() {
        // RED: Test NDJSON upload to S3
        String ndjson = "{\"requestId\":\"001\",\"name\":\"Test\"}\n";
        String fileName = "customers-2026-01-16.ndjson";
        
        service.uploadToS3(ndjson, fileName);
        
        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        
        PutObjectRequest request = requestCaptor.getValue();
        assertEquals(inputBucket, request.bucket());
        assertEquals(fileName, request.key());
    }

    @Test
    void testSubmitBulkJobWithS3Path() throws Exception {
        // RED: Test bulk job submission with s3InputPath
        String s3Path = "s3://watchman-input/customers-2026-01-16.ndjson";
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode mockResponse = mapper.createObjectNode()
            .put("jobId", "job-abc123")
            .put("status", "SUBMITTED");
        
        when(restTemplate.postForEntity(
            eq(watchmanUrl + "/v2/batch/bulk-job"),
            any(),
            eq(JsonNode.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.ACCEPTED));
        
        String jobId = service.submitBulkJobWithS3(s3Path);
        
        assertEquals("job-abc123", jobId);
        verify(restTemplate).postForEntity(
            eq(watchmanUrl + "/v2/batch/bulk-job"),
            any(),
            eq(JsonNode.class)
        );
    }

    @Test
    void testPollForCompletion() throws Exception {
        // RED: Test polling logic
        String jobId = "job-abc123";
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode completedResponse = mapper.createObjectNode()
            .put("jobId", jobId)
            .put("status", "COMPLETED")
            .put("percentComplete", 100)
            .put("resultPath", "s3://watchman-results/" + jobId + "/matches.json");
        
        when(restTemplate.getForEntity(
            eq(watchmanUrl + "/v2/batch/bulk-job/" + jobId),
            eq(JsonNode.class)
        )).thenReturn(new ResponseEntity<>(completedResponse, HttpStatus.OK));
        
        String resultPath = service.pollForCompletion(jobId, 1);
        
        assertEquals("s3://watchman-results/" + jobId + "/matches.json", resultPath);
    }

    @Test
    void testDownloadMatchesFromS3() throws Exception {
        // RED: Test downloading matches.json from S3
        String jobId = "job-abc123";
        String matchesJson = "[{\"customerId\":\"001\",\"name\":\"Test\",\"entityId\":\"123\",\"matchScore\":0.95,\"source\":\"US_OFAC\"}]";
        
        ResponseInputStream<GetObjectResponse> stream = new ResponseInputStream<>(
            GetObjectResponse.builder().build(),
            new ByteArrayInputStream(matchesJson.getBytes())
        );
        
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(stream);
        
        List<JsonNode> matches = service.downloadMatches(jobId);
        
        assertEquals(1, matches.size());
        assertEquals("001", matches.get(0).get("customerId").asText());
    }

    @Test
    void testTransformMatchToOFACResult() {
        // RED: Test transformation from match JSON to OFACResult
        ObjectMapper mapper = new ObjectMapper();
        JsonNode match = mapper.createObjectNode()
            .put("customerId", "cust_001")
            .put("name", "Nicolas Maduro")
            .put("entityId", "21200")
            .put("matchScore", 1.0)
            .put("source", "US_OFAC");
        
        Customer customer = createCustomer("cust_001", "Nicolas Maduro", "INDIVIDUAL");
        customer.setId(12345L);
        when(customerService.findByCustomerId("cust_001")).thenReturn(customer);
        
        OFACResult result = service.transformToOFACResult(match);
        
        assertNotNull(result);
        assertEquals(12345L, result.getCustomerId());
        assertEquals("Nicolas Maduro", result.getName());
        assertEquals(1.0, result.getMatchScore());
        assertEquals(OFACEnums.Status.REVIEW, result.getStatus());
    }

    @Test
    void testCreateAlertForMatch() {
        // RED: Test alert creation using existing service
        ObjectMapper mapper = new ObjectMapper();
        JsonNode match = mapper.createObjectNode()
            .put("customerId", "cust_001")
            .put("name", "Nicolas Maduro")
            .put("entityId", "21200")
            .put("matchScore", 1.0)
            .put("source", "US_OFAC");
        
        Customer customer = createCustomer("cust_001", "Nicolas Maduro", "INDIVIDUAL");
        when(customerService.findByCustomerId("cust_001")).thenReturn(customer);
        
        OFACResult ofacResult = new OFACResult();
        ofacResult.setStatus(OFACEnums.Status.REVIEW);
        
        service.createAlertForMatch(match);
        
        verify(alertCreationService).createAlert(
            eq(AlertEnums.Type.OFAC),
            eq(AlertEnums.ContextType.OFAC),
            any(OFACResult.class),
            isNull(),
            anyString(),
            eq(AlertEnums.OfacAlertParam.ENTITY.name())
        );
    }

    @Test
    void testEndToEndWorkflow() throws Exception {
        // RED: Test complete workflow from customers to alerts
        List<Customer> customers = List.of(
            createCustomer("cust_001", "Test User", "INDIVIDUAL")
        );
        
        // Mock S3 upload
        doNothing().when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        
        // Mock job submission
        ObjectMapper mapper = new ObjectMapper();
        JsonNode submitResponse = mapper.createObjectNode()
            .put("jobId", "job-test")
            .put("status", "SUBMITTED");
        when(restTemplate.postForEntity(anyString(), any(), eq(JsonNode.class)))
            .thenReturn(new ResponseEntity<>(submitResponse, HttpStatus.ACCEPTED));
        
        // Mock polling
        JsonNode statusResponse = mapper.createObjectNode()
            .put("status", "COMPLETED")
            .put("resultPath", "s3://watchman-results/job-test/matches.json");
        when(restTemplate.getForEntity(anyString(), eq(JsonNode.class)))
            .thenReturn(new ResponseEntity<>(statusResponse, HttpStatus.OK));
        
        // Mock S3 download
        String matchesJson = "[{\"customerId\":\"cust_001\",\"name\":\"Test User\",\"entityId\":\"123\",\"matchScore\":0.95,\"source\":\"US_OFAC\"}]";
        ResponseInputStream<GetObjectResponse> stream = new ResponseInputStream<>(
            GetObjectResponse.builder().build(),
            new ByteArrayInputStream(matchesJson.getBytes())
        );
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(stream);
        
        // Mock customer lookup
        when(customerService.findByCustomerId("cust_001")).thenReturn(customers.get(0));
        
        // Execute workflow
        service.performNightlyScreening(customers);
        
        // Verify S3 upload happened
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        
        // Verify job submitted
        verify(restTemplate).postForEntity(anyString(), any(), eq(JsonNode.class));
        
        // Verify alert created
        verify(alertCreationService).createAlert(
            eq(AlertEnums.Type.OFAC),
            eq(AlertEnums.ContextType.OFAC),
            any(OFACResult.class),
            isNull(),
            anyString(),
            eq(AlertEnums.OfacAlertParam.ENTITY.name())
        );
    }

    // Helper methods
    private Customer createCustomer(String customerId, String name, String type) {
        Customer customer = new Customer();
        customer.setCustomerId(customerId);
        customer.setName(name);
        customer.setType(type);
        return customer;
    }
}
