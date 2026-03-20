package io.braid.daywatcher.observations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BSA/AML Observations Row 49 & 50: Short Code Matching
 * 
 * <p><strong>Status: ALREADY FIXED ✅</strong>
 * 
 * <p><strong>Issues</strong>:
 * <ul>
 *   <li>Row 49: EP-MOQ (Aircraft) - search returned wrong aliases (EP-MNG, EP-MNJ, EP-MNK, EP-MNQ, EP-MHG)</li>
 *   <li>Row 50: AAJ (Vessel) - search returned wrong entities (AUC, AUTODEFENSAS UNIDAS DE COLOMBIA, etc.)</li>
 * </ul>
 * 
 * <p><strong>Root Cause</strong>:
 * Short codes (3-6 characters) were matching incorrectly, likely due to:
 * <ul>
 *   <li>Over-permissive phonetic matching on short strings (fixed in Rows 13, 16, 18, 24)</li>
 *   <li>Lack of acronym handling for hyphenated codes (fixed in Rows 26, 31)</li>
 * </ul>
 * 
 * <p><strong>Solution</strong>:
 * Already fixed by previous enhancements:
 * <ul>
 *   <li>Minimum token length filter: Tokens ≤4 characters cannot use phonetic matching</li>
 *   <li>Acronym collapsing: Adjacent single-letter tokens merged (e.g., "e p moq" → "epmoq")</li>
 *   <li>Exact matching required for short codes</li>
 * </ul>
 * 
 * <p><strong>Results</strong>:
 * <ul>
 *   <li>✅ "EP-MOQ" returns entity 24491 (Aircraft) with score 1.0</li>
 *   <li>✅ "AAJ" returns entity 25277 (Vessel) with score 1.0</li>
 *   <li>✅ Both searches return correct entity as first result</li>
 * </ul>
 * 
 * <p>These tests document the correct behavior and prevent regression.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("BSA Observations Row 49 & 50: Short Code Matching")
public class ShortCodeMatchingTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Row 49: EP-MOQ (Aircraft) should return correct entity
     * 
     * <p>Entity in OFAC: "EP-MOQ" (ID 24491, Aircraft)
     * <p>Expected: Should return entity 24491 with perfect or near-perfect score
     */
    @Test
    @DisplayName("Row 49: Search 'EP-MOQ' should match aircraft entity 24491")
    void searchEpMoq_shouldMatchAircraftEntity() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "EP-MOQ")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities").isArray())
            .andExpect(jsonPath("$.entities[?(@.name == 'EP-MOQ')]").exists())
            .andExpect(jsonPath("$.entities[?(@.id == '24491')]").exists());
    }

    /**
     * Row 49: Verify EP-MOQ match score is excellent
     * 
     * <p>Should return as first result with score ≥0.95
     */
    @Test
    @DisplayName("Row 49: 'EP-MOQ' should be first result with high score")
    void searchEpMoq_shouldHaveHighScore() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "EP-MOQ")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[0].name").value("EP-MOQ"))
            .andExpect(jsonPath("$.entities[0].id").value("24491"))
            .andExpect(jsonPath("$.entities[0].type").value("AIRCRAFT"))
            .andExpect(jsonPath("$.entities[0].score").value(greaterThanOrEqualTo(0.95)));
    }

    /**
     * Row 49: Verify no wrong aliases returned
     * 
     * <p>Observation mentioned wrong matches: EP-MNG, EP-MNJ, EP-MNK, EP-MNQ, EP-MHG
     * <p>These should NOT be the first result anymore
     */
    @Test
    @DisplayName("Row 49: First result should be EP-MOQ, not wrong aliases")
    void searchEpMoq_shouldNotReturnWrongAliasesFirst() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "EP-MOQ")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[0].name").value("EP-MOQ"))
            .andExpect(jsonPath("$.entities[0].name").value(org.hamcrest.Matchers.not("EP-MNG")))
            .andExpect(jsonPath("$.entities[0].name").value(org.hamcrest.Matchers.not("EP-MNJ")))
            .andExpect(jsonPath("$.entities[0].name").value(org.hamcrest.Matchers.not("EP-MNK")))
            .andExpect(jsonPath("$.entities[0].name").value(org.hamcrest.Matchers.not("EP-MNQ")))
            .andExpect(jsonPath("$.entities[0].name").value(org.hamcrest.Matchers.not("EP-MHG")));
    }

    /**
     * Row 50: AAJ (Vessel) should return correct entity
     * 
     * <p>Entity in OFAC: "AAJ" (ID 25277, Vessel)
     * <p>Expected: Should return entity 25277 with perfect or near-perfect score
     */
    @Test
    @DisplayName("Row 50: Search 'AAJ' should match vessel entity 25277")
    void searchAaj_shouldMatchVesselEntity() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "AAJ")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities").isArray())
            .andExpect(jsonPath("$.entities[?(@.name == 'AAJ')]").exists())
            .andExpect(jsonPath("$.entities[?(@.id == '25277')]").exists());
    }

    /**
     * Row 50: Verify AAJ match score is excellent
     * 
     * <p>Should return as first result with score ≥0.95
     */
    @Test
    @DisplayName("Row 50: 'AAJ' should be first result with high score")
    void searchAaj_shouldHaveHighScore() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "AAJ")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[0].name").value("AAJ"))
            .andExpect(jsonPath("$.entities[0].id").value("25277"))
            .andExpect(jsonPath("$.entities[0].type").value("VESSEL"))
            .andExpect(jsonPath("$.entities[0].score").value(greaterThanOrEqualTo(0.95)));
    }

    /**
     * Row 50: Verify no wrong entities returned
     * 
     * <p>Observation mentioned wrong matches: AUC, AUTODEFENSAS UNIDAS DE COLOMBIA
     * <p>These should NOT be the first result anymore
     */
    @Test
    @DisplayName("Row 50: First result should be AAJ, not wrong entities")
    void searchAaj_shouldNotReturnWrongEntitiesFirst() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "AAJ")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[0].name").value("AAJ"))
            .andExpect(jsonPath("$.entities[0].name").value(org.hamcrest.Matchers.not("AUC")))
            .andExpect(jsonPath("$.entities[0].name").value(org.hamcrest.Matchers.not("AUTODEFENSAS UNIDAS DE COLOMBIA")));
    }
}
