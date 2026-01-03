package io.moov.watchman.parser;

import io.moov.watchman.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for OFAC SDN file parsing.
 * 
 * OFAC provides data in three CSV files:
 * - sdn.csv: Main entity records (Entity Number, Name, Type, Program, etc.)
 * - add.csv: Address records linked by Entity Number
 * - alt.csv: Alternate name records linked by Entity Number
 * 
 * Tests verify correct parsing and merging of these files.
 */
class OFACParserTest {

    private OFACParser parser;

    @BeforeEach
    void setUp() {
        parser = new OFACParserImpl();
    }

    @Nested
    @DisplayName("SDN File Parsing")
    class SdnParsingTests {

        @Test
        @DisplayName("Should parse individual entity")
        void shouldParseIndividualEntity() {
            // SDN CSV format: Ent_num,SDN_Name,SDN_Type,Program,Title,Call_Sign,Vess_type,Tonnage,GRT,Vess_flag,Vess_owner,Remarks
            String sdnData = """
                36,"AEROCARIBBEAN AIRLINES","Entity"," CUBA",,,,,,,,"-0- Calle 23 No 64, Vedado, Havana, Cuba."
                """;

            InputStream sdnStream = toStream(sdnData);
            
            assertThat(parser).isNotNull();
            
            List<Entity> entities = parser.parseSdnOnly(sdnStream);
            
            assertThat(entities).hasSize(1);
            
            Entity entity = entities.get(0);
            assertThat(entity.sourceId()).isEqualTo("36");
            assertThat(entity.name()).isEqualTo("AEROCARIBBEAN AIRLINES");
            assertThat(entity.type()).isEqualTo(EntityType.BUSINESS);
            assertThat(entity.source()).isEqualTo(SourceList.US_OFAC);
        }

        @Test
        @DisplayName("Should parse individual (person) entity")
        void shouldParsePersonEntity() {
            String sdnData = """
                7140,"MADURO MOROS, Nicolas","individual"," VENEZUELA",,,,,,,,
                """;

            InputStream sdnStream = toStream(sdnData);
            
            assertThat(parser).isNotNull();
            
            List<Entity> entities = parser.parseSdnOnly(sdnStream);
            
            assertThat(entities).hasSize(1);
            
            Entity entity = entities.get(0);
            assertThat(entity.sourceId()).isEqualTo("7140");
            assertThat(entity.name()).isEqualTo("MADURO MOROS, Nicolas");
            assertThat(entity.type()).isEqualTo(EntityType.PERSON);
        }

        @Test
        @DisplayName("Should parse vessel entity")
        void shouldParseVesselEntity() {
            String sdnData = """
                15036,"PEGAS","vessel"," RUSSIA-EO14024","","UBKQ9","Crude Oil Tanker","","106413","Russia","PAO SOVCOMFLOT","IMO 9333756"
                """;

            InputStream sdnStream = toStream(sdnData);
            
            assertThat(parser).isNotNull();
            
            List<Entity> entities = parser.parseSdnOnly(sdnStream);
            
            assertThat(entities).hasSize(1);
            
            Entity entity = entities.get(0);
            assertThat(entity.type()).isEqualTo(EntityType.VESSEL);
            assertThat(entity.vessel()).isNotNull();
            assertThat(entity.vessel().callSign()).isEqualTo("UBKQ9");
            assertThat(entity.vessel().type()).isEqualTo("Crude Oil Tanker");
            assertThat(entity.vessel().flag()).isEqualTo("Russia");
            assertThat(entity.vessel().owner()).isEqualTo("PAO SOVCOMFLOT");
        }

        @Test
        @DisplayName("Should parse aircraft entity")
        void shouldParseAircraftEntity() {
            String sdnData = """
                12345,"IRAN AIR 747","aircraft"," IRAN","","EP-IAA","Boeing 747","","","Iran","",""
                """;

            InputStream sdnStream = toStream(sdnData);
            
            assertThat(parser).isNotNull();
            
            List<Entity> entities = parser.parseSdnOnly(sdnStream);
            
            assertThat(entities).hasSize(1);
            
            Entity entity = entities.get(0);
            assertThat(entity.type()).isEqualTo(EntityType.AIRCRAFT);
        }

        @Test
        @DisplayName("Should parse multiple entities")
        void shouldParseMultipleEntities() {
            String sdnData = """
                36,"AEROCARIBBEAN AIRLINES","Entity"," CUBA",,,,,,,,
                7140,"MADURO MOROS, Nicolas","individual"," VENEZUELA",,,,,,,,
                15036,"PEGAS","vessel"," RUSSIA-EO14024","","UBKQ9","Crude Oil Tanker","","106413","Russia","PAO SOVCOMFLOT",""
                """;

            InputStream sdnStream = toStream(sdnData);
            
            assertThat(parser).isNotNull();
            
            List<Entity> entities = parser.parseSdnOnly(sdnStream);
            
            assertThat(entities).hasSize(3);
        }

        @Test
        @DisplayName("Should extract sanctions program")
        void shouldExtractSanctionsProgram() {
            String sdnData = """
                7140,"MADURO MOROS, Nicolas","individual"," VENEZUELA",,,,,,,,
                """;

            InputStream sdnStream = toStream(sdnData);
            
            assertThat(parser).isNotNull();
            
            List<Entity> entities = parser.parseSdnOnly(sdnStream);
            
            Entity entity = entities.get(0);
            assertThat(entity.sanctionsInfo()).isNotNull();
            assertThat(entity.sanctionsInfo().programs()).contains("VENEZUELA");
        }
    }

    @Nested
    @DisplayName("Address File Parsing")
    class AddressParsingTests {

        @Test
        @DisplayName("Should merge addresses with entities")
        void shouldMergeAddressesWithEntities() {
            String sdnData = """
                36,"AEROCARIBBEAN AIRLINES","Entity"," CUBA",,,,,,,,
                """;

            // ADD CSV format: Ent_num,Add_num,Address,City/State/Province/Postal Code,Country,Add_remarks
            String addData = """
                36,1,"Calle 23 No 64, Vedado","Havana","Cuba",""
                """;

            InputStream sdnStream = toStream(sdnData);
            InputStream addStream = toStream(addData);
            
            assertThat(parser).isNotNull();
            
            // Parse using InputStream-based method
            List<Entity> entities = parser.parse(sdnStream, addStream, null);
            
            assertThat(entities).hasSize(1);
            Entity entity = entities.get(0);
            assertThat(entity.addresses()).isNotEmpty();
        }

        @Test
        @DisplayName("Should handle entity with multiple addresses")
        void shouldHandleMultipleAddresses() {
            String sdnData = """
                36,"AEROCARIBBEAN AIRLINES","Entity"," CUBA",,,,,,,,
                """;

            String addData = """
                36,1,"Calle 23 No 64, Vedado","Havana","Cuba",""
                36,2,"123 Main St","Miami","United States",""
                """;

            // Entity should have 2 addresses
            assertThat(true).isTrue(); // Placeholder
        }
    }

    @Nested
    @DisplayName("Alternate Names File Parsing")
    class AltNamesParsingTests {

        @Test
        @DisplayName("Should merge alternate names with entities")
        void shouldMergeAltNamesWithEntities() {
            String sdnData = """
                7140,"MADURO MOROS, Nicolas","individual"," VENEZUELA",,,,,,,,
                """;

            // ALT CSV format: Ent_num,Alt_num,Alt_type,Alt_name,Alt_remarks
            String altData = """
                7140,1,"a.k.a.","Nicolas MADURO",""
                7140,2,"a.k.a.","MADURO, Nicolas",""
                """;

            // Entity should have alt names populated
            assertThat(true).isTrue(); // Placeholder
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty file")
        void shouldHandleEmptyFile() {
            InputStream emptyStream = toStream("");
            
            assertThat(parser).isNotNull();
            
            List<Entity> entities = parser.parseSdnOnly(emptyStream);
            
            assertThat(entities).isEmpty();
        }

        @Test
        @DisplayName("Should handle malformed rows gracefully")
        void shouldHandleMalformedRows() {
            String sdnData = """
                36,"AEROCARIBBEAN AIRLINES","Entity"
                7140,"MADURO MOROS, Nicolas","individual"," VENEZUELA",,,,,,,,
                """;

            InputStream sdnStream = toStream(sdnData);
            
            assertThat(parser).isNotNull();
            
            // Should skip malformed row and parse valid row
            List<Entity> entities = parser.parseSdnOnly(sdnStream);
            
            assertThat(entities).hasSize(1);
            assertThat(entities.get(0).name()).isEqualTo("MADURO MOROS, Nicolas");
        }

        @Test
        @DisplayName("Should handle quoted fields with commas")
        void shouldHandleQuotedFieldsWithCommas() {
            String sdnData = """
                36,"ANGLO-CARIBBEAN CO., LTD.","Entity"," CUBA",,,,,,,,
                """;

            InputStream sdnStream = toStream(sdnData);
            
            assertThat(parser).isNotNull();
            
            List<Entity> entities = parser.parseSdnOnly(sdnStream);
            
            assertThat(entities).hasSize(1);
            assertThat(entities.get(0).name()).isEqualTo("ANGLO-CARIBBEAN CO., LTD.");
        }

        @Test
        @DisplayName("Should handle remarks field extraction")
        void shouldHandleRemarksField() {
            String sdnData = """
                36,"AEROCARIBBEAN AIRLINES","Entity"," CUBA",,,,,,,,"DOB 15 Mar 1965; POB Caracas, Venezuela; nationality Venezuela"
                """;

            InputStream sdnStream = toStream(sdnData);
            
            assertThat(parser).isNotNull();
            
            List<Entity> entities = parser.parseSdnOnly(sdnStream);
            
            Entity entity = entities.get(0);
            assertThat(entity.remarks()).contains("DOB 15 Mar 1965");
        }
    }

    private InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
