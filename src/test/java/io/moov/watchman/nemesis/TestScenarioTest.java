package io.moov.watchman.nemesis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestScenarioTest {

    @Test
    void shouldCreateValidScenario() {
        // Given/When
        TestScenario scenario = TestScenario.builder()
            .id("test-001")
            .name("John Smith")
            .type("INDIVIDUAL")
            .build();

        // Then
        assertEquals("test-001", scenario.getId());
        assertEquals("John Smith", scenario.getName());
        assertEquals("INDIVIDUAL", scenario.getType());
    }

    @Test
    void shouldFailWithoutRequiredFields() {
        // When/Then: missing id
        assertThrows(IllegalArgumentException.class, 
            () -> TestScenario.builder().name("John Smith").type("INDIVIDUAL").build());

        // When/Then: missing name
        assertThrows(IllegalArgumentException.class,
            () -> TestScenario.builder().id("test-001").type("INDIVIDUAL").build());

        // When/Then: missing type
        assertThrows(IllegalArgumentException.class,
            () -> TestScenario.builder().id("test-001").name("John Smith").build());
    }

    @Test
    void shouldHandleOptionalFieldsAsEmptyLists() {
        // Given/When
        TestScenario scenario = TestScenario.builder()
            .id("test-001")
            .name("John Smith")
            .type("INDIVIDUAL")
            .build();

        // Then: optional collections should be empty, not null
        assertNotNull(scenario.getAltNames());
        assertTrue(scenario.getAltNames().isEmpty());
        assertNotNull(scenario.getAddresses());
        assertTrue(scenario.getAddresses().isEmpty());
    }

    @Test
    void shouldDefaultExpectedMatchToNull() {
        // Given/When
        TestScenario scenario = TestScenario.builder()
            .id("test-001")
            .name("John Smith")
            .type("INDIVIDUAL")
            .build();

        // Then: expectedMatch is optional, should be null if not specified
        assertNull(scenario.isExpectedMatch());
    }
}
