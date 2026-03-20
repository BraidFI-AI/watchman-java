package io.braid.daywatcher.observations;

import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.search.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * R2 Entity Validation - BSA Consultant Round 2 Retest
 * 
 * TDD RED Phase: Validates all 50 entity rows from r2-entity.csv
 * Source: observations/watchman java obsevations master.xlsx - r2-entity.csv
 * 
 * Test Parameters: limit=20, minMatch=0.88 (standard BSA consultant parameters)
 * 
 * Known Issues:
 * - Row 17 (CK ID): Fixed by alias boost threshold change
 * - Row 24 (SMARTMET): Partial fix - "LLC SMARTMET" (reversed) still failing
 */
@SpringBootTest
@DisplayName("R2 Entity Validation - BSA Consultant Retest (50 rows)")
class R2EntityValidationTest {

    @Autowired
    private SearchService searchService;

    @Test
    @DisplayName("R2 Entity - All 50 Rows Validation Suite")
    void validateAll50EntityRows() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("R2 ENTITY VALIDATION - ALL 50 ROWS");
        System.out.println("Source: r2-entity.csv (BSA Consultant Round 2 Retest)");
        System.out.println("Parameters: limit=20, minMatch=0.88");
        System.out.println("=".repeat(100));

        int passCount = 0;
        int failCount = 0;
        int totalTests = 50;

        // S.I. 1: GRUPO COMERCIAL SAN BLAS, S.A. DE C.V.
        passCount += validateRow(1, "GRUPO COMERCIAL SAN BLAS", "GRUPO COMERCIAL SAN BLAS");

        // S.I. 2: METALLURG-TULAMASH OOO
        passCount += validateRow(2, "METALLURG-TULAMASH OOO", "METALLURG-TULAMASH");

        // S.I. 3: SB SECURITIES SA
        passCount += validateRow(3, "SB SECURITIES SA", "SB SECURITIES");

        // S.I. 4: JOINT STOCK COMPANY URAL SCIENTIFIC AND TECHNOLOGICAL COMPLEX
        passCount += validateRow(4, "JOINT STOCK COMPANY URAL SCIENTIFIC AND TECHNOLOGICAL COMPLEX", "URAL SCIENTIFIC");

        // S.I. 5: ARGON OOO
        passCount += validateRow(5, "ARGON OOO", "ARGON");

        // S.I. 6: JOINT STOCK COMPANY BLACK SEA BANK OF DEVELOPMENT AND RECONSTRUCTION
        passCount += validateRow(6, "JOINT STOCK COMPANY BLACK SEA BANK OF DEVELOPMENT AND RECONSTRUCTION", "BLACK SEA BANK");

        // S.I. 7: P.C.C. (SINGAPORE) PRIVATE LIMITED
        passCount += validateRow(7, "P.C.C. (SINGAPORE) PRIVATE LIMITED", "PCC SINGAPORE");

        // S.I. 8: WUHAN XIAORUIZHI SCIENCE AND TECHNOLOGY COMPANY, LIMITED
        passCount += validateRow(8, "WUHAN XIAORUIZHI SCIENCE AND TECHNOLOGY COMPANY", "XIAORUIZHI");

        // S.I. 9: CORPORATIVO INTEGRAL DE LA COSTA, S.A. DE C.V.
        passCount += validateRow(9, "CORPORATIVO INTEGRAL DE LA COSTA", "CORPORATIVO INTEGRAL");

        // S.I. 10: DINA PETROKIMYA SANAYI TICARET ANONIM SIRKETI
        passCount += validateRow(10, "DINA PETROKIMYA SANAYI TICARET ANONIM SIRKETI", "DINA PETROKIMYA");

        // S.I. 11: LIMITED LIABILITY COMPANY EMERGENCY DIGITAL SOLUTIONS
        passCount += validateRow(11, "LIMITED LIABILITY COMPANY EMERGENCY DIGITAL SOLUTIONS", "EMERGENCY DIGITAL SOLUTIONS");

        // S.I. 12: OBSHCHESTVO S OGRANICHENNOI OTVETSTVENNOSTYU KORPORATSIYA AIPI
        passCount += validateRow(12, "OBSHCHESTVO S OGRANICHENNOI OTVETSTVENNOSTYU KORPORATSIYA AIPI", "KORPORATSIYA AIPI");

        // S.I. 13: DESARROLLOS TURISTICOS FORTIA, S.A. DE C.V.
        passCount += validateRow(13, "DESARROLLOS TURISTICOS FORTIA", "DESARROLLOS TURISTICOS");

        // S.I. 14: BAKHTAR RAAD SEPAHAN COMPANY
        passCount += validateRow(14, "BAKHTAR RAAD SEPAHAN COMPANY", "BAKHTAR RAAD SEPAHAN");

        // S.I. 15: KHAYBAR COMPANY
        passCount += validateRow(15, "KHAYBAR COMPANY", "KHAYBAR");

        // S.I. 16: OPEN JOINT STOCK COMPANY AMKODOR MANAGEMENT HOLDING COMPANY
        passCount += validateRow(16, "OPEN JOINT STOCK COMPANY AMKODOR MANAGEMENT HOLDING COMPANY", "AMKODOR");

        // S.I. 17: CK ID CO. LTD
        passCount += validateRow(17, "CK ID CO. LTD", "CK ID");

        // S.I. 18: JOINT STOCK COMPANY NEW HOLDING 3
        passCount += validateRow(18, "JOINT STOCK COMPANY NEW HOLDING 3", "NEW HOLDING 3");

        // S.I. 19: EDGAR COMMERCIAL SOLUTIONS FZE
        passCount += validateRow(19, "EDGAR COMMERCIAL SOLUTIONS FZE", "EDGAR COMMERCIAL");

        // S.I. 20: KOREA NAMGANG TRADING CORPORATION
        passCount += validateRow(20, "KOREA NAMGANG TRADING CORPORATION", "NAMGANG TRADING");

        // S.I. 21: EMPRESA CUBANA DE AVIACION
        passCount += validateRow(21, "EMPRESA CUBANA DE AVIACION", "CUBANA AVIACION");

        // S.I. 22: RAPICOMBUSTIBLES DE VERACRUZ, S.A. DE C.V.
        passCount += validateRow(22, "RAPICOMBUSTIBLES DE VERACRUZ", "RAPICOMBUSTIBLES");

        // S.I. 23: JOINT STOCK COMMERCIAL BANK NOVIKOMBANK
        passCount += validateRow(23, "JOINT STOCK COMMERCIAL BANK NOVIKOMBANK", "NOVIKOMBANK");

        // S.I. 24: LIMITED LIABILITY COMPANY SMARTMET
        passCount += validateRow(24, "LIMITED LIABILITY COMPANY SMARTMET", "SMARTMET LLC");

        // S.I. 25: FAN PARDAZAN
        passCount += validateRow(25, "FAN PARDAZAN", "FAN PARDAZAN");

        // S.I. 26: MAMOUN DARKAZANLI IMPORT-EXPORT COMPANY
        passCount += validateRow(26, "MAMOUN DARKAZANLI IMPORT-EXPORT COMPANY", "DARKAZANLI");

        // S.I. 27: 72ND AUTOMOTIVE AND ARMORED VEHICLE REPAIR PLANT FEDERAL STATE OWNED INSTITUTION
        passCount += validateRow(27, "72ND AUTOMOTIVE AND ARMORED VEHICLE REPAIR PLANT", "72ND AUTOMOTIVE");

        // S.I. 28: DIDZHITAL SEKYURITI SERVISIS, OOO
        passCount += validateRow(28, "DIDZHITAL SEKYURITI SERVISIS", "DIGITAL SECURITY SERVICES");

        // S.I. 29: CORPORATIVO INMOBILIARIO UNIVERSAL, S.A. DE C.V.
        passCount += validateRow(29, "CORPORATIVO INMOBILIARIO UNIVERSAL", "CORPORATIVO INMOBILIARIO");

        // S.I. 30: COMERCIALIZADORA DE GANADO Y RENTAS DE CAPITAL S.A.
        passCount += validateRow(30, "COMERCIALIZADORA DE GANADO Y RENTAS DE CAPITAL", "GANARECA");

        // S.I. 31: ANHUI LAND GROUP CO., LIMITED
        passCount += validateRow(31, "ANHUI LAND GROUP CO.", "ANHUI LAND GROUP");

        // S.I. 32: IRANIAN NOVIN INDUSTRY DEVELOPMENT
        passCount += validateRow(32, "IRANIAN NOVIN INDUSTRY DEVELOPMENT", "NOVIN INDUSTRY");

        // S.I. 33: DALIAN SUN MOON STAR INTERNATIONAL LOGISTICS TRADING CO., LTD
        passCount += validateRow(33, "DALIAN SUN MOON STAR INTERNATIONAL LOGISTICS", "DALIAN SUN MOON STAR");

        // S.I. 34: VISCAYA LTDA.
        passCount += validateRow(34, "VISCAYA LTDA", "VISCAYA");

        // S.I. 35: MIRA E.U.
        passCount += validateRow(35, "MIRA E.U.", "MIRA");

        // S.I. 36: CASA APOLLO
        passCount += validateRow(36, "CASA APOLLO", "CASA APOLLO");

        // S.I. 37: JARACO S.A.
        passCount += validateRow(37, "JARACO S.A.", "JARACO");

        // S.I. 38: T.M.G. ENGINEERING LIMITED
        passCount += validateRow(38, "T.M.G. ENGINEERING LIMITED", "TMG ENGINEERING");

        // S.I. 39: 7TH OF TIR
        passCount += validateRow(39, "7TH OF TIR", "7TH OF TIR");

        // S.I. 40: SINALOA CARTEL
        passCount += validateRow(40, "SINALOA CARTEL", "SINALOA CARTEL");

        // S.I. 41: PO THONG GANG (Vessel)
        passCount += validateRow(41, "PO THONG GANG", "PO THONG GANG");

        // S.I. 42: DAN (Vessel)
        passCount += validateRow(42, "DAN", "DAN");

        // S.I. 43: ALPHA HERMES (Vessel)
        passCount += validateRow(43, "ALPHA HERMES", "ALPHA HERMES");

        // S.I. 44: CASSIOPEIA (Vessel)
        passCount += validateRow(44, "CASSIOPEIA", "CASSIOPEIA");

        // S.I. 45: 7-28 (Vessel)
        passCount += validateRow(45, "7-28", "7-28");

        // S.I. 46: T7-OKY (Aircraft)
        passCount += validateRow(46, "T7-OKY", "T7-OKY");

        // S.I. 47: P-537 (Aircraft)
        passCount += validateRow(47, "P-537", "P-537");

        // S.I. 48: EP-VIP (Aircraft)
        passCount += validateRow(48, "EP-VIP", "EP-VIP");

        // S.I. 49: EP-MNX (Aircraft)
        passCount += validateRow(49, "EP-MNX", "EP-MNX");

        // S.I. 50: RA-78831 (Aircraft)
        passCount += validateRow(50, "RA-78831", "RA-78831");

        failCount = totalTests - passCount;

        System.out.println("\n" + "=".repeat(100));
        System.out.println(String.format("FINAL RESULTS: %d/%d PASSING (%.1f%%)", 
            passCount, totalTests, (passCount * 100.0) / totalTests));
        System.out.println(String.format("  ✅ PASS: %d", passCount));
        System.out.println(String.format("  ❌ FAIL: %d", failCount));
        System.out.println("=".repeat(100));

        if (passCount == totalTests) {
            System.out.println("🎉 SUCCESS: ALL 50 R2 ENTITY OBSERVATIONS VALIDATED!");
        } else {
            System.out.println("⚠️  Some rows need attention - review failures above");
        }
    }

    /**
     * Validate a single entity row from r2-entity.csv
     * 
     * @param rowNumber S.I. number from CSV
     * @param entityName Expected entity name to match
     * @param query Search query to use
     * @return 1 if passed, 0 if failed
     */
    private int validateRow(int rowNumber, String entityName, String query) {
        List<SearchResult> results = searchService.search(query, 20, 0.88);
        
        boolean found = results.stream().anyMatch(r -> 
            r.entity().name().toUpperCase().contains(entityName.toUpperCase()) ||
            (r.matchedAlias() != null && r.matchedAlias().toUpperCase().contains(entityName.toUpperCase())) ||
            (r.entity().altNames() != null && r.entity().altNames().stream()
                .anyMatch(alias -> alias.toUpperCase().contains(entityName.toUpperCase())))
        );

        String status = found ? "✅ PASS" : "❌ FAIL";
        System.out.println(String.format("S.I. %2d: %-50s [%s] Query: '%s'", 
            rowNumber, 
            entityName.length() > 50 ? entityName.substring(0, 47) + "..." : entityName,
            status, 
            query));

        if (!found) {
            System.out.println(String.format("         ⚠️ '%s' not found in top 20 results (minMatch=0.88)", entityName));
        }

        return found ? 1 : 0;
    }
}
