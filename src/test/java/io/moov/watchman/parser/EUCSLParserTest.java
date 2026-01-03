package io.moov.watchman.parser;

import io.moov.watchman.model.*;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for EU CSL (European Union Consolidated Sanctions List) Parser.
 * 
 * EU sanctions list uses semicolon-delimited CSV with ~90 columns.
 * Rows with same EntityLogicalID should be merged.
 */
@DisplayName("EU CSL Parser Tests")
class EUCSLParserTest {

    private EUCSLParser parser;

    @BeforeEach
    void setUp() {
        parser = new EUCSLParserImpl();
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
        @DisplayName("Parses person entity")
        void parsesPersonEntity() {
            // EU CSV uses semicolon delimiter
            // Key columns: 1=EntityLogicalID, 8=EntitySubjectType, 19=NameAliasWholeName
            String csv = createEUCSVRow("12345", "person", "IVANOV, Vladimir", "Moscow", "Russia", "1960-05-15");
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            Entity entity = entities.get(0);
            assertThat(entity.name()).isEqualTo("IVANOV, Vladimir");
            assertThat(entity.type()).isEqualTo(EntityType.PERSON);
            assertThat(entity.source()).isEqualTo(SourceList.EU_CSL);
        }

        @Test
        @DisplayName("Parses non-person entity as unknown type")
        void parsesNonPersonEntity() {
            String csv = createEUCSVRow("67890", "enterprise", "EVIL CORP LTD", "Moscow", "Russia", "");
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            Entity entity = entities.get(0);
            assertThat(entity.name()).isEqualTo("EVIL CORP LTD");
            assertThat(entity.type()).isEqualTo(EntityType.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("Entity Merging Tests")
    class EntityMergingTests {

        @Test
        @DisplayName("Merges rows with same EntityLogicalID")
        void mergesRowsWithSameId() {
            String row1 = createEUCSVRow("12345", "person", "IVANOV, Vladimir", "Moscow", "Russia", "1960-05-15");
            String row2 = createEUCSVRow("12345", "person", "Vladimir IVANOV", "St. Petersburg", "Russia", "");
            String csv = row1 + row2;
            
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            // Should merge into single entity with multiple names/addresses
            assertThat(entities).hasSize(1);
            Entity entity = entities.get(0);
            assertThat(entity.altNames()).contains("Vladimir IVANOV");
        }

        @Test
        @DisplayName("Keeps separate entities for different IDs")
        void keepsSeparateEntitiesForDifferentIds() {
            String row1 = createEUCSVRow("12345", "person", "IVANOV, Vladimir", "Moscow", "Russia", "");
            String row2 = createEUCSVRow("67890", "person", "PETROV, Sergei", "Kiev", "Ukraine", "");
            String csv = row1 + row2;
            
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Address Parsing Tests")
    class AddressParsingTests {

        @Test
        @DisplayName("Parses address fields")
        void parsesAddressFields() {
            String csv = createEUCSVRowWithAddress("12345", "person", "IVANOV, Vladimir", 
                "123 Main St", "Moscow", "123456", "Russia");
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            assertThat(entities.get(0).addresses()).hasSize(1);
            Address addr = entities.get(0).addresses().get(0);
            assertThat(addr.line1()).isEqualTo("123 Main St");
            assertThat(addr.city()).isEqualTo("Moscow");
            assertThat(addr.postalCode()).isEqualTo("123456");
            assertThat(addr.country()).isEqualTo("Russia");
        }
    }

    @Nested
    @DisplayName("Birth Information Tests")
    class BirthInformationTests {

        @Test
        @DisplayName("Parses birth date and place")
        void parsesBirthDateAndPlace() {
            String csv = createEUCSVRowWithBirth("12345", "person", "IVANOV, Vladimir", 
                "1960-05-15", "Moscow", "Russia");
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            Entity entity = entities.get(0);
            assertThat(entity.person()).isNotNull();
            // Birth info stored in remarks or person record
        }
    }

    @Nested
    @DisplayName("Remarks and Regulation Tests")
    class RemarksTests {

        @Test
        @DisplayName("Parses entity remarks")
        void parsesEntityRemarks() {
            String csv = createEUCSVRowWithRemarks("12345", "person", "IVANOV, Vladimir", 
                "Subject to asset freeze under EU Regulation 123/2022");
            List<Entity> entities = parser.parse(toInputStream(csv));
            
            assertThat(entities).hasSize(1);
            assertThat(entities.get(0).remarks()).contains("asset freeze");
        }
    }

    // Helper methods to create EU CSV rows with semicolon delimiter
    // EU CSV has ~90 columns, we only populate the ones we need
    
    private String createEUCSVRow(String entityId, String subjectType, String name, 
                                   String city, String country, String birthDate) {
        StringBuilder sb = new StringBuilder();
        // Create 90 empty fields separated by semicolons
        String[] fields = new String[90];
        for (int i = 0; i < 90; i++) {
            fields[i] = "";
        }
        
        // Set key fields:
        // 1 = EntityLogicalID
        // 8 = EntitySubjectType  
        // 19 = NameAliasWholeName
        // 34 = AddressCity
        // 43 = AddressCountryDescription
        // 54 = BirthDate
        fields[1] = entityId;
        fields[8] = subjectType;
        fields[19] = name;
        fields[34] = city;
        fields[43] = country;
        fields[54] = birthDate;
        
        return String.join(";", fields) + "\n";
    }

    private String createEUCSVRowWithAddress(String entityId, String subjectType, String name,
                                              String street, String city, String zipCode, String country) {
        String[] fields = new String[90];
        for (int i = 0; i < 90; i++) {
            fields[i] = "";
        }
        
        fields[1] = entityId;
        fields[8] = subjectType;
        fields[19] = name;
        fields[34] = city;
        fields[35] = street;
        fields[37] = zipCode;
        fields[43] = country;
        
        return String.join(";", fields) + "\n";
    }

    private String createEUCSVRowWithBirth(String entityId, String subjectType, String name,
                                            String birthDate, String birthCity, String birthCountry) {
        String[] fields = new String[90];
        for (int i = 0; i < 90; i++) {
            fields[i] = "";
        }
        
        fields[1] = entityId;
        fields[8] = subjectType;
        fields[19] = name;
        fields[54] = birthDate;
        fields[65] = birthCity;
        fields[67] = birthCountry;
        
        return String.join(";", fields) + "\n";
    }

    private String createEUCSVRowWithRemarks(String entityId, String subjectType, String name, String remarks) {
        String[] fields = new String[90];
        for (int i = 0; i < 90; i++) {
            fields[i] = "";
        }
        
        fields[1] = entityId;
        fields[6] = remarks;
        fields[8] = subjectType;
        fields[19] = name;
        
        return String.join(";", fields) + "\n";
    }
}
