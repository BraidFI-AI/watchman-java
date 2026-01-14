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
    void shouldGenerateValidABARoutingNumber() {
        // Given
        DemographicGenerator generator = new DemographicGenerator();

        // When: Generate multiple routing numbers
        for (int i = 0; i < 100; i++) {
            String routing = generator.generateRoutingNumber();

            // Then: Each should pass ABA check digit validation
            assertTrue(isValidABARoutingNumber(routing),
                    "Routing number " + routing + " failed ABA check digit validation");
        }
    }

    /**
     * Validates ABA routing number using check digit algorithm.
     * Formula: 3*(d1+d4+d7) + 7*(d2+d5+d8) + (d3+d6+d9) mod 10 = 0
     */
    private boolean isValidABARoutingNumber(String routing) {
        if (routing == null || routing.length() != 9 || !routing.matches("\\d{9}")) {
            return false;
        }

        int[] digits = routing.chars().map(c -> c - '0').toArray();
        int checksum = 3 * (digits[0] + digits[3] + digits[6])
                + 7 * (digits[1] + digits[4] + digits[7])
                + (digits[2] + digits[5] + digits[8]);

        return checksum % 10 == 0;
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
