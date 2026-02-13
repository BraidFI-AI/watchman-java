package io.moov.watchman.observations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BSA/AML Observations Row 26 & 31: Punctuation Sensitivity
 * 
 * <p><strong>TDD Phase: GREEN ✅</strong>
 * 
 * <p><strong>Issues</strong>:
 * <ul>
 *   <li>Row 26: "ACCESOS ELECTRONICOS SADE CV" doesn't match "ACCESOS ELECTRONICOS S.A.DE C.V."</li>
 *   <li>Row 31: "TEG LIMITED" doesn't match "T.E.G. LIMITED"</li>
 * </ul>
 * 
 * <p><strong>Root Cause</strong>:
 * Normalization converts periods to spaces creating token mismatches:
 * <ul>
 *   <li>Entity: "S.A.DE" → "s a de" (3 tokens) vs Query: "SADE" → "sade" (1 token)</li>
 *   <li>Entity: "T.E.G." → "t e g" (3 tokens) vs Query: "TEG" → "teg" (1 token)</li>
 * </ul>
 * 
 * <p><strong>Solution</strong>:
 * Added `collapseAcronymTokens()` in JaroWinklerSimilarity to merge adjacent single-letter tokens:
 * <ul>
 *   <li>["t", "e", "g", "limited"] → ["teg", "limited"]</li>
 *   <li>["accesos", "electronicos", "s", "a", "de", "c", "v"] → ["accesos", "electronicos", "sade", "cv"]</li>
 * </ul>
 * 
 * <p><strong>Results</strong>:
 * <ul>
 *   <li>✅ "TEG LIMITED" now matches "T.E.G. LIMITED" with score 1.0</li>
 *   <li>✅ "ACCESOS ELECTRONICOS SADE CV" matches entity with high score</li>
 *   <li>✅ Exact searches with punctuation still work perfectly</li>
 *   <li>✅ No regressions in existing BSA observation tests</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("BSA Observations Row 26 & 31: Punctuation Sensitivity")
public class PunctuationSensitivityTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * RED Phase Test 1: Row 26 - ACCESOS ELECTRONICOS SADE CV should match entity
     * 
     * <p>Entity in OFAC: "ACCESOS ELECTRONICOS S.A.DE C.V." (ID 7151)
     * <p>User query: "ACCESOS ELECTRONICOS SADE CV" (typed without periods)
     * 
     * <p>Expected: Should return entity 7151 with reasonable score (≥70%)
     * <p>This test will FAIL until we implement a fix for punctuation handling.
     */
    @Test
    @DisplayName("Row 26: Search 'ACCESOS ELECTRONICOS SADE CV' should match 'ACCESOS ELECTRONICOS S.A.DE C.V.'")
    void searchAccesosWithoutPunctuation_shouldMatchEntity() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "ACCESOS ELECTRONICOS SADE CV")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities").isArray())
            .andExpect(jsonPath("$.entities[?(@.name == 'ACCESOS ELECTRONICOS S.A.DE C.V.')]").exists())
            .andExpect(jsonPath("$.entities[?(@.name == 'ACCESOS ELECTRONICOS S.A.DE C.V.')].score").exists());
    }

    /**
     * RED Phase Test 2: Row 26 - Verify score is reasonable
     * 
     * <p>The match score should be good enough to surface the entity in results.
     */
    @Test
    @DisplayName("Row 26: Match score for 'ACCESOS ELECTRONICOS SADE CV' should be ≥70%")
    void searchAccesosWithoutPunctuation_shouldHaveGoodScore() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "ACCESOS ELECTRONICOS SADE CV")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[0].score")
                .value(org.hamcrest.Matchers.greaterThanOrEqualTo(0.70)));
    }

    /**
     * RED Phase Test 3: Row 31 - TEG LIMITED should match entity
     * 
     * <p>Entity in OFAC: "T.E.G. LIMITED" (ID 8404)
     * <p>User query: "TEG LIMITED" (typed without periods)
     * 
     * <p>Expected: Should return entity 8404 with reasonable score (≥70%)
     * <p>This test will FAIL until we implement a fix for punctuation handling.
     */
    @Test
    @DisplayName("Row 31: Search 'TEG LIMITED' should match 'T.E.G. LIMITED'")
    void searchTegWithoutPunctuation_shouldMatchEntity() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "TEG LIMITED")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities").isArray())
            .andExpect(jsonPath("$.entities[?(@.name == 'T.E.G. LIMITED')]").exists())
            .andExpect(jsonPath("$.entities[?(@.name == 'T.E.G. LIMITED')].score").exists());
    }

    /**
     * RED Phase Test 4: Row 31 - Verify score is reasonable
     * 
     * <p>The match score should be good enough to surface the entity in results.
     */
    @Test
    @DisplayName("Row 31: Match score for 'TEG LIMITED' should be ≥70%")
    void searchTegWithoutPunctuation_shouldHaveGoodScore() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "TEG LIMITED")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[0].score")
                .value(org.hamcrest.Matchers.greaterThanOrEqualTo(0.70)));
    }

    /**
     * RED Phase Test 5: Verify exact name searches still work
     * 
     * <p>Searching with exact punctuation should continue to work perfectly.
     */
    @Test
    @DisplayName("Exact search 'ACCESOS ELECTRONICOS S.A.DE C.V.' should have perfect score")
    void searchAccesosWithExactPunctuation_shouldHavePerfectScore() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "ACCESOS ELECTRONICOS S.A.DE C.V.")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[0].score")
                .value(org.hamcrest.Matchers.closeTo(1.0, 0.05)));
    }

    /**
     * RED Phase Test 6: Verify exact name searches still work for TEG
     */
    @Test
    @DisplayName("Exact search 'T.E.G. LIMITED' should have perfect score")
    void searchTegWithExactPunctuation_shouldHavePerfectScore() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "T.E.G. LIMITED")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[0].score")
                .value(org.hamcrest.Matchers.closeTo(1.0, 0.05)));
    }
}
