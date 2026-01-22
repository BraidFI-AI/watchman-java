package io.moov.watchman.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public TestErrorController testErrorController() {
            return new TestErrorController();
        }
    }

    // Test controller for simulating various exception scenarios
    @RestController
    static class TestErrorController {
        
        @GetMapping("/test/echo")
        public String echo(@RequestParam String q) {
            return "echo: " + q;
        }
        
        @GetMapping("/test/error")
        public String error() {
            throw new RuntimeException("Simulated error");
        }
        
        @GetMapping("/test/not-found")
        public String notFound() {
            throw new EntityNotFoundException("Test entity", "123");
        }
        
        @GetMapping("/test/bad-request")
        public String badRequest() {
            throw new IllegalArgumentException("Invalid parameter value");
        }
        
        @GetMapping("/test/unavailable")
        public String unavailable() {
            throw new ServiceUnavailableException("Database offline");
        }
    }

    @Test
    void shouldReturnRequestIdHeader() throws Exception {
        mockMvc.perform(get("/test/echo")
                .param("q", "test")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"));
    }

    @Test
    void shouldUseProvidedRequestId() throws Exception {
        String customRequestId = "my-request-123";
        
        mockMvc.perform(get("/test/echo")
                .param("q", "test")
                .header("X-Request-ID", customRequestId)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", customRequestId));
    }

    @Test
    void shouldReturnErrorResponseForMissingParameter() throws Exception {
        mockMvc.perform(get("/test/echo")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("q")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/test/echo"))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturnMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/test/echo")
                .param("q", "test")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void shouldHandleInternalServerError() throws Exception {
        mockMvc.perform(get("/test/error")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.status").value(500));
    }
    
    @Test
    void shouldHandleEntityNotFound() throws Exception {
        mockMvc.perform(get("/test/not-found")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }
    
    @Test
    void shouldHandleIllegalArgument() throws Exception {
        mockMvc.perform(get("/test/bad-request")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid parameter value"))
                .andExpect(jsonPath("$.status").value(400));
    }
    
    @Test
    void shouldHandleServiceUnavailable() throws Exception {
        mockMvc.perform(get("/test/unavailable")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.status").value(503));
    }
}
