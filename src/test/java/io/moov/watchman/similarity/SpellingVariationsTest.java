package io.moov.watchman.similarity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test spelling variations to ensure phonetic matching threshold doesn't break legitimate cases.
 */
@SpringBootTest
@DisplayName("Spelling Variations Test")
class SpellingVariationsTest {

    @Autowired
    private JaroWinklerSimilarity similarity;

    @Test
    @DisplayName("MOHAMMED vs MOHAMED (12.5% length diff) - should still match with high score")
    void mohammedVsMohamed() {
        String query = "MOHAMMED";
        String candidate = "MOHAMED";
        
        double score = similarity.tokenizedSimilarity(query, candidate);
        
        System.out.println("=== MOHAMMED vs MOHAMED ===");
        System.out.println("Length diff: " + Math.abs(query.length() - candidate.length()) / (double)Math.max(query.length(), candidate.length()) * 100 + "%");
        System.out.println("Score: " + score);
        
        // Should have high score via Jaro-Winkler even if phonetic fails
        assertThat(score).as("MOHAMMED and MOHAMED are spelling variations").isGreaterThan(0.85);
    }

    @Test
    @DisplayName("MIKHAIL vs MICHAEL (0% length diff) - should match")
    void mikhailVsMichael() {
        String query = "MIKHAIL";
        String candidate = "MICHAEL";
        
        double score = similarity.tokenizedSimilarity(query, candidate);
        
        System.out.println("=== MIKHAIL vs MICHAEL ===");
        System.out.println("Score: " + score);
        
        assertThat(score).as("MIKHAIL and MICHAEL are spelling variations").isGreaterThan(0.85);
    }
}
