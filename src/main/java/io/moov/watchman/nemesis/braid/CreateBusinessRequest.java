package io.moov.watchman.nemesis.braid;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request to create a business customer in Braid.
 * Per OpenAPI spec BusinessRequest:
 * Required: name, businessIdType, idNumber, address, productId
 * Optional: mobilePhone, email, dba, businessEntityType, formationDate, incorporationState, website, ach, achCompanyId, submittedBy, mcc, naics, achCompanyName, externalId
 * CRITICAL: idNumber must be digits only (no dashes!) - API validates this
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateBusinessRequest(
        String name,
        String mobilePhone,
        String email,
        BraidAddress address,
        String businessIdType,  // EIN, SSN, OTHER_ID, TIN, DRIVING_LICENSE, NATIONAL_ID_CARD
        String idNumber,
        String dba,
        String businessEntityType,  // SOLE_PROPRIETOR, LIMITED_LIABILITY_COMPANY, CORPORATION, etc.
        String formationDate,  // ISO date format YYYY-MM-DD
        String incorporationState,
        String website,
        BraidAch ach,
        String achCompanyId,
        String mcc,  // 4 digits
        String naics,  // 6 digits
        Integer productId,
        String achCompanyName,
        String externalId
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String mobilePhone;
        private String email;
        private BraidAddress address;
        private String businessIdType = "EIN";
        private String idNumber;
        private String dba;
        private String businessEntityType;
        private String formationDate;
        private String incorporationState;
        private String website;
        private BraidAch ach;
        private String achCompanyId;
        private String mcc;
        private String naics;
        private Integer productId;
        private String achCompanyName;
        private String externalId;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder mobilePhone(String mobilePhone) {
            this.mobilePhone = mobilePhone;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder address(BraidAddress address) {
            this.address = address;
            return this;
        }

        public Builder businessIdType(String businessIdType) {
            this.businessIdType = businessIdType;
            return this;
        }

        public Builder idNumber(String idNumber) {
            this.idNumber = idNumber;
            return this;
        }

        public Builder dba(String dba) {
            this.dba = dba;
            return this;
        }

        public Builder businessEntityType(String businessEntityType) {
            this.businessEntityType = businessEntityType;
            return this;
        }

        public Builder formationDate(String formationDate) {
            this.formationDate = formationDate;
            return this;
        }

        public Builder incorporationState(String incorporationState) {
            this.incorporationState = incorporationState;
            return this;
        }

        public Builder website(String website) {
            this.website = website;
            return this;
        }

        public Builder ach(BraidAch ach) {
            this.ach = ach;
            return this;
        }

        public Builder achCompanyId(String achCompanyId) {
            this.achCompanyId = achCompanyId;
            return this;
        }

        public Builder mcc(String mcc) {
            this.mcc = mcc;
            return this;
        }

        public Builder naics(String naics) {
            this.naics = naics;
            return this;
        }

        public Builder productId(Integer productId) {
            this.productId = productId;
            return this;
        }

        public Builder achCompanyName(String achCompanyName) {
            this.achCompanyName = achCompanyName;
            return this;
        }

        public Builder externalId(String externalId) {
            this.externalId = externalId;
            return this;
        }

        public CreateBusinessRequest build() {
            if (name == null || idNumber == null || businessIdType == null || address == null || productId == null) {
                throw new IllegalArgumentException("name, idNumber, businessIdType, address, and productId are required");
            }
            return new CreateBusinessRequest(
                    name, mobilePhone, email, address, businessIdType, idNumber, dba,
                    businessEntityType, formationDate, incorporationState, website, ach,
                    achCompanyId, mcc, naics, productId, achCompanyName, externalId
            );
        }
    }
}
