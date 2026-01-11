package io.moov.watchman.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NemesisController.class)
@Import(ObjectMapper.class)
class NemesisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testTriggerWithDefaultParameters() throws Exception {
        mockMvc.perform(post("/v2/nemesis/trigger")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").exists())
                .andExpect(jsonPath("$.status").value("running"))
                .andExpect(jsonPath("$.statusUrl").exists());
    }

    @Test
    void testTriggerWithCustomParameters() throws Exception {
        String requestBody = """
            {
                "queries": 50,
                "includeOfacApi": false,
                "async": true
            }
            """;

        mockMvc.perform(post("/v2/nemesis/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").exists())
                .andExpect(jsonPath("$.status").value("running"));
    }

    @Test
    void testTriggerSynchronous() throws Exception {
        String requestBody = """
            {
                "queries": 10,
                "includeOfacApi": false,
                "async": false
            }
            """;

        // Note: This will actually try to execute Python script, might fail in test environment
        mockMvc.perform(post("/v2/nemesis/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").exists());
    }

    @Test
    void testStatusNotFound() throws Exception {
        mockMvc.perform(get("/v2/nemesis/status/nonexistent-job"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testListReports() throws Exception {
        mockMvc.perform(get("/v2/nemesis/reports"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void testInvalidQueryCount() throws Exception {
        String requestBody = """
            {
                "queries": 0,
                "includeOfacApi": false,
                "async": true
            }
            """;

        mockMvc.perform(post("/v2/nemesis/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testQueryCountTooHigh() throws Exception {
        String requestBody = """
            {
                "queries": 2000,
                "includeOfacApi": false,
                "async": true
            }
            """;

        mockMvc.perform(post("/v2/nemesis/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }
}
