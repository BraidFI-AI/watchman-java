package io.braid.daywatcher.observations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Investigation: ARELLANO FELIX Ranking Issue (Row 6)
 * 
 * <p><strong>Issue</strong>: When searching "Ramon Eduardo ARELLANO FELIX", the system returns:
 * <ul>
 *   <li>1st result: ARELLANO FELIX, Eduardo Ramon (YOB 1956) ❌ Wrong person</li>
 *   <li>Should be 1st: ARELLANO FELIX, Ramon Eduardo (YOB 1964) ✅ Correct person</li>
 * </ul>
 * 
 * <p><strong>Root Cause Analysis</strong>:
 * Both individuals have very similar names:
 * <ul>
 *   <li>"ARELLANO FELIX, Ramon Eduardo" (query matches perfectly)</li>
 *   <li>"ARELLANO FELIX, Eduardo Ramon" (query matches with middle name swapped)</li>
 * </ul>
 * 
 * The system needs to prioritize exact token order matches over permutations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Investigation: ARELLANO FELIX Ranking (Row 6)")
public class ArellanoFelixRankingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Search 'Ramon Eduardo ARELLANO FELIX' - analyze ranking")
    void analyzeRamونEduardoSearch() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "Ramon Eduardo ARELLANO FELIX")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();
        
        String response = result.getResponse().getContentAsString();
        System.out.println("\n=== ARELLANO FELIX RANKING ANALYSIS ===");
        System.out.println("Query: Ramon Eduardo ARELLANO FELIX");
        System.out.println("\nTop 5 Results:");
        System.out.println(response);
        System.out.println("\n=== END ANALYSIS ===\n");
    }

    @Test
    @DisplayName("Search 'ARELLANO FELIX, Ramon Eduardo' - comma format")
    void searchWithCommaFormat() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "ARELLANO FELIX, Ramon Eduardo")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();
        
        String response = result.getResponse().getContentAsString();
        System.out.println("\n=== COMMA FORMAT SEARCH ===");
        System.out.println("Query: ARELLANO FELIX, Ramon Eduardo");
        System.out.println("\nTop 5 Results:");
        System.out.println(response);
        System.out.println("\n=== END ANALYSIS ===\n");
    }

    @Test
    @DisplayName("Compare scores: Ramon Eduardo vs Eduardo Ramon")
    void compareScores() throws Exception {
        // Search for Ramon Eduardo
        MvcResult result1 = mockMvc.perform(get("/v1/search")
                .param("name", "Ramon Eduardo ARELLANO FELIX")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();
        
        // Search for Eduardo Ramon (expecting lower score for Ramon Eduardo entity)
        MvcResult result2 = mockMvc.perform(get("/v1/search")
                .param("name", "Eduardo Ramon ARELLANO FELIX")
                .param("minMatch", "0.70")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();
        
        System.out.println("\n=== SCORE COMPARISON ===");
        System.out.println("Query 1: Ramon Eduardo ARELLANO FELIX");
        System.out.println(result1.getResponse().getContentAsString());
        System.out.println("\nQuery 2: Eduardo Ramon ARELLANO FELIX");
        System.out.println(result2.getResponse().getContentAsString());
        System.out.println("\n=== END COMPARISON ===\n");
    }
}
