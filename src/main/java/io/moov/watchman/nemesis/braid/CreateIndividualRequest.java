package io.moov.watchman.nemesis.braid;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request to create an individual customer in Braid.
 * Per OpenAPI spec IndividualRequest:
 * Required: firstName, lastName, idNumber, address, productId
 * Optional: middleName, email, mobilePhone, dateOfBirth, idType, ach, subType, customerToken, achCompanyId, externalId
 * Note: idNumber can include dashes (e.g., "123-45-6789")
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateIndividualRequest(
        String firstName,
        String middleName,
        String lastName,
        String email,
        String mobilePhone,
        BraidAddress address,
        String dateOfBirth,  // ISO date format YYYY-MM-DD
        String idType,       // SSN, EIN, ITIN, PASSPORT, DRIVING_LICENSE, etc.
        String idNumber,
        BraidAch ach,
        Integer productId,
        String subType,      // UBO, CUSTOMER, USER
        String customerToken,
        String achCompanyId,
        String externalId
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String firstName;
        private String middleName;
        private String lastName;
        private String email;
        private String mobilePhone;
        private BraidAddress address;
        private String dateOfBirth;
        private String idType = "SSN";
        private String idNumber;
        private BraidAch ach;
        private Integer productId;
        private String subType = "CUSTOMER";
        private String customerToken;
        private String achCompanyId;
        private String externalId;

        public Builder firstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public Builder middleName(String middleName) {
            this.middleName = middleName;
            return this;
        }

        public Builder lastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder mobilePhone(String mobilePhone) {
            this.mobilePhone = mobilePhone;
            return this;
        }

        public Builder address(BraidAddress address) {
            this.address = address;
            return this;
        }

        public Builder dateOfBirth(String dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
            return this;
        }

        public Builder idType(String idType) {
            this.idType = idType;
            return this;
        }

        public Builder idNumber(String idNumber) {
            this.idNumber = idNumber;
            return this;
        }

        public Builder ach(BraidAch ach) {
            this.ach = ach;
            return this;
        }

        public Builder productId(Integer productId) {
            this.productId = productId;
            return this;
        }

        public Builder subType(String subType) {
            this.subType = subType;
            return this;
        }

        public Builder customerToken(String customerToken) {
            this.customerToken = customerToken;
            return this;
        }

        public Builder achCompanyId(String achCompanyId) {
            this.achCompanyId = achCompanyId;
            return this;
        }

        public Builder externalId(String externalId) {
            this.externalId = externalId;
            return this;
        }

        public CreateIndividualRequest build() {
            if (firstName == null || lastName == null || idNumber == null || address == null || productId == null) {
                throw new IllegalArgumentException("firstName, lastName, idNumber, address, and productId are required");
            }
            return new CreateIndividualRequest(
                    firstName, middleName, lastName, email, mobilePhone, address,
                    dateOfBirth, idType, idNumber, ach, productId, subType,
                    customerToken, achCompanyId, externalId
            );
        }
    }
}
