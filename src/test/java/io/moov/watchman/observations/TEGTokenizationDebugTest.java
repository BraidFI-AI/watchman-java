package io.moov.watchman.observations;

import io.moov.watchman.model.Entity;
import io.moov.watchman.similarity.JaroWinklerSimilarity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TEGTokenizationDebugTest {

    @Autowired
    private JaroWinklerSimilarity similarity;

    @Test
    void debugTEGTokenization() {
        System.out.println("\n=== TEG TOKENIZATION DEBUG ===");
        System.out.println();
        
        String query = "TEG LIMITED";
        String entityName = "T.E.G. LIMITED";
        
        System.out.println("Query: \"" + query + "\"");
        System.out.println("Entity: \"" + entityName + "\"");
        System.out.println();
        
        // Test using the tokenized similarity method
        double score = similarity.tokenizedSimilarity(query, entityName);
        
        System.out.println("Score: " + score);
        System.out.println("Expected: 1.0 (perfect match after acronym collapsing)");
        System.out.println();
        
        if (score < 0.88) {
            System.out.println("❌ SCORE TOO LOW - Would be filtered out at 88% threshold!");
        } else if (score < 1.0) {
            System.out.println("⚠️  SCORE < 1.0 - Acronym collapsing may not be working correctly");
        } else {
            System.out.println("✅ PERFECT SCORE - Acronym collapsing is working!");
        }
        
        System.out.println();
        System.out.println("=== Testing various queries ===");
        String[] testQueries = {
            "TEG LIMITED",
            "T.E.G. LIMITED",
            "T E G LIMITED",
            "TEG",
            "T.E.G."
        };
        
        for (String testQuery : testQueries) {
            double testScore = similarity.tokenizedSimilarity(testQuery, entityName);
            System.out.printf("%-20s vs %-20s = %.2f%n", 
                "\"" + testQuery + "\"", "\"" + entityName + "\"", testScore * 100);
        }
    }
}
