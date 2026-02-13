package io.moov.watchman.api;

import io.moov.watchman.WatchmanApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for legal suffix removal across multiple entity types.
 * 
 * Covers CSV rows 23, 30, 39, 42:
 * - Row 23: GALAPAGOS S.A → search "GALAPAGOS" should find entity
 * - Row 30: ADP, S.C. → search "ADP" should find entity
 * - Row 39: STROYTRANSGAZ OJSC → search "STROYTRANSGAZ" should find entity
 * - Row 42: NPK TEKHMASH OAO → search "TEKHMASH" should find entity
 * 
 * Root Cause: After punctuation removal, "S.A." → "s a", "OJSC" → "ojsc"
 * The company suffix list must include spaced-out versions after normalization.
 */
@SpringBootTest(classes = WatchmanApplication.class)
@AutoConfigureMockMvc
public class LegalSuffixRemovalTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Row 23: 'GALAPAGOS' should find 'GALAPAGOS S.A'")
    void galapagosWithoutSuffixShouldFindEntity() throws Exception {
        // GIVEN: Entity name is "GALAPAGOS S.A"
        // WHEN: User searches without suffix "S.A."
        // THEN: Should find entity (not unrelated "GLBC" or "GLAVGOSEKSPERTIZA")
        
        mockMvc.perform(get("/v1/search")
                .param("name", "GALAPAGOS")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.entities[?(@.name =~ /GALAPAGOS.*/i)]").exists());
    }

    @Test
    @DisplayName("Row 30: 'ADP' should find 'ADP, S.C.'")
    void adpWithoutSuffixShouldFindEntity() throws Exception {
        // GIVEN: Entity name is "ADP, S.C."
        // WHEN: User searches partial name without suffix
        // THEN: Should find entity (not unrelated "ADF" alias)
        
        mockMvc.perform(get("/v1/search")
                .param("name", "ADP")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.entities[?(@.name =~ /ADP.*/i)]").exists());
    }

    @Test
    @DisplayName("Row 39: 'STROYTRANSGAZ' should find 'STROYTRANSGAZ OJSC'")
    void stroytransgazWithoutSuffixShouldFindEntity() throws Exception {
        // GIVEN: Entity name is "STROYTRANSGAZ OJSC"
        // WHEN: User searches without Russian legal suffix
        // THEN: Should find primary entity and related "OAO STROYTRANSGAZ"
        
        mockMvc.perform(get("/v1/search")
                .param("name", "STROYTRANSGAZ")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.entities[?(@.name =~ /STROYTRANSGAZ.*/i)]").exists());
    }

    @Test
    @DisplayName("Row 42: 'TEKHMASH' should find 'NPK TEKHMASH OAO'")
    void tekhmashWithoutPrefixOrSuffixShouldFindEntity() throws Exception {
        // GIVEN: Entity name is "NPK TEKHMASH OAO"
        // WHEN: User searches core name without prefix/suffix
        // THEN: Should find entity (not unrelated "TSNIIAG" alias)
        
        mockMvc.perform(get("/v1/search")
                .param("name", "TEKHMASH")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.entities[?(@.name =~ /.*TEKHMASH.*/i)]").exists());
    }

    @Test
    @DisplayName("S.A. suffix removal: Full name with suffix should also match")
    void fullNameWithSuffixShouldStillMatch() throws Exception {
        // Verify full name searches still work after suffix handling changes
        mockMvc.perform(get("/v1/search")
                .param("name", "GALAPAGOS S.A")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("Russian OAO suffix removal: Normalized matching")
    void russianOaoSuffixShouldBeRemoved() throws Exception {
        // Test that OAO suffix is properly handled
        mockMvc.perform(get("/v1/search")
                .param("name", "NPK TEKHMASH")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.entities[?(@.name =~ /.*TEKHMASH.*/i)]").exists());
    }

    @Test
    @DisplayName("Row 12: 'MASKAN' should find 'BANK MASKAN'")
    void maskanWithoutPrefixShouldFindEntity() throws Exception {
        // GIVEN: Entity name is "BANK MASKAN"
        // WHEN: User searches without "BANK" prefix
        // THEN: Should find entity (tokenized matching should work)
        
        mockMvc.perform(get("/v1/search")
                .param("name", "MASKAN")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.entities[?(@.name =~ /.*MASKAN.*/i)]").exists());
    }

    @Test
    @DisplayName("Row 38: 'ROSSIYA' should find 'BANK ROSSIYA'")
    void rossiyaWithoutPrefixShouldFindEntity() throws Exception {
        // GIVEN: Entity name is "BANK ROSSIYA"
        // WHEN: User searches without "BANK" prefix
        // THEN: Should find entity (not unrelated "RIHS" alias)
        
        mockMvc.perform(get("/v1/search")
                .param("name", "ROSSIYA")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.entities[?(@.name =~ /.*ROSSIYA.*/i)]").exists());
    }

    @Test
    @DisplayName("Row 52: 'OTKRITIE' should find 'OOO OTKRITIE CAPITAL'")
    void otkritieWithoutPrefixShouldFindEntity() throws Exception {
        // GIVEN: Entity name is "OOO OTKRITIE CAPITAL"
        // WHEN: User searches core name without prefix
        // THEN: Should find related entities
        
        mockMvc.perform(get("/v1/search")
                .param("name", "OTKRITIE")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.entities[?(@.name =~ /.*OTKRITIE.*/i)]").exists());
    }
}
