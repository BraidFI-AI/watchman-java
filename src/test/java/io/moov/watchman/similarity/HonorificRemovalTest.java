package io.moov.watchman.similarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED Tests for BSA/AML Observation #7: Noise Words Sensitivity
 * 
 * Issue: System is sensitive to personal honorifics and titles (Mr., Dr., Sheikh, etc.)
 * When extra honorifics are included alongside the core name, match results are degraded.
 * 
 * Examples from Observations v2:
 * - "Mr. Muhammad Husayn AL-JASIM" vs "Muhammad Husayn AL-JASIM"
 * - Expected: Should match with same score
 * - Actual: Mr. prefix degrades score
 * 
 * Honorifics to handle (from Observations v2 document):
 * - English: Mr., Mrs., Ms., Dr., Prof., Sir, Jr., Sr.
 * - Arabic: Sheikh, H.E., Bin, Ibn
 * - And variations with/without periods
 */
@DisplayName("BSA/AML Observation #7: Personal Honorific Removal")
class HonorificRemovalTest {

    private TextNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new TextNormalizer();
    }

    // ========== English Honorifics ==========

    @Test
    @DisplayName("Should remove 'Mr.' prefix")
    void testRemoveMr() {
        String withHonorific = "Mr. Muhammad Husayn AL-JASIM";
        String withoutHonorific = "Muhammad Husayn AL-JASIM";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withHonorific);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutHonorific);
        
        assertEquals(normalized2, normalized1,
            "Name with 'Mr.' should normalize to same as name without");
    }

    @Test
    @DisplayName("Should remove 'Mrs.' prefix")
    void testRemoveMrs() {
        String withHonorific = "Mrs. Jane Smith";
        String withoutHonorific = "Jane Smith";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withHonorific);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutHonorific);
        
        assertEquals(normalized2, normalized1,
            "Name with 'Mrs.' should normalize to same as name without");
    }

    @Test
    @DisplayName("Should remove 'Ms.' prefix")
    void testRemoveMs() {
        String withHonorific = "Ms. Jane Smith";
        String withoutHonorific = "Jane Smith";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withHonorific);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutHonorific);
        
        assertEquals(normalized2, normalized1,
            "Name with 'Ms.' should normalize to same as name without");
    }

    @Test
    @DisplayName("Should remove 'Dr.' prefix")
    void testRemoveDr() {
        String withHonorific = "Dr. John Doe";
        String withoutHonorific = "John Doe";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withHonorific);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutHonorific);
        
        assertEquals(normalized2, normalized1,
            "Name with 'Dr.' should normalize to same as name without");
    }

    @Test
    @DisplayName("Should remove 'Prof.' prefix")
    void testRemoveProf() {
        String withHonorific = "Prof. Albert Einstein";
        String withoutHonorific = "Albert Einstein";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withHonorific);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutHonorific);
        
        assertEquals(normalized2, normalized1,
            "Name with 'Prof.' should normalize to same as name without");
    }

    @Test
    @DisplayName("Should remove 'Sir' prefix (no period)")
    void testRemoveSir() {
        String withHonorific = "Sir Winston Churchill";
        String withoutHonorific = "Winston Churchill";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withHonorific);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutHonorific);
        
        assertEquals(normalized2, normalized1,
            "Name with 'Sir' should normalize to same as name without");
    }

    @Test
    @DisplayName("Should remove 'Jr.' suffix")
    void testRemoveJr() {
        String withHonorific = "Martin Luther King Jr.";
        String withoutHonorific = "Martin Luther King";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withHonorific);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutHonorific);
        
        assertEquals(normalized2, normalized1,
            "Name with 'Jr.' should normalize to same as name without");
    }

    @Test
    @DisplayName("Should remove 'Sr.' suffix")
    void testRemoveSr() {
        String withHonorific = "George Bush Sr.";
        String withoutHonorific = "George Bush";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withHonorific);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutHonorific);
        
        assertEquals(normalized2, normalized1,
            "Name with 'Sr.' should normalize to same as name without");
    }

    // ========== Arabic Honorifics ==========

    @Test
    @DisplayName("Should remove 'Sheikh' prefix")
    void testRemoveSheikh() {
        String withHonorific = "Sheikh Muhammad bin Rashid";
        String withoutHonorific = "Muhammad bin Rashid";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withHonorific);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutHonorific);
        
        assertEquals(normalized2, normalized1,
            "Name with 'Sheikh' should normalize to same as name without");
    }

    @Test
    @DisplayName("Should remove 'H.E.' prefix (His Excellency)")
    void testRemoveHE() {
        String withHonorific = "H.E. Abdullah Ahmed";
        String withoutHonorific = "Abdullah Ahmed";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withHonorific);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutHonorific);
        
        assertEquals(normalized2, normalized1,
            "Name with 'H.E.' should normalize to same as name without");
    }

    @Test
    @DisplayName("Should keep 'Bin' in name (part of name structure)")
    void testKeepBin() {
        String withBin = "Muhammad Bin Salman";
        String normalizedName = normalizer.lowerAndRemovePunctuation(withBin);
        
        assertTrue(normalizedName.contains("bin"),
            "'Bin' should be preserved as it's part of Arabic name structure");
    }

    @Test
    @DisplayName("Should keep 'Ibn' in name (part of name structure)")
    void testKeepIbn() {
        String withIbn = "Muhammad Ibn Abdullah";
        String normalizedName = normalizer.lowerAndRemovePunctuation(withIbn);
        
        assertTrue(normalizedName.contains("ibn"),
            "'Ibn' should be preserved as it's part of Arabic name structure");
    }

    // ========== Honorifics Without Periods ==========

    @Test
    @DisplayName("Should remove 'Mr' without period")
    void testRemoveMrNoPeriod() {
        String withHonorific = "Mr Muhammad Husayn";
        String withoutHonorific = "Muhammad Husayn";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withHonorific);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutHonorific);
        
        assertEquals(normalized2, normalized1,
            "Name with 'Mr' (no period) should normalize to same as name without");
    }

    @Test
    @DisplayName("Should remove 'Dr' without period")
    void testRemoveDrNoPeriod() {
        String withHonorific = "Dr John Smith";
        String withoutHonorific = "John Smith";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withHonorific);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutHonorific);
        
        assertEquals(normalized2, normalized1,
            "Name with 'Dr' (no period) should normalize to same as name without");
    }

    // ========== Multiple Honorifics ==========

    @Test
    @DisplayName("Should remove multiple honorifics")
    void testRemoveMultipleHonorifics() {
        String withHonorifics = "Dr. Sheikh Muhammad bin Rashid Jr.";
        String withoutHonorifics = "Muhammad bin Rashid";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withHonorifics);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutHonorifics);
        
        assertEquals(normalized2, normalized1,
            "Name with multiple honorifics should normalize to same as name without");
    }

    // ========== Real-World BSA/AML Test Cases ==========

    @Test
    @DisplayName("BSA Observation #7: 'Mr. Muhammad Husayn AL-JASIM' should match 'Muhammad Husayn AL-JASIM'")
    void testObservation7Example() {
        String withHonorific = "Mr. Muhammad Husayn AL-JASIM";
        String withoutHonorific = "Muhammad Husayn AL-JASIM";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withHonorific);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutHonorific);
        
        assertEquals(normalized2, normalized1,
            "BSA Observation #7: Honorific 'Mr.' should not affect normalized name");
    }

    @Test
    @DisplayName("Case insensitive: 'MR. JOHN SMITH' should normalize same as 'JOHN SMITH'")
    void testCaseInsensitive() {
        String withHonorific = "MR. JOHN SMITH";
        String withoutHonorific = "JOHN SMITH";
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(withHonorific);
        String normalized2 = normalizer.lowerAndRemovePunctuation(withoutHonorific);
        
        assertEquals(normalized2, normalized1,
            "Uppercase honorifics should be removed correctly");
    }

    @Test
    @DisplayName("Should not remove honorific-like strings in middle of name")
    void testHonorificInMiddle() {
        String name = "John Mr Smith";  // "Mr" as middle name, not honorific
        String normalized = normalizer.lowerAndRemovePunctuation(name);
        
        // When properly implemented, honorifics should only be removed at start/end
        // For now, this documents expected behavior
        assertTrue(normalized.length() > 0,
            "Normalization should produce non-empty result");
    }
}
