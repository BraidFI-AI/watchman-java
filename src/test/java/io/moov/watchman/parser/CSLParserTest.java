package io.moov.watchman.parser;

import io.moov.watchman.model.*;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for US CSL (Consolidated Screening List) Parser.
 * 
 * The CSL combines multiple export control screening lists:
 * - Bureau of Industry and Security (Entity List, Denied Persons, etc.)
 * - State Department (ITAR Debarred, Nonproliferation)
 * - Treasury (CMIC, SSI, FSE, etc.)
 */
@DisplayName("US CSL Parser Tests")
class CSLParserTest {

    private CSLParser parser;

    @BeforeEach
    void setUp() {
        parser = new CSLParserImpl();
    }

    private InputStream toInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("Basic Parsing Tests")
    class BasicParsingTests {

        @Test
        @DisplayName("Parses empty input returns empty list")
        void parsesEmptyInput() {
            List<Entity> entities = parser.parse(toInputStream(""));
            assertThat(entities).isEmpty();
        }

        @Test
        @DisplayName("Skips header row")
        void skipsHeaderRow() {
            String csv = """
                _id,source,entity_number,type,programs,name,title,addresses,federal_register_notice,start_date,end_date,standard_order,license_requirement,license_policy,call_sign,vessel_type,gross_tonnage,gross_registered_tonnage,vessel_flag,vessel_owner,remarks,source_list_url,alt_names,citizenships,dates_of_birth,nationalities,places_of_birth,source_information_url,ids
                """;
            List<Entity> entities = parser.parse(toInputStream(csv));
            assertThat(entities).isEmpty();
        }

        @Test
        @DisplayName("Parses individual entity")
        void parsesIndividualEntity() {
            String csv = """
                _id,source,entity_number,type,programs,name,title,addresses,federal_register_notice,start_date,end_date,standard_order,license_requirement,license_policy,call_sign,vessel_type,gross_tonnage,gross_registered_tonnage,vessel_flag,vessel_owner,remarks,source_list_url,alt_names,citizenships,dates_of_birth,nationalities,places_of_birth,source_information_url,ids
                12345,Entity List,EL-001,individual,IRAN,JOHN DOE,Director,"123 Main St, Tehran, Iran",,2020-01-01,,,,,,,,,,,Linked to WMD program,https://example.com,Johnny D,IR,1970-01-15,Iranian,Tehran,https://source.com,"Passport, A12345, Iran"
                """;
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            Entity entity = entities.get(0);
            assertThat(entity.name()).isEqualTo("JOHN DOE");
            assertThat(entity.type()).isEqualTo(EntityType.PERSON);
            assertThat(entity.source()).isEqualTo(SourceList.US_CSL);
            assertThat(entity.sourceId()).isEqualTo("12345");
        }

        @Test
        @DisplayName("Parses business entity")
        void parsesBusinessEntity() {
            String csv = """
                _id,source,entity_number,type,programs,name,title,addresses,federal_register_notice,start_date,end_date,standard_order,license_requirement,license_policy,call_sign,vessel_type,gross_tonnage,gross_registered_tonnage,vessel_flag,vessel_owner,remarks,source_list_url,alt_names,citizenships,dates_of_birth,nationalities,places_of_birth,source_information_url,ids
                67890,Entity List,EL-002,entity,SDGT,EVIL CORP LTD,,"456 Business Ave, Moscow, Russia",,2021-06-15,,,,,,,,,,,Shell company,https://example.com,Evil Corporation,,,,Moscow,https://source.com,
                """;
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            Entity entity = entities.get(0);
            assertThat(entity.name()).isEqualTo("EVIL CORP LTD");
            assertThat(entity.type()).isEqualTo(EntityType.BUSINESS);
            assertThat(entity.source()).isEqualTo(SourceList.US_CSL);
        }

        @Test
        @DisplayName("Parses vessel entity")
        void parsesVesselEntity() {
            String csv = """
                _id,source,entity_number,type,programs,name,title,addresses,federal_register_notice,start_date,end_date,standard_order,license_requirement,license_policy,call_sign,vessel_type,gross_tonnage,gross_registered_tonnage,vessel_flag,vessel_owner,remarks,source_list_url,alt_names,citizenships,dates_of_birth,nationalities,places_of_birth,source_information_url,ids
                99999,Sectoral Sanctions,SS-001,vessel,UKRAINE-EO13662,M/V CARGO SHIP,,,,,,,,,ABC123,Cargo,5000,4500,Russia,Russian Shipping Co,Oil tanker,https://example.com,CARGO VESSEL,,,,,,IMO 1234567
                """;
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            Entity entity = entities.get(0);
            assertThat(entity.name()).isEqualTo("M/V CARGO SHIP");
            assertThat(entity.type()).isEqualTo(EntityType.VESSEL);
            assertThat(entity.vessel()).isNotNull();
            assertThat(entity.vessel().callSign()).isEqualTo("ABC123");
            assertThat(entity.vessel().type()).isEqualTo("Cargo");
            assertThat(entity.vessel().flag()).isEqualTo("Russia");
        }

        @Test
        @DisplayName("Parses aircraft entity")
        void parsesAircraftEntity() {
            String csv = """
                _id,source,entity_number,type,programs,name,title,addresses,federal_register_notice,start_date,end_date,standard_order,license_requirement,license_policy,call_sign,vessel_type,gross_tonnage,gross_registered_tonnage,vessel_flag,vessel_owner,remarks,source_list_url,alt_names,citizenships,dates_of_birth,nationalities,places_of_birth,source_information_url,ids
                88888,CMIC List,CM-001,aircraft,CMIC,BOEING 747,,,,,,,,,N12345,Passenger,,,Iran,Iranian Airlines,Cargo aircraft,https://example.com,747-400,,,,,,"Serial, ABC123, Iran"
                """;
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            Entity entity = entities.get(0);
            assertThat(entity.name()).isEqualTo("BOEING 747");
            assertThat(entity.type()).isEqualTo(EntityType.AIRCRAFT);
            assertThat(entity.aircraft()).isNotNull();
            assertThat(entity.aircraft().icaoCode()).isEqualTo("N12345");
        }
    }

    @Nested
    @DisplayName("Address Parsing Tests")
    class AddressParsingTests {

        @Test
        @DisplayName("Parses single address")
        void parsesSingleAddress() {
            // CSL has 29 columns, address is column 7
            String csv = "_id,source,entity_number,type,programs,name,title,addresses,federal_register_notice,start_date,end_date,standard_order,license_requirement,license_policy,call_sign,vessel_type,gross_tonnage,gross_registered_tonnage,vessel_flag,vessel_owner,remarks,source_list_url,alt_names,citizenships,dates_of_birth,nationalities,places_of_birth,source_information_url,ids\n" +
                "12345,Entity List,EL-001,individual,IRAN,JOHN DOE,,\"123 Main Street, Tehran, Iran\",,,,,,,,,,,,,,,,,,,,,\n";
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            assertThat(entities.get(0).addresses()).hasSize(1);
            Address addr = entities.get(0).addresses().get(0);
            assertThat(addr.line1()).isEqualTo("123 Main Street");
            assertThat(addr.city()).isEqualTo("Tehran");
            assertThat(addr.country()).isEqualTo("Iran");
        }

        @Test
        @DisplayName("Parses multiple semicolon-separated addresses")
        void parsesMultipleAddresses() {
            String csv = "_id,source,entity_number,type,programs,name,title,addresses,federal_register_notice,start_date,end_date,standard_order,license_requirement,license_policy,call_sign,vessel_type,gross_tonnage,gross_registered_tonnage,vessel_flag,vessel_owner,remarks,source_list_url,alt_names,citizenships,dates_of_birth,nationalities,places_of_birth,source_information_url,ids\n" +
                "12345,Entity List,EL-001,individual,IRAN,JOHN DOE,,\"123 Main St, Tehran, Iran; 456 Oak Ave, Dubai, UAE\",,,,,,,,,,,,,,,,,,,,,\n";
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            assertThat(entities.get(0).addresses()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Alternate Names Tests")
    class AlternateNamesTests {

        @Test
        @DisplayName("Parses semicolon-separated alternate names")
        void parsesAlternateNames() {
            String csv = """
                _id,source,entity_number,type,programs,name,title,addresses,federal_register_notice,start_date,end_date,standard_order,license_requirement,license_policy,call_sign,vessel_type,gross_tonnage,gross_registered_tonnage,vessel_flag,vessel_owner,remarks,source_list_url,alt_names,citizenships,dates_of_birth,nationalities,places_of_birth,source_information_url,ids
                12345,Entity List,EL-001,individual,IRAN,JOHN DOE,,,,,,,,,,,,,,,,https://example.com,"Johnny D; John D.; J. Doe",,,,,,
                """;
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            assertThat(entities.get(0).altNames()).containsExactlyInAnyOrder(
                "Johnny D", "John D.", "J. Doe"
            );
        }
    }

    @Nested
    @DisplayName("Sanctions Info Tests")
    class SanctionsInfoTests {

        @Test
        @DisplayName("Parses sanction programs")
        void parsesSanctionPrograms() {
            String csv = """
                _id,source,entity_number,type,programs,name,title,addresses,federal_register_notice,start_date,end_date,standard_order,license_requirement,license_policy,call_sign,vessel_type,gross_tonnage,gross_registered_tonnage,vessel_flag,vessel_owner,remarks,source_list_url,alt_names,citizenships,dates_of_birth,nationalities,places_of_birth,source_information_url,ids
                12345,Entity List,EL-001,individual,"IRAN; SDGT; WMD",JOHN DOE,,,,,,,,,,,,,,,,,,,,,,,,
                """;
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            assertThat(entities.get(0).sanctionsInfo().programs())
                .containsExactlyInAnyOrder("IRAN", "SDGT", "WMD");
        }

        @Test
        @DisplayName("Stores remarks in sanctions info")
        void storesRemarksInSanctionsInfo() {
            String csv = """
                _id,source,entity_number,type,programs,name,title,addresses,federal_register_notice,start_date,end_date,standard_order,license_requirement,license_policy,call_sign,vessel_type,gross_tonnage,gross_registered_tonnage,vessel_flag,vessel_owner,remarks,source_list_url,alt_names,citizenships,dates_of_birth,nationalities,places_of_birth,source_information_url,ids
                12345,Entity List,EL-001,individual,IRAN,JOHN DOE,,,,,,,,,,,,,,,"Linked to WMD proliferation network",,,,,,,,,
                """;
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            assertThat(entities.get(0).remarks()).isEqualTo("Linked to WMD proliferation network");
        }
    }

    @Nested
    @DisplayName("Person Details Tests")
    class PersonDetailsTests {

        @Test
        @DisplayName("Parses person with birth date and place")
        void parsesPersonWithBirthInfo() {
            String csv = """
                _id,source,entity_number,type,programs,name,title,addresses,federal_register_notice,start_date,end_date,standard_order,license_requirement,license_policy,call_sign,vessel_type,gross_tonnage,gross_registered_tonnage,vessel_flag,vessel_owner,remarks,source_list_url,alt_names,citizenships,dates_of_birth,nationalities,places_of_birth,source_information_url,ids
                12345,Entity List,EL-001,individual,IRAN,JOHN DOE,Director,,,,,,,,,,,,,,,https://example.com,,Iranian,1970-01-15,Iran,Tehran,https://source.com,
                """;
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            Entity entity = entities.get(0);
            assertThat(entity.person()).isNotNull();
            assertThat(entity.person().name()).isEqualTo("JOHN DOE");
        }
    }

    @Nested
    @DisplayName("Multiple Entities Tests")
    class MultipleEntitiesTests {

        @Test
        @DisplayName("Parses multiple entities from CSV")
        void parsesMultipleEntities() {
            String csv = """
                _id,source,entity_number,type,programs,name,title,addresses,federal_register_notice,start_date,end_date,standard_order,license_requirement,license_policy,call_sign,vessel_type,gross_tonnage,gross_registered_tonnage,vessel_flag,vessel_owner,remarks,source_list_url,alt_names,citizenships,dates_of_birth,nationalities,places_of_birth,source_information_url,ids
                12345,Entity List,EL-001,individual,IRAN,JOHN DOE,,,,,,,,,,,,,,,,,,,,,,,,
                67890,Denied Persons,DP-001,entity,BIS,ACME CORP,,,,,,,,,,,,,,,,,,,,,,,,
                99999,CMIC List,CM-001,individual,CMIC,JANE SMITH,,,,,,,,,,,,,,,,,,,,,,,,
                """;
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(3);
            assertThat(entities).extracting(Entity::name)
                .containsExactly("JOHN DOE", "ACME CORP", "JANE SMITH");
        }

        @Test
        @DisplayName("Skips malformed rows")
        void skipsMalformedRows() {
            String csv = """
                _id,source,entity_number,type,programs,name,title,addresses,federal_register_notice,start_date,end_date,standard_order,license_requirement,license_policy,call_sign,vessel_type,gross_tonnage,gross_registered_tonnage,vessel_flag,vessel_owner,remarks,source_list_url,alt_names,citizenships,dates_of_birth,nationalities,places_of_birth,source_information_url,ids
                12345,Entity List,EL-001,individual,IRAN,JOHN DOE,,,,,,,,,,,,,,,,,,,,,,,,
                bad,row,only,few,columns
                67890,Entity List,EL-002,individual,IRAN,JANE SMITH,,,,,,,,,,,,,,,,,,,,,,,,
                """;
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            // Should parse 2 valid entities, skipping the malformed row
            assertThat(entities).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Source List Detection Tests")
    class SourceListDetectionTests {

        @Test
        @DisplayName("Identifies Entity List source")
        void identifiesEntityListSource() {
            String csv = """
                _id,source,entity_number,type,programs,name,title,addresses,federal_register_notice,start_date,end_date,standard_order,license_requirement,license_policy,call_sign,vessel_type,gross_tonnage,gross_registered_tonnage,vessel_flag,vessel_owner,remarks,source_list_url,alt_names,citizenships,dates_of_birth,nationalities,places_of_birth,source_information_url,ids
                12345,Entity List,EL-001,individual,IRAN,JOHN DOE,,,,,,,,,,,,,,,,,,,,,,,,
                """;
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            assertThat(entities.get(0).source()).isEqualTo(SourceList.US_CSL);
        }

        @Test
        @DisplayName("Organization type for military-industrial source")
        void organizationTypeForMilitaryIndustrial() {
            String csv = """
                _id,source,entity_number,type,programs,name,title,addresses,federal_register_notice,start_date,end_date,standard_order,license_requirement,license_policy,call_sign,vessel_type,gross_tonnage,gross_registered_tonnage,vessel_flag,vessel_owner,remarks,source_list_url,alt_names,citizenships,dates_of_birth,nationalities,places_of_birth,source_information_url,ids
                12345,Non-SDN Chinese Military-Industrial Complex Companies List,CM-001,entity,CMIC,CHINA DEFENSE CORP,,,,,,,,,,,,,,,,,,,,,,,,
                """;
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            assertThat(entities.get(0).type()).isEqualTo(EntityType.ORGANIZATION);
        }
    }
}
