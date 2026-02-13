package io.moov.watchman.observations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Individual Observations: Name Order Variation Testing
 * 
 * <p><strong>Purpose</strong>: Verify that name order variations (FirstName LastName vs LAST, FirstName)
 * correctly return matching individuals.
 * 
 * <p><strong>Context</strong>: The Individual observations CSV identifies multiple cases where
 * name order variations did not return the sanctioned individual. However, the system already
 * implements:
 * <ul>
 *   <li>reorderSDNName() - converts OFAC "LAST, FIRST" to "FIRST LAST" during normalization</li>
 *   <li>Token-based matching - order-independent comparison of name components</li>
 * </ul>
 * 
 * <p><strong>Testing Strategy</strong>:
 * These tests verify whether the existing implementation already resolves the reported issues,
 * or if additional fixes are needed.
 * 
 * <p>CSV Source: observations/Watchman Java Obsevations Master.xlsx - Individual.csv
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Individual Observations: Name Order Variations")
public class IndividualNameOrderTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Row 1: ABBAS, Abu
     * 
     * <p><strong>Issue</strong>: "The FN–LN name order variation (Abu ABBAS) did not return 
     * the sanctioned individual."
     * 
     * <p><strong>Expected</strong>: Searching "Abu ABBAS" should find entity with name "ABBAS, Abu"
     */
    @Nested
    @DisplayName("Row 1: ABBAS, Abu")
    class Row01_Abbas_Abu {
        
        @Test
        @DisplayName("Baseline: 'ABBAS, Abu' should return entity")
        void baseline_commaFormat() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "ABBAS, Abu")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'ABBAS, Abu')]").exists());
        }
        
        @Test
        @DisplayName("Name order variation: 'Abu ABBAS' should return entity")
        void nameOrderVariation_firstLast() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "Abu ABBAS")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'ABBAS, Abu')]").exists());
        }
    }

    /**
     * Row 4: NASRALLAH, Hasan
     * 
     * <p><strong>Issue</strong>: "The FN–LN name order variation (Hasan NASRALLAH) did not return 
     * the sanctioned individual."
     * 
     * <p><strong>Expected</strong>: Searching "Hasan NASRALLAH" should find entity with name "NASRALLAH, Hasan"
     */
    @Nested
    @DisplayName("Row 4: NASRALLAH, Hasan")
    class Row04_Nasrallah_Hasan {
        
        @Test
        @DisplayName("Baseline: 'NASRALLAH, Hasan' should return entity")
        void baseline_commaFormat() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "NASRALLAH, Hasan")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'NASRALLAH, Hasan')]").exists());
        }
        
        @Test
        @DisplayName("Name order variation: 'Hasan NASRALLAH' should return entity")
        void nameOrderVariation_firstLast() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "Hasan NASRALLAH")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'NASRALLAH, Hasan')]").exists());
        }
    }

    /**
     * Row 5: AMEZCUA CONTRERAS, Jose de Jesus
     * 
     * <p><strong>Issue</strong>: "Name order variation for Chuy AMEZCUA missed to list the true match."
     * <p><strong>Alias</strong>: AMEZCUA, Chuy
     * 
     * <p><strong>Expected</strong>: Searching "Chuy AMEZCUA" should find entity via alias
     */
    @Nested
    @DisplayName("Row 5: AMEZCUA CONTRERAS, Jose de Jesus")
    class Row05_Amezcua_Contreras {
        
        @Test
        @DisplayName("Baseline: Full name should return entity")
        void baseline_fullName() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "AMEZCUA CONTRERAS, Jose de Jesus")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'AMEZCUA CONTRERAS, Jose de Jesus')]").exists());
        }
        
        @Test
        @DisplayName("Alias variation: 'Chuy AMEZCUA' should match via alias")
        void aliasVariation_chuyAmezcua() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "Chuy AMEZCUA")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'AMEZCUA CONTRERAS, Jose de Jesus')]").exists());
        }
    }

    /**
     * Row 6: ARELLANO FELIX, Ramon Eduardo (YOB 1964)
     * 
     * <p><strong>Issue</strong>: "Name order variation for Ramon Eduardo ARELLANO FELIX missed to 
     * list the true match and listed another close match ARELLANO FELIX, Eduardo Ramon (YOB 1956)."
     * 
     * <p><strong>Expected</strong>: Searching "Ramon Eduardo ARELLANO FELIX" should prioritize the 
     * 1964 YOB individual over the 1956 YOB individual
     */
    @Nested
    @DisplayName("Row 6: ARELLANO FELIX, Ramon Eduardo (YOB 1964)")
    class Row06_Arellano_Ramon {
        
        @Test
        @DisplayName("Baseline: Comma format should return 1964 entity")
        void baseline_commaFormat() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "ARELLANO FELIX, Ramon Eduardo")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'ARELLANO FELIX, Ramon Eduardo')]").exists());
        }
        
        @Test
        @DisplayName("Name order variation: 'Ramon Eduardo ARELLANO FELIX' should return 1964 entity")
        void nameOrderVariation_firstLast() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "Ramon Eduardo ARELLANO FELIX")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'ARELLANO FELIX, Ramon Eduardo')]").exists());
        }
        
        @Test
        @DisplayName("Verify 1964 entity ranks higher than 1956 for reversed name")
        void verify_correctEntityRanksHigher() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "Ramon Eduardo ARELLANO FELIX")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[0].name").value("ARELLANO FELIX, Ramon Eduardo"));
        }
    }

    /**
     * Row 8: CHANG, Chi Fu
     * 
     * <p><strong>Issue</strong>: "Name order variation for Chi Fu CHANG (FN-LN) missed to list 
     * the true match and listed another individual CHU, Kyu-Chang"
     * 
     * <p><strong>Expected</strong>: Searching "Chi Fu CHANG" should return "CHANG, Chi Fu"
     */
    @Nested
    @DisplayName("Row 8: CHANG, Chi Fu")
    class Row08_Chang_ChiFu {
        
        @Test
        @DisplayName("Baseline: 'CHANG, Chi Fu' should return entity")
        void baseline_commaFormat() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "CHANG, Chi Fu")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'CHANG, Chi Fu')]").exists());
        }
        
        @Test
        @DisplayName("Name order variation: 'Chi Fu CHANG' should return entity")
        void nameOrderVariation_firstLast() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "Chi Fu CHANG")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'CHANG, Chi Fu')]").exists());
        }
    }

    /**
     * Row 11: GILBOA, Joseph
     * 
     * <p><strong>Issue</strong>: "Name order variation for Josef GIL missed to list the true match."
     * <p><strong>Alias</strong>: GIL, Josef
     * 
     * <p><strong>Expected</strong>: Searching "Josef GIL" should match via alias
     */
    @Nested
    @DisplayName("Row 11: GILBOA, Joseph")
    class Row11_Gilboa_Joseph {
        
        @Test
        @DisplayName("Baseline: 'GILBOA, Joseph' should return entity")
        void baseline_primaryName() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "GILBOA, Joseph")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'GILBOA, Joseph')]").exists());
        }
        
        @Test
        @DisplayName("Alias variation: 'Josef GIL' should match via alias")
        void aliasVariation_josefGil() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "Josef GIL")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'GILBOA, Joseph')]").exists());
        }
    }

    /**
     * Row 12: CHANG, Ping Yun
     * 
     * <p><strong>Issue</strong>: "Name order variation for Ping Yun CHANG (FN-LN) missed to list 
     * the true match and listed a vessel PING AN."
     * 
     * <p><strong>Expected</strong>: Searching "Ping Yun CHANG" should return "CHANG, Ping Yun"
     */
    @Nested
    @DisplayName("Row 12: CHANG, Ping Yun")
    class Row12_Chang_PingYun {
        
        @Test
        @DisplayName("Baseline: 'CHANG, Ping Yun' should return entity")
        void baseline_commaFormat() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "CHANG, Ping Yun")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'CHANG, Ping Yun')]").exists());
        }
        
        @Test
        @DisplayName("Name order variation: 'Ping Yun CHANG' should return entity")
        void nameOrderVariation_firstLast() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "Ping Yun CHANG")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'CHANG, Ping Yun')]").exists());
        }
    }

    /**
     * Row 13: AL-ADL, Sayf
     * 
     * <p><strong>Issue</strong>: "Name order variation for Sayf AL ADL (FN-LN) missed to list any hits."
     * 
     * <p><strong>Expected</strong>: Searching "Sayf AL ADL" should return "AL-ADL, Sayf"
     */
    @Nested
    @DisplayName("Row 13: AL-ADL, Sayf")
    class Row13_AlAdl_Sayf {
        
        @Test
        @DisplayName("Baseline: 'AL-ADL, Sayf' should return entity")
        void baseline_commaFormat() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "AL-ADL, Sayf")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'AL-ADL, Sayf')]").exists());
        }
        
        @Test
        @DisplayName("Name order variation: 'Sayf AL ADL' should return entity")
        void nameOrderVariation_firstLast() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "Sayf AL ADL")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'AL-ADL, Sayf')]").exists());
        }
    }

    /**
     * Row 16: AL-HAQ, Amin
     * 
     * <p><strong>Issue</strong>: "Name order variation for Amin AL HAQ (FN-LN) missed to list the true match."
     * 
     * <p><strong>Expected</strong>: Searching "Amin AL HAQ" should return "AL-HAQ, Amin"
     */
    @Nested
    @DisplayName("Row 16: AL-HAQ, Amin")
    class Row16_AlHaq_Amin {
        
        @Test
        @DisplayName("Baseline: 'AL-HAQ, Amin' should return entity")
        void baseline_commaFormat() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "AL-HAQ, Amin")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'AL-HAQ, Amin')]").exists());
        }
        
        @Test
        @DisplayName("Name order variation: 'Amin AL HAQ' should return entity")
        void nameOrderVariation_firstLast() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "Amin AL HAQ")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'AL-HAQ, Amin')]").exists());
        }
    }

    /**
     * Row 20: CHURO, Leanid Mikalaevich
     * 
     * <p><strong>Issue</strong>: "Name order variation for Leanid Mikalaevich CHURO (FN-LN) 
     * missed to list any hits."
     * 
     * <p><strong>Expected</strong>: Searching "Leanid Mikalaevich CHURO" should return 
     * "CHURO, Leanid Mikalaevich"
     */
    @Nested
    @DisplayName("Row 20: CHURO, Leanid Mikalaevich")
    class Row20_Churo_Leanid {
        
        @Test
        @DisplayName("Baseline: 'CHURO, Leanid Mikalaevich' should return entity")
        void baseline_commaFormat() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "CHURO, Leanid Mikalaevich")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'CHURO, Leanid Mikalaevich')]").exists());
        }
        
        @Test
        @DisplayName("Name order variation: 'Leanid Mikalaevich CHURO' should return entity")
        void nameOrderVariation_firstLast() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "Leanid Mikalaevich CHURO")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'CHURO, Leanid Mikalaevich')]").exists());
        }
    }

    /**
     * Row 15: GHAILANI, Ahmed Khalfan
     * 
     * <p><strong>Issue</strong>: "Aliases 'FOOPIE' 'FUPI' missed to list the true match."
     * 
     * <p><strong>Expected</strong>: Searching for these unusual aliases should return the entity
     */
    @Nested
    @DisplayName("Row 15: GHAILANI, Ahmed Khalfan - Unusual Aliases")
    class Row15_Ghailani_UnusualAliases {
        
        @Test
        @DisplayName("Baseline: Full name should return entity")
        void baseline_fullName() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "GHAILANI, Ahmed Khalfan")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'GHAILANI, Ahmed Khalfan')]").exists());
        }
        
        @Test
        @DisplayName("Unusual alias: 'FOOPIE' should match entity")
        void unusualAlias_foopie() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "FOOPIE")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'GHAILANI, Ahmed Khalfan')]").exists());
        }
        
        @Test
        @DisplayName("Unusual alias: 'FUPI' should match entity")
        void unusualAlias_fupi() throws Exception {
            mockMvc.perform(get("/v1/search")
                    .param("name", "FUPI")
                    .param("minMatch", "0.70")
                    .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[?(@.name == 'GHAILANI, Ahmed Khalfan')]").exists());
        }
    }
}
