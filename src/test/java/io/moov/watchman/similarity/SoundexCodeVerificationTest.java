package io.moov.watchman.similarity;

import org.apache.commons.codec.language.Soundex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verify Soundex codes for the broad phonetic matching failure cases.
 * 
 * Goal: Understand why unrelated entities are matching phonetically.
 */
@DisplayName("Soundex Code Verification")
class SoundexCodeVerificationTest {

    private final Soundex soundex = new Soundex();

    @Test
    @DisplayName("Row 13: SHINRIKYO vs SUNRISE vs SOMERSET")
    void shinrikyoCodes() throws Exception {
        System.out.println("=== Row 13: SHINRIKYO ===");
        System.out.println("SHINRIKYO → " + soundex.encode("SHINRIKYO"));
        System.out.println("SUNRISE → " + soundex.encode("SUNRISE"));
        System.out.println("SOMERSET → " + soundex.encode("SOMERSET"));
        System.out.println("");
    }

    @Test
    @DisplayName("Row 16: PIJ vs PKK")
    void pijVsPkkCodes() throws Exception {
        System.out.println("=== Row 16: PIJ vs PKK ===");
        System.out.println("PIJ → " + soundex.encode("PIJ"));
        System.out.println("PKK → " + soundex.encode("PKK"));
        System.out.println("PALESTINE → " + soundex.encode("PALESTINE"));
        System.out.println("ISLAMIC → " + soundex.encode("ISLAMIC"));
        System.out.println("JIHAD → " + soundex.encode("JIHAD"));
        System.out.println("");
    }

    @Test
    @DisplayName("Row 18: SAYARA vs SRA")
    void sayaraCodes() throws Exception {
        System.out.println("=== Row 18: SAYARA vs SRA ===");
        System.out.println("SAYARA → " + soundex.encode("SAYARA"));
        System.out.println("SRA → " + soundex.encode("SRA"));
        System.out.println("SANABEL → " + soundex.encode("SANABEL"));
        System.out.println("RELIEF → " + soundex.encode("RELIEF"));
        System.out.println("");
    }

    @Test
    @DisplayName("Row 24: IRA vs IARA")
    void iraCodes() throws Exception {
        System.out.println("=== Row 24: IRA vs IARA ===");
        System.out.println("IRA → " + soundex.encode("IRA"));
        System.out.println("IARA → " + soundex.encode("IARA"));
        System.out.println("CONTINUITY → " + soundex.encode("CONTINUITY"));
        System.out.println("ISLAMIC → " + soundex.encode("ISLAMIC"));
        System.out.println("RELIEF → " + soundex.encode("RELIEF"));
        System.out.println("AGENCY → " + soundex.encode("AGENCY"));
        System.out.println("");
    }

    @Test
    @DisplayName("Positive case: MUHAMMAD vs MOHAMMAD")
    void muhammadCodes() throws Exception {
        System.out.println("=== Positive Case: MUHAMMAD vs MOHAMMAD ===");
        System.out.println("MUHAMMAD → " + soundex.encode("MUHAMMAD"));
        System.out.println("MOHAMMAD → " + soundex.encode("MOHAMMAD"));
        System.out.println("");
    }
}
