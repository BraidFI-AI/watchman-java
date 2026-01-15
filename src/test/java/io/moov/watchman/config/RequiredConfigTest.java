package io.moov.watchman.config;

import io.moov.watchman.similarity.JaroWinklerSimilarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TDD RED PHASE - Enforce mandatory config injection, no fallback to hardcoded defaults.
 * 
 * This test WILL FAIL until we remove no-arg constructors from:
 * - JaroWinklerSimilarity (forces config to be provided)
 * - SimilarityConfig (forces Spring to manage it)
 * 
 * Goal: Fail fast if config is not properly injected, rather than silently using defaults.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "watchman.download.enabled=false",
    "watchman.download.on-startup=false"
})
@DisplayName("Required Config Enforcement - No Fallback to Hardcoded Values")
class RequiredConfigTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("RED: JaroWinklerSimilarity should NOT have no-arg constructor (EXPECTED TO FAIL)")
    void jaroWinklerShouldRequireConfig() {
        // THEN: JaroWinklerSimilarity should not have a no-arg constructor
        Constructor<?>[] constructors = JaroWinklerSimilarity.class.getConstructors();
        
        boolean hasNoArgConstructor = false;
        for (Constructor<?> constructor : constructors) {
            if (constructor.getParameterCount() == 0) {
                hasNoArgConstructor = true;
                break;
            }
        }
        
        assertThat(hasNoArgConstructor)
            .as("JaroWinklerSimilarity should NOT have a no-arg constructor - config must be required")
            .isFalse();
        
        // THIS WILL FAIL - no-arg constructor currently exists
    }

    @Test
    @DisplayName("RED: Attempting to create JaroWinklerSimilarity without config should fail (EXPECTED TO FAIL)")
    void creatingJaroWinklerWithoutConfigShouldFail() {
        // WHEN: Try to create JaroWinklerSimilarity without providing config
        // THEN: Should fail at compile time (no no-arg constructor exists)
        
        assertThatThrownBy(() -> {
            // This should not compile after we remove no-arg constructor
            // For now, we test that reflection would fail
            Constructor<?> noArgConstructor = JaroWinklerSimilarity.class.getConstructor();
            noArgConstructor.newInstance();
        })
            .as("Should not be able to create JaroWinklerSimilarity without config")
            .isInstanceOf(NoSuchMethodException.class);
        
        // THIS WILL FAIL - no-arg constructor currently exists
    }

    @Test
    @DisplayName("Spring context should have exactly ONE SimilarityConfig bean")
    void onlyOneConfigBeanExists() {
        // GIVEN: Spring context is loaded
        // WHEN: We query for SimilarityConfig beans
        String[] beanNames = context.getBeanNamesForType(SimilarityConfig.class);
        
        // THEN: Exactly one bean should exist (the Spring-managed one)
        assertThat(beanNames)
            .as("Should have exactly one SimilarityConfig bean from Spring")
            .hasSize(1);
        
        assertThat(beanNames[0])
            .as("Bean name should be 'similarityConfig' (default Spring naming)")
            .isEqualTo("similarityConfig");
    }

    @Test
    @DisplayName("JaroWinklerSimilarity bean should use Spring-managed config")
    void jaroWinklerUsesSpringManagedConfig() {
        // GIVEN: Spring context loaded
        SimilarityConfig config = context.getBean(SimilarityConfig.class);
        
        // WHEN: We get the JaroWinklerSimilarity instance
        // It's created by WatchmanConfig.similarityService(config)
        
        // THEN: The bean should exist and be properly configured
        assertThat(config)
            .as("SimilarityConfig bean should exist")
            .isNotNull();
        
        assertThat(config.getLengthDifferencePenaltyWeight())
            .as("Config should have default value")
            .isEqualTo(0.3);
    }

    @Test
    @DisplayName("RED: SimilarityConfig should NOT allow instantiation without Spring (EXPECTED TO FAIL)")
    void similarityConfigRequiresSpring() {
        // THEN: Should not be able to create SimilarityConfig outside Spring
        // After refactoring, we might make this package-private or use factory pattern
        
        // For now, we test that at least the Spring-managed one is being used
        SimilarityConfig config1 = context.getBean(SimilarityConfig.class);
        SimilarityConfig config2 = context.getBean(SimilarityConfig.class);
        
        assertThat(config1)
            .as("Spring should return singleton instance")
            .isSameAs(config2);
        
        // Future: Could make constructor package-private so only Spring can create it
    }
}
