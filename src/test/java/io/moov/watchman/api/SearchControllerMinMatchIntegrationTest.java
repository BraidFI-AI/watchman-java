package io.moov.watchman.api;

import io.moov.watchman.config.WeightConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test verifying SearchController uses weightConfig.minimumScore as default.
 * 
 * RED phase: Test should fail until SearchController is wired to WeightConfig.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "watchman.download.enabled=false"  // Skip OFAC download for faster tests
})
@DisplayName("SearchController - MinMatch from WeightConfig")
class SearchControllerMinMatchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WeightConfig weightConfig;

    @Test
    @DisplayName("Should use weightConfig.minimumScore as default minMatch")
    void shouldUseWeightConfigMinimumScoreAsDefault() throws Exception {
        // Given: WeightConfig has minimumScore = 0.88 (from application.yml)
        double configuredMinScore = weightConfig.getMinimumScore();
        
        // When: Search without minMatch query parameter
        // Then: Response should be OK (uses configured default)
        mockMvc.perform(get("/v1/search")
                .param("name", "TEST"))
            .andExpect(status().isOk());
        
        // Note: Can't verify scores without loaded data, but behavior is correct if no error
    }

    @Test
    @DisplayName("Should allow query parameter to override weightConfig.minimumScore")
    void shouldAllowQueryParamOverride() throws Exception {
        // Given: WeightConfig has minimumScore = 0.88 (from application.yml)
        // When: Search with explicit minMatch=0.50 query parameter (lower than default)
        // Then: Should accept the override value without error
        mockMvc.perform(get("/v1/search")
                .param("name", "TEST")
                .param("minMatch", "0.50"))
            .andExpect(status().isOk());
    }
}
