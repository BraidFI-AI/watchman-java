package io.braid.daywatcher.observations;

import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class FinalVerificationTest {

    @Autowired
    private SearchService searchService;

    @Test
    void verifyAllSevenCasesPass() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("FINAL VERIFICATION: All 7 BSA Consultant Cases");
        System.out.println("=".repeat(80));
        
        int passCount = 0;
        int totalCases = 7;
        
        // Case 1: TALIBAN → TEHRIK-E TALIBAN
        List<SearchResult> taliban = searchService.search("TALIBAN", 10, 0.88);
        boolean case1 = taliban.stream().anyMatch(r -> r.entity().name().toUpperCase().contains("TEHRIK-E TALIBAN"));
        System.out.println("1. TALIBAN → TEHRIK-E TALIBAN: " + (case1 ? "✅ PASS" : "❌ FAIL"));
        if (case1) passCount++;
        
        // Case 2: CIMEX → CORPORACION CIMEX
        List<SearchResult> cimex = searchService.search("CIMEX", 10, 0.88);
        boolean case2 = cimex.stream().anyMatch(r -> r.entity().name().toUpperCase().contains("CORPORACION CIMEX"));
        System.out.println("2. CIMEX → CORPORACION CIMEX: " + (case2 ? "✅ PASS" : "❌ FAIL"));
        if (case2) passCount++;
        
        // Case 3: CIMEX → FINANCIERA CIMEX
        boolean case3 = cimex.stream().anyMatch(r -> r.entity().name().toUpperCase().contains("FINANCIERA CIMEX"));
        System.out.println("3. CIMEX → FINANCIERA CIMEX: " + (case3 ? "✅ PASS" : "❌ FAIL"));
        if (case3) passCount++;
        
        // Case 4: AL QA'IDA → ISLAMIC STATE (JIHAD IN IRAQ)
        List<SearchResult> alqaida = searchService.search("AL QA'IDA", 10, 0.88);
        boolean case4 = alqaida.stream().anyMatch(r -> {
            String name = r.entity().name();
            return name.equals("ISLAMIC STATE OF IRAQ AND THE LEVANT") || 
                   name.toUpperCase().matches(".*ISLAMIC STATE.*IRAQ.*");
        });
        System.out.println("4. AL QA'IDA → ISLAMIC STATE/IRAQ: " + (case4 ? "✅ PASS" : "❌ FAIL"));
        if (case4) passCount++;
        
        // Case 5: AL QA'IDA → SYRIA
        boolean case5 = alqaida.stream().anyMatch(r -> {
            String name = r.entity().name().toUpperCase();
            String matchedAlias = r.matchedAlias() != null ? r.matchedAlias().toUpperCase() : "";
            boolean anyAliasMatch = r.entity().altNames() != null && r.entity().altNames().stream()
                .anyMatch(alt -> alt.toUpperCase().contains("SYRIA"));
            return name.contains("SYRIA") || matchedAlias.contains("SYRIA") || anyAliasMatch;
        });
        System.out.println("5. AL QA'IDA → SYRIA: " + (case5 ? "✅ PASS" : "❌ FAIL"));
        if (case5) passCount++;
        
        // Case 6: OTKRITIE → OTKRITIE BANK
        List<SearchResult> otkritie = searchService.search("OTKRITIE", 10, 0.88);
        boolean case6 = otkritie.stream().anyMatch(r -> {
            String name = r.entity().name().toUpperCase();
            String matchedAlias = r.matchedAlias() != null ? r.matchedAlias().toUpperCase() : "";
            boolean anyAliasMatch = r.entity().altNames() != null && r.entity().altNames().stream()
                .anyMatch(alt -> alt.toUpperCase().contains("OTKRITIE BANK"));
            return name.contains("OTKRITIE BANK") || matchedAlias.contains("OTKRITIE BANK") || anyAliasMatch;
        });
        System.out.println("6. OTKRITIE → OTKRITIE BANK: " + (case6 ? "✅ PASS" : "❌ FAIL"));
        if (case6) passCount++;
        
        // Case 7: OTKRITIE → OTKRITIE LTD
        boolean case7 = otkritie.stream().anyMatch(r -> r.entity().name().toUpperCase().contains("OTKRITIE LTD"));
        System.out.println("7. OTKRITIE → OTKRITIE LTD: " + (case7 ? "✅ PASS" : "❌ FAIL"));
        if (case7) passCount++;
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("RESULT: " + passCount + "/" + totalCases + " cases passing");
        System.out.println("=".repeat(80));
        
        // Verify precision: Randy San Nicolas should return 0 results
        System.out.println("\nPRECISION CHECK:");
        List<SearchResult> randy = searchService.search("Randy San Nicolas", 50, 0.88);
        System.out.println("Randy San Nicolas results: " + randy.size() + " (expected: 0)");
        
        System.out.println("\n" + "=".repeat(80));
        if (passCount == totalCases && randy.size() == 0) {
System.out.println("🎉 SUCCESS: All 7 cases pass + precision maintained!");
        } else {
            System.out.println("⚠️  Some cases failed or precision issue detected");
        }
        System.out.println("=".repeat(80));
        
        // Assertions
        assertEquals(totalCases, passCount, "All 7 consultant cases should pass");
        assertEquals(0, randy.size(), "No false positives for Randy San Nicolas");
    }
}
