package io.moov.watchman.config;

import io.moov.watchman.similarity.JaroWinklerSimilarity;
import io.moov.watchman.similarity.SimilarityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD RED PHASE - Test that proves SimilarityConfig is injected into SimilarityService.
 * 
 * This test WILL FAIL until we fix WatchmanConfig.similarityService() to inject config.
 * 
 * Expected behavior:
 * - SimilarityService bean should use the SimilarityConfig bean from Spring context
 * - Changing config values should affect scoring behavior
 * - Config should not use hardcoded defaults
 */
@SpringBootTest
@TestPropertySource(properties = {
    "watchman.download.enabled=false",
    "watchman.download.on-startup=false",
    "watchman.similarity.length-difference-penalty-weight=0.5"  // Override default 0.3
})
@DisplayName("WatchmanConfig Integration - SimilarityConfig Injection")
class WatchmanConfigIntegrationTest {

    @Autowired
    private SimilarityConfig config;

    @Autowired
    private SimilarityService similarityService;

    @Test
    @DisplayName("SimilarityService bean should exist")
    void similarityServiceBeanExists() {
        assertThat(similarityService)
            .as("SimilarityService should be autowired from Spring context")
            .isNotNull();
    }

    @Test
    @DisplayName("SimilarityService should be JaroWinklerSimilarity implementation")
    void similarityServiceIsJaroWinkler() {
        assertThat(similarityService)
            .as("SimilarityService should be implemented by JaroWinklerSimilarity")
            .isInstanceOf(JaroWinklerSimilarity.class);
    }

    @Test
    @DisplayName("SimilarityConfig bean should exist and be configured")
    void similarityConfigBeanExists() {
        assertThat(config)
            .as("SimilarityConfig should be autowired from Spring context")
            .isNotNull();
        
        // Verify it loaded our test property override
        assertThat(config.getLengthDifferencePenaltyWeight())
            .as("Config should load from test properties")
            .isEqualTo(0.5);
    }

    @Test
    @DisplayName("RED: JaroWinklerSimilarity should use injected SimilarityConfig (EXPECTED TO FAIL)")
    void jaroWinklerUsesInjectedConfig() {
        // GIVEN: A SimilarityConfig with overridden value (0.5 instead of default 0.3)
        assertThat(config.getLengthDifferencePenaltyWeight())
            .as("Precondition: Config should have overridden value")
            .isEqualTo(0.5);
        
        // WHEN: We get the JaroWinklerSimilarity instance
        JaroWinklerSimilarity jw = (JaroWinklerSimilarity) similarityService;
        
        // THEN: It should be using the Spring-managed config (not creating its own)
        // We can verify this by checking if it respects our config override
        
        // Test with strings that trigger length difference penalty
        // "John" vs "Jonathan" - 4 chars vs 8 chars, 50% length difference
        double score1 = jw.jaroWinkler("John", "Jonathan");
        
        // Now test with a fresh instance using default config (0.3 penalty)
        JaroWinklerSimilarity jwDefault = new JaroWinklerSimilarity();
        double score2 = jwDefault.jaroWinkler("John", "Jonathan");
        
        // The scores should be DIFFERENT if config is being used
        // Higher penalty weight (0.5) should result in LOWER score
        assertThat(score1)
            .as("Score with injected config (0.5 penalty) should differ from default config (0.3 penalty)")
            .isNotEqualTo(score2)
            .as("Injected config should produce lower score due to higher penalty")
            .isLessThan(score2);
        
        // THIS TEST WILL FAIL because WatchmanConfig creates JaroWinklerSimilarity 
        // with no-arg constructor, which creates its own config with default 0.3
    }

    @Test
    @DisplayName("RED: Config changes should affect scoring behavior (EXPECTED TO FAIL)")
    void configChangesShouldAffectScoring() {
        // GIVEN: Custom config with lengthDifferencePenaltyWeight = 0.5
        assertThat(config.getLengthDifferencePenaltyWeight()).isEqualTo(0.5);
        
        // WHEN: We score two strings with significant length difference
        double score = similarityService.jaroWinkler("Sam", "Samuel");
        
        // THEN: The score should reflect the custom penalty weight
        // With default 0.3: score would be higher
        // With custom 0.5: score should be lower
        
        // Create a baseline with known config
        SimilarityConfig defaultConfig = new SimilarityConfig();
        assertThat(defaultConfig.getLengthDifferencePenaltyWeight())
            .as("Default config should have 0.3 penalty")
            .isEqualTo(0.3);
        
        JaroWinklerSimilarity baseline = new JaroWinklerSimilarity(
            new io.moov.watchman.similarity.TextNormalizer(),
            new io.moov.watchman.similarity.PhoneticFilter(true),
            defaultConfig
        );
        double baselineScore = baseline.jaroWinkler("Sam", "Samuel");
        
        // Our Spring bean should have DIFFERENT score due to custom config
        assertThat(score)
            .as("Spring-managed bean should use injected config (0.5 penalty) not default (0.3)")
            .isNotEqualTo(baselineScore)
            .as("Higher penalty should result in lower score")
            .isLessThan(baselineScore);
        
        // THIS WILL FAIL - Spring bean uses same defaults as baseline
    }
}
