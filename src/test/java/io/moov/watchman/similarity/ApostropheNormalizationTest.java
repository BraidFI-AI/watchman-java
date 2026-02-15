package io.moov.watchman.similarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD Phase - RED: Tests for Row 50 - Apostrophe normalization issue
 * 
 * <p><strong>Problem</strong>: Apostrophes are converted to spaces, splitting tokens incorrectly.
 * - "Yo'ng" → ["yo", "ng"] doesn't match "Yong" → ["yong"]
 * 
 * <p><strong>Solution</strong>: Remove apostrophes entirely before tokenization.
 * - "Yo'ng" → "yong" matches "Yong" → "yong"
 * 
 * <p><strong>Expected Behavior</strong>:
 * <ul>
 *   <li>"Yo'ng chu" normalizes to "yong chu" (2 tokens)</li>
 *   <li>"Yong chu" normalizes to "yong chu" (2 tokens)</li>
 *   <li>"O'Brien" normalizes to "obrien" (1 token)</li>
 *   <li>"Sha'ban" normalizes to "shaban" (1 token)</li>
 * </ul>
 */
@DisplayName("Row 50: Apostrophe Normalization - TDD RED Phase")
class ApostropheNormalizationTest {

    private TextNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new TextNormalizer();
    }

    @Test
    @DisplayName("Korean romanization: Yo'ng should match Yong")
    void testKoreanRomanizationApostrophe() {
        String withApostrophe = "Yo'ng chu";
        String withoutApostrophe = "Yong chu";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withApostrophe);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutApostrophe);
        
        assertThat(normalized1).isEqualTo(normalized2)
            .describedAs("'Yo'ng chu' should normalize to same as 'Yong chu'");
        
        // Both should be 2 tokens, not 3
        String[] tokens1 = normalizer.tokenize(normalized1);
        String[] tokens2 = normalizer.tokenize(normalized2);
        
        assertThat(tokens1).hasSize(2)
            .describedAs("Should have 2 tokens: ['yong', 'chu'], not 3 ['yo', 'ng', 'chu']");
        assertThat(tokens2).hasSize(2);
        assertThat(tokens1).isEqualTo(tokens2);
    }

    @Test
    @DisplayName("O'Brien should match OBrien")
    void testIrishNameApostrophe() {
        String withApostrophe = "O'Brien";
        String withoutApostrophe = "OBrien";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withApostrophe);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutApostrophe);
        
        assertThat(normalized1).isEqualTo(normalized2)
            .describedAs("'O'Brien' should normalize to same as 'OBrien'");
        
        // Should be 1 token, not 2
        String[] tokens = normalizer.tokenize(normalized1);
        assertThat(tokens).hasSize(1)
            .describedAs("Should be 1 token ['obrien'], not 2 ['o', 'brien']");
    }

    @Test
    @DisplayName("Arabic names: Sha'ban should match Shaban")
    void testArabicNameApostrophe() {
        String withApostrophe = "Sha'ban";
        String withoutApostrophe = "Shaban";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withApostrophe);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutApostrophe);
        
        assertThat(normalized1).isEqualTo(normalized2)
            .describedAs("'Sha'ban' should normalize to same as 'Shaban'");
    }

    @Test
    @DisplayName("Multiple apostrophes: O'Neal's should normalize correctly")
    void testMultipleApostrophes() {
        String input = "O'Neal's Restaurant";
        
        String normalized = normalizer.lowerAndRemovePunctuation(input);
        
        // "O'Neal's" → "oneals" (apostrophes removed)
        assertThat(normalized).contains("oneals")
            .describedAs("Multiple apostrophes should be removed");
    }

    @Test
    @DisplayName("Full KIM Yo'ng-chu test case from Row 50")
    void testKimYongChuFullCase() {
        // The actual OFAC entity name variations
        String ofacFormat = "KIM, Yo'ng-chu";
        String searchQuery1 = "Yong chu KIM";
        String searchQuery2 = "Yong-chu KIM";
        String searchQuery3 = "Yo'ng chu KIM";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(ofacFormat);
        String normalized2 = normalizer.lowerAndRemovePunctuation(searchQuery1);
        String normalized3 = normalizer.lowerAndRemovePunctuation(searchQuery2);
        String normalized4 = normalizer.lowerAndRemovePunctuation(searchQuery3);
        
        // All should normalize to "kim yong chu" (tokens: ["kim", "yong", "chu"])
        // Note: Hyphen creates token boundary, but apostrophe does not
        String[] tokens1 = normalizer.tokenize(normalized1);
        String[] tokens2 = normalizer.tokenize(normalized2);
        String[] tokens3 = normalizer.tokenize(normalized3);
        String[] tokens4 = normalizer.tokenize(normalized4);
        
        // After normalization, all should have same tokens: ["kim", "yong", "chu"]
        // The key fix: apostrophe removal means "Yo'ng" → "yong" (not "yo ng")
        assertThat(tokens1).containsExactlyInAnyOrder("kim", "yong", "chu")
            .describedAs("OFAC 'KIM, Yo'ng-chu' should have tokens [kim, yong, chu]");
        assertThat(tokens2).containsExactlyInAnyOrder("kim", "yong", "chu")
            .describedAs("Query 'Yong chu KIM' should have tokens [kim, yong, chu]");
        assertThat(tokens3).containsExactlyInAnyOrder("kim", "yong", "chu")
            .describedAs("Query 'Yong-chu KIM' should have tokens [kim, yong, chu]");
        assertThat(tokens4).containsExactlyInAnyOrder("kim", "yong", "chu")
            .describedAs("Query 'Yo'ng chu KIM' should have tokens [kim, yong, chu]");
        
        // Most importantly: ALL variations should produce SAME token set (order may vary)
        assertThat(tokens1).containsExactlyInAnyOrder(tokens2);
        assertThat(tokens1).containsExactlyInAnyOrder(tokens3);
        assertThat(tokens1).containsExactlyInAnyOrder(tokens4);
    }
}
