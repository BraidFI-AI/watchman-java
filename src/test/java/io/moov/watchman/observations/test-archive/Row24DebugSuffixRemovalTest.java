package io.moov.watchman.observations;

import io.moov.watchman.model.Entity;
import io.moov.watchman.similarity.TextNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Debug test to verify company suffix removal works correctly.
 */
@DisplayName("Row 24 - Debug Company Suffix Removal")
public class Row24DebugSuffixRemovalTest {

    @Test
    @DisplayName("Verify removeCompanyTitles removes ' llc' suffix")
    void testRemoveCompanyTitles() {
        TextNormalizer normalizer = new TextNormalizer();
        
        // Test case 1: "SMARTMET LLC"
        String query1 = "SMARTMET LLC";
        String normalized1 = normalizer.lowerAndRemovePunctuation(query1);
        System.out.println("Query: '" + query1 + "'");
        System.out.println("After lowerAndRemovePunctuation: '" + normalized1 + "'");
        
        String withoutSuffix1 = Entity.removeCompanyTitles(normalized1);
        System.out.println("After removeCompanyTitles: '" + withoutSuffix1 + "'");
        System.out.println();
        
        assertThat(withoutSuffix1)
            .as("' llc' suffix should be removed")
            .isEqualTo("smartmet");
        
        // Test case 2: "LLC SMARTMET"
        String query2 = "LLC SMARTMET";
        String normalized2 = normalizer.lowerAndRemovePunctuation(query2);
        System.out.println("Query: '" + query2 + "'");
        System.out.println("After lowerAndRemovePunctuation: '" + normalized2 + "'");
        
        String withoutSuffix2 = Entity.removeCompanyTitles(normalized2);
        System.out.println("After removeCompanyTitles: '" + withoutSuffix2 + "'");
        System.out.println();
        
        // Note: LLC at the beginning won't be removed by removeCompanyTitles
        // since it only removes suffixes (endsWith check)
        System.out.println("Note: 'llc' at beginning is not removed (suffix-only removal)");
        
        // Test case 3: Entity name normalization
        String entityName = "LIMITED LIABILITY COMPANY SMARTMET";
        String normalized3 = normalizer.lowerAndRemovePunctuation(entityName);
        System.out.println("Entity name: '" + entityName + "'");
        System.out.println("After lowerAndRemovePunctuation: '" + normalized3 + "'");
        
        String withoutSuffix3 = Entity.removeCompanyTitles(normalized3);
        System.out.println("After removeCompanyTitles: '" + withoutSuffix3 + "'");
        System.out.println();
        
        // The entity doesn't end with a company suffix that would be removed
        // "limited liability company" are in the middle, not at the end
    }
}
