package io.moov.watchman.similarity;

import io.moov.watchman.config.SimilarityConfig;
import io.moov.watchman.config.WeightConfig;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SourceList;
import io.moov.watchman.search.EntityScorer;
import io.moov.watchman.search.EntityScorerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRIPLE VERIFICATION TEST
 * 
 * Tests to confirm whether word order sensitivity is actually implemented.
 * Based on BSA consultant feedback in observations/initial_feedback.md
 * 
 * Expected: "AL-JASIM, Muhammad Husayn" vs "Muhammad Husayn AL-JASIM" should produce same/similar results
 */
@SpringBootTest
@DisplayName("Word Order Verification - BSA Feedback Response")
class WordOrderVerificationTest {

    @Autowired
    private SimilarityConfig similarityConfig;

    @Autowired
    private WeightConfig weightConfig;

    private EntityScorer scorer;

    @BeforeEach
    void setup() {
        TextNormalizer normalizer = new TextNormalizer();
        PhoneticFilter phoneticFilter = new PhoneticFilter();
        SimilarityService similarityService = new JaroWinklerSimilarity(normalizer, phoneticFilter, similarityConfig);
        scorer = new EntityScorerImpl(similarityService, weightConfig);
    }

    @Test
    @DisplayName("VERIFICATION 1: Same words different order - exact tokens")
    void sameWordsReorderedExactTokens() {
        // From BSA feedback: "AL-JASIM, Muhammad Husayn" vs "Muhammad Husayn AL-JASIM"
        Entity index = Entity.of("1", "AL-JASIM, Muhammad Husayn", EntityType.PERSON, SourceList.US_OFAC);
        Entity normalized = index.normalize();

        String query1 = "AL-JASIM Muhammad Husayn";
        String query2 = "Muhammad Husayn AL-JASIM";

        double score1 = scorer.score(query1, normalized);
        double score2 = scorer.score(query2, normalized);

        System.out.println("=== VERIFICATION 1 ===");
        System.out.println("Index entity: " + index.name());
        System.out.println("Query 1: " + query1 + " -> Score: " + score1);
        System.out.println("Query 2: " + query2 + " -> Score: " + score2);
        System.out.println("Difference: " + Math.abs(score1 - score2));
        System.out.println("");

        // Check if scores are identical (word order insensitive)
        assertThat(score1).as("Word order should not affect score").isEqualTo(score2);
    }

    @Test
    @DisplayName("VERIFICATION 2: Business name word reordering")
    void businessNameReordering() {
        Entity index = Entity.of("1", "AEROCARIBBEAN AIRLINES", EntityType.BUSINESS, SourceList.US_OFAC);
        Entity normalized = index.normalize();

        String query1 = "AEROCARIBBEAN AIRLINES";
        String query2 = "AIRLINES AEROCARIBBEAN";

        double score1 = scorer.score(query1, normalized);
        double score2 = scorer.score(query2, normalized);

        System.out.println("=== VERIFICATION 2 ===");
        System.out.println("Index entity: " + index.name());
        System.out.println("Query 1: " + query1 + " -> Score: " + score1);
        System.out.println("Query 2: " + query2 + " -> Score: " + score2);
        System.out.println("Difference: " + Math.abs(score1 - score2));
        System.out.println("");

        // Check if scores are identical (word order insensitive)
        assertThat(score1).as("Word order should not affect score").isEqualTo(score2);
    }

    @Test
    @DisplayName("VERIFICATION 3: Token set equality check in code")
    void tokenSetEqualityCheck() {
        // This verifies the actual implementation in JaroWinklerSimilarity.tokenizedSimilarity()
        TextNormalizer normalizer = new TextNormalizer();
        PhoneticFilter phoneticFilter = new PhoneticFilter();
        JaroWinklerSimilarity similarity = new JaroWinklerSimilarity(normalizer, phoneticFilter, similarityConfig);

        String s1 = "AEROCARIBBEAN AIRLINES";
        String s2 = "AIRLINES AEROCARIBBEAN";

        double score = similarity.tokenizedSimilarity(s1, s2);

        System.out.println("=== VERIFICATION 3 ===");
        System.out.println("String 1: " + s1);
        System.out.println("String 2: " + s2);
        System.out.println("Score: " + score);
        System.out.println("Expected: 1.0 (if set equality check works)");
        System.out.println("");

        // If implementation has set equality check (lines 114-121), should return 1.0
        assertThat(score).as("Same tokens in different order should score 1.0").isEqualTo(1.0);
    }

    @Test
    @DisplayName("VERIFICATION 4: Direct set equality implementation")
    void directSetEqualityTest() {
        TextNormalizer normalizer = new TextNormalizer();
        
        String s1 = "Muhammad Husayn AL-JASIM";
        String s2 = "AL-JASIM Muhammad Husayn";

        String norm1 = normalizer.lowerAndRemovePunctuation(s1);
        String norm2 = normalizer.lowerAndRemovePunctuation(s2);

        String[] tokens1 = normalizer.tokenize(norm1);
        String[] tokens2 = normalizer.tokenize(norm2);

        System.out.println("=== VERIFICATION 4 ===");
        System.out.println("Input 1: " + s1);
        System.out.println("Normalized 1: " + norm1);
        System.out.println("Tokens 1: " + String.join(", ", tokens1));
        System.out.println("");
        System.out.println("Input 2: " + s2);
        System.out.println("Normalized 2: " + norm2);
        System.out.println("Tokens 2: " + String.join(", ", tokens2));
        System.out.println("");

        // Check if tokens are the same (as sets)
        java.util.Set<String> set1 = new java.util.HashSet<>(java.util.Arrays.asList(tokens1));
        java.util.Set<String> set2 = new java.util.HashSet<>(java.util.Arrays.asList(tokens2));

        boolean sameTokens = set1.equals(set2);
        System.out.println("Same token sets: " + sameTokens);
        System.out.println("Set 1: " + set1);
        System.out.println("Set 2: " + set2);

        assertThat(sameTokens).as("Reordered names should have same token sets").isTrue();
    }
}
