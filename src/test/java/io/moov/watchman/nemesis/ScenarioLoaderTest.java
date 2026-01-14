package io.moov.watchman.nemesis;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioLoaderTest {

    @Test
    void shouldLoadScenariosFromYamlFile() {
        // Given: scenarios.yml exists in src/main/resources
        ScenarioLoader loader = new ScenarioLoader();

        // When: loading scenarios
        List<TestScenario> scenarios = loader.loadAll();

        // Then: scenarios are loaded
        assertNotNull(scenarios);
        assertFalse(scenarios.isEmpty(), "Should load at least one scenario");
    }

    @Test
    void shouldParseScenarioFields() {
        // Given
        ScenarioLoader loader = new ScenarioLoader();

        // When
        List<TestScenario> scenarios = loader.loadAll();
        TestScenario first = scenarios.get(0);

        // Then: all required fields are present
        assertNotNull(first.getId(), "Scenario must have ID");
        assertNotNull(first.getName(), "Scenario must have name");
        assertNotNull(first.getType(), "Scenario must have type");
    }

    @Test
    void shouldValidateRequiredFields() {
        // Given: scenario missing required field
        ScenarioLoader loader = new ScenarioLoader();

        // When/Then: validation should fail
        assertThrows(IllegalArgumentException.class, 
            () -> loader.loadFromString("scenarios:\n  - id: test-1\n    # missing name field"));
    }

    @Test
    void shouldDetectDuplicateIds() {
        // Given: scenarios with duplicate IDs
        String yaml = """
            scenarios:
              - id: duplicate-id
                name: Test One
                type: INDIVIDUAL
              - id: duplicate-id
                name: Test Two
                type: INDIVIDUAL
            """;

        ScenarioLoader loader = new ScenarioLoader();

        // When/Then: should fail on duplicate
        assertThrows(IllegalArgumentException.class, () -> loader.loadFromString(yaml));
    }

    @Test
    void shouldLoadScenarioById() {
        // Given
        ScenarioLoader loader = new ScenarioLoader();
        loader.loadAll();

        // When
        TestScenario scenario = loader.getById("putin-001");

        // Then
        assertNotNull(scenario);
        assertEquals("putin-001", scenario.getId());
    }

    @Test
    void shouldReturnNullForNonExistentId() {
        // Given
        ScenarioLoader loader = new ScenarioLoader();
        loader.loadAll();

        // When
        TestScenario scenario = loader.getById("does-not-exist");

        // Then
        assertNull(scenario);
    }

    @Test
    void shouldParseOptionalFields() {
        // Given
        String yaml = """
            scenarios:
              - id: full-scenario
                name: Vladimir Putin
                altNames: 
                  - Vladimir Vladimirovich Putin
                  - Putin
                addresses:
                  - Moscow, Russia
                  - Kremlin
                type: INDIVIDUAL
                category: high-risk-exact
                expectedMatch: true
                notes: Russian President - should always match
            """;

        ScenarioLoader loader = new ScenarioLoader();

        // When
        List<TestScenario> scenarios = loader.loadFromString(yaml);
        TestScenario scenario = scenarios.get(0);

        // Then
        assertEquals(2, scenario.getAltNames().size());
        assertEquals(2, scenario.getAddresses().size());
        assertEquals("high-risk-exact", scenario.getCategory());
        assertTrue(scenario.isExpectedMatch());
        assertNotNull(scenario.getNotes());
    }
}
