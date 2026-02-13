package io.moov.watchman.similarity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Debug test for Related Entity Coverage scoring issue.
 * 
 * Issue: Searching "AL QA'IDA" (3 tokens) should return related entities like:
 * - "AL-QA'IDA KURDISH BATTALIONS" (4 tokens)
 * - "AL-QAIDA GROUP OF JIHAD IN IRAQ" (6 tokens)
 * 
 * Hypothesis: Token count mismatch drops scores below 0.70 threshold.
 */
@SpringBootTest
@DisplayName("Related Entity Scoring Debug")
class RelatedEntityScoringDebugTest {

    @Autowired
    private JaroWinklerSimilarity similarity;

    @Test
    @DisplayName("Score: 'AL QA'IDA' vs 'AL-QA'IDA KURDISH BATTALIONS'")
    void alQaidaVsKurdishBattalions() {
        String query = "AL QA'IDA";  // 3 tokens
        String candidate = "AL-QA'IDA KURDISH BATTALIONS";  // 4 tokens (after normalization)
        
        double score = similarity.tokenizedSimilarity(query, candidate);
        
        System.out.println("\n=== AL QA'IDA vs KURDISH BATTALIONS ===");
        System.out.println("Query: " + query + " (3 tokens)");
        System.out.println("Candidate: " + candidate + " (4 tokens after normalization)");
        System.out.println("Score: " + score);
        System.out.println("Threshold: 0.70");
        System.out.println("Above threshold: " + (score >= 0.70));
    }

    @Test
    @DisplayName("Score: 'AL QA'IDA' vs 'AL-QAIDA GROUP OF JIHAD IN IRAQ'")
    void alQaidaVsIraqGroup() {
        String query = "AL QA'IDA";  // 3 tokens
        String candidate = "AL-QAIDA GROUP OF JIHAD IN IRAQ";  // 7 tokens
        
        double score = similarity.tokenizedSimilarity(query, candidate);
        
        System.out.println("\n=== AL QA'IDA vs IRAQ GROUP ===");
        System.out.println("Query: " + query + " (3 tokens)");
        System.out.println("Candidate: " + candidate + " (7 tokens)");
        System.out.println("Score: " + score);
        System.out.println("Threshold: 0.70");
        System.out.println("Above threshold: " + (score >= 0.70));
    }

    @Test
    @DisplayName("Score: 'TALIBAN' vs 'KURDISH TALIBAN'")
    void talibanVsKurdishTaliban() {
        String query = "TALIBAN";  // 1 token
        String candidate = "KURDISH TALIBAN";  // 2 tokens
        
        double score = similarity.tokenizedSimilarity(query, candidate);
        
        System.out.println("\n=== TALIBAN vs KURDISH TALIBAN ===");
        System.out.println("Query: " + query + " (1 token)");
        System.out.println("Candidate: " + candidate + " (2 tokens)");
        System.out.println("Score: " + score);
        System.out.println("Threshold: 0.70");
        System.out.println("Above threshold: " + (score >= 0.70));
    }

    @Test
    @DisplayName("Score: 'CIM EX' vs 'FINANCIERA CIMEX S.A'")
    void cimexVsFinancieraCimex() {
        String query = "CIMEX";  // 1 token
        String candidate = "FINANCIERA CIMEX S.A";  // 3 tokens (after normalization, S.A. removed)
        
        double score = similarity.tokenizedSimilarity(query, candidate);
        
        System.out.println("\n=== CIMEX vs FINANCIERA CIMEX ===");
        System.out.println("Query: " + query + " (1 token)");
        System.out.println("Candidate: " + candidate + " (3 tokens)");
        System.out.println("Score: " + score);
        System.out.println("Threshold: 0.70");
        System.out.println("Above threshold: " + (score >= 0.70));
        System.out.println("\nNote: CIMEX case WORKS, this is for comparison");
    }
}
