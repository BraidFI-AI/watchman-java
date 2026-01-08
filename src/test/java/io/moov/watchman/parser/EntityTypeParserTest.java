package io.moov.watchman.parser;

import io.moov.watchman.model.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for parsing entity type from OFAC SDN data.
 * 
 * OFAC uses specific strings in the SDN_Type field:
 * - "individual" → PERSON
 * - "Entity" → BUSINESS
 * - "vessel" → VESSEL
 * - "aircraft" → AIRCRAFT
 * - blank/null/unrecognized → UNKNOWN
 */
class EntityTypeParserTest {

    private EntityTypeParser parser;

    @BeforeEach
    void setUp() {
        parser = new EntityTypeParser();
    }

    @Nested
    @DisplayName("SDN Type Parsing")
    class SdnTypeParsingTests {

        @ParameterizedTest(name = "'{0}' → {1}")
        @CsvSource({
            "'individual', PERSON",
            "'Individual', PERSON",
            "'INDIVIDUAL', PERSON",
            
            "'Entity', BUSINESS",
            "'entity', BUSINESS",
            "'ENTITY', BUSINESS",
            
            "'vessel', VESSEL",
            "'Vessel', VESSEL",
            "'VESSEL', VESSEL",
            
            "'aircraft', AIRCRAFT",
            "'Aircraft', AIRCRAFT",
            "'AIRCRAFT', AIRCRAFT",
            
            "'', UNKNOWN",
            "'   ', UNKNOWN"
        })
        void shouldParseEntityType(String sdnType, EntityType expected) {
            EntityType result = parser.parse(sdnType);
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Null should return UNKNOWN")
        void nullShouldReturnUnknown() {
            assertThat(parser.parse(null)).isEqualTo(EntityType.UNKNOWN);
        }

        @Test
        @DisplayName("Unrecognized type should return UNKNOWN")
        void unrecognizedShouldReturnUnknown() {
            assertThat(parser.parse("something_else")).isEqualTo(EntityType.UNKNOWN);
            assertThat(parser.parse("organization")).isEqualTo(EntityType.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("Person Detection")
    class PersonDetectionTests {

        @Test
        @DisplayName("Should detect person type")
        void shouldDetectPerson() {
            assertThat(parser.isPerson("individual")).isTrue();
            assertThat(parser.isPerson("Individual")).isTrue();
        }

        @Test
        @DisplayName("Non-person types should return false")
        void nonPersonShouldReturnFalse() {
            assertThat(parser.isPerson("")).isFalse();
            assertThat(parser.isPerson("Entity")).isFalse();
            assertThat(parser.isPerson("vessel")).isFalse();
        }
    }

    @Nested
    @DisplayName("Business/Entity Detection")
    class BusinessDetectionTests {

        @Test
        @DisplayName("Blank type should be unknown")
        void blankShouldBeUnknown() {
            assertThat(parser.isBusiness("")).isFalse();
            assertThat(parser.isBusiness("   ")).isFalse();
        }

        @Test
        @DisplayName("Entity type should be business")
        void entityShouldBeBusiness() {
            assertThat(parser.isBusiness("Entity")).isTrue();
            assertThat(parser.isBusiness("entity")).isTrue();
        }
    }
}