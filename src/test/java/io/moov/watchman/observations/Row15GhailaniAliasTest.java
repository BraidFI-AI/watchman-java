package io.moov.watchman.observations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Row 15: GHAILANI, Ahmed Khalfan - Unusual Alias Investigation
 * 
 * <p><strong>Issue</strong>: Aliases "FOOPIE" and "FUPI" not matching entity
 * 
 * <p><strong>Analysis</strong>: Short tokens (≤4 chars) require exact matching per 
 * phonetic restrictions. Need to verify if these aliases exist in current OFAC data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Row 15: GHAILANI, Ahmed Khalfan - FOOPIE/FUPI Aliases")
public class Row15GhailaniAliasTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Search 'GHAILANI' - verify entity and aliases")
    void searchGhailani() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "GHAILANI")
                .param("minMatch", "0.70")
                .param("limit", "20"))
            .andDo(print())
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Search 'Ahmed Khalfan GHAILANI' - verify entity exists")
    void searchFullName() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "Ahmed Khalfan GHAILANI")
                .param("minMatch", "0.70")
                .param("limit", "20"))
            .andDo(print())
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Search 'FOOPIE' - check if alias exists and matches")
    void searchFoopie() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "FOOPIE")
                .param("minMatch", "0.50")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andReturn();
        
        String json = result.getResponse().getContentAsString();
        boolean ghailaniFound = json.contains("\"entityID\":\"6925\"");
        System.out.println("\n=== FOOPIE Search Results ===");
        System.out.println("GHAILANI (entityID 6925) found: " + ghailaniFound);
        
        if (!ghailaniFound) {
            System.out.println("⚠️ FOOPIE does NOT match GHAILANI - alias missing from OFAC data");
        }
    }

    @Test
    @DisplayName("Search 'FUPI' - check if alias exists and matches")
    void searchFupi() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "FUPI")
                .param("minMatch", "0.50")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andReturn();
        
        String json = result.getResponse().getContentAsString();
        boolean ghailaniFound = json.contains("\"entityID\":\"6925\"");
        System.out.println("\n=== FUPI Search Results ===");
        System.out.println("GHAILANI (entityID 6925) found: " + ghailaniFound);
        
        if (!ghailaniFound) {
            System.out.println("⚠️ FUPI does NOT match GHAILANI - alias missing from OFAC data");
        }
    }
}
