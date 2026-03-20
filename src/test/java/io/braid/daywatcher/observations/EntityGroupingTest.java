package io.braid.daywatcher.observations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BSA/AML Observations Row 14 & 19: Entity Grouping and Alias Substring Ranking
 * 
 * <p><strong>TDD Phase: RED</strong>
 * 
 * <p><strong>Issues</strong>:
 * <ul>
 *   <li>Row 14: AL-ISLAMIYYA and ISLAMIC GROUP searches - coverage concerns (may be resolved)</li>
 *   <li>Row 19: HIZBALLAH BAYT AL-MAQDIS search ranks HIZBALLAH first, but entity with matching alias substring ranks much lower</li>
 * </ul>
 * 
 * <p><strong>Root Cause Analysis</strong>:
 * Row 19 issue: When searching "HIZBALLAH BAYT AL-MAQDIS":
 * <ul>
 *   <li>HIZBALLAH (ID 4697) ranks 1st with score 0.917 - only matches "HIZBALLAH" token</li>
 *   <li>PIJ (ID 4707) ranks 75th with score 0.836 - has alias "ABU GHUNAYM SQUAD OF THE HIZBALLAH BAYT AL-MAQDIS"</li>
 *   <li>PIJ's alias contains exact substring "HIZBALLAH BAYT AL-MAQDIS" but scores lower than partial match</li>
 * </ul>
 * 
 * <p><strong>Expected Behavior</strong>:
 * Entities with alias substrings matching more query tokens should rank higher than entities
 * with only partial token matches.
 * 
 * <p>These tests define expected behavior for entity grouping and alias substring ranking.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("BSA Observations Row 14 & 19: Entity Grouping and Alias Ranking")
public class EntityGroupingTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * RED Phase Test 1 (Row 19): HIZBALLAH BAYT AL-MAQDIS should return PIJ entity
     * 
     * <p>Entity: PALESTINE ISLAMIC JIHAD - SHAQAQI FACTION (ID 4707)
     * <p>Alias: "ABU GHUNAYM SQUAD OF THE HIZBALLAH BAYT AL-MAQDIS"
     * <p>Expected: Should be in results
     */
    @Test
    @DisplayName("Row 19: Search 'HIZBALLAH BAYT AL-MAQDIS' should return PIJ with matching alias")
    void searchHizballahBaytAlMaqdis_shouldReturnPij() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "HIZBALLAH BAYT AL-MAQDIS")
                .param("minMatch", "0.70")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[?(@.id == '4707')]").exists());
    }

    /**
     * RED Phase Test 2 (Row 19): PIJ with alias substring should rank in top 5
     * 
     * <p>PIJ has alias "ABU GHUNAYM SQUAD OF THE HIZBALLAH BAYT AL-MAQDIS"
     * <p>Alias contains exact substring "HIZBALLAH BAYT AL-MAQDIS"
     * <p>This test will FAIL - currently ranks at position 75
     */
    @Test
    @DisplayName("Row 19: PIJ with matching alias substring should rank in top 5")
    void searchHizballahBaytAlMaqdis_pijShouldRankHigh() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "HIZBALLAH BAYT AL-MAQDIS")
                .param("minMatch", "0.70")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[0:5][?(@.id == '4707')]").exists());
    }

    /**
     * RED Phase Test 3 (Row 19): PIJ should rank higher than HIZBALLAH
     * 
     * <p>PIJ alias contains "HIZBALLAH BAYT AL-MAQDIS" (3 tokens matched)
     * <p>HIZBALLAH only matches "HIZBALLAH" (1 token matched)
     * <p>This test will FAIL - currently HIZBALLAH ranks 1st, PIJ ranks 75th
     */
    @Test
    @DisplayName("Row 19: PIJ should rank higher than HIZBALLAH for 'HIZBALLAH BAYT AL-MAQDIS' search")
    void searchHizballahBaytAlMaqdis_pijShouldRankHigherThanHizballah() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "HIZBALLAH BAYT AL-MAQDIS")
                .param("minMatch", "0.70")
                .param("limit", "20"))
            .andExpect(status().isOk())
            // PIJ (4707) should score higher than HIZBALLAH (4697)
            .andExpect(jsonPath("$.entities[?(@.id == '4707' && @.score > 0.92)]").exists());
    }

    /**
     * RED Phase Test 4 (Row 19): Verify HIZBALLAH should not be first result
     * 
     * <p>HIZBALLAH (ID 4697) currently ranks first but only has partial match
     */
    @Test
    @DisplayName("Row 19: HIZBALLAH should not be first result")
    void searchHizballahBaytAlMaqdis_hizballahShouldNotBeFirst() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "HIZBALLAH BAYT AL-MAQDIS")
                .param("minMatch", "0.70")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[0].id").value(not("4697")));
    }

    /**
     * RED Phase Test 5 (Row 14): AL-ISLAMIYYA search should return GAMA'A AL-ISLAMIYYA
     * 
     * <p>Entity: GAMA'A AL-ISLAMIYYA (ID 4694)
     * <p>This may already be working - verification test
     */
    @Test
    @DisplayName("Row 14: Search 'AL-ISLAMIYYA' should return GAMA'A AL-ISLAMIYYA")
    void searchAlIslamiyya_shouldReturnGamaaAlIslamiyya() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "AL-ISLAMIYYA")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[?(@.id == '4694')]").exists())
            .andExpect(jsonPath("$.entities[?(@.name == 'GAMA\\'A AL-ISLAMIYYA')]").exists());
    }

    /**
     * RED Phase Test 6 (Row 14): ISLAMIC GROUP search should return GAMA'A AL-ISLAMIYYA
     * 
     * <p>GAMA'A AL-ISLAMIYYA has "ISLAMIC GROUP" as an alias
     * <p>This may already be working - verification test
     */
    @Test
    @DisplayName("Row 14: Search 'ISLAMIC GROUP' should return GAMA'A AL-ISLAMIYYA")
    void searchIslamicGroup_shouldReturnGamaaAlIslamiyya() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "ISLAMIC GROUP")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[?(@.id == '4694')]").exists())
            .andExpect(jsonPath("$.entities[?(@.name == 'GAMA\\'A AL-ISLAMIYYA')]").exists());
    }

    /**
     * RED Phase Test 7 (Row 14): ISLAMIC GROUP should return GAMA'A AL-ISLAMIYYA with high score
     * 
     * <p>"ISLAMIC GROUP" is an exact alias match, should have score close to 1.0
     */
    @Test
    @DisplayName("Row 14: 'ISLAMIC GROUP' should match GAMA'A AL-ISLAMIYYA with high score")
    void searchIslamicGroup_gamaaAlIslamiyyaShouldHaveHighScore() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "ISLAMIC GROUP")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[?(@.id == '4694' && @.score >= 0.95)]").exists());
    }
}
