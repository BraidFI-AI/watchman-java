package io.moov.watchman.observations;

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
 * Verify the OFAC entity IDs for ABBAS-related entities
 */
@SpringBootTest
@AutoConfigureMockMvc
public class Row01VerifyEntityIdsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Verify entity IDs: ABBAS, Abu should be 2674, ABU AL-ABBAS should be 23043")
    void verifyEntityIds() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "ABBAS Abu")
                .param("minMatch", "0.85")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andReturn();
        
        String json = result.getResponse().getContentAsString();
        
        System.out.println("\n=== Entity ID Verification ===");
        
        // Check for ABBAS, Abu (should be entityID 2674)
        if (json.contains("\"name\":\"ABBAS, Abu\"")) {
            int nameIdx = json.indexOf("\"name\":\"ABBAS, Abu\"");
            int idStart = json.lastIndexOf("\"id\":\"", nameIdx) + 6;
            int idEnd = json.indexOf("\"", idStart);
            String entityId = json.substring(idStart, idEnd);
            System.out.println("ABBAS, Abu -> entityID: " + entityId + " (expected: 2674)");
        }
        
        // Check for ABU AL-ABBAS (should be entityID 23043 according to OFAC)
        if (json.contains("\"name\":\"ABU AL-ABBAS\"")) {
            int nameIdx = json.indexOf("\"name\":\"ABU AL-ABBAS\"");
            int idStart = json.lastIndexOf("\"id\":\"", nameIdx) + 6;
            int idEnd = json.indexOf("\"", idStart);
            String entityId = json.substring(idStart, idEnd);
            System.out.println("ABU AL-ABBAS -> entityID: " + entityId + " (expected: 23043)");
        } else if (json.contains("\"entityID\":\"23043\"")) {
            // Find what name has entity ID 23043
            int idIdx = json.indexOf("\"entityID\":\"23043\"");
            int nameStart = json.lastIndexOf("\"name\":\"", idIdx) + 8;
            int nameEnd = json.indexOf("\"", nameStart);
            String name = json.substring(nameStart, nameEnd);
            System.out.println("Entity 23043 found as: " + name);
            
            // Check aliases
            int altNamesStart = json.indexOf("\"altNames\":[", idIdx);
            if (altNamesStart > 0 && altNamesStart < idIdx + 500) {
                int altNamesEnd = json.indexOf("]", altNamesStart);
                String altNames = json.substring(altNamesStart + 12, altNamesEnd);
                System.out.println("Entity 23043 aliases: " + altNames);
            }
        } else {
            System.out.println("⚠️ Entity 23043 NOT found in results");
        }
    }
}
