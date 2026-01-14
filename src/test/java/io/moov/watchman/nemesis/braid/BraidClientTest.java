package io.moov.watchman.nemesis.braid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BraidClient.
 * Uses TDD approach: RED -> GREEN -> REFACTOR
 */
class BraidClientTest {

    private BraidClient client;

    @BeforeEach
    void setUp() {
        // Using actual sandbox credentials for integration tests
        client = new BraidClient(
                "https://api.sandbox.braid.zone",
                "randysandbox",
                "8046edcf-587e-4c3d-a023-2908b756b197",
                5662271
        );
    }

    @Test
    void shouldCreateIndividualCustomer() {
        // Given: Valid individual customer request
        CreateIndividualRequest request = CreateIndividualRequest.builder()
                .firstName("Vladimir")
                .lastName("Putin")
                .idNumber("123-45-6789")
                .dateOfBirth("1952-10-07")
                .email("test@example.com")
                .mobilePhone("555-555-5555")
                .address(BraidAddress.builder()
                        .street("123 Kremlin St")
                        .city("Moscow")
                        .state("CA")
                        .zipCode("90210")
                        .build())
                .ach(BraidAch.builder()
                        .routingNumber("021000021")
                        .accountNumber("1234567890")
                        .bankName("Test Bank")
                        .build())
                .productId(5662271)
                .build();

        // When: Create customer
        BraidCustomerResponse response = client.createIndividualCustomer(request);

        // Then: Should return response with OFAC check
        assertNotNull(response);
        assertNotNull(response.id());
        assertNotNull(response.ofacId(), "OFAC screening should have been performed");
        assertEquals("INDIVIDUAL", response.type());
        
        // Status should be BLOCKED, NEEDS_OFAC, or ACTIVE depending on match
        assertNotNull(response.status());
        System.out.println("Created individual customer with status: " + response.status());
        System.out.println("OFAC ID: " + response.ofacId());
    }

    @Test
    void shouldCreateBusinessCustomer() {
        // Given: Valid business customer request
        CreateBusinessRequest request = CreateBusinessRequest.builder()
                .name("TALIBAN ORGANIZATION")
                .idNumber("123456789")
                .businessIdType("EIN")
                .email("business@example.com")
                .mobilePhone("555-555-5555")
                .address(BraidAddress.builder()
                        .street("456 Terror Ave")
                        .city("Kabul")
                        .state("CA")
                        .zipCode("90210")
                        .build())
                .ach(BraidAch.builder()
                        .routingNumber("021000021")
                        .accountNumber("9876543210")
                        .bankName("Test Bank")
                        .build())
                .productId(5662271)
                .build();

        // When: Create customer
        BraidCustomerResponse response = client.createBusinessCustomer(request);

        // Then: Should return response with OFAC check
        assertNotNull(response);
        assertNotNull(response.id());
        assertNotNull(response.ofacId(), "OFAC screening should have been performed");
        assertEquals("BUSINESS", response.type());
        
        assertNotNull(response.status());
        System.out.println("Created business customer with status: " + response.status());
        System.out.println("OFAC ID: " + response.ofacId());
    }

    @Test
    void shouldHandleApiErrors() {
        // Given: Invalid request (missing required field)
        CreateIndividualRequest invalidRequest = CreateIndividualRequest.builder()
                .firstName("Test")
                .lastName("User")
                .idNumber("123-45-6789")
                .address(BraidAddress.builder()
                        .street("123 Test St")
                        .city("Test City")
                        .state("CA")
                        .zipCode("12345")
                        .build())
                // Missing productId - should fail validation
                .productId(null)
                .build();

        // When/Then: Should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            invalidRequest.toString(); // Force validation
        });
    }

    @Test
    void shouldValidateRequiredFieldsForIndividual() {
        // When/Then: Should throw when missing required fields
        assertThrows(IllegalArgumentException.class, () -> {
            CreateIndividualRequest.builder()
                    .firstName("Test")
                    // Missing lastName
                    .idNumber("123-45-6789")
                    .address(BraidAddress.builder().build())
                    .productId(5662271)
                    .build();
        });
    }

    @Test
    void shouldValidateRequiredFieldsForBusiness() {
        // When/Then: Should throw when missing required fields
        assertThrows(IllegalArgumentException.class, () -> {
            CreateBusinessRequest.builder()
                    .name("Test Business")
                    // Missing idNumber
                    .businessIdType("EIN")
                    .address(BraidAddress.builder().build())
                    .productId(5662271)
                    .build();
        });
    }
}
