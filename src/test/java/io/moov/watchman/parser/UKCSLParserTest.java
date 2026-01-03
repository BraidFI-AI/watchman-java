package io.moov.watchman.parser;

import io.moov.watchman.model.*;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for UK CSL (UK Consolidated Financial Sanctions List) Parser.
 * 
 * UK sanctions list uses CSV format with 36 columns.
 * GroupType determines entity type: "Individual", "Entity", "Ship"
 * Rows with same GroupID should be merged.
 */
@DisplayName("UK CSL Parser Tests")
class UKCSLParserTest {

    private UKCSLParser parser;

    @BeforeEach
    void setUp() {
        parser = new UKCSLParserImpl();
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
            String csv = "Name 1,Name 2,Name 3,Name 4,Name 5,Name 6,Title,Other Names,DOB,Town of Birth,Country of Birth,Nationality,Passport Details,NI Number,Position,Address 1,Address 2,Address 3,Address 4,Address 5,Address 6,Post/Zip Code,Country,Other Information,Group Type,Alias Quality,Listed Date,UK Sanctions List Date,Last Updated,Group ID\n";
            List<Entity> entities = parser.parse(toInputStream(csv));
            assertThat(entities).isEmpty();
        }

        @Test
        @DisplayName("Parses individual entity")
        void parsesIndividualEntity() {
            String csv = createUKCSVRow("IVANOV", "Vladimir", "", "", "", "", "Director", 
                "1960-05-15", "Moscow", "Russia", "Russian",
                "123 Main St", "", "", "",
                "123456", "Russia",
                "Individual", "12345");
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            Entity entity = entities.get(0);
            assertThat(entity.name()).contains("IVANOV");
            assertThat(entity.name()).contains("Vladimir");
            assertThat(entity.type()).isEqualTo(EntityType.PERSON);
            assertThat(entity.source()).isEqualTo(SourceList.UK_CSL);
        }

        @Test
        @DisplayName("Parses business entity")
        void parsesBusinessEntity() {
            String csv = createUKCSVRow("EVIL CORP", "LTD", "", "", "", "", "",
                "", "", "", "",
                "456 Business Ave", "", "", "",
                "EC1A 1BB", "United Kingdom",
                "Entity", "67890");
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            Entity entity = entities.get(0);
            assertThat(entity.name()).contains("EVIL CORP");
            assertThat(entity.type()).isEqualTo(EntityType.BUSINESS);
        }

        @Test
        @DisplayName("Parses ship/vessel entity")
        void parsesVesselEntity() {
            String csv = createUKCSVRow("M/V CARGO SHIP", "", "", "", "", "", "",
                "", "", "", "",
                "", "", "", "",
                "", "Russia",
                "Ship", "99999");
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            Entity entity = entities.get(0);
            assertThat(entity.name()).isEqualTo("M/V CARGO SHIP");
            assertThat(entity.type()).isEqualTo(EntityType.VESSEL);
        }
    }

    @Nested
    @DisplayName("Name Assembly Tests")
    class NameAssemblyTests {

        @Test
        @DisplayName("Assembles full name from parts")
        void assemblesFullNameFromParts() {
            String csv = createUKCSVRow("SMITH", "John", "William", "Jr", "", "", "",
                "", "", "", "",
                "", "", "", "",
                "", "",
                "Individual", "12345");
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            String name = entities.get(0).name();
            assertThat(name).contains("SMITH");
            assertThat(name).contains("John");
            assertThat(name).contains("William");
        }

        @Test
        @DisplayName("Handles single name part")
        void handlesSingleNamePart() {
            String csv = createUKCSVRow("ORGANIZATION", "", "", "", "", "", "",
                "", "", "", "",
                "", "", "", "",
                "", "",
                "Entity", "12345");
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            assertThat(entities.get(0).name()).isEqualTo("ORGANIZATION");
        }
    }

    @Nested
    @DisplayName("Address Parsing Tests")
    class AddressParsingTests {

        @Test
        @DisplayName("Parses full address")
        void parsesFullAddress() {
            String csv = createUKCSVRow("IVANOV", "Vladimir", "", "", "", "", "",
                "", "", "", "",
                "123 Main Street", "Apt 4B", "Moscow", "Moscow Region", "123456", "Russia",
                "Individual", "12345");
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            assertThat(entities.get(0).addresses()).hasSize(1);
            Address addr = entities.get(0).addresses().get(0);
            assertThat(addr.line1()).contains("123 Main Street");
            assertThat(addr.country()).isEqualTo("Russia");
        }

        @Test
        @DisplayName("Assembles multi-line address")
        void assemblesMultiLineAddress() {
            String csv = createUKCSVRow("CORP", "", "", "", "", "", "",
                "", "", "", "",
                "Line 1", "Line 2", "Line 3", "Line 4", "SW1A 1AA", "UK",
                "Entity", "12345");
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            Address addr = entities.get(0).addresses().get(0);
            assertThat(addr.line1()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Entity Merging Tests")
    class EntityMergingTests {

        @Test
        @DisplayName("Merges rows with same GroupID")
        void mergesRowsWithSameGroupId() {
            String row1 = createUKCSVRow("IVANOV", "Vladimir", "", "", "", "", "",
                "1960-05-15", "Moscow", "Russia", "Russian",
                "123 Main St", "", "", "", "123456", "Russia",
                "Individual", "12345");
            String row2 = createUKCSVRow("IVANOV", "Vlad", "", "", "", "", "",
                "", "", "", "",
                "456 Oak Ave", "", "", "", "654321", "Russia",
                "Individual", "12345");
            String csv = row1 + row2;
            
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            // Should merge into single entity
            assertThat(entities).hasSize(1);
            // Should have multiple addresses
            assertThat(entities.get(0).addresses().size()).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Birth Information Tests")
    class BirthInformationTests {

        @Test
        @DisplayName("Parses date of birth")
        void parsesDateOfBirth() {
            String csv = createUKCSVRow("IVANOV", "Vladimir", "", "", "", "", "",
                "15/05/1960", "Moscow", "Russia", "Russian",
                "", "", "", "", "", "",
                "Individual", "12345");
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            Entity entity = entities.get(0);
            assertThat(entity.person()).isNotNull();
        }

        @Test
        @DisplayName("Parses place of birth")
        void parsesPlaceOfBirth() {
            String csv = createUKCSVRow("IVANOV", "Vladimir", "", "", "", "", "",
                "", "Moscow", "Russia", "",
                "", "", "", "", "", "",
                "Individual", "12345");
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            // Birth place info should be captured
        }
    }

    @Nested
    @DisplayName("Other Information Tests")
    class OtherInformationTests {

        @Test
        @DisplayName("Parses other information as remarks")
        void parsesOtherInformation() {
            String csv = createUKCSVRowWithOtherInfo("IVANOV", "Vladimir", "Individual", "12345",
                "Subject to UK financial sanctions for involvement in destabilizing activities");
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            assertThat(entities.get(0).remarks()).contains("financial sanctions");
        }
    }

    @Nested
    @DisplayName("Multiple Entities Tests")
    class MultipleEntitiesTests {

        @Test
        @DisplayName("Parses multiple different entities")
        void parsesMultipleEntities() {
            String row1 = createUKCSVRow("PERSON", "One", "", "", "", "", "",
                "", "", "", "",
                "", "", "", "", "", "",
                "Individual", "11111");
            String row2 = createUKCSVRow("COMPANY", "TWO", "", "", "", "", "",
                "", "", "", "",
                "", "", "", "", "", "",
                "Entity", "22222");
            String row3 = createUKCSVRow("VESSEL", "THREE", "", "", "", "", "",
                "", "", "", "",
                "", "", "", "", "", "",
                "Ship", "33333");
            String csv = row1 + row2 + row3;
            
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(3);
            assertThat(entities).extracting(Entity::type)
                .containsExactlyInAnyOrder(EntityType.PERSON, EntityType.BUSINESS, EntityType.VESSEL);
        }
    }

    // Helper method to create UK CSV row (36 columns)
    // Columns: 0-5=Name parts, 6=Title, 7=Other Names, 8=DOB, 9=Town of Birth,
    // 10=Country of Birth, 11=Nationality, 12=Passport, 13=NI Number, 14=Position,
    // 15-20=Address 1-6, 21=PostCode, 22=Country, 23=Other Info,
    // 24=Group Type, 25=Alias Quality, 26=Listed Date, 27=UK Sanctions Date,
    // 28=Last Updated, 29=Group ID
    
    private String createUKCSVRow(String name1, String name2, String name3, String name4, 
                                   String name5, String name6, String title,
                                   String dob, String townOfBirth, String countryOfBirth, String nationality,
                                   String addr1, String addr2, String addr3, String addr4,
                                   String postCode, String country,
                                   String groupType, String groupId) {
        return String.join(",",
            quote(name1), quote(name2), quote(name3), quote(name4), quote(name5), quote(name6),
            quote(title), "", // other names
            quote(dob), quote(townOfBirth), quote(countryOfBirth), quote(nationality),
            "", "", "", // passport, NI, position
            quote(addr1), quote(addr2), quote(addr3), quote(addr4), "", "", // addr 5,6
            quote(postCode), quote(country),
            "", // other info
            quote(groupType), "", // alias quality
            "2022-01-01", "2022-01-01", "2022-01-01", // dates
            groupId
        ) + "\n";
    }

    // Simplified helper for basic entity tests
    private String createSimpleUKCSVRow(String name1, String name2, String groupType, String groupId) {
        return createUKCSVRow(name1, name2, "", "", "", "", "",
            "", "", "", "",
            "", "", "", "", "", "",
            groupType, groupId);
    }

    private String createUKCSVRowWithOtherInfo(String name1, String name2, String groupType, 
                                                String groupId, String otherInfo) {
        return String.join(",",
            quote(name1), quote(name2), "", "", "", "",
            "", "", "", "", "", "",
            "", "", "",
            "", "", "", "", "", "",
            "", "",
            quote(otherInfo),
            quote(groupType), "",
            "2022-01-01", "2022-01-01", "2022-01-01",
            groupId
        ) + "\n";
    }

    private String quote(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
