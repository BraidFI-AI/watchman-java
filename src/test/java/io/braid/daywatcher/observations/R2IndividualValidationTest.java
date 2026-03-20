package io.braid.daywatcher.observations;

import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.search.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * R2 Individual Validation - BSA Consultant Round 2 Retest
 * 
 * TDD RED Phase: Validates all 50 individual rows from r2-ind.csv
 * Source: observations/watchman java obsevations master.xlsx - r2-ind.csv
 * 
 * Test Parameters: limit=20, minMatch=0.88 (standard BSA consultant parameters)
 * 
 * Known Issues:
 * - Row 26 (GRIGORYEV): "GRIGOREV" without apostrophe doesn't match - marked "Retest"
 */
@SpringBootTest
@DisplayName("R2 Individual Validation - BSA Consultant Retest (50 rows)")
class R2IndividualValidationTest {

    @Autowired
    private SearchService searchService;

    @Test
    @DisplayName("R2 Individual - All 50 Rows Validation Suite")
    void validateAll50IndividualRows() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("R2 INDIVIDUAL VALIDATION - ALL 50 ROWS");
        System.out.println("Source: r2-ind.csv (BSA Consultant Round 2 Retest)");
        System.out.println("Parameters: limit=20, minMatch=0.88");
        System.out.println("=".repeat(100));

        int passCount = 0;
        int failCount = 0;
        int totalTests = 50;

        // S.I. 1: NAFRIEH, Edman
        passCount += validateRow(1, "NAFRIEH, Edman", "Edman Nafrieh");

        // S.I. 2: MUJYAMBERE, Leopold
        passCount += validateRow(2, "MUJYAMBERE, Leopold", "Leopold Mujyambere");

        // S.I. 3: HALILI, Nevzat
        passCount += validateRow(3, "HALILI, Nevzat", "Nevzat Halili");

        // S.I. 4: KAYA, Mehmet
        passCount += validateRow(4, "KAYA, Mehmet", "Mehmet Kaya");

        // S.I. 5: LUGO LEON, Toribio Alberto
        passCount += validateRow(5, "LUGO LEON, Toribio Alberto", "Toribio Alberto Lugo Leon");

        // S.I. 6: KHAN, Hafiz Saeed
        passCount += validateRow(6, "KHAN, Hafiz Saeed", "Hafiz Saeed Khan");

        // S.I. 7: ARIF, Said
        passCount += validateRow(7, "ARIF, Said", "Said Arif");

        // S.I. 8: LLANOS GAZIA, Jorge Luis
        passCount += validateRow(8, "LLANOS GAZIA, Jorge Luis", "Jorge Luis Llanos Gazia");

        // S.I. 9: ZAKHAROV, Nikita Aleksandrovich
        passCount += validateRow(9, "ZAKHAROV, Nikita Aleksandrovich", "Nikita Aleksandrovich Zakharov");

        // S.I. 10: FITCH PARENTE, Pablo Antonio
        passCount += validateRow(10, "FITCH PARENTE, Pablo Antonio", "Pablo Antonio Fitch Parente");

        // S.I. 11: BAKHSHI, Dariush
        passCount += validateRow(11, "BAKHSHI, Dariush", "Dariush Bakhshi");

        // S.I. 12: AL-AJOURI, Akram
        passCount += validateRow(12, "AL-AJOURI, Akram", "Akram Al-Ajouri");

        // S.I. 13: MALPICA HURTADO, Erica Patricia
        passCount += validateRow(13, "MALPICA HURTADO, Erica Patricia", "Erica Patricia Malpica Hurtado");

        // S.I. 14: NIKOLAEV, Nikolay Petrovich
        passCount += validateRow(14, "NIKOLAEV, Nikolay Petrovich", "Nikolay Petrovich Nikolaev");

        // S.I. 15: OZIA MAZIO, Dieudonne
        passCount += validateRow(15, "OZIA MAZIO, Dieudonne", "Dieudonne Ozia Mazio");

        // S.I. 16: VLASOVA, Veronika Valerievna
        passCount += validateRow(16, "VLASOVA, Veronika Valerievna", "Veronika Valerievna Vlasova");

        // S.I. 17: AZIZABADI, Reza Mohammadi
        passCount += validateRow(17, "AZIZABADI, Reza Mohammadi", "Reza Mohammadi Azizabadi");

        // S.I. 18: PAVKOVIC, Nebojsa
        passCount += validateRow(18, "PAVKOVIC, Nebojsa", "Nebojsa Pavkovic");

        // S.I. 19: MILOSEVIC, Marija
        passCount += validateRow(19, "MILOSEVIC, Marija", "Marija Milosevic");

        // S.I. 20: MICHIELSEN, Tom
        passCount += validateRow(20, "MICHIELSEN, Tom", "Tom Michielsen");

        // S.I. 21: RAMIREZ GARCIA, Freyner Alfonso
        passCount += validateRow(21, "RAMIREZ GARCIA, Freyner Alfonso", "Freyner Alfonso Ramirez Garcia");

        // S.I. 22: IVAKIN, Yuriy Vladimirovich
        passCount += validateRow(22, "IVAKIN, Yuriy Vladimirovich", "Yuriy Vladimirovich Ivakin");

        // S.I. 23: KOLOMEITSEV, Nikolay Vasilievich
        passCount += validateRow(23, "KOLOMEITSEV, Nikolay Vasilievich", "Nikolay Vasilievich Kolomeitsev");

        // S.I. 24: ABDALLAH, Ahmad Jalal Reda
        passCount += validateRow(24, "ABDALLAH, Ahmad Jalal Reda", "Ahmad Jalal Reda Abdallah");

        // S.I. 25: KARADZIC-JOVICEVIC, Sonja
        passCount += validateRow(25, "KARADZIC-JOVICEVIC, Sonja", "Sonja Karadzic-Jovicevic");

        // S.I. 26: GRIGORYEV, Andrey Ivanovich
        // Known issue: "GRIGOREV" (without apostrophe) doesn't match
        passCount += validateRow(26, "GRIGORYEV, Andrey Ivanovich", "Andrey Ivanovich Grigoryev");

        // S.I. 27: SUGAIPOV, Umar
        passCount += validateRow(27, "SUGAIPOV, Umar", "Umar Sugaipov");

        // S.I. 28: KHUCHIEV, Muslim Magomedovich
        passCount += validateRow(28, "KHUCHIEV, Muslim Magomedovich", "Muslim Magomedovich Khuchiev");

        // S.I. 29: MOOR, Aleksandr Viktorovich
        passCount += validateRow(29, "MOOR, Aleksandr Viktorovich", "Aleksandr Viktorovich Moor");

        // S.I. 30: MAKOLOV, Yury Yuryevich
        passCount += validateRow(30, "MAKOLOV, Yury Yuryevich", "Yury Yuryevich Makolov");

        // S.I. 31: LI, Kai Shou
        passCount += validateRow(31, "LI, Kai Shou", "Kai Shou Li");

        // S.I. 32: BABAIE, Nematollah Hosein
        passCount += validateRow(32, "BABAIE, Nematollah Hosein", "Nematollah Hosein Babaie");

        // S.I. 33: GASTELUM SERRANO, Cesar
        passCount += validateRow(33, "GASTELUM SERRANO, Cesar", "Cesar Gastelum Serrano");

        // S.I. 34: SOE, Aung
        passCount += validateRow(34, "SOE, Aung", "Aung Soe");

        // S.I. 35: RAMOS ACOSTA, Alvaro
        passCount += validateRow(35, "RAMOS ACOSTA, Alvaro", "Alvaro Ramos Acosta");

        // S.I. 36: BOSHAB, Evariste
        passCount += validateRow(36, "BOSHAB, Evariste", "Evariste Boshab");

        // S.I. 37: RODRIGUEZ RODRIGUEZ, Caryslia Beatriz
        passCount += validateRow(37, "RODRIGUEZ RODRIGUEZ, Caryslia Beatriz", "Caryslia Beatriz Rodriguez Rodriguez");

        // S.I. 38: KIM, Se Un
        passCount += validateRow(38, "KIM, Se Un", "Se Un Kim");

        // S.I. 39: AHMAD, Tariq Anwar al-Sayyid
        passCount += validateRow(39, "AHMAD, Tariq Anwar al-Sayyid", "Tariq Anwar Al-Sayyid Ahmad");

        // S.I. 40: AWALE, Mohamed Jumale Ali
        passCount += validateRow(40, "AWALE, Mohamed Jumale Ali", "Mohamed Jumale Ali Awale");

        // S.I. 41: CHERBOV, Gleb Sergeyevich
        passCount += validateRow(41, "CHERBOV, Gleb Sergeyevich", "Gleb Sergeyevich Cherbov");

        // S.I. 42: AKBULUT, Cerkez
        passCount += validateRow(42, "AKBULUT, Cerkez", "Cerkez Akbulut");

        // S.I. 43: LI, Fangwei
        passCount += validateRow(43, "LI, Fangwei", "Fangwei Li");

        // S.I. 44: GASHASBI, Mansoor
        passCount += validateRow(44, "GASHASBI, Mansoor", "Mansoor Gashasbi");

        // S.I. 45: TSUDA, Chikara
        passCount += validateRow(45, "TSUDA, Chikara", "Chikara Tsuda");

        // S.I. 46: JUBAYR AL-RAWI, Fawaz Muhammad
        passCount += validateRow(46, "JUBAYR AL-RAWI, Fawaz Muhammad", "Fawaz Muhammad Jubayr Al-Rawi");

        // S.I. 47: IQBAL, Zafar
        passCount += validateRow(47, "IQBAL, Zafar", "Zafar Iqbal");

        // S.I. 48: CHUOL, James Koang
        passCount += validateRow(48, "CHUOL, James Koang", "James Koang Chuol");

        // S.I. 49: KIM, Tong-Myo'ng
        passCount += validateRow(49, "KIM, Tong-Myo'ng", "Tong-Myo'Ng Kim");

        // S.I. 50: AL-SAYYID, Ibrahim Amin
        passCount += validateRow(50, "AL-SAYYID, Ibrahim Amin", "Ibrahim Amin Al-Sayyid");

        failCount = totalTests - passCount;

        System.out.println("\n" + "=".repeat(100));
        System.out.println(String.format("FINAL RESULTS: %d/%d PASSING (%.1f%%)", 
            passCount, totalTests, (passCount * 100.0) / totalTests));
        System.out.println(String.format("  ✅ PASS: %d", passCount));
        System.out.println(String.format("  ❌ FAIL: %d", failCount));
        System.out.println("=".repeat(100));

        if (passCount == totalTests) {
            System.out.println("🎉 SUCCESS: ALL 50 R2 INDIVIDUAL OBSERVATIONS VALIDATED!");
        } else {
            System.out.println("⚠️  Some rows need attention - review failures above");
        }
    }

    /**
     * Validate a single individual row from r2-ind.csv
     * 
     * @param rowNumber S.I. number from CSV
     * @param individualName Expected individual name to match
     * @param query Search query to use (natural name order)
     * @return 1 if passed, 0 if failed
     */
    private int validateRow(int rowNumber, String individualName, String query) {
        List<SearchResult> results = searchService.search(query, 20, 0.88);
        
        boolean found = results.stream().anyMatch(r -> 
            r.entity().name().toUpperCase().contains(individualName.toUpperCase()) ||
            (r.matchedAlias() != null && r.matchedAlias().toUpperCase().contains(individualName.toUpperCase())) ||
            (r.entity().altNames() != null && r.entity().altNames().stream()
                .anyMatch(alias -> alias.toUpperCase().contains(individualName.toUpperCase())))
        );

        String status = found ? "✅ PASS" : "❌ FAIL";
        System.out.println(String.format("S.I. %2d: %-40s [%s] Query: '%s'", 
            rowNumber, 
            individualName.length() > 40 ? individualName.substring(0, 37) + "..." : individualName,
            status, 
            query));

        if (!found) {
            System.out.println(String.format("         ⚠️ '%s' not found in top 20 results (minMatch=0.88)", individualName));
        }

        return found ? 1 : 0;
    }
}
