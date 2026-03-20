package io.braid.daywatcher.api;

import io.braid.daywatcher.WatchmanApplication;
import io.braid.daywatcher.trace.TraceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Test for S.I. 2: ANGLO-CARIBBEAN CO., LTD.
 * 
 * BSA Observation: Pass on all criteria
 * - Baseline Name Matching: Pass
 * - Partial / Close Name Handling: Pass
 * - Alias-Based Matching: Pass (AVIA IMPORT)
 * 
 * This is a positive test case to verify correct behavior.
 */
@SpringBootTest(classes = WatchmanApplication.class)
@AutoConfigureMockMvc
public class AngloCaribbeanTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TraceRepository traceRepository;

    @Test
    @DisplayName("S.I. 2.1: Full name search should find ANGLO-CARIBBEAN CO., LTD.")
    void fullNameShouldFindEntity() throws Exception {
        // GIVEN: BSA test case S.I. 2 - ANGLO-CARIBBEAN CO., LTD.
        // EXPECTED: Direct name search returns the entity with high confidence (>0.85)
        
        mockMvc.perform(get("/v1/search")
                .param("name", "ANGLO-CARIBBEAN CO., LTD.")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.entities[?(@.name == 'ANGLO-CARIBBEAN CO., LTD.')]").exists())
            .andExpect(jsonPath("$.entities[0].score", greaterThanOrEqualTo(0.85)));
    }

    @Test
    @DisplayName("S.I. 2.2: Partial name 'ANGLO-CARIBBEAN' should find entity")
    void partialNameShouldFindEntity() throws Exception {
        // GIVEN: Partial name search without legal suffix
        // EXPECTED: Entity returned with reasonable confidence
        
        mockMvc.perform(get("/v1/search")
                .param("name", "ANGLO-CARIBBEAN")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.entities[?(@.name == 'ANGLO-CARIBBEAN CO., LTD.')]").exists());
    }

    @Test
    @DisplayName("S.I. 2.3: Alias 'AVIA IMPORT' should find entity")
    void aliasShouldFindEntity() throws Exception {
        // GIVEN: BSA verified alias "AVIA IMPORT"
        // EXPECTED: Entity returned with matchedAlias populated
        
        mockMvc.perform(get("/v1/search")
                .param("name", "AVIA IMPORT")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.entities[?(@.name == 'ANGLO-CARIBBEAN CO., LTD.')]").exists())
            .andExpect(jsonPath("$.entities[0].matchedAlias", notNullValue()));
    }

    @Test
    @DisplayName("S.I. 2.4: All searches should produce high-confidence scores (trace-eligible)")
    void searchesShouldProduceHighConfidence() throws Exception {
        // GIVEN: S.I. 2 shows "Pass" - meaning all searches work correctly
        // EXPECTED: Scores meet trace confidence threshold (>=0.85)
        
        // Test full name
        mockMvc.perform(get("/v1/search")
                .param("name", "ANGLO-CARIBBEAN CO., LTD.")
                .param("minMatch", "0.70")
                .param("trace", "true")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trace").exists())
            .andExpect(jsonPath("$.entities[0].score", greaterThanOrEqualTo(0.85)));
        
        // Test alias
        mockMvc.perform(get("/v1/search")
                .param("name", "AVIA IMPORT")
                .param("minMatch", "0.70")
                .param("trace", "true")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trace").exists())
            .andExpect(jsonPath("$.entities[0].score", greaterThanOrEqualTo(0.85)));
    }
}
