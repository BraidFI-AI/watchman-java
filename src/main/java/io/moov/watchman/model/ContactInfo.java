package io.moov.watchman.model;

/**
 * Contact information for an entity.
 */
public record ContactInfo(
    String emailAddress,
    String phoneNumber,
    String faxNumber,
    String website
) {
    public static ContactInfo empty() {
        return new ContactInfo(null, null, null, null);
    }
}
