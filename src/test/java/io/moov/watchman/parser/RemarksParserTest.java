package io.moov.watchman.parser;

import io.moov.watchman.model.GovernmentId;
import io.moov.watchman.model.GovernmentIdType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for parsing the OFAC remarks field.
 * 
 * The remarks field contains structured data in a semi-structured format:
 * - DOB: Date of birth
 * - POB: Place of birth
 * - Passport numbers
 * - National IDs
 * - Other identifiers
 * 
 * Format examples:
 * - "DOB 15 Mar 1965; POB Caracas, Venezuela; nationality Venezuela"
 * - "Passport E123456 (Venezuela); Tax ID No. 12345678"
 */
class RemarksParserTest {

    private RemarksParser parser;

    @BeforeEach
    void setUp() {
        parser = new RemarksParser();
    }

    @Nested
    @DisplayName("Date of Birth Parsing")
    class DobParsingTests {

        @ParameterizedTest(name = "'{0}' should extract DOB")
        @CsvSource({
            "'DOB 15 Mar 1965', 1965-03-15",
            "'DOB 01 Jan 1980', 1980-01-01",
            "'DOB 31 Dec 1999', 1999-12-31",
            "'DOB 1965', 1965-01-01",  // Year only
            "'DOB circa 1960', 1960-01-01",  // Approximate
        })
        void shouldExtractDob(String remarks, String expectedDate) {
            LocalDate dob = parser.extractDateOfBirth(remarks);
            assertThat(dob).isEqualTo(LocalDate.parse(expectedDate));
        }

        @Test
        @DisplayName("Multiple DOB formats")
        void multipleDobFormats() {
            // Various formats found in OFAC data
            assertThat(parser.extractDateOfBirth("DOB 15 Mar 1965")).isNotNull();
            assertThat(parser.extractDateOfBirth("DOB 15 March 1965")).isNotNull();
            assertThat(parser.extractDateOfBirth("DOB 1965-03-15")).isNotNull();
            assertThat(parser.extractDateOfBirth("DOB 03/15/1965")).isNotNull();
        }

        @Test
        @DisplayName("No DOB should return null")
        void noDobShouldReturnNull() {
            assertThat(parser.extractDateOfBirth("POB Caracas; nationality Venezuela")).isNull();
            assertThat(parser.extractDateOfBirth("")).isNull();
            assertThat(parser.extractDateOfBirth(null)).isNull();
        }

        @Test
        @DisplayName("DOB embedded in longer text")
        void dobEmbeddedInText() {
            String remarks = "DOB 15 Mar 1965; POB Caracas, Venezuela; nationality Venezuela; Passport E12345";
            LocalDate dob = parser.extractDateOfBirth(remarks);
            assertThat(dob).isEqualTo(LocalDate.of(1965, 3, 15));
        }
    }

    @Nested
    @DisplayName("Place of Birth Parsing")
    class PobParsingTests {

        @Test
        @DisplayName("Should extract POB")
        void shouldExtractPob() {
            assertThat(parser.extractPlaceOfBirth("POB Caracas, Venezuela"))
                .isEqualTo("Caracas, Venezuela");
            
            assertThat(parser.extractPlaceOfBirth("POB Moscow, Russia"))
                .isEqualTo("Moscow, Russia");
        }

        @Test
        @DisplayName("POB with semicolon delimiter")
        void pobWithSemicolon() {
            String remarks = "DOB 15 Mar 1965; POB Caracas, Venezuela; nationality Venezuela";
            assertThat(parser.extractPlaceOfBirth(remarks)).isEqualTo("Caracas, Venezuela");
        }

        @Test
        @DisplayName("No POB should return null")
        void noPobShouldReturnNull() {
            assertThat(parser.extractPlaceOfBirth("DOB 15 Mar 1965")).isNull();
        }
    }

    @Nested
    @DisplayName("Nationality Parsing")
    class NationalityParsingTests {

        @ParameterizedTest(name = "'{0}' → '{1}'")
        @CsvSource({
            "'nationality Venezuela', 'Venezuela'",
            "'Nationality: Russia', 'Russia'",
            "'nationality Iran', 'Iran'",
            "'alt. nationality Cuba', 'Cuba'",
        })
        void shouldExtractNationality(String remarks, String expected) {
            assertThat(parser.extractNationality(remarks)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Government ID Parsing")
    class GovernmentIdParsingTests {

        @Test
        @DisplayName("Should extract passport number")
        void shouldExtractPassport() {
            String remarks = "Passport E123456 (Venezuela)";
            List<GovernmentId> ids = parser.extractGovernmentIds(remarks);
            
            assertThat(ids).hasSize(1);
            assertThat(ids.get(0).type()).isEqualTo(GovernmentIdType.PASSPORT);
            assertThat(ids.get(0).identifier()).isEqualTo("E123456");
            assertThat(ids.get(0).country()).isEqualTo("Venezuela");
        }

        @Test
        @DisplayName("Should extract tax ID")
        void shouldExtractTaxId() {
            String remarks = "Tax ID No. 12345678";
            List<GovernmentId> ids = parser.extractGovernmentIds(remarks);
            
            assertThat(ids).hasSize(1);
            assertThat(ids.get(0).type()).isEqualTo(GovernmentIdType.TAX_ID);
            assertThat(ids.get(0).identifier()).isEqualTo("12345678");
        }

        @Test
        @DisplayName("Should extract national ID")
        void shouldExtractNationalId() {
            String remarks = "National ID No. A1234567 (Iran)";
            List<GovernmentId> ids = parser.extractGovernmentIds(remarks);
            
            assertThat(ids).hasSize(1);
            assertThat(ids.get(0).type()).isEqualTo(GovernmentIdType.NATIONAL_ID);
        }

        @Test
        @DisplayName("Should extract multiple IDs")
        void shouldExtractMultipleIds() {
            String remarks = "Passport E123456 (Venezuela); Tax ID No. 12345678; National ID No. V987654";
            List<GovernmentId> ids = parser.extractGovernmentIds(remarks);
            
            assertThat(ids).hasSize(3);
        }

        @Test
        @DisplayName("No IDs should return empty list")
        void noIdsShouldReturnEmptyList() {
            assertThat(parser.extractGovernmentIds("DOB 15 Mar 1965")).isEmpty();
            assertThat(parser.extractGovernmentIds("")).isEmpty();
            assertThat(parser.extractGovernmentIds(null)).isEmpty();
        }

        @ParameterizedTest(name = "'{0}' should extract ID type {1}")
        @CsvSource({
            "'Cedula No. 12345', CEDULA",
            "'CURP ABC123', CURP",
            "'C.U.I.T. 12-34567890-1', CUIT",
            "'SSN 123-45-6789', SSN",
            "'Driver's License D12345', DRIVERS_LICENSE",
            "'Business Registration No. BR123', BUSINESS_REGISTRATION",
            "'Commercial Registry 456789', COMMERCIAL_REGISTRY",
        })
        void shouldExtractVariousIdTypes(String remarks, GovernmentIdType expectedType) {
            List<GovernmentId> ids = parser.extractGovernmentIds(remarks);
            
            assertThat(ids).isNotEmpty();
            assertThat(ids.get(0).type()).isEqualTo(expectedType);
        }
    }

    @Nested
    @DisplayName("Full Remarks Parsing")
    class FullRemarksParsingTests {

        @Test
        @DisplayName("Should parse complex remarks string")
        void shouldParseComplexRemarks() {
            String remarks = "DOB 15 Mar 1965; POB Caracas, Venezuela; nationality Venezuela; " +
                           "Passport E123456 (Venezuela) issued 2010; Tax ID No. 12345678; " +
                           "alt. nationality Cuba; a.k.a. 'EL PRESIDENTE'";
            
            assertThat(parser.extractDateOfBirth(remarks)).isEqualTo(LocalDate.of(1965, 3, 15));
            assertThat(parser.extractPlaceOfBirth(remarks)).isEqualTo("Caracas, Venezuela");
            assertThat(parser.extractNationality(remarks)).isEqualTo("Venezuela");
            assertThat(parser.extractGovernmentIds(remarks)).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("Should handle malformed remarks gracefully")
        void shouldHandleMalformedRemarks() {
            // These should not throw exceptions
            assertThat(parser.extractDateOfBirth("DOB invalid date")).isNull();
            assertThat(parser.extractPlaceOfBirth("POB")).isNull();
            assertThat(parser.extractGovernmentIds("Passport")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Vessel-Specific Remarks")
    class VesselRemarksTests {

        @Test
        @DisplayName("Should extract IMO number")
        void shouldExtractImoNumber() {
            String remarks = "IMO 9333756; Flag Russia";
            String imo = parser.extractImoNumber(remarks);
            assertThat(imo).isEqualTo("9333756");
        }

        @Test
        @DisplayName("Should extract MMSI")
        void shouldExtractMmsi() {
            String remarks = "MMSI 273456789; IMO 9333756";
            String mmsi = parser.extractMmsi(remarks);
            assertThat(mmsi).isEqualTo("273456789");
        }
    }

    @Nested
    @DisplayName("Aircraft-Specific Remarks")
    class AircraftRemarksTests {

        @Test
        @DisplayName("Should extract aircraft serial number")
        void shouldExtractSerialNumber() {
            String remarks = "Serial No. 12345; ICAO EP-IAA";
            String serial = parser.extractSerialNumber(remarks);
            assertThat(serial).isEqualTo("12345");
        }

        @Test
        @DisplayName("Should extract ICAO code")
        void shouldExtractIcaoCode() {
            String remarks = "ICAO EP-IAA; Model Boeing 747";
            String icao = parser.extractIcaoCode(remarks);
            assertThat(icao).isEqualTo("EP-IAA");
        }
    }
}
