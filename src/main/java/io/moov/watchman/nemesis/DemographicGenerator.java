package io.moov.watchman.nemesis;

import net.datafaker.Faker;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Generates realistic demographic data for Nemesis test scenarios.
 * Uses Faker library to create valid-looking SSNs, bank accounts, etc.
 * Supports seeded generation for reproducible tests.
 */
public class DemographicGenerator {

    private final Faker faker;
    private final Random random;

    /**
     * Create generator with random seed (non-reproducible).
     */
    public DemographicGenerator() {
        this.random = new Random();
        this.faker = new Faker(random);
    }

    /**
     * Create generator with specific seed (reproducible).
     *
     * @param seed Seed for random number generator
     */
    public DemographicGenerator(long seed) {
        this.random = new Random(seed);
        this.faker = new Faker(random);
    }

    /**
     * Generate SSN in format XXX-XX-XXXX.
     */
    public String generateSSN() {
        // Avoid invalid SSNs (000, 666, 900-999 for first group)
        int area = 1 + random.nextInt(899);  // 001-899 (skip 000, 666, 900+)
        if (area == 666) {
            area = 667;
        }

        int group = random.nextInt(100);      // 00-99
        int serial = random.nextInt(10000);   // 0000-9999

        return String.format("%03d-%02d-%04d", area, group, serial);
    }

    /**
     * Generate date of birth (18-80 years ago) in ISO format YYYY-MM-DD.
     */
    public String generateDateOfBirth() {
        int yearsAgo = 18 + random.nextInt(63);  // 18-80 years old
        LocalDate dob = LocalDate.now().minusYears(yearsAgo)
                .minusDays(random.nextInt(365));
        return dob.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * Generate phone number in format XXX-XXX-XXXX.
     */
    public String generatePhoneNumber() {
        // Use valid US area codes (avoid 555-01XX reserved for fiction)
        int area = 200 + random.nextInt(800);  // 200-999
        int exchange = 200 + random.nextInt(800);
        int line = random.nextInt(10000);

        return String.format("%03d-%03d-%04d", area, exchange, line);
    }

    /**
     * Generate email address.
     */
    public String generateEmail() {
        return faker.internet().emailAddress();
    }

    /**
     * Generate bank account number (10-12 digits).
     */
    public String generateAccountNumber() {
        int length = 10 + random.nextInt(3);  // 10, 11, or 12 digits
        StringBuilder account = new StringBuilder();
        for (int i = 0; i < length; i++) {
            account.append(random.nextInt(10));
        }
        return account.toString();
    }

    /**
     * Generate routing number (9 digits) with valid ABA check digit.
     * Uses ABA routing number algorithm: 3*(d1+d4+d7) + 7*(d2+d5+d8) + (d3+d6+d9) mod 10 = 0
     */
    public String generateRoutingNumber() {
        // First 2 digits: Federal Reserve routing symbol (01-12)
        int d1 = 0;
        int d2 = 1 + random.nextInt(2);  // 1 or 2 (results in 01-12)
        if (d2 == 2) {
            d1 = 1;
            d2 = random.nextInt(3);  // 10, 11, or 12
        }

        // Next 6 digits: ABA institution identifier (random)
        int d3 = random.nextInt(10);
        int d4 = random.nextInt(10);
        int d5 = random.nextInt(10);
        int d6 = random.nextInt(10);
        int d7 = random.nextInt(10);
        int d8 = random.nextInt(10);

        // Calculate check digit using ABA formula
        // Formula: 3*(d1+d4+d7) + 7*(d2+d5+d8) + (d3+d6+d9) ≡ 0 (mod 10)
        // Solving for d9: d9 = (10 - ((3*(d1+d4+d7) + 7*(d2+d5+d8) + d3+d6) mod 10)) mod 10
        int sum = 3 * (d1 + d4 + d7) + 7 * (d2 + d5 + d8) + d3 + d6;
        int d9 = (10 - (sum % 10)) % 10;

        return String.format("%d%d%d%d%d%d%d%d%d", d1, d2, d3, d4, d5, d6, d7, d8, d9);
    }
}
