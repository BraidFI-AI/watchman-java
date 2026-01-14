package io.moov.watchman.nemesis.braid;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Client for Braid API integration.
 * Handles customer creation and OFAC screening.
 */
public class BraidClient {

    private final String baseUrl;
    private final String username;
    private final String apiKey;
    private final Integer defaultProductId;
    private final RestTemplate restTemplate;

    public BraidClient(String baseUrl, String username, String apiKey, Integer defaultProductId) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.apiKey = apiKey;
        this.defaultProductId = defaultProductId;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Create an individual customer in Braid.
     * Triggers OFAC screening automatically.
     */
    public BraidCustomerResponse createIndividualCustomer(CreateIndividualRequest request) {
        String url = baseUrl + "/individual";
        HttpEntity<CreateIndividualRequest> entity = new HttpEntity<>(request, createHeaders());
        
        ResponseEntity<BraidCustomerResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                BraidCustomerResponse.class
        );
        
        return response.getBody();
    }

    /**
     * Create a business customer in Braid.
     * Triggers OFAC screening automatically.
     */
    public BraidCustomerResponse createBusinessCustomer(CreateBusinessRequest request) {
        String url = baseUrl + "/business";
        HttpEntity<CreateBusinessRequest> entity = new HttpEntity<>(request, createHeaders());
        
        ResponseEntity<BraidCustomerResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                BraidCustomerResponse.class
        );
        
        return response.getBody();
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Basic Auth: username:apiKey encoded in Base64
        String auth = username + ":" + apiKey;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + new String(encodedAuth);
        headers.set("Authorization", authHeader);
        
        return headers;
    }
}
