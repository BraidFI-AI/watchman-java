package io.moov.watchman.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD tests for ConfigOverride DTOs.
 *
 * These tests are written FIRST (RED phase) before implementation.
 * They define the expected behavior of config override deserialization.
 */
class ConfigOverrideTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldDeserializeMinimalConfigOverride() throws Exception {
        String json = """
            {
              "query": {
                "name": "Juan Garcia"
              }
            }
            """;

        SearchRequestBody request = mapper.readValue(json, SearchRequestBody.class);

        assertThat(request.query()).isNotNull();
        assertThat(request.query().name()).isEqualTo("Juan Garcia");
        assertThat(request.config()).isNull();
        assertThat(request.trace()).isNull();
    }

    @Test
    void shouldDeserializeSimilarityConfigOverride() throws Exception {
        String json = """
            {
              "query": {
                "name": "Juan Garcia"
              },
              "config": {
                "similarity": {
                  "jaroWinklerBoostThreshold": 0.8,
                  "jaroWinklerPrefixSize": 5,
                  "lengthDifferencePenaltyWeight": 0.25
                }
              },
              "trace": true
            }
            """;

        SearchRequestBody request = mapper.readValue(json, SearchRequestBody.class);

        assertThat(request.query().name()).isEqualTo("Juan Garcia");
        assertThat(request.config()).isNotNull();
        assertThat(request.config().similarity()).isNotNull();
        assertThat(request.config().similarity().jaroWinklerBoostThreshold()).isEqualTo(0.8);
        assertThat(request.config().similarity().jaroWinklerPrefixSize()).isEqualTo(5);
        assertThat(request.config().similarity().lengthDifferencePenaltyWeight()).isEqualTo(0.25);
        assertThat(request.trace()).isTrue();
    }

    @Test
    void shouldDeserializeScoringConfigOverride() throws Exception {
        String json = """
            {
              "query": {
                "name": "Juan Garcia"
              },
              "config": {
                "scoring": {
                  "nameWeight": 50.0,
                  "addressEnabled": false,
                  "criticalIdWeight": 60.0
                }
              }
            }
            """;

        SearchRequestBody request = mapper.readValue(json, SearchRequestBody.class);

        assertThat(request.config()).isNotNull();
        assertThat(request.config().scoring()).isNotNull();
        assertThat(request.config().scoring().nameWeight()).isEqualTo(50.0);
        assertThat(request.config().scoring().addressEnabled()).isFalse();
        assertThat(request.config().scoring().criticalIdWeight()).isEqualTo(60.0);
    }

    @Test
    void shouldDeserializeSearchConfigOverride() throws Exception {
        String json = """
            {
              "query": {
                "name": "Juan Garcia"
              },
              "config": {
                "search": {
                  "minMatch": 0.75,
                  "limit": 20
                }
              }
            }
            """;

        SearchRequestBody request = mapper.readValue(json, SearchRequestBody.class);

        assertThat(request.config()).isNotNull();
        assertThat(request.config().search()).isNotNull();
        assertThat(request.config().search().minMatch()).isEqualTo(0.75);
        assertThat(request.config().search().limit()).isEqualTo(20);
    }

    @Test
    void shouldDeserializeAllConfigOverrides() throws Exception {
        String json = """
            {
              "query": {
                "name": "Juan Garcia",
                "addresses": ["123 Main St"]
              },
              "config": {
                "similarity": {
                  "jaroWinklerBoostThreshold": 0.8,
                  "phoneticFilteringDisabled": true
                },
                "scoring": {
                  "nameWeight": 50.0,
                  "addressEnabled": false
                },
                "search": {
                  "minMatch": 0.85,
                  "limit": 15
                }
              },
              "trace": true
            }
            """;

        SearchRequestBody request = mapper.readValue(json, SearchRequestBody.class);

        assertThat(request.query().name()).isEqualTo("Juan Garcia");
        assertThat(request.query().addresses()).containsExactly("123 Main St");
        assertThat(request.config().similarity().jaroWinklerBoostThreshold()).isEqualTo(0.8);
        assertThat(request.config().similarity().phoneticFilteringDisabled()).isTrue();
        assertThat(request.config().scoring().nameWeight()).isEqualTo(50.0);
        assertThat(request.config().scoring().addressEnabled()).isFalse();
        assertThat(request.config().search().minMatch()).isEqualTo(0.85);
        assertThat(request.config().search().limit()).isEqualTo(15);
        assertThat(request.trace()).isTrue();
    }

    @Test
    void shouldHandlePartialSimilarityOverride() throws Exception {
        String json = """
            {
              "query": {
                "name": "Test"
              },
              "config": {
                "similarity": {
                  "jaroWinklerBoostThreshold": 0.6
                }
              }
            }
            """;

        SearchRequestBody request = mapper.readValue(json, SearchRequestBody.class);

        assertThat(request.config().similarity().jaroWinklerBoostThreshold()).isEqualTo(0.6);
        // Other fields should be null (will use defaults when resolved)
        assertThat(request.config().similarity().jaroWinklerPrefixSize()).isNull();
        assertThat(request.config().similarity().lengthDifferencePenaltyWeight()).isNull();
    }

    @Test
    void shouldHandleAllSimilarityFields() throws Exception {
        String json = """
            {
              "query": {
                "name": "Test"
              },
              "config": {
                "similarity": {
                  "jaroWinklerBoostThreshold": 0.8,
                  "jaroWinklerPrefixSize": 5,
                  "lengthDifferenceCutoffFactor": 0.85,
                  "lengthDifferencePenaltyWeight": 0.25,
                  "differentLetterPenaltyWeight": 0.95,
                  "unmatchedIndexTokenWeight": 0.10,
                  "exactMatchFavoritism": 0.05,
                  "phoneticFilteringDisabled": true,
                  "keepStopwords": true,
                  "logStopwordDebugging": false
                }
              }
            }
            """;

        SearchRequestBody request = mapper.readValue(json, SearchRequestBody.class);
        SimilarityConfigOverride sim = request.config().similarity();

        assertThat(sim.jaroWinklerBoostThreshold()).isEqualTo(0.8);
        assertThat(sim.jaroWinklerPrefixSize()).isEqualTo(5);
        assertThat(sim.lengthDifferenceCutoffFactor()).isEqualTo(0.85);
        assertThat(sim.lengthDifferencePenaltyWeight()).isEqualTo(0.25);
        assertThat(sim.differentLetterPenaltyWeight()).isEqualTo(0.95);
        assertThat(sim.unmatchedIndexTokenWeight()).isEqualTo(0.10);
        assertThat(sim.exactMatchFavoritism()).isEqualTo(0.05);
        assertThat(sim.phoneticFilteringDisabled()).isTrue();
        assertThat(sim.keepStopwords()).isTrue();
        assertThat(sim.logStopwordDebugging()).isFalse();
    }

    @Test
    void shouldHandleAllScoringFields() throws Exception {
        String json = """
            {
              "query": {
                "name": "Test"
              },
              "config": {
                "scoring": {
                  "nameWeight": 40.0,
                  "addressWeight": 30.0,
                  "criticalIdWeight": 55.0,
                  "supportingInfoWeight": 20.0,
                  "nameEnabled": true,
                  "altNamesEnabled": false,
                  "governmentIdEnabled": true,
                  "cryptoEnabled": true,
                  "contactEnabled": false,
                  "addressEnabled": true,
                  "dateEnabled": false
                }
              }
            }
            """;

        SearchRequestBody request = mapper.readValue(json, SearchRequestBody.class);
        ScoringConfigOverride scoring = request.config().scoring();

        assertThat(scoring.nameWeight()).isEqualTo(40.0);
        assertThat(scoring.addressWeight()).isEqualTo(30.0);
        assertThat(scoring.criticalIdWeight()).isEqualTo(55.0);
        assertThat(scoring.supportingInfoWeight()).isEqualTo(20.0);
        assertThat(scoring.nameEnabled()).isTrue();
        assertThat(scoring.altNamesEnabled()).isFalse();
        assertThat(scoring.governmentIdEnabled()).isTrue();
        assertThat(scoring.cryptoEnabled()).isTrue();
        assertThat(scoring.contactEnabled()).isFalse();
        assertThat(scoring.addressEnabled()).isTrue();
        assertThat(scoring.dateEnabled()).isFalse();
    }
}
