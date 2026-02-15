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
 * Verify which KIM entities exist in OFAC data
 */
@SpringBootTest
@AutoConfigureMockMvc
public class Row50KimEntityVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("List all KIM entities with 'Yo' in name")
    void listKimYoEntities() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "KIM Yo")
                .param("minMatch", "0.70")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andReturn();
        
        String json = result.getResponse().getContentAsString();
        System.out.println("\n=== KIM entities with 'Yo' name ===");
        
        // Extract and print all KIM entities
        int index = 0;
        while ((index = json.indexOf("\"name\":\"KIM,", index)) != -1) {
            int nameStart = json.indexOf("\"", index + 8) + 1;
            int nameEnd = json.indexOf("\"", nameStart);
            String name = json.substring(nameStart, nameEnd);
            
            if (name.contains("Yo")) {
                System.out.println("Found: " + name);
            }
            index = nameEnd;
        }
    }
}