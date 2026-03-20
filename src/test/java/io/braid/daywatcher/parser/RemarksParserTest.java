package io.braid.daywatcher.parser;

import io.braid.daywatcher.parser.RemarksParser.ParsedRemarks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD RED Phase Tests for Phase 3: Identifying Attributes
 * Task 3.1: Parse OFAC Remarks Field
 * 
 * Tests use real OFAC remarks patterns from SDN data.
 * These tests WILL FAIL until RemarksParser is implemented.
 */
@DisplayName("RemarksParser - Extract Identifying Attributes from OFAC Remarks")
class RemarksParserTest {

    private RemarksParser parser;

    @BeforeEach
    void setUp() {
        parser = new RemarksParser();
    }

    @Nested
    @DisplayName("Date of Birth Extraction")
    class DateOfBirthTests {

        @Test
        @DisplayName("RED: Extract DOB in format 'DOB DD MMM YYYY'")
        void extractDOB_standardFormat() {
            // Real OFAC example: "DOB 10 Dec 1948; Secondary sanctions risk..."
            String remarks = "DOB 10 Dec 1948; Secondary sanctions risk: section 1(b) of Executive Order 13224";
            
            Optional<LocalDate> dob = parser.extractDateOfBirth(remarks);
            
            assertThat(dob).isPresent();
            assertThat(dob.get()).isEqualTo(LocalDate.of(1948, 12, 10));
        }

        @Test
        @DisplayName("RED: Extract DOB in format 'DOB DD Mon YYYY'")
        void extractDOB_abbreviatedMonth() {
            // Real OFAC example: "DOB 03 May 1938; POB Egypt..."
            String remarks = "DOB 03 May 1938; POB Egypt; Secondary sanctions risk";
            
            Optional<LocalDate> dob = parser.extractDateOfBirth(remarks);
            
            assertThat(dob).isPresent();
            assertThat(dob.get()).isEqualTo(LocalDate.of(1938, 5, 3));
        }

        @Test
        @DisplayName("RED: Extract DOB in format 'DOB YYYY'")
        void extractDOB_yearOnly() {
            String remarks = "DOB 1946; Chief Ideological Figure";
            
            Optional<LocalDate> dob = parser.extractDateOfBirth(remarks);
            
            assertThat(dob).isPresent();
            assertThat(dob.get()).isEqualTo(LocalDate.of(1946, 1, 1)); // Default to Jan 1
        }

        @Test
        @DisplayName("RED: Return empty when no DOB present")
        void extractDOB_notPresent() {
            String remarks = "Secondary sanctions risk: section 1(b) of Executive Order 13224";
            
            Optional<LocalDate> dob = parser.extractDateOfBirth(remarks);
            
            assertThat(dob).isEmpty();
        }

        @Test
        @DisplayName("RED: Handle null or empty remarks")
        void extractDOB_nullOrEmpty() {
            assertThat(parser.extractDateOfBirth(null)).isEmpty();
            assertThat(parser.extractDateOfBirth("")).isEmpty();
            assertThat(parser.extractDateOfBirth("-0-")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Place of Birth Extraction")
    class PlaceOfBirthTests {

        @Test
        @DisplayName("RED: Extract POB - single location")
        void extractPOB_singleLocation() {
            // Real OFAC example: "DOB 03 May 1938; POB Egypt; Secondary sanctions..."
            String remarks = "DOB 03 May 1938; POB Egypt; Secondary sanctions risk";
            
            Optional<String> pob = parser.extractPlaceOfBirth(remarks);
            
            assertThat(pob).isPresent();
            assertThat(pob.get()).isEqualTo("Egypt");
        }

        @Test
        @DisplayName("RED: Extract POB - city and country")
        void extractPOB_cityAndCountry() {
            // Real OFAC example: "DOB 19 Jun 1951; POB Giza, Egypt; Secondary..."
            String remarks = "DOB 19 Jun 1951; POB Giza, Egypt; Secondary sanctions risk";
            
            Optional<String> pob = parser.extractPlaceOfBirth(remarks);
            
            assertThat(pob).isPresent();
            assertThat(pob.get()).isEqualTo("Giza, Egypt");
        }

        @Test
        @DisplayName("RED: Extract POB - detailed location")
        void extractPOB_detailedLocation() {
            // Real OFAC example: "DOB 19 Apr 1947; POB Nahia, Giza, Egypt; nationality Egypt..."
            String remarks = "DOB 19 Apr 1947; POB Nahia, Giza, Egypt; nationality Egypt";
            
            Optional<String> pob = parser.extractPlaceOfBirth(remarks);
            
            assertThat(pob).isPresent();
            assertThat(pob.get()).isEqualTo("Nahia, Giza, Egypt");
        }

        @Test
        @DisplayName("RED: Return empty when no POB present")
        void extractPOB_notPresent() {
            String remarks = "DOB 10 Dec 1948; Secondary sanctions risk";
            
            Optional<String> pob = parser.extractPlaceOfBirth(remarks);
            
            assertThat(pob).isEmpty();
        }
    }

    @Nested
    @DisplayName("Nationality Extraction")
    class NationalityTests {

        @Test
        @DisplayName("RED: Extract nationality")
        void extractNationality() {
            // Real OFAC example: "...POB Nahia, Giza, Egypt; nationality Egypt; Gender Male..."
            String remarks = "DOB 19 Apr 1947; POB Nahia, Giza, Egypt; nationality Egypt; Gender Male";
            
            ParsedRemarks parsed = parser.parse(remarks);
            
            assertThat(parsed.nationality()).isPresent();
            assertThat(parsed.nationality().get()).isEqualTo("Egypt");
        }

        @Test
        @DisplayName("RED: Return empty when no nationality present")
        void extractNationality_notPresent() {
            String remarks = "DOB 10 Dec 1948; POB Egypt";
            
            ParsedRemarks parsed = parser.parse(remarks);
            
            assertThat(parsed.nationality()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Government ID Extraction")
    class GovernmentIdTests {

        @Test
        @DisplayName("RED: Extract passport number with country")
        void extractGovernmentIds_passport() {
            // Real OFAC example: "...Passport 1084010 (Egypt); alt. Passport 19820215..."
            String remarks = "DOB 19 Jun 1951; POB Giza, Egypt; Passport 1084010 (Egypt); alt. Passport 19820215";
            
            List<RemarksParser.ExtractedId> ids = parser.extractGovernmentIds(remarks);
            
            assertThat(ids).hasSize(2);
            assertThat(ids.get(0).type()).isEqualTo("Passport");
            assertThat(ids.get(0).number()).isEqualTo("1084010");
            assertThat(ids.get(0).country()).isPresent();
            assertThat(ids.get(0).country().get()).isEqualTo("Egypt");
            
            assertThat(ids.get(1).type()).isEqualTo("Passport");
            assertThat(ids.get(1).number()).isEqualTo("19820215");
        }

        @Test
        @DisplayName("RED: Return empty list when no IDs present")
        void extractGovernmentIds_notPresent() {
            String remarks = "DOB 10 Dec 1948; POB Egypt";
            
            List<RemarksParser.ExtractedId> ids = parser.extractGovernmentIds(remarks);
            
            assertThat(ids).isEmpty();
        }
    }

    @Nested
    @DisplayName("Complete Parsing")
    class CompleteParsingTests {

        @Test
        @DisplayName("RED: Parse all fields from comprehensive remarks")
        void parse_allFields() {
            // Real OFAC example with all fields
            String remarks = "DOB 19 Apr 1947; POB Nahia, Giza, Egypt; nationality Egypt; Gender Male; " +
                           "Passport 1234567 (Egypt); Secondary sanctions risk: section 1(b) of Executive Order 13224";
            
            ParsedRemarks parsed = parser.parse(remarks);
            
            assertThat(parsed.dateOfBirth()).isPresent();
            assertThat(parsed.dateOfBirth().get()).isEqualTo(LocalDate.of(1947, 4, 19));
            
            assertThat(parsed.placeOfBirth()).isPresent();
            assertThat(parsed.placeOfBirth().get()).isEqualTo("Nahia, Giza, Egypt");
            
            assertThat(parsed.nationality()).isPresent();
            assertThat(parsed.nationality().get()).isEqualTo("Egypt");
            
            assertThat(parsed.governmentIds()).hasSize(1);
            assertThat(parsed.governmentIds().get(0).number()).isEqualTo("1234567");
        }

        @Test
        @DisplayName("RED: Parse remarks with partial data")
        void parse_partialData() {
            String remarks = "DOB 03 May 1938; POB Egypt; Secondary sanctions risk";
            
            ParsedRemarks parsed = parser.parse(remarks);
            
            assertThat(parsed.dateOfBirth()).isPresent();
            assertThat(parsed.placeOfBirth()).isPresent();
            assertThat(parsed.nationality()).isEmpty();
            assertThat(parsed.governmentIds()).isEmpty();
        }

        @Test
        @DisplayName("RED: Parse empty or null remarks safely")
        void parse_emptyRemarks() {
            ParsedRemarks parsedNull = parser.parse(null);
            ParsedRemarks parsedEmpty = parser.parse("");
            ParsedRemarks parsedDash = parser.parse("-0-");
            
            assertThat(parsedNull.dateOfBirth()).isEmpty();
            assertThat(parsedEmpty.dateOfBirth()).isEmpty();
            assertThat(parsedDash.dateOfBirth()).isEmpty();
        }
    }
}
