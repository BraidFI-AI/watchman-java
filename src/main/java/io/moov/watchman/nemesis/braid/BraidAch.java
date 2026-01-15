package io.moov.watchman.nemesis.braid;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ACH payment details for Braid API.
 * Maps to Ach schema in OpenAPI spec.
 * Required: routingNumber, accountNumber, bankAccountType
 * Optional: bankName, rdfiNumberQualifier, address, gatewayRoutingNumber
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BraidAch(
        String routingNumber,      // Required
        String accountNumber,      // Required
        String bankName,           // Optional
        String bankAccountType     // Required: CHECKING or SAVINGS
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String routingNumber;
        private String accountNumber;
        private String bankName;
        private String bankAccountType = "CHECKING";

        public Builder routingNumber(String routingNumber) {
            this.routingNumber = routingNumber;
            return this;
        }

        public Builder accountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
            return this;
        }

        public Builder bankName(String bankName) {
            this.bankName = bankName;
            return this;
        }

        public Builder bankAccountType(String bankAccountType) {
            this.bankAccountType = bankAccountType;
            return this;
        }

        public BraidAch build() {
            return new BraidAch(routingNumber, accountNumber, bankName, bankAccountType);
        }
    }
}
