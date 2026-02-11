package io.moov.watchman.api;

import io.moov.watchman.WatchmanApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Test for S.I. 5: CECOEX, S.A.
 * 
 * BSA Observation: CRITICAL BUG - False Negative
 * - Full name "CECOEX, S.A.": Pass
 * - Partial name "CECOEX" (without "S.A."): FAILED - entity not listed
 * - Instead matched unrelated individual with alias "CHACHAJEE"
 * 
 * Risk: Partial-name suppression increases the risk of missed entity-level sanctions hits.
 * This is a dangerous false negative that could result in compliance failures.
 * 
 * Expected Behavior:
 * Searching for "CECOEX" should find "CECOEX, S.A." with reasonable confidence.
 * Legal suffixes like "S.A." should not prevent core name matching.
 */
@SpringBootTest(classes = WatchmanApplication.class)
@AutoConfigureMockMvc
public class CecoexPartialNameTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("S.I. 5.1: Full name 'CECOEX, S.A.' should find entity")
    void fullNameShouldFindEntity() throws Exception {
        // GIVEN: BSA confirmed full name search works
        // EXPECTED: Entity found with high confidence
        
        mockMvc.perform(get("/v1/search")
                .param("name", "CECOEX, S.A.")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.entities[?(@.name == 'CECOEX, S.A.')]").exists())
            .andExpect(jsonPath("$.entities[0].score", greaterThanOrEqualTo(0.85)));
    }

    @Test
    @DisplayName("S.I. 5.2: Partial name 'CECOEX' should find 'CECOEX, S.A.' (CURRENTLY FAILS)")
    void partialNameShouldFindEntityNotUnrelatedAlias() throws Exception {
        // GIVEN: User searches for "CECOEX" without legal suffix "S.A."
        // EXPECTED: Should find "CECOEX, S.A." entity
        // ACTUAL (BUG): Returns unrelated individual with alias "CHACHAJEE"
        
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "CECOEX")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();
        
        String response = result.getResponse().getContentAsString();
        
        // Extract entities array to check results
        // BUG: Currently does NOT contain "CECOEX, S.A."
        assertThat(response)
            .as("Partial name 'CECOEX' should find 'CECOEX, S.A.' entity")
            .contains("CECOEX, S.A.");
        
        // Verify it's in the top results (not buried at position 10+)
        mockMvc.perform(get("/v1/search")
                .param("name", "CECOEX")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(jsonPath("$.entities[?(@.name == 'CECOEX, S.A.')]").exists());
    }

    @Test
    @DisplayName("S.I. 5.3: Verify 'CECOEX' search does not prioritize unrelated 'CHACHAJEE' alias")
    void partialNameShouldNotPrioritizeUnrelatedAlias() throws Exception {
        // GIVEN: BSA observation - search returns unrelated individual with alias "CHACHAJEE"
        // EXPECTED: "CECOEX, S.A." should rank higher than unrelated aliases
        
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "CECOEX")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();
        
        String response = result.getResponse().getContentAsString();
        
        // Check if CECOEX, S.A. appears in results
        boolean hasCecoexEntity = response.contains("CECOEX, S.A.");
        
        // Check if unrelated CHACHAJEE alias appears
        boolean hasChachajeeAlias = response.contains("CHACHAJEE");
        
        // EXPECTED: CECOEX entity should be present
        assertThat(hasCecoexEntity)
            .as("CECOEX entity must be in results when searching 'CECOEX'")
            .isTrue();
        
        // If both appear, CECOEX should come before CHACHAJEE
        if (hasCecoexEntity && hasChachajeeAlias) {
            int cecoexPosition = response.indexOf("CECOEX, S.A.");
            int chachajeePosition = response.indexOf("CHACHAJEE");
            
            assertThat(cecoexPosition)
                .as("CECOEX, S.A. should rank higher than unrelated CHACHAJEE alias")
                .isLessThan(chachajeePosition);
        }
    }

    @Test
    @DisplayName("S.I. 5.4: Legal suffix removal should not prevent core name matching")
    void legalSuffixRemovalShouldNotBlockCoreNameMatch() throws Exception {
        // GIVEN: Entity name has legal suffix "S.A."
        // WHEN: User searches without suffix
        // THEN: Core name "CECOEX" should still match with reasonable score
        
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "CECOEX")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();
        
        String response = result.getResponse().getContentAsString();
        
        // Verify the exact entity is found
        assertThat(response)
            .as("Core name matching should work despite legal suffix difference")
            .contains("CECOEX, S.A.");
        
        // Verify it's scored reasonably high (>0.90 expected for near-exact match)
        // BUG: Currently scored at ~0.9466 but ranked 8th due to alias matches scoring 1.0
        assertThat(response)
            .as("CECOEX entity should have high score")
            .containsPattern("\"name\":\"CECOEX, S\\.A\\.\".*?\"score\":(0\\.9[0-9]+|1\\.0)");
    }
}
