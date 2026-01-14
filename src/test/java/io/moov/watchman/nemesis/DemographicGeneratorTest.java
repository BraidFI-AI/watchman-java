package io.moov.watchman.nemesis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DemographicGeneratorTest {

    @Test
    void shouldGenerateValidSSN() {
        // Given
        DemographicGenerator generator = new DemographicGenerator();

        // When
        String ssn = generator.generateSSN();

        // Then
        assertNotNull(ssn);
        assertTrue(ssn.matches("\\d{3}-\\d{2}-\\d{4}"), "SSN should match pattern XXX-XX-XXXX");
    }

    @Test
    void shouldGenerateDateOfBirth() {
        // Given
        DemographicGenerator generator = new DemographicGenerator();

        // When
        String dob = generator.generateDateOfBirth();

        // Then
        assertNotNull(dob);
        // Should be between 18-80 years ago
        assertTrue(dob.matches("\\d{4}-\\d{2}-\\d{2}"), "DOB should match ISO format YYYY-MM-DD");
    }

    @Test
    void shouldGeneratePhoneNumber() {
        // Given
        DemographicGenerator generator = new DemographicGenerator();

        // When
        String phone = generator.generatePhoneNumber();

        // Then
        assertNotNull(phone);
        assertTrue(phone.matches("\\d{3}-\\d{3}-\\d{4}"), "Phone should match pattern XXX-XXX-XXXX");
    }

    @Test
    void shouldGenerateEmail() {
        // Given
        DemographicGenerator generator = new DemographicGenerator();

        // When
        String email = generator.generateEmail();

        // Then
        assertNotNull(email);
        assertTrue(email.contains("@"));
        assertTrue(email.contains("."));
    }

    @Test
    void shouldGenerateAccountNumber() {
        // Given
        DemographicGenerator generator = new DemographicGenerator();

        // When
        String account = generator.generateAccountNumber();

        // Then
        assertNotNull(account);
        assertTrue(account.matches("\\d{10,12}"), "Account number should be 10-12 digits");
    }

    @Test
    void shouldGenerateRoutingNumber() {
        // Given
        DemographicGenerator generator = new DemographicGenerator();

        // When
        String routing = generator.generateRoutingNumber();

        // Then
        assertNotNull(routing);
        assertTrue(routing.matches("\\d{9}"), "Routing number should be 9 digits");
    }

    @Test
    void shouldGenerateReproducibleDataWithSeed() {
        // Given: same seed
        DemographicGenerator gen1 = new DemographicGenerator(12345L);
        DemographicGenerator gen2 = new DemographicGenerator(12345L);

        // When
        String ssn1 = gen1.generateSSN();
        String ssn2 = gen2.generateSSN();

        // Then: should be identical
        assertEquals(ssn1, ssn2, "Same seed should produce same data");
    }

    @Test
    void shouldGenerateDifferentDataWithDifferentSeeds() {
        // Given: different seeds
        DemographicGenerator gen1 = new DemographicGenerator(12345L);
        DemographicGenerator gen2 = new DemographicGenerator(67890L);

        // When
        String ssn1 = gen1.generateSSN();
        String ssn2 = gen2.generateSSN();

        // Then: should be different
        assertNotEquals(ssn1, ssn2, "Different seeds should produce different data");
    }
}
