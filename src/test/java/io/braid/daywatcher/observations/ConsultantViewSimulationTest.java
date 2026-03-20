package io.braid.daywatcher.observations;

import io.braid.daywatcher.search.SearchService;
import io.braid.daywatcher.model.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Simulate what the BSA consultant actually SEES when testing.
 * 
 * Key insight: If results appear at position 15 but consultant uses limit=10,
 * they don't see them. From their perspective, entities are "not listed".
 * 
 * We need to test with:
 * - Default API parameters (limit=10, minMatch=0.88)
 * - Admin UI likely parameters
 * - OFAC.gov comparable view (top 10-20 results)
 */
@SpringBootTest
@DisplayName("Consultant View Simulation - What They Actually See")
class ConsultantViewSimulationTest {

    @Autowired
    private SearchService searchService;

    @Test
    @DisplayName("Row 22: Does TEHRIK-E TALIBAN PAKISTAN appear in TOP 10 results for 'TALIBAN'?")
    void consultantViewTaliban() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("CONSULTANT VIEW: TALIBAN Search (limit=10, minMatch=0.88)");
        System.out.println("=".repeat(80));
        
        // Default API parameters
        List<SearchResult> results = searchService.search("TALIBAN", 10, 0.88);
        
        System.out.println("\nTop 10 Results (what consultant sees):");
        System.out.println("-".repeat(80));
        
        boolean foundTehrikE = false;
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String name = r.entity().name();
            System.out.printf("%2d. [%.1f%%] %s%n", i + 1, r.score() * 100, name);
            
            if (name.toUpperCase().contains("TEHRIK-E TALIBAN") || 
                name.toUpperCase().contains("TEHREEK-E-TALIBAN")) {
                foundTehrikE = true;
                System.out.println("    ✅ CONSULTANT CAN SEE THIS");
            }
        }
        
        System.out.println("\n" + "=".repeat(80));
        if (!foundTehrikE) {
            System.out.println("❌ TEHRIK-E TALIBAN PAKISTAN NOT VISIBLE IN TOP 10");
            System.out.println("   Consultant is correct - it's not in their view");
            
            // Check where it actually appears
            List<SearchResult> moreResults = searchService.search("TALIBAN", 50, 0.88);
            for (int i = 0; i < moreResults.size(); i++) {
                if (moreResults.get(i).entity().name().toUpperCase().contains("TEHRIK-E TALIBAN")) {
                    System.out.println("   Actually appears at position: " + (i + 1));
                    System.out.println("   Score: " + String.format("%.1f%%", moreResults.get(i).score() * 100));
                    break;
                }
            }
        } else {
            System.out.println("✅ VISIBLE - Consultant should see this");
        }
    }

    @Test
    @DisplayName("Row 6: Does CORPORACION/FINANCIERA CIMEX appear in TOP 10 for 'CIMEX'?")
    void consultantViewCimex() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("CONSULTANT VIEW: CIMEX Search (limit=10, minMatch=0.88)");
        System.out.println("=".repeat(80));
        
        List<SearchResult> results = searchService.search("CIMEX", 10, 0.88);
        
        System.out.println("\nTop 10 Results (what consultant sees):");
        System.out.println("-".repeat(80));
        
        int corporacionCount = 0;
        int financieraCount = 0;
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String name = r.entity().name();
            System.out.printf("%2d. [%.1f%%] %s%n", i + 1, r.score() * 100, name);
            
            if (name.toUpperCase().contains("CORPORACION CIMEX")) {
                corporacionCount++;
                System.out.println("    ✅ CORPORACION CIMEX visible");
            }
            if (name.toUpperCase().contains("FINANCIERA CIMEX")) {
                financieraCount++;
                System.out.println("    ✅ FINANCIERA CIMEX visible");
            }
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("CORPORACION CIMEX in top 10: " + corporacionCount);
        System.out.println("FINANCIERA CIMEX in top 10: " + financieraCount);
        
        if (corporacionCount == 0 || financieraCount == 0) {
            System.out.println("\n❌ CONSULTANT IS CORRECT - Missing from top 10 view");
            
            // Check positions
            List<SearchResult> moreResults = searchService.search("CIMEX", 50, 0.88);
            System.out.println("\nActual positions:");
            for (int i = 0; i < moreResults.size(); i++) {
                String name = moreResults.get(i).entity().name().toUpperCase();
                if (name.contains("CORPORACION CIMEX") || name.contains("FINANCIERA CIMEX")) {
                    System.out.printf("   Position %2d: %s (%.1f%%)%n", 
                        i + 1, 
                        moreResults.get(i).entity().name(),
                        moreResults.get(i).score() * 100);
                }
            }
        }
    }

    @Test
    @DisplayName("Row 21: Does AL-QAIDA JIHAD IN IRAQ appear in TOP 10 for 'AL QA'IDA'?")
    void consultantViewAlQaida() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("CONSULTANT VIEW: AL QA'IDA Search (limit=10, minMatch=0.88)");
        System.out.println("=".repeat(80));
        
        List<SearchResult> results = searchService.search("AL QA'IDA", 10, 0.88);
        
        System.out.println("\nTop 10 Results (what consultant sees):");
        System.out.println("-".repeat(80));
        
        boolean foundJihad = false;
        boolean foundSyria = false;
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String name = r.entity().name();
            String alias = r.matchedAlias() != null ? r.matchedAlias() : "";
            
            System.out.printf("%2d. [%.1f%%] %s%n", i + 1, r.score() * 100, name);
            if (!alias.isEmpty()) {
                System.out.println("    Alias: " + alias);
            }
            
            // Check for JIHAD IN IRAQ related entities
            // This includes:
            // 1. Entities with name/alias containing both "JIHAD" and "IRAQ"
            // 2. ISLAMIC STATE OF IRAQ (ISIS/ISIL) which has alias "AL-QAIDA GROUP OF JIHAD IN IRAQ"
            if ((name.toUpperCase().contains("JIHAD") && name.toUpperCase().contains("IRAQ")) ||
                (alias.toUpperCase().contains("JIHAD") && alias.toUpperCase().contains("IRAQ")) ||
                (name.equals("ISLAMIC STATE OF IRAQ AND THE LEVANT"))) {
                foundJihad = true;
                System.out.println("    ✅ JIHAD IN IRAQ visible");
            }
            if (name.toUpperCase().contains("SYRIA") || alias.toUpperCase().contains("SYRIA")) {
                foundSyria = true;
                System.out.println("    ✅ SYRIA visible");
            }
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("'JIHAD IN IRAQ' in top 10: " + foundJihad);
        System.out.println("'SYRIA' in top 10: " + foundSyria);
        
        if (!foundJihad || !foundSyria) {
            System.out.println("\n❌ CONSULTANT IS CORRECT - Missing from results");
            System.out.println("   These entities likely score below 88% threshold");
        }
    }

    @Test
    @DisplayName("Row 52: Does OTKRITIE BANK/LTD appear in TOP 10 for 'OTKRITIE'?")
    void consultantViewOtkritie() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("CONSULTANT VIEW: OTKRITIE Search (limit=10, minMatch=0.88)");
        System.out.println("=".repeat(80));
        
        List<SearchResult> results = searchService.search("OTKRITIE", 10, 0.88);
        
        System.out.println("\nTop 10 Results (what consultant sees):");
        System.out.println("-".repeat(80));
        
        boolean foundBank = false;
        boolean foundLtd = false;
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String name = r.entity().name();
            String matchedAlias = r.matchedAlias() != null ? r.matchedAlias() : "";
            System.out.printf("%2d. [%.1f%%] %s%n", i + 1, r.score() * 100, name);
            
            // Check if this is OTKRITIE BANK (primary name or alias)
            if (name.toUpperCase().contains("OTKRITIE BANK") ||
                name.equals("PUBLIC JOINT STOCK COMPANY BANK FINANCIAL CORPORATION OTKRITIE") ||
                matchedAlias.toUpperCase().contains("OTKRITIE BANK")) {
                foundBank = true;
                if (matchedAlias.toUpperCase().contains("OTKRITIE BANK")) {
                    System.out.println("    Alias: " + matchedAlias);
                }
                System.out.println("    ✅ OTKRITIE BANK visible");
            }
            if (name.toUpperCase().contains("OTKRITIE LTD") ||
                matchedAlias.toUpperCase().contains("OTKRITIE LTD")) {
                foundLtd = true;
                System.out.println("    ✅ OTKRITIE LTD visible");
            }
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("'OTKRITIE BANK' in top 10: " + foundBank);
        System.out.println("'OTKRITIE LTD' in top 10: " + foundLtd);
        
        if (!foundBank && !foundLtd) {
            System.out.println("\n⚠️  Neither BANK nor LTD visible in top 10");
        }
    }

    @Test
    @DisplayName("Summary: All 5 failing cases with TOP 10 view")
    void consultantViewSummary() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("SUMMARY: What Consultant Sees (Top 10, minMatch=0.88)");
        System.out.println("=".repeat(80));
        
        String[][] testCases = {
            {"TALIBAN", "TEHRIK-E TALIBAN"},
            {"CIMEX", "CORPORACION CIMEX"},
            {"CIMEX", "FINANCIERA CIMEX"},
            {"AL QA'IDA", "ISLAMIC STATE.*IRAQ"},  // ISIS/ISIL has alias "AL-QAIDA GROUP OF JIHAD IN IRAQ"
            {"AL QA'IDA", "SYRIA"},
            {"OTKRITIE", "OTKRITIE BANK"},
            {"OTKRITIE", "OTKRITIE LTD"}
        };
        
        System.out.println();
        for (String[] testCase : testCases) {
            String query = testCase[0];
            String expectedEntity = testCase[1];
            
            List<SearchResult> results = searchService.search(query, 10, 0.88);
            
            boolean found = results.stream()
                .anyMatch(r -> {
                    String name = r.entity().name().toUpperCase();
                    String matchedAlias = r.matchedAlias() != null ? r.matchedAlias().toUpperCase() : "";
                    boolean nameMatch = name.matches(".*" + expectedEntity.toUpperCase() + ".*");
                    boolean matchedAliasMatch = matchedAlias.matches(".*" + expectedEntity.toUpperCase() + ".*");
                    
                    // Also check all aliases (not just matchedAlias) for entities that matched on primary name
                    boolean anyAliasMatch = r.entity().altNames() != null && r.entity().altNames().stream()
                        .anyMatch(alt -> alt.toUpperCase().matches(".*" + expectedEntity.toUpperCase() + ".*"));
                    
                    return nameMatch || matchedAliasMatch || anyAliasMatch;
                });
            
            String status = found ? "✅ VISIBLE" : "❌ MISSING";
            System.out.printf("%-15s | %-25s | %s%n", query, expectedEntity, status);
        }
    }
}
