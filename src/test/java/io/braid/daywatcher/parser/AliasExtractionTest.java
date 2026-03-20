package io.braid.daywatcher.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for extracting alias names from OFAC Remarks field.
 * 
 * BSA/AML Observation #9: Entity vs Individual Record Coverage
 * 
 * OFAC data contains aliases in the Remarks field using patterns:
 * - a.k.a. 'NAME' (also known as) - 2026 occurrences
 * - f.k.a. 'NAME' (formerly known as) - 11 occurrences
 * 
 * Example from Entity 21727:
 * "a.k.a. 'AL-FATEH, Abu Hamzah'; a.k.a. 'AL-MALIZI, Abu Sayyaf'"
 * 
 * Searching "Abu Sayyaf" should return:
 * - Entity 4688: ABU SAYYAF GROUP (name match)
 * - Entity 21727: JEDI, Muhammad Wanndy Bin Mohamed (alias match)
 */
@DisplayName("Alias Extraction from Remarks")
class AliasExtractionTest {

    private RemarksParser parser;

    @BeforeEach
    void setUp() {
        parser = new RemarksParser();
    }

    @Nested
    @DisplayName("a.k.a. (Also Known As) Pattern")
    class AlsoKnownAsTests {

        @Test
        @DisplayName("Should extract single a.k.a. alias")
        void shouldExtractSingleAlias() {
            String remarks = "a.k.a. 'BNC'.";
            
            List<String> aliases = parser.extractAliases(remarks);
            
            assertThat(aliases).containsExactly("BNC");
        }

        @Test
        @DisplayName("Should extract multiple a.k.a. aliases")
        void shouldExtractMultipleAliases() {
            // Real OFAC data from Entity 21727
            String remarks = "DOB 16 Nov 1990; a.k.a. 'AL-FATEH, Abu Hamzah'; a.k.a. 'AL-MALIZI, Abu Sayyaf'; Linked To: ISLAMIC STATE.";
            
            List<String> aliases = parser.extractAliases(remarks);
            
            assertThat(aliases).containsExactlyInAnyOrder(
                "AL-FATEH, Abu Hamzah",
                "AL-MALIZI, Abu Sayyaf"
            );
        }

        @Test
        @DisplayName("Should handle a.k.a. with various nickname formats")
        void shouldHandleNicknames() {
            // Real OFAC data patterns
            String remarks = "a.k.a. 'PACHO'; a.k.a. 'H7'; a.k.a. 'THE CHESS PLAYER'.";
            
            List<String> aliases = parser.extractAliases(remarks);
            
            assertThat(aliases).containsExactlyInAnyOrder(
                "PACHO",
                "H7",
                "THE CHESS PLAYER"
            );
        }

        @Test
        @DisplayName("Should handle case insensitive a.k.a.")
        void shouldHandleCaseInsensitive() {
            String remarks = "A.K.A. 'UPPER CASE'; a.k.a. 'lower case'; A.k.A. 'MiXeD'.";
            
            List<String> aliases = parser.extractAliases(remarks);
            
            assertThat(aliases).containsExactlyInAnyOrder(
                "UPPER CASE",
                "lower case",
                "MiXeD"
            );
        }

        @Test
        @DisplayName("Should handle double single quotes (Row 15: GHAILANI case)")
        void shouldHandleDoubleSingleQuotes() {
            // Real OFAC data from Entity 6925 - GHAILANI, Ahmed Khalfan
            // BSA observation: FOOPIE and FUPI aliases use double single quotes
            String remarks = "DOB 14 Mar 1974; a.k.a. ''FOOPIE''; a.k.a. ''FUPI''; a.k.a. ''AHMED THE TANZANIAN''.";
            
            List<String> aliases = parser.extractAliases(remarks);
            
            assertThat(aliases).containsExactlyInAnyOrder(
                "FOOPIE",
                "FUPI",
                "AHMED THE TANZANIAN"
            );
        }
    }

    @Nested
    @DisplayName("f.k.a. (Formerly Known As) Pattern")
    class FormerlyKnownAsTests {

        @Test
        @DisplayName("Should extract single f.k.a. alias")
        void shouldExtractSingleFormerName() {
            String remarks = "f.k.a. 'ANSAR INSTITUTE'.";
            
            List<String> aliases = parser.extractAliases(remarks);
            
            assertThat(aliases).containsExactly("ANSAR INSTITUTE");
        }

        @Test
        @DisplayName("Should extract multiple f.k.a. aliases")
        void shouldExtractMultipleFormerNames() {
            // Real OFAC data from Entity 12478
            String remarks = "f.k.a. 'ANSAR INSTITUTE'; f.k.a. 'ANSAR AL-MOJAHEDIN NO-INTEREST LOAN INSTITUTE'; f.k.a. 'ANSAR SAVING AND INTEREST FREE-LOANS FUND'.";
            
            List<String> aliases = parser.extractAliases(remarks);
            
            assertThat(aliases).containsExactlyInAnyOrder(
                "ANSAR INSTITUTE",
                "ANSAR AL-MOJAHEDIN NO-INTEREST LOAN INSTITUTE",
                "ANSAR SAVING AND INTEREST FREE-LOANS FUND"
            );
        }

        @Test
        @DisplayName("Should handle mixed a.k.a. and f.k.a.")
        void shouldHandleMixedAliasTypes() {
            // Real OFAC data from Entity 12727
            String remarks = "a.k.a. 'NATIONAL RESISTANCE MOBILIZATION'; a.k.a. 'RESISTANCE MOBILIZATION FORCE'; f.k.a. 'NATIONAL MOBILIZATION ORGANIZATION'.";
            
            List<String> aliases = parser.extractAliases(remarks);
            
            assertThat(aliases).containsExactlyInAnyOrder(
                "NATIONAL RESISTANCE MOBILIZATION",
                "RESISTANCE MOBILIZATION FORCE",
                "NATIONAL MOBILIZATION ORGANIZATION"
            );
        }
    }

    @Nested
    @DisplayName("Edge Cases and Robustness")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should return empty list for null remarks")
        void shouldHandleNullRemarks() {
            List<String> aliases = parser.extractAliases(null);
            
            assertThat(aliases).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list for empty remarks")
        void shouldHandleEmptyRemarks() {
            List<String> aliases = parser.extractAliases("");
            assertThat(aliases).isEmpty();
            
            aliases = parser.extractAliases("   ");
            assertThat(aliases).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list for -0- remarks")
        void shouldHandleNullMarkerRemarks() {
            List<String> aliases = parser.extractAliases("-0-");
            
            assertThat(aliases).isEmpty();
        }

        @Test
        @DisplayName("Should ignore remarks without alias patterns")
        void shouldIgnoreRemarksWithoutAliases() {
            String remarks = "DOB 16 Nov 1990; POB Malaysia; nationality Malaysia.";
            
            List<String> aliases = parser.extractAliases(remarks);
            
            assertThat(aliases).isEmpty();
        }

        @Test
        @DisplayName("Should handle incomplete alias patterns gracefully")
        void shouldHandleIncompletePatterns() {
            // Missing closing quote
            String remarks = "a.k.a. 'INCOMPLETE";
            
            List<String> aliases = parser.extractAliases(remarks);
            
            // Should not extract incomplete patterns
            assertThat(aliases).isEmpty();
        }

        @Test
        @DisplayName("Should trim whitespace from extracted aliases")
        void shouldTrimWhitespace() {
            String remarks = "a.k.a. '  PADDED  '; f.k.a. ' SPACES '.";
            
            List<String> aliases = parser.extractAliases(remarks);
            
            assertThat(aliases).containsExactlyInAnyOrder(
                "PADDED",
                "SPACES"
            );
        }

        @Test
        @DisplayName("Should preserve multi-word aliases with internal commas")
        void shouldPreserveComplexNames() {
            String remarks = "a.k.a. 'RODRIGUEZ OREJUELA, Gilberto Jose'; a.k.a. 'HERRERA BUITRAGO, Helmer'.";
            
            List<String> aliases = parser.extractAliases(remarks);
            
            assertThat(aliases).containsExactlyInAnyOrder(
                "RODRIGUEZ OREJUELA, Gilberto Jose",
                "HERRERA BUITRAGO, Helmer"
            );
        }
    }

    @Nested
    @DisplayName("Real OFAC Data Integration")
    class RealDataTests {

        @Test
        @DisplayName("Abu Sayyaf case - Entity 21727 (BSA Audit Evidence)")
        void abuSayyafCase() {
            // Entity 21727: JEDI, Muhammad Wanndy Bin Mohamed
            // This is the key test case from BSA/AML Observation #9
            String remarks = "DOB 16 Nov 1990; alt. DOB 1989 to 1991; POB Durian Tunggal, Malacca, Malaysia; " +
                           "nationality Malaysia; Gender Male; Passport A33373751 (Malaysia); " +
                           "National ID No. 90116-04-5293; a.k.a. 'AL-FATEH, Abu Hamzah'; " +
                           "a.k.a. 'AL-MALIZI, Abu Sayyaf'; Linked To: ISLAMIC STATE OF IRAQ AND THE LEVANT.";
            
            List<String> aliases = parser.extractAliases(remarks);
            
            assertThat(aliases)
                .as("Abu Sayyaf should be extracted as alias to enable OFAC parity")
                .contains("AL-MALIZI, Abu Sayyaf");
            
            assertThat(aliases)
                .as("All aliases should be extracted")
                .containsExactlyInAnyOrder(
                    "AL-FATEH, Abu Hamzah",
                    "AL-MALIZI, Abu Sayyaf"
                );
        }

        @Test
        @DisplayName("Ansar Bank case - Entity 12478 (f.k.a. example)")
        void ansarBankCase() {
            // Entity 12478: ANSAR BANK with multiple former names
            String remarks = "Additional Sanctions Information - Subject to Secondary Sanctions; " +
                           "f.k.a. 'ANSAR INSTITUTE'; " +
                           "f.k.a. 'ANSAR AL-MOJAHEDIN NO-INTEREST LOAN INSTITUTE'; " +
                           "f.k.a. 'ANSAR SAVING AND INTEREST FREE-LOANS FUND'.";
            
            List<String> aliases = parser.extractAliases(remarks);
            
            assertThat(aliases).hasSize(3);
            assertThat(aliases).containsExactlyInAnyOrder(
                "ANSAR INSTITUTE",
                "ANSAR AL-MOJAHEDIN NO-INTEREST LOAN INSTITUTE",
                "ANSAR SAVING AND INTEREST FREE-LOANS FUND"
            );
        }

        @Test
        @DisplayName("Short nickname case - Entity 4106 (PACHO, H7)")
        void shortNicknameCase() {
            // Entity 4106: HERRERA BUITRAGO, Helmer
            String remarks = "DOB 24 Aug 1951; alt. DOB 05 Jul 1951; " +
                           "Cedula No. 16247821 (Colombia); Passport J287011 (Colombia); " +
                           "a.k.a. 'PACHO'; a.k.a. 'H7'.";
            
            List<String> aliases = parser.extractAliases(remarks);
            
            assertThat(aliases).containsExactlyInAnyOrder("PACHO", "H7");
        }
    }
}
