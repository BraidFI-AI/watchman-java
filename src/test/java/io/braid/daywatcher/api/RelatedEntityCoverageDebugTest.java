package io.braid.daywatcher.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Debug test for Related Entity Coverage issue (Row 6: CIMEX).
 * 
 * BSA Observation: Searching "CIMEX" finds main entity but misses related entities:
 * - CIMEX IBERICA
 * - COMERCIAL CIMEX S.A.
 * - FINANCIERA CIMEX S.A.
 * 
 * Need to determine if these are:
 * 1. Separate entities in OFAC data that contain "CIMEX" token
 * 2. Aliases of the main CIMEX entity
 * 3. Related but separate sanctioned entities
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Related Entity Coverage Debug - CIMEX")
public class RelatedEntityCoverageDebugTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Row 6: Search 'CIMEX' - check what entities are returned")
    void searchCimex() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "CIMEX")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andReturn();
        
        String response = result.getResponse().getContentAsString();
        
        System.out.println("\n=== CIMEX Search Results ===");
        System.out.println("Query: CIMEX");
        System.out.println("\nLooking for these entities:");
        System.out.println("1. CIMEX (entity 535)");
        System.out.println("2. CIMEX IBERICA (entity 536)");
        System.out.println("3. CIMEX, S.A. (entity 537)");
        System.out.println("4. COMERCIAL CIMEX, S.A. (entity 551)");
        System.out.println("5. CORPORACION CIMEX, S.A. (entity 576)");
        System.out.println("6. CORPORACION CIMEX S.A. (entity 8125)");
        System.out.println("7. FINANCIERA CIMEX S.A (entity 30630)");
        System.out.println("\nResponse:\n" + response);
    }

    @Test
    @DisplayName("Row 21: Search 'AL QA'IDA' - check for related entities")
    void searchAlQaida() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "AL QA'IDA")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andReturn();
        
        String response = result.getResponse().getContentAsString();
        
        System.out.println("\n=== AL QA'IDA Search Results ===");
        System.out.println(response);
        System.out.println("\n=== Looking for: ===");
        System.out.println("1. AL QA'IDA (main entity)");
        System.out.println("2. AL-QA'IDA KURDISH BATTALIONS");
        System.out.println("3. AL-QAIDA GROUP OF JIHAD IN IRAQ");
        System.out.println("4. AL-QA'IDA IN YEMEN");
        System.out.println("5. AL-QAIDA IN SYRIA");
    }

    @Test
    @DisplayName("Row 22: Search 'TALIBAN' - check for related entities")
    void searchTaliban() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "TALIBAN")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andReturn();
        
        String response = result.getResponse().getContentAsString();
        
        System.out.println("\n=== TALIBAN Search Results ===");
        System.out.println(response);
        System.out.println("\n=== Looking for: ===");
        System.out.println("1. TALIBAN (main entity)");
        System.out.println("2. KURDISH TALIBAN");
        System.out.println("3. TEHREEK-E-TALIBAN");
        System.out.println("4. TEHRIK-E-TALIBAN-TARIQ GIDAR GROUP");
    }

    @Test
    @DisplayName("Row 35: Search 'OFFICE 39' - check for related entities")
    void searchOffice39() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "OFFICE 39")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andReturn();
        
        String response = result.getResponse().getContentAsString();
        
        System.out.println("\n=== OFFICE 39 Search Results ===");
        System.out.println(response);
        System.out.println("\n=== Aliases should include: ===");
        System.out.println("OFFICE #39 / OFFICE NO. 39 / BUREAU 39 / etc.");
    }
}
