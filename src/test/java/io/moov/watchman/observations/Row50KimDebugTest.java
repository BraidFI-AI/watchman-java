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
 * Debug test to understand KIM, Yo'ng-chu entity structure
 */
@SpringBootTest
@AutoConfigureMockMvc
public class Row50KimDebugTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Search 'KIM Yongchu' without punctuation - check if Yo'ng-chu matches")
    void searchNoPunctuation() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "KIM Yongchu")
                .param("minMatch", "0.80")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();
        
        String json = result.getResponse().getContentAsString();
        System.out.println("\n=== KIM Yongchu (no punctuation) ===");
        
        if (json.contains("\"name\":\"KIM, Yo'ng-chu\"")) {
            System.out.println("✅ Found: KIM, Yo'ng-chu");
            
            // Extract score
            int nameIndex = json.indexOf("\"name\":\"KIM, Yo'ng-chu\"");
            int scoreStart = json.lastIndexOf("\"score\":", nameIndex) + 8;
            int scoreEnd = json.indexOf(",", scoreStart);
            String score = json.substring(scoreStart, scoreEnd);
            System.out.println("Score: " + score);
        } else {
            System.out.println("⚠️ KIM, Yo'ng-chu not found with 'KIM Yongchu'");
        }
    }

    @Test
    @DisplayName("Search exact 'KIM, Yo'ng-chu' - get all details")
    void searchExactMatch() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "KIM, Yo'ng-chu")
                .param("minMatch", "0.70")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andReturn();
        
        String json = result.getResponse().getContentAsString();
        System.out.println("\n=== Exact: KIM, Yo'ng-chu ===");
        System.out.println(json);
    }
}
