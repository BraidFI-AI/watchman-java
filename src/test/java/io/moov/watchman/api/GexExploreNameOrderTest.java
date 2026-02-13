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
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test for Row 27: GEX EXPLORE S. DE R.L. DE C.V.
 * 
 * Issue: Name order variation (EXPLORE GEX) didn't return the main entity.
 * Root Cause: TBD - system should handle token order automatically via bestPairCombinationJaroWinkler
 * 
 * Expected: Both "GEX EXPLORE" and "EXPLORE GEX" should find the entity
 */
@SpringBootTest(classes = WatchmanApplication.class)
@AutoConfigureMockMvc
public class GexExploreNameOrderTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Row 27.1: Standard order 'GEX EXPLORE' should find entity")
    void standardOrderShouldFindEntity() throws Exception {
        // GIVEN: Entity name is "GEX EXPLORE S. DE R.L. DE C.V."
        // WHEN: User searches with standard order
        // THEN: Should find entity with high score
        
        mockMvc.perform(get("/v1/search")
                .param("name", "GEX EXPLORE")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.entities[?(@.name =~ /GEX EXPLORE.*/i)]").exists());
    }

    @Test
    @DisplayName("Row 27.2: Reversed order 'EXPLORE GEX' should find entity (BSA ISSUE)")
    void reversedOrderShouldFindEntity() throws Exception {
        // GIVEN: Entity name is "GEX EXPLORE S. DE R.L. DE C.V."
        // WHEN: User searches with reversed token order
        // THEN: Should find entity (token order should not matter)
        
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "EXPLORE GEX")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();
        
        String response = result.getResponse().getContentAsString();
        
        // Debug: Print response to see what we get
        System.out.println("=== EXPLORE GEX Search Results ===");
        System.out.println(response);
        
        // Verify entity is found
        assertThat(response)
            .as("Reversed name order 'EXPLORE GEX' should find 'GEX EXPLORE' entity")
            .containsIgnoringCase("GEX EXPLORE");
    }

    @Test
    @DisplayName("Row 27.3: Full name with legal suffix should match")
    void fullNameWithSuffixShouldMatch() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "GEX EXPLORE S DE R L DE C V")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.entities[?(@.name =~ /GEX EXPLORE.*/i)]").exists());
    }

    @Test
    @DisplayName("Row 27.4: Verify token order independence is working")
    void tokenOrderIndependenceVerification() throws Exception {
        // Get results for both orders
        MvcResult standardResult = mockMvc.perform(get("/v1/search")
                .param("name", "GEX EXPLORE")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();
        
        MvcResult reversedResult = mockMvc.perform(get("/v1/search")
                .param("name", "EXPLORE GEX")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();
        
        String standardResponse = standardResult.getResponse().getContentAsString();
        String reversedResponse = reversedResult.getResponse().getContentAsString();
        
        // Both should contain the entity
        boolean standardContains = standardResponse.toLowerCase().contains("gex explore");
        boolean reversedContains = reversedResponse.toLowerCase().contains("gex explore");
        
        System.out.println("Standard order contains entity: " + standardContains);
        System.out.println("Reversed order contains entity: " + reversedContains);
        
        assertThat(standardContains)
            .as("Standard order should find entity")
            .isTrue();
        
        assertThat(reversedContains)
            .as("Reversed order should find entity (token order independence)")
            .isTrue();
    }
}
