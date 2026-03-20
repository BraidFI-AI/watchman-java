package io.braid.daywatcher.similarity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Debug test for Broad Phonetic Matching issues (Rows 13, 16, 18, 24).
 * 
 * Issues reported:
 * - Row 13: SHINRIKYO matched SUNRISE & SOMERSET
 * - Row 16: PIJ matched PKK
 * - Row 18: SAYARA matched SRA
 * - Row 24: IRA matched IARA
 * 
 * Root Cause: Soundex phonetic matching is too broad for short strings/acronyms
 */
@SpringBootTest
@DisplayName("Broad Phonetic Matching Debug")
class BroadPhoneticMatchingDebugTest {

    @Autowired
    private JaroWinklerSimilarity similarity;

    @Test
    @DisplayName("Row 13: SHINRIKYO vs SUNRISE - should NOT match phonetically")
    void shinrikyoVsSunrise() {
        String query = "SHINRIKYO";
        String candidate = "SUNRISE";
        
        double score = similarity.tokenizedSimilarity(query, candidate);
        
        System.out.println("=== Row 13: SHINRIKYO vs SUNRISE ===");
        System.out.println("Query: " + query + " (length: " + query.length() + ")");
        System.out.println("Candidate: " + candidate + " (length: " + candidate.length() + ")");
        System.out.println("Score: " + score);
        System.out.println("Length diff: " + Math.abs(query.length() - candidate.length()) / (double)Math.max(query.length(), candidate.length()) * 100 + "%");
        System.out.println("");
        
        // Should be low score (not phonetically similar)
        assertThat(score).as("SHINRIKYO and SUNRISE should have low similarity").isLessThan(0.70);
    }

    @Test
    @DisplayName("Row 13: SHINRIKYO vs SOMERSET - should NOT match phonetically")
    void shinrikyoVsSomerset() {
        String query = "SHINRIKYO";
        String candidate = "SOMERSET";
        
        double score = similarity.tokenizedSimilarity(query, candidate);
        
        System.out.println("=== Row 13: SHINRIKYO vs SOMERSET ===");
        System.out.println("Query: " + query + " (length: " + query.length() + ")");
        System.out.println("Candidate: " + candidate + " (length: " + candidate.length() + ")");
        System.out.println("Score: " + score);
        System.out.println("Length diff: " + Math.abs(query.length() - candidate.length()) / (double)Math.max(query.length(), candidate.length()) * 100 + "%");
        System.out.println("");
        
        assertThat(score).as("SHINRIKYO and SOMERSET should have low similarity").isLessThan(0.70);
    }

    @Test
    @DisplayName("Row 16: PIJ vs PKK - should NOT match")
    void pijVsPkk() {
        String query = "PIJ";
        String candidate = "PKK";
        
        double score = similarity.tokenizedSimilarity(query, candidate);
        
        System.out.println("=== Row 16: PIJ vs PKK ===");
        System.out.println("Query: " + query);
        System.out.println("Candidate: " + candidate);
        System.out.println("Score: " + score);
        System.out.println("");
        
        assertThat(score).as("PIJ and PKK should have low similarity").isLessThan(0.70);
    }

    @Test
    @DisplayName("Row 18: SAYARA vs SRA - should NOT match")
    void sayaraVsSra() {
        String query = "SAYARA";
        String candidate = "SRA";
        
        double score = similarity.tokenizedSimilarity(query, candidate);
        
        System.out.println("=== Row 18: SAYARA vs SRA ===");
        System.out.println("Query: " + query + " (length: " + query.length() + ")");
        System.out.println("Candidate: " + candidate + " (length: " + candidate.length() + ")");
        System.out.println("Score: " + score);
        System.out.println("Length diff: " + Math.abs(query.length() - candidate.length()) / (double)Math.max(query.length(), candidate.length()) * 100 + "%");
        System.out.println("");
        
        // 50% length difference should block phonetic match
        assertThat(score).as("SAYARA and SRA should have low similarity (50% length diff)").isLessThan(0.70);
    }

    @Test
    @DisplayName("Row 24: IRA vs IARA - borderline case (25% length diff)")
    void iraVsIara() {
        String query = "IRA";
        String candidate = "IARA";
        
        double score = similarity.tokenizedSimilarity(query, candidate);
        
        System.out.println("=== Row 24: IRA vs IARA ===");
        System.out.println("Query: " + query + " (length: " + query.length() + ")");
        System.out.println("Candidate: " + candidate + " (length: " + candidate.length() + ")");
        System.out.println("Score: " + score);
        System.out.println("Length diff: " + Math.abs(query.length() - candidate.length()) / (double)Math.max(query.length(), candidate.length()) * 100 + "%");
        System.out.println("");
        
        // 25% difference is within 30% threshold, so might match phonetically
        // But IRA vs IARA are different entities, so score should still be lower
        assertThat(score).as("IRA and IARA should have moderate similarity").isLessThan(0.85);
    }

    @Test
    @DisplayName("Positive case: Muhammad vs Mohammad - SHOULD match")
    void muhammadVsMohammad() {
        String query = "MUHAMMAD";
        String candidate = "MOHAMMAD";
        
        double score = similarity.tokenizedSimilarity(query, candidate);
        
        System.out.println("=== Positive Case: MUHAMMAD vs MOHAMMAD ===");
        System.out.println("Query: " + query);
        System.out.println("Candidate: " + candidate);
        System.out.println("Score: " + score);
        System.out.println("");
        
        // Should have high score (spelling variation)
        assertThat(score).as("MUHAMMAD and MOHAMMAD are spelling variations").isGreaterThan(0.85);
    }
}
