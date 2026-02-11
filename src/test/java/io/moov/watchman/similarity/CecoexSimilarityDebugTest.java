package io.moov.watchman.similarity;

import io.moov.watchman.config.SimilarityConfig;
import io.moov.watchman.similarity.TextNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Debug test to investigate S.I. 5 CECOEX false negative bug.
 * 
 * Issue: Search for "CECOEX" returns unrelated entity "LAKHVI" with alias "CHACHAJEE"
 * ranked HIGHER than actual target "CECOEX, S.A.".
 * 
 * This test isolates the similarity calculation to understand why "CECOEX" matches
 * "CHACHAJEE" so strongly.
 */
@DisplayName("S.I. 5 Debug: CECOEX vs CHACHAJEE Similarity")
class CecoexSimilarityDebugTest {

    private JaroWinklerSimilarity similarity;
    
    @BeforeEach
    void setUp() {
        SimilarityConfig config = new SimilarityConfig();
        TextNormalizer normalizer = new TextNormalizer();
        PhoneticFilter phoneticFilter = new PhoneticFilter();
        
        similarity = new JaroWinklerSimilarity(normalizer, phoneticFilter, config);
    }
    
    @Test
    @DisplayName("CECOEX vs CHACHAJEE should have LOW similarity (unrelated strings)")
    void cecoexVsChachajee() {
        double score = similarity.tokenizedSimilarity("CECOEX", "CHACHAJEE");
        
        System.out.println("CECOEX vs CHACHAJEE score: " + score);
        
        // These are completely unrelated strings
        // Expected: < 0.60 (should not match)
        // Actual: TBD - this test will reveal the bug
        assertThat(score).isLessThan(0.60)
            .withFailMessage("CECOEX and CHACHAJEE are unrelated - similarity should be < 0.60, but got: " + score);
    }
    
    @Test
    @DisplayName("CECOEX vs CECOEX, S.A. should have HIGH similarity (same entity)")
    void cecoexVsCecoexSA() {
        double score = similarity.tokenizedSimilarity("CECOEX", "CECOEX, S.A.");
        
        System.out.println("CECOEX vs CECOEX, S.A. score: " + score);
        
        // Same entity, just with legal suffix
        // Expected: >= 0.90 (strong match)
        assertThat(score).isGreaterThanOrEqualTo(0.90)
            .withFailMessage("CECOEX and CECOEX, S.A. are the same entity - similarity should be >= 0.90, but got: " + score);
    }
    
    @Test
    @DisplayName("Token breakdown: understand how CECOEX matches CHACHAJEE")
    void tokenBreakdown() {
        // Test individual token similarities
        double cVsC = similarity.tokenizedSimilarity("C", "C");
        double cecoexVsChacha = similarity.tokenizedSimilarity("CECOEX", "CHACHA");
        double cecoexVsJee = similarity.tokenizedSimilarity("CECOEX", "JEE");
        
        System.out.println("Token analysis:");
        System.out.println("  C vs C: " + cVsC);
        System.out.println("  CECOEX vs CHACHA: " + cecoexVsChacha);
        System.out.println("  CECOEX vs JEE: " + cecoexVsJee);
        
        // Reference: CECOEX = 6 chars, CHACHAJEE = 9 chars
        // Only commonality: both start with 'C' and contain 'E'
        System.out.println("\nString characteristics:");
        System.out.println("  CECOEX: 6 chars");
        System.out.println("  CHACHAJEE: 9 chars");
        System.out.println("  Length ratio: " + (6.0/9.0));
        
        // Test phonetic codes (Soundex)
        PhoneticFilter filter = new PhoneticFilter();
        String soundexCecoex = filter.soundex("CECOEX");
        String soundexChachajee = filter.soundex("CHACHAJEE");
        
        System.out.println("\nPhonetic analysis (Soundex):");
        System.out.println("  CECOEX Soundex: " + soundexCecoex);
        System.out.println("  CHACHAJEE Soundex: " + soundexChachajee);
        System.out.println("  Soundex match: " + soundexCecoex.equals(soundexChachajee));
        System.out.println("\n⚠️ BUG IDENTIFIED: If Soundex codes match, tokenizedSimilarity returns 1.0 (false positive)");
    }
    
    @Test
    @DisplayName("Compare scores: verify CECOEX, S.A. should rank higher than CHACHAJEE alias")
    void compareScores() {
        double targetScore = similarity.tokenizedSimilarity("CECOEX", "CECOEX, S.A.");
        double unrelatedScore = similarity.tokenizedSimilarity("CECOEX", "CHACHAJEE");
        
        System.out.println("\nScore comparison:");
        System.out.println("  Target (CECOEX, S.A.): " + targetScore);
        System.out.println("  Unrelated (CHACHAJEE): " + unrelatedScore);
        System.out.println("  Difference: " + (targetScore - unrelatedScore));
        
        assertThat(targetScore).isGreaterThan(unrelatedScore)
            .withFailMessage(
                "Target entity 'CECOEX, S.A.' (score=" + targetScore + ") " +
                "should rank HIGHER than unrelated alias 'CHACHAJEE' (score=" + unrelatedScore + ")"
            );
    }
}
