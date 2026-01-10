package io.moov.watchman.similarity;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.PreparedFields;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 15: Name Scoring & Final Score Calculation
 * 
 * Tests for:
 * - calculateNameScore() - Centralized name scoring with primary/alt blending
 * - isNameCloseEnough() - Performance optimization pre-filter
 * - calculateFinalScore() - Weighted component aggregation
 * - compareNameTerms() verification - Already implemented as bestPairJaro()
 * 
 * Goal: Upgrade 3 partial implementations (⚠️ → ✅) + add 1 new function
 * Expected: 12 comprehensive tests
 */
@DisplayName("Phase 15: Name Scoring & Final Score Calculation")
public class Phase15NameScoringTest {

    // ==================== Helper Methods ====================
    
    private Entity createEntityWithPrimaryName(String name) {
        PreparedFields prepared = new PreparedFields(
            name,              // normalizedPrimaryName
            List.of(),         // normalizedAltNames
            List.of(),         // nameCombinations
            List.of(),         // altNameCombinations
            null,              // language
            false              // stopwordsRemoved
        );
        
        return new Entity(
            name,              // name
            List.of(),         // altNames
            null,              // entityType
            null,              // source
            null,              // sourceId
            prepared,          // preparedFields
            null,              // remarks
            null,              // person
            null,              // business
            null,              // organization
            null,              // aircraft
            null,              // vessel
            List.of(),         // addresses
            null,              // contactInfo
            List.of(),         // governmentIds
            List.of(),         // cryptoAddresses
            null,              // sanctionsInfo
            List.of()          // historicalInfo
        );
    }
    
    private Entity createEntityWithAltNames(List<String> altNames) {
        PreparedFields prepared = new PreparedFields(
            "",                // normalizedPrimaryName (empty)
            altNames,          // normalizedAltNames
            List.of(),         // nameCombinations
            List.of(),         // altNameCombinations
            null,              // language
            false              // stopwordsRemoved
        );
        
        return new Entity(
            "",                // name (empty)
            altNames,          // altNames
            null,              // entityType
            null,              // source
            null,              // sourceId
            prepared,          // preparedFields
            null,              // remarks
            null,              // person
            null,              // business
            null,              // organization
            null,              // aircraft
            null,              // vessel
            List.of(),         // addresses
            null,              // contactInfo
            List.of(),         // governmentIds
            List.of(),         // cryptoAddresses
            null,              // sanctionsInfo
            List.of()          // historicalInfo
        );
    }
    
    private Entity createEntityWithBothNames(String primaryName, List<String> altNames) {
        PreparedFields prepared = new PreparedFields(
            primaryName,       // normalizedPrimaryName
            altNames,          // normalizedAltNames
            List.of(),         // nameCombinations
            List.of(),         // altNameCombinations
            null,              // language
            false              // stopwordsRemoved
        );
        
        return new Entity(
            primaryName,       // name
            altNames,          // altNames
            null,              // entityType
            null,              // source
            null,              // sourceId
            prepared,          // preparedFields
            null,              // remarks
            null,              // person
            null,              // business
            null,              // organization
            null,              // aircraft
            null,              // vessel
            List.of(),         // addresses
            null,              // contactInfo
            List.of(),         // governmentIds
            List.of(),         // cryptoAddresses
            null,              // sanctionsInfo
            List.of()          // historicalInfo
        );
    }
    
    private Entity createEntityWithNoNames() {
        PreparedFields prepared = new PreparedFields(
            "",                // normalizedPrimaryName
            List.of(),         // normalizedAltNames
            List.of(),         // nameCombinations
            List.of(),         // altNameCombinations
            null,              // language
            false              // stopwordsRemoved
        );
        
        return new Entity(
            "",                // name
            List.of(),         // altNames
            null,              // entityType
            null,              // source
            null,              // sourceId
            prepared,          // preparedFields
            null,              // remarks
            null,              // person
            null,              // business
            null,              // organization
            null,              // aircraft
            null,              // vessel
            List.of(),         // addresses
            null,              // contactInfo
            List.of(),         // governmentIds
            List.of(),         // cryptoAddresses
            null,              // sanctionsInfo
            List.of()          // historicalInfo
        );
    }

    // ==================== calculateNameScore() Tests ====================
    
    @Nested
    @DisplayName("calculateNameScore() Tests")
    class CalculateNameScoreTests {
        
        @Test
        @DisplayName("Should calculate score for primary names only")
        void primaryNameOnly() {
            // Arrange
            Entity query = createEntityWithPrimaryName("john smith");
            Entity index = createEntityWithPrimaryName("john smith");
            
            // Act
            NameScorer.NameScore result = NameScorer.calculateNameScore(query, index);
            
            // Assert
            assertTrue(result.score() > 0.9, "Exact primary name match should score > 0.9");
            assertEquals(1, result.fieldsCompared(), "Should compare 1 field (primary name)");
        }
        
        @Test
        @DisplayName("Should calculate score for alternative names only")
        void altNamesOnly() {
            // Arrange
            Entity query = createEntityWithAltNames(List.of("johnny", "jonathan"));
            Entity index = createEntityWithAltNames(List.of("john", "johnny"));
            
            // Act
            NameScorer.NameScore result = NameScorer.calculateNameScore(query, index);
            
            // Assert
            assertTrue(result.score() > 0.8, "Matching alt name 'johnny' should score high");
            assertEquals(1, result.fieldsCompared(), "Should compare 1 field (alt names)");
        }
        
        @Test
        @DisplayName("Should blend primary and alternative name scores")
        void blendPrimaryAndAlt() {
            // Arrange
            Entity query = createEntityWithBothNames("john smith", List.of("johnny smith"));
            Entity index = createEntityWithBothNames("john smith", List.of("j smith"));
            
            // Act
            NameScorer.NameScore result = NameScorer.calculateNameScore(query, index);
            
            // Assert
            assertTrue(result.score() > 0.7, "Blended score should be high for close matches");
            assertEquals(2, result.fieldsCompared(), "Should compare 2 fields (primary + alt)");
        }
        
        @Test
        @DisplayName("Should return zero score when no name data available")
        void noNameData() {
            // Arrange
            Entity query = createEntityWithNoNames();
            Entity index = createEntityWithNoNames();
            
            // Act
            NameScorer.NameScore result = NameScorer.calculateNameScore(query, index);
            
            // Assert
            assertEquals(0.0, result.score(), "Score should be 0.0 when no names available");
            assertEquals(0, result.fieldsCompared(), "Should compare 0 fields");
        }
    }

    // ==================== isNameCloseEnough() Tests ====================
    
    @Nested
    @DisplayName("isNameCloseEnough() Tests")
    class IsNameCloseEnoughTests {
        
        @Test
        @DisplayName("Should return true when name score above threshold")
        void aboveThreshold() {
            // Arrange
            Entity query = createEntityWithPrimaryName("john smith");
            Entity index = createEntityWithPrimaryName("john smith");
            
            // Act
            boolean result = NameScorer.isNameCloseEnough(query, index);
            
            // Assert
            assertTrue(result, "Exact name match should be above threshold (0.4)");
        }
        
        @Test
        @DisplayName("Should return false when name score below threshold")
        void belowThreshold() {
            // Arrange
            Entity query = createEntityWithPrimaryName("john smith");
            Entity index = createEntityWithPrimaryName("zhang wei");
            
            // Act
            boolean result = NameScorer.isNameCloseEnough(query, index);
            
            // Assert
            assertFalse(result, "Completely different names should be below threshold (0.4)");
        }
        
        @Test
        @DisplayName("Should return true when no name data available")
        void noNameData() {
            // Arrange
            Entity query = createEntityWithNoNames();
            Entity index = createEntityWithNoNames();
            
            // Act
            boolean result = NameScorer.isNameCloseEnough(query, index);
            
            // Assert
            assertTrue(result, "Should return true when no name data (allow comparison to proceed)");
        }
    }

    // ==================== calculateFinalScore() Tests ====================
    
    @Nested
    @DisplayName("calculateFinalScore() Tests")
    class CalculateFinalScoreTests {
        
        @Test
        @DisplayName("Should calculate weighted average of components")
        void weightedAverage() {
            // Arrange - simulate component scores
            Map<String, Double> components = Map.of(
                "name", 0.9,
                "address", 0.7,
                "dates", 0.8,
                "identifiers", 1.0,
                "supportingInfo", 0.6,
                "contactInfo", 0.5
            );
            
            // Default weights: name=40, address=10, dates=15, identifiers=15, supportingInfo=15, contactInfo=5
            // Calculation: (0.9*40 + 0.7*10 + 0.8*15 + 1.0*15 + 0.6*15 + 0.5*5) / (40+10+15+15+15+5)
            //            = (36 + 7 + 12 + 15 + 9 + 2.5) / 100
            //            = 81.5 / 100 = 0.815
            
            // Act
            double result = EntityScorer.calculateFinalScore(components);
            
            // Assert
            assertTrue(result > 0.8 && result < 0.85, 
                "Weighted average should be ~0.815, got: " + result);
        }
        
        @Test
        @DisplayName("Should ignore zero scores in calculation")
        void zeroScoresIgnored() {
            // Arrange - some components have zero scores
            Map<String, Double> components = Map.of(
                "name", 0.9,
                "address", 0.0,      // Zero - should be ignored
                "dates", 0.0,        // Zero - should be ignored
                "identifiers", 1.0
            );
            
            // Only name and identifiers should contribute
            // Calculation: (0.9*40 + 1.0*15) / (40+15) = (36 + 15) / 55 = 0.927
            
            // Act
            double result = EntityScorer.calculateFinalScore(components);
            
            // Assert
            assertTrue(result > 0.92 && result < 0.93, 
                "Should ignore zeros, expected ~0.927, got: " + result);
        }
        
        @Test
        @DisplayName("Should return zero when no components have scores")
        void noComponents() {
            // Arrange - empty components map
            Map<String, Double> components = Map.of();
            
            // Act
            double result = EntityScorer.calculateFinalScore(components);
            
            // Assert
            assertEquals(0.0, result, "Should return 0.0 when no components provided");
        }
    }

    // ==================== Integration Tests ====================
    
    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        
        @Test
        @DisplayName("Should handle full name scoring pipeline")
        void fullPipeline() {
            // Arrange
            Entity query = createEntityWithBothNames("john smith", List.of("johnny smith", "j smith"));
            Entity index = createEntityWithBothNames("john smith", List.of("john s", "johnny"));
            
            // Act - Calculate name score
            NameScorer.NameScore nameScore = NameScorer.calculateNameScore(query, index);
            
            // Act - Use in final score calculation
            Map<String, Double> components = Map.of(
                "name", nameScore.score(),
                "identifiers", 1.0  // Assume perfect ID match
            );
            double finalScore = EntityScorer.calculateFinalScore(components);
            
            // Assert
            assertTrue(nameScore.score() > 0.7, "Name score should be high");
            assertTrue(finalScore > 0.8, "Final score should be high with good name + ID match");
        }
        
        @Test
        @DisplayName("Should use early exit for non-matching names")
        void earlyExit() {
            // Arrange
            Entity query = createEntityWithPrimaryName("john smith");
            Entity index = createEntityWithPrimaryName("zhang wei");
            
            // Act
            boolean shouldProceed = NameScorer.isNameCloseEnough(query, index);
            
            // Assert
            assertFalse(shouldProceed, 
                "Should return false for completely different names (early exit optimization)");
        }
    }
}
