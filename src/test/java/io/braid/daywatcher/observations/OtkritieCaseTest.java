package io.braid.daywatcher.observations;

import io.braid.daywatcher.search.SearchService;
import io.braid.daywatcher.model.SearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Row 52 (OTKRITIE) - Consultant View Verification Test
 * 
 * Consultant Issue: "Screening using OTKRITIE failed to list several matching records compared to OFAC list"
 * Expected entities to appear:
 * 1. OTKRITIE LTD GROUP (Entity ID 34499)
 * 2. OTKRITIE BANK (alias for Entity ID 34497)
 * 
 * Previous Analysis: Marked as ✅ RESOLVED with all 8 OTKRITIE entities returned
 * 
 * This test validates what the BSA consultant actually sees in top 20 results.
 */
@SpringBootTest
public class OtkritieCaseTest {

    @Autowired
    private SearchService searchService;

    @Test
    public void consultantViewOtkritie() {
        // Consultant searches for "OTKRITIE"
        List<SearchResult> results = searchService.search("OTKRITIE", 20, 0.88);

        System.out.println("\n=== OTKRITIE Search Results (Top 20) ===");
        System.out.println("Total results: " + results.size());
        
        // Track what the consultant expects to see
        boolean foundOtkritieBank = false;
        boolean foundOtkritieLtdGroup = false;
        
        for (int i = 0; i < Math.min(20, results.size()); i++) {
            SearchResult result = results.get(i);
            String entityName = result.entity().name();
            String matchedAlias = result.matchedAlias();
            
            System.out.printf("%d. [%.2f%%] %s%n", 
                    i + 1,
                    result.score() * 100,
                    entityName);
            
            if (matchedAlias != null && !matchedAlias.equals(entityName)) {
                System.out.printf("   └─ via alias: %s%n", matchedAlias);
            }
            
            // Check if this is one of the entities the consultant expects
            // Note: Check all aliases, not just matchedAlias, because consultant can see
            // all aliases in the entity details
            boolean hasOtkritieBank = entityName.contains("OTKRITIE BANK") || 
                    (matchedAlias != null && matchedAlias.contains("OTKRITIE BANK")) ||
                    result.entity().altNames().stream()
                            .anyMatch(alias -> alias.contains("OTKRITIE BANK"));
            
            if (hasOtkritieBank) {
                foundOtkritieBank = true;
                System.out.println("   ✅ Found OTKRITIE BANK");
            }
            
            if (entityName.equals("OTKRITIE LTD GROUP")) {
                foundOtkritieLtdGroup = true;
                System.out.println("   ✅ Found OTKRITIE LTD GROUP");
            }
            
            // List all aliases containing OTKRITIE for debugging
            List<String> otkritiAliases = result.entity().altNames().stream()
                    .filter(alias -> alias.toUpperCase().contains("OTKRITIE"))
                    .toList();
            if (!otkritiAliases.isEmpty() && otkritiAliases.size() > 1) {
                System.out.println("   Other OTKRITIE aliases: " + otkritiAliases);
            }
        }

        System.out.println("\n=== Consultant Expectations ===");
        System.out.println("OTKRITIE BANK visible: " + (foundOtkritieBank ? "✅ YES" : "❌ NO"));
        System.out.println("OTKRITIE LTD GROUP visible: " + (foundOtkritieLtdGroup ? "✅ YES" : "❌ NO"));

        // Assertions based on consultant's expectations
        assertTrue(foundOtkritieBank, 
                "Consultant expects to see OTKRITIE BANK in results");
        assertTrue(foundOtkritieLtdGroup, 
                "Consultant expects to see OTKRITIE LTD GROUP in results");
        
        // Both entities should be in top 20
        assertTrue(results.size() >= 2, 
                "Should return at least 2 OTKRITIE entities");
    }

    @Test
    public void detailedOtkritieEntityCoverage() {
        // Extended search to see all OTKRITIE entities
        List<SearchResult> results = searchService.search("OTKRITIE", 50, 0.70);

        System.out.println("\n=== All OTKRITIE Entities (Extended Search) ===");
        System.out.println("Threshold: 70%, Limit: 50");
        
        int otkritiCount = 0;
        for (SearchResult result : results) {
            String entityName = result.entity().name();
            String matchedAlias = result.matchedAlias();
            
            // Only show entities with OTKRITIE in name or matched alias
            if (entityName.contains("OTKRITIE") || 
                (matchedAlias != null && matchedAlias.contains("OTKRITIE"))) {
                otkritiCount++;
                System.out.printf("%d. [%.2f%%] %s%n", 
                        otkritiCount,
                        result.score() * 100,
                        entityName);
                
                if (matchedAlias != null && !matchedAlias.equals(entityName)) {
                    System.out.printf("   └─ matched via: %s%n", matchedAlias);
                }
            }
        }
        
        System.out.println("\nTotal OTKRITIE entities found: " + otkritiCount);
        
        // According to OFAC data, there are at least 7 entities with OTKRITIE:
        // 34497, 34499, 34500, 34501, 34503, 34507, 34509
        assertTrue(otkritiCount >= 7, 
                "Should find at least 7 OTKRITIE entities in OFAC data");
    }

    @Test
    public void compareWithOFACData() {
        List<SearchResult> results = searchService.search("OOO OTKRITIE CAPITAL", 20, 0.88);

        System.out.println("\n=== Search: OOO OTKRITIE CAPITAL (Exact Entity Name) ===");
        
        boolean foundExactMatch = false;
        for (int i = 0; i < Math.min(10, results.size()); i++) {
            SearchResult result = results.get(i);
            System.out.printf("%d. [%.2f%%] %s%n", 
                    i + 1,
                    result.score() * 100,
                    result.entity().name());
            
            // Check for the specific entity or its alias
            if (result.entity().name().contains("OTKRITIE CAPITAL") ||
                result.entity().altNames().stream()
                        .anyMatch(alias -> alias.contains("OTKRITIE CAPITAL"))) {
                foundExactMatch = true;
                System.out.println("   ✅ Found OTKRITIE CAPITAL entity");
                System.out.println("   Aliases: " + result.entity().altNames());
            }
        }
        
        assertTrue(foundExactMatch, 
                "Should find OTKRITIE CAPITAL entity or its aliases");
    }
}
