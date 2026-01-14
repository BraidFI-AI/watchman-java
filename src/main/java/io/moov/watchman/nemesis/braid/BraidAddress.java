package io.moov.watchman.nemesis.braid;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Address for Braid API requests.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BraidAddress(
        String street,
        String street2,
        String city,
        String state,
        String zipCode,
        String country
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String street;
        private String street2;
        private String city;
        private String state;
        private String zipCode;
        private String country = "US";

        public Builder street(String street) {
            this.street = street;
            return this;
        }

        public Builder street2(String street2) {
            this.street2 = street2;
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

        public Builder zipCode(String zipCode) {
            this.zipCode = zipCode;
            return this;
        }

        public Builder country(String country) {
            this.country = country;
            return this;
        }

        public BraidAddress build() {
            return new BraidAddress(street, street2, city, state, zipCode, country);
        }
    }
}
