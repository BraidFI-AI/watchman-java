package io.moov.watchman.nemesis.braid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response from Braid after creating a customer (individual or business).
 * Contains OFAC screening results.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BraidCustomerResponse(
        Integer id,
        String firstName,  // Individual only
        String lastName,   // Individual only
        String name,       // Business only (or individual full name)
        String email,
        String mobilePhone,
        String idNumber,
        String type,       // INDIVIDUAL or BUSINESS
        String status,     // ACTIVE, BLOCKED, NEEDS_OFAC, PENDING_UNBLOCK, DELETED
        Integer ofacId,
        Object blockedResults,  // JSON object with OFAC match details
        String createdAt,
        String updatedAt
) {
}
