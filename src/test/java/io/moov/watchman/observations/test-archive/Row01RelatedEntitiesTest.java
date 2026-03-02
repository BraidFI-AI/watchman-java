package io.moov.watchman.observations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Investigation: Row 1 Related Entities Issue
 * 
 * <p><strong>Issue</strong>: "Still, some OFAC listed entities are not returned by Watchman. 
 * (Eg; ABU AL-ABBAS, KATA'IB ABU FADL AL-ABBAS, PLF-ABU ABBAS, etc.)"
 * 
 * <p><strong>Analysis</strong>: The query "ABBAS, Abu" correctly returns the individual, but the
 * retest comment claims related entities like "ABU AL-ABBAS", "PLF-ABU ABBAS" are not returned.
 * Need to investigate: are these different entries in OFAC data, and if so, what entity types are they?
 * 
 * <p><strong>Expected Behavior</strong>: If these are distinct OFAC entities, they should appear in
 * search results when query matches them above the threshold.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Row 1: ABBAS, Abu - Related Entities Investigation")
public class Row01RelatedEntitiesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Search 'Abu ABBAS' - verify which entities are returned")
    void searchAbuAbbas() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "Abu ABBAS")
                .param("minMatch", "0.70")
                .param("limit", "50"))
            .andDo(print())
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Search 'ABU AL-ABBAS' - verify if this entity exists")
    void searchAbuAlAbbas() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "ABU AL-ABBAS")
                .param("minMatch", "0.70")
                .param("limit", "50"))
            .andDo(print())
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Search 'PLF-ABU ABBAS' - verify if this entity exists")
    void searchPlfAbuAbbas() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "PLF-ABU ABBAS")
                .param("minMatch", "0.70")
                .param("limit", "50"))
            .andDo(print())
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Search 'KATA''IB ABU FADL AL-ABBAS' - verify if this entity exists")
    void searchKataibAbuFadlAlAbbas() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "KATA'IB ABU FADL AL-ABBAS")
                .param("minMatch", "0.70")
                .param("limit", "50"))
            .andDo(print())
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Search 'ABBAS' - return all ABBAS-related entities (low threshold)")
    void searchAbbasLowThreshold() throws Exception {
        mockMvc.perform(get("/v1/search")
                .param("name", "ABBAS")
                .param("minMatch", "0.50")
                .param("limit", "100"))
            .andDo(print())
            .andExpect(status().isOk());
    }
}
