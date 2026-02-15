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
 * Row 50: KIM, Yong Ju - Name Order and Spacing Variations
 * 
 * <p><strong>Issue</strong>: Name order variations (Yong Ju KIM, Yong KIM) and spacing/punctuation 
 * variations (Yong chu KIM, Yong-chu KIM, Yo'ng chu KIM) not consistently detected
 * 
 * <p><strong>Expected</strong>: Token-based matching should handle:
 * <ul>
 *   <li>Name order: "KIM, Yong Ju" ↔ "Yong Ju KIM"</li>
 *   <li>Spacing: "Yong chu" ↔ "Yong-chu" ↔ "Yo'ng chu"</li>
 *   <li>Punctuation normalization via tokenization</li>
 * </ul>
 * 
 * <p><strong>Test Strategy</strong>:
 * <ol>
 *   <li>Search OFAC format "KIM, Yong Ju" - baseline (should return entity)</li>
 *   <li>Search "Yong Ju KIM" - name order variation (FN-MN-LN)</li>
 *   <li>Search "Yong KIM" - middle name omitted</li>
 *   <li>Search "Yong chu KIM" - spacing variation (if different middle name)</li>
 *   <li>Search "Yong-chu KIM" - hyphenated variation</li>
 *   <li>Search "Yo'ng chu KIM" - apostrophe variation</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
public class Row50KimNameVariationsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Search 'KIM, Yong Ju' - OFAC format baseline")
    void searchOfacFormat() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "KIM, Yong Ju")
                .param("minMatch", "0.85")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();
        
        String json = result.getResponse().getContentAsString();
        System.out.println("\n=== KIM, Yong Ju (OFAC format) ===");
        
        // Look for entity ID in results
        if (json.contains("\"name\":\"KIM, Yong Ju\"")) {
            // Extract entity ID
            int nameIndex = json.indexOf("\"name\":\"KIM, Yong Ju\"");
            int idStart = json.lastIndexOf("\"id\":\"", nameIndex) + 6;
            int idEnd = json.indexOf("\"", idStart);
            String entityId = json.substring(idStart, idEnd);
            System.out.println("✅ Found: KIM, Yong Ju (entityID: " + entityId + ")");
        } else {
            System.out.println("⚠️ KIM, Yong Ju not found");
        }
    }

    @Test
    @DisplayName("Search 'Yong Ju KIM' - name order variation (FN-MN-LN)")
    void searchNameOrderVariation() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "Yong Ju KIM")
                .param("minMatch", "0.85")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();
        
        String json = result.getResponse().getContentAsString();
        System.out.println("\n=== Yong Ju KIM (FN-MN-LN order) ===");
        
        if (json.contains("\"name\":\"KIM, Yong Ju\"")) {
            System.out.println("✅ Found: KIM, Yong Ju via name order variation");
        } else {
            System.out.println("❌ KIM, Yong Ju NOT found - name order variation failed");
        }
    }

    @Test
    @DisplayName("Search 'Yong KIM' - middle name omitted")
    void searchMiddleNameOmitted() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "Yong KIM")
                .param("minMatch", "0.80")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();
        
        String json = result.getResponse().getContentAsString();
        System.out.println("\n=== Yong KIM (middle name omitted) ===");
        
        if (json.contains("\"name\":\"KIM, Yong Ju\"") || json.contains("\"name\":\"KIM, Yong")) {
            System.out.println("✅ Found KIM, Yong* match");
        } else {
            System.out.println("⚠️ No KIM, Yong* matches found");
        }
    }

    @Test
    @DisplayName("Search 'Yong chu KIM' - spacing variation")
    void searchSpacingVariation() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "Yong chu KIM")
                .param("minMatch", "0.85")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();
        
        String json = result.getResponse().getContentAsString();
        System.out.println("\n=== Yong chu KIM (spacing variation) ===");
        
        // Check if KIM, Yo'ng-chu is returned
        if (json.contains("\"name\":\"KIM, Yo'ng-chu\"")) {
            System.out.println("✅ Found: KIM, Yo'ng-chu");
        } else {
            System.out.println("⚠️ KIM, Yo'ng-chu not found");
        }
        
        // Check for any other KIM, Yong* matches
        if (json.contains("\"name\":\"KIM, Yong")) {
            System.out.println("✅ Found other KIM, Yong* matches");
        }
    }

    @Test
    @DisplayName("Search 'Yong-chu KIM' - hyphenated variation")
    void searchHyphenatedVariation() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "Yong-chu KIM")
                .param("minMatch", "0.85")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();
        
        String json = result.getResponse().getContentAsString();
        System.out.println("\n=== Yong-chu KIM (hyphenated) ===");
        
        if (json.contains("\"name\":\"KIM, Yo'ng-chu\"")) {
            System.out.println("✅ Found: KIM, Yo'ng-chu");
        } else {
            System.out.println("⚠️ KIM, Yo'ng-chu not found");
        }
    }

    @Test
    @DisplayName("Search \"Yo'ng chu KIM\" - apostrophe + spacing variation")
    void searchApostropheVariation() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/search")
                .param("name", "Yo'ng chu KIM")
                .param("minMatch", "0.85")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();
        
        String json = result.getResponse().getContentAsString();
        System.out.println("\n=== Yo'ng chu KIM (apostrophe + space) ===");
        
        if (json.contains("\"name\":\"KIM, Yo'ng-chu\"")) {
            System.out.println("✅ Found: KIM, Yo'ng-chu");
        } else {
            System.out.println("⚠️ KIM, Yo'ng-chu not found");
        }
    }
}
