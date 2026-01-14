package io.moov.watchman.nemesis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads test scenarios from YAML file for Nemesis testing.
 * Scenarios are static, reproducible test cases used to validate
 * Java Watchman against Braid/Go/OFAC-API.
 */
public class ScenarioLoader {
    private static final Logger log = LoggerFactory.getLogger(ScenarioLoader.class);
    private static final String DEFAULT_SCENARIOS_FILE = "scenarios.yml";

    private final Map<String, TestScenario> scenarioCache = new HashMap<>();

    /**
     * Load all scenarios from default scenarios.yml file.
     */
    public List<TestScenario> loadAll() {
        return loadFromFile(DEFAULT_SCENARIOS_FILE);
    }

    /**
     * Load scenarios from a specific file.
     */
    public List<TestScenario> loadFromFile(String filename) {
        try {
            ClassPathResource resource = new ClassPathResource(filename);
            try (InputStream is = resource.getInputStream()) {
                return parseYaml(is);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load scenarios from " + filename, e);
        }
    }

    /**
     * Load scenarios from YAML string (used for testing).
     */
    public List<TestScenario> loadFromString(String yaml) {
        Yaml yamlParser = new Yaml();
        try {
            Map<String, Object> data = yamlParser.load(new StringReader(yaml));
            return parseScenarios(data);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid YAML format", e);
        }
    }

    /**
     * Get a scenario by ID (must call loadAll() first).
     */
    public TestScenario getById(String id) {
        return scenarioCache.get(id);
    }

    private List<TestScenario> parseYaml(InputStream is) {
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(is);
        return parseScenarios(data);
    }

    @SuppressWarnings("unchecked")
    private List<TestScenario> parseScenarios(Map<String, Object> data) {
        if (data == null || !data.containsKey("scenarios")) {
            throw new IllegalArgumentException("YAML must contain 'scenarios' key");
        }

        List<Map<String, Object>> scenarioList = (List<Map<String, Object>>) data.get("scenarios");
        if (scenarioList == null || scenarioList.isEmpty()) {
            return Collections.emptyList();
        }

        List<TestScenario> scenarios = scenarioList.stream()
                .map(this::parseScenario)
                .collect(Collectors.toList());

        // Validate no duplicate IDs
        Set<String> ids = new HashSet<>();
        for (TestScenario scenario : scenarios) {
            if (!ids.add(scenario.getId())) {
                throw new IllegalArgumentException("Duplicate scenario ID: " + scenario.getId());
            }
            scenarioCache.put(scenario.getId(), scenario);
        }

        log.info("Loaded {} scenarios", scenarios.size());
        return scenarios;
    }

    @SuppressWarnings("unchecked")
    private TestScenario parseScenario(Map<String, Object> map) {
        TestScenario.Builder builder = TestScenario.builder();

        // Required fields
        String id = (String) map.get("id");
        String name = (String) map.get("name");
        String type = (String) map.get("type");

        if (id == null || name == null || type == null) {
            throw new IllegalArgumentException("Scenario must have id, name, and type fields");
        }

        builder.id(id).name(name).type(type);

        // Optional fields
        if (map.containsKey("altNames")) {
            builder.altNames((List<String>) map.get("altNames"));
        }

        if (map.containsKey("addresses")) {
            builder.addresses((List<String>) map.get("addresses"));
        }

        if (map.containsKey("category")) {
            builder.category((String) map.get("category"));
        }

        if (map.containsKey("expectedMatch")) {
            builder.expectedMatch((Boolean) map.get("expectedMatch"));
        }

        if (map.containsKey("notes")) {
            builder.notes((String) map.get("notes"));
        }

        return builder.build();
    }
}
