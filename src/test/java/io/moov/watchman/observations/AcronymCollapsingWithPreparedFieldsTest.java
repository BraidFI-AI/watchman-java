package io.moov.watchman.observations;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SourceList;
import io.moov.watchman.similarity.JaroWinklerSimilarity;
import io.moov.watchman.similarity.TextNormalizer;
import io.moov.watchman.config.SimilarityConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify acronym collapsing works correctly with PreparedFields.
 */
public class AcronymCollapsingWithPreparedFieldsTest {

    @Test
    void testTEGWithPreparedFields() {
        // Create entity "T.E.G. LIMITED" and normalize it
        Entity original = Entity.of("8404", "T.E.G. LIMITED", EntityType.BUSINESS, SourceList.US_OFAC);
        Entity normalized = original.normalize();
        
        System.out.println("=== PREPARED FIELDS TEST ===");
        System.out.println("Original name: " + original.name());
        System.out.println("Normalized primary name: " + normalized.preparedFields().normalizedPrimaryName());
        
        // Create similarity service
        SimilarityConfig config = new SimilarityConfig();
        JaroWinklerSimilarity similarity = new JaroWinklerSimilarity(
            new TextNormalizer(),
            new io.moov.watchman.similarity.PhoneticFilter(true),
            config
        );
        
        // Test: Search "teg limited" against normalized entity's prepared field
        String query = "teg limited";
        List<String> preparedNames = List.of(normalized.preparedFields().normalizedPrimaryName());
        
        System.out.println("\nQuery: '" + query + "'");
        System.out.println("PreparedNames: " + preparedNames);
        
        // Manually trace what should happen:
        TextNormalizer normalizer = new TextNormalizer();
        String normalizedQuery = normalizer.lowerAndRemovePunctuation(query);
        String[] queryTokens = normalizer.tokenize(normalizedQuery);
        System.out.println("\n1. Query tokens before collapse: " + String.join(", ", queryTokens));
        
        String preparedName = normalized.preparedFields().normalizedPrimaryName();
        String[] candidateTokens = normalizer.tokenize(preparedName);
        System.out.println("2. Candidate tokens before collapse: " + String.join(", ", candidateTokens));
        
        // Now test via the similarity service
        double score = similarity.tokenizedSimilarityWithPrepared(query, preparedNames);
        
        System.out.println("\n3. Final similarity score: " + String.format("%.2f%%", score * 100));
        
        assertThat(score)
            .withFailMessage("TEG LIMITED should match T.E.G. LIMITED with acronym collapsing")
            .isGreaterThanOrEqualTo(0.88);
    }
    
    @Test
    void testTEGStandaloneScoring() {
        // Also test using the non-prepared path for comparison
        Entity original = Entity.of("8404", "T.E.G. LIMITED", EntityType.BUSINESS, SourceList.US_OFAC);
        
        System.out.println("\n=== FALLBACK (NON-PREPARED) TEST ===");
        System.out.println("Entity name: " + original.name());
        
        SimilarityConfig config = new SimilarityConfig();
        JaroWinklerSimilarity similarity = new JaroWinklerSimilarity(
            new TextNormalizer(),
            new io.moov.watchman.similarity.PhoneticFilter(true),
            config
        );
        
        String query = "teg limited";
        String entityName = original.name();
        
        System.out.println("Query: '" + query + "'");
        System.out.println("Entity name: '" + entityName + "'");
        
        double score = similarity.tokenizedSimilarity(query, entityName);
        
        System.out.println("Similarity score: " + String.format("%.2f%%", score * 100));
        
        assertThat(score)
            .withFailMessage("TEG LIMITED should match T.E.G. LIMITED with acronym collapsing (fallback)")
            .isGreaterThanOrEqualTo(0.88);
    }
}
