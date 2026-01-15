package io.moov.watchman.nemesis.braid;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Address for Braid API requests.
 * Maps to AddressRequest in OpenAPI spec.
 * Fields: type, line1, line2, city, state, postalCode, countryCode
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BraidAddress(
        String type,          // MAILING, RESIDENCE, BUSINESS, OTHER
        String line1,         // Max 40 chars
        String line2,         // Max 40 chars  
        String city,          // Max 40 chars
        String state,
        String postalCode,    // Max 10 chars
        String countryCode
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String type;
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postalCode;
        private String countryCode = "US";

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder line1(String line1) {
            this.line1 = line1;
            return this;
        }

        public Builder line2(String line2) {
            this.line2 = line2;
            return this;
        }

        public Builder city(String city) {
            this.city = city;
            return this;
        }

        public Builder state(String state) {
            this.state = state;
            return this;
        }

        public Builder postalCode(String postalCode) {
            this.postalCode = postalCode;
            return this;
        }

        public Builder countryCode(String countryCode) {
            this.countryCode = countryCode;
            return this;
        }

        public BraidAddress build() {
            return new BraidAddress(type, line1, line2, city, state, postalCode, countryCode);
        }
    }
}
