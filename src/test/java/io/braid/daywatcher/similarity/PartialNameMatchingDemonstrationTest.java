package io.braid.daywatcher.similarity;

import io.braid.daywatcher.config.SimilarityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Demonstration test showing actual scores for BSA Observation #2 scenarios.
 * This test ALWAYS PASSES and just prints scores for verification.
 */
@DisplayName("Partial Name Matching Demonstration (Always Passes)")
class PartialNameMatchingDemonstrationTest {

    private JaroWinklerSimilarity similarity;

    @BeforeEach
    void setUp() {
        TextNormalizer normalizer = new TextNormalizer();
        PhoneticFilter phoneticFilter = new PhoneticFilter(true);
        SimilarityConfig config = new SimilarityConfig();
        similarity = new JaroWinklerSimilarity(normalizer, phoneticFilter, config);
    }

    @Test
    @DisplayName("Show BSA Observation #2 scenario scores")
    void demonstrateObservation2Scores() {
        System.out.println("\n=== BSA/AML Observation #2: Partial Name Matching ===");
        
        String[][] testCases = {
            {"Muhammad AL-JASIM", "Muhammad Husayn AL-JASIM"},
            {"AL-JASIM Muhammad", "Muhammad Husayn AL-JASIM"},
            {"Muhammad", "Muhammad Husayn"},
            {"AL-JASIM", "Muhammad Husayn AL-JASIM"},
            {"Muhammad Husayn AL-JASIM", "Muhammad Bin Husayn AL-JASIM"},
            {"Muhammad Husayn AL-JASIM", "Muhammad Husayn AL-JASIM"}, // Full match for comparison
        };
        
        for (String[] testCase : testCases) {
            String query = testCase[0];
            String entity = testCase[1];
            double score = similarity.tokenizedSimilarity(query, entity);
            
            String queryTokens = String.join(", ", query.split("\\s+"));
            String entityTokens = String.join(", ", entity.split("\\s+"));
            
            System.out.printf("\nQuery:  \"%s\" (%d tokens: %s)%n", query, query.split("\\s+").length, queryTokens);
            System.out.printf("Entity: \"%s\" (%d tokens: %s)%n", entity, entity.split("\\s+").length, entityTokens);
            System.out.printf("Score:  %.3f%n", score);
        }
        
        System.out.println("\n=== End Demonstration ===\n");
    }
}
