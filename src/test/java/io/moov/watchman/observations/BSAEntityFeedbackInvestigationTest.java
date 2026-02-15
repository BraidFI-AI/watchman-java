package io.moov.watchman.observations;

import io.moov.watchman.search.SearchService;
import io.moov.watchman.model.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Test to investigate why BSA consultant says certain entities are "not listed in Watchman"
 * when searching for parent entities.
 */
@SpringBootTest
@DisplayName("BSA Entity Feedback Investigation")
class BSAEntityFeedbackInvestigationTest {

    @Autowired
    private SearchService searchService;

    @Test
    @DisplayName("Row 22: Search 'TALIBAN' and check if TEHRIK-E TALIBAN PAKISTAN appears")
    void searchTalibanCheckForTehrikE() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("BSA Row 22: TALIBAN Search Investigation");
        System.out.println("=".repeat(80));
        
        System.out.println("\nConsultant says: 'TEHREEK-E-TALIBAN or its main entity TEHRIK-E TALIBAN PAKISTAN (TTP) is not getting listed in Watchman.'");
        System.out.println("\nTesting search: 'TALIBAN'");
        
        List<SearchResult> results = searchService.search("TALIBAN", 20, 0.70);
        
        System.out.println("\nResults: " + results.size() + " entities found");
        System.out.println("-".repeat(80));
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            System.out.printf("%2d. [%.1f%%] %s (ID: %s)%n", 
                i + 1,
                result.score() * 100,
                result.entity().name(),
                result.entity().sourceId()
            );
            
            if (result.matchedAlias() != null && !result.matchedAlias().isEmpty()) {
                System.out.println("    Matched Alias: " + result.matchedAlias());
            }
        }
        
        // Check if TEHRIK-E TALIBAN PAKISTAN is in the results
        boolean foundTehrikE = results.stream()
            .anyMatch(r -> {
                String name = r.entity().name().toUpperCase();
                String alias = r.matchedAlias() != null ? r.matchedAlias().toUpperCase() : "";
                return name.contains("TEHRIK-E TALIBAN") || name.contains("TEHREEK-E-TALIBAN") ||
                       alias.contains("TEHRIK-E TALIBAN") || alias.contains("TEHREEK-E-TALIBAN");
            });
        
        System.out.println("\n" + "-".repeat(80));
        System.out.println("TEHRIK-E TALIBAN PAKISTAN found in results: " + foundTehrikE);
        
        if (!foundTehrikE) {
            System.out.println("\n⚠️  Consultant is correct - TEHRIK-E TALIBAN PAKISTAN not returned");
            
            // Try direct search for "TEHRIK-E TALIBAN PAKISTAN"
            System.out.println("\nTrying direct search: 'TEHRIK-E TALIBAN PAKISTAN'");
            List<SearchResult> directResults = searchService.search("TEHRIK-E TALIBAN PAKISTAN", 5, 0.70);
            System.out.println("Direct search results: " + directResults.size());
            directResults.forEach(r -> System.out.println("  - " + r.entity().name() + " (" + (r.score() * 100) + "%)"));
            
            if (directResults.isEmpty()) {
                System.out.println("\n❌ CRITICAL: Entity does not exist in database OR is not being matched");
            }
        }
    }

    @Test
    @DisplayName("Row 21: Search 'AL QA'IDA' and check for related entities")
    void searchAlQaidaCheckForRelated() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("BSA Row 21: AL QA'IDA Search Investigation");
        System.out.println("=".repeat(80));
        
        System.out.println("\nConsultant says: 'There are more results in OFAC like AL-QAIDA GROUP OF JIHAD IN IRAQ; AL-QAIDA IN SYRIA'");
        System.out.println("\nTesting search: 'AL QA'IDA'");
        
        List<SearchResult> results = searchService.search("AL QA'IDA", 20, 0.70);
        
        System.out.println("\nResults: " + results.size() + " entities found");
        System.out.println("-".repeat(80));
        
        for (int i = 0; i < Math.min(results.size(), 20); i++) {
            SearchResult result = results.get(i);
            System.out.printf("%2d. [%.1f%%] %s (ID: %s)%n", 
                i + 1,
                result.score() * 100,
                result.entity().name(),
                result.entity().sourceId()
            );
        }
        
        // Check for specific mentions
        boolean hasJihadInIraq = results.stream()
            .anyMatch(r -> {
                String name = r.entity().name().toUpperCase();
                String alias = r.matchedAlias() != null ? r.matchedAlias().toUpperCase() : "";
                return name.contains("JIHAD IN IRAQ") || alias.contains("JIHAD IN IRAQ");
            });
        
        boolean hasInSyria = results.stream()
            .anyMatch(r -> {
                String name = r.entity().name().toUpperCase();
                String alias = r.matchedAlias() != null ? r.matchedAlias().toUpperCase() : "";
                return name.contains("IN SYRIA") || alias.contains("IN SYRIA");
            });
        
        System.out.println("\n" + "-".repeat(80));
        System.out.println("'JIHAD IN IRAQ' found: " + hasJihadInIraq);
        System.out.println("'IN SYRIA' found: " + hasInSyria);
        
        if (!hasJihadInIraq || !hasInSyria) {
            System.out.println("\n⚠️  Consultant is correct - some AL-QAIDA related entities not returned");
        }
    }

    @Test
    @DisplayName("Row 6: Search 'CIMEX' and check for CORPORACION/FINANCIERA")
    void searchCimexCheckForCorporacion() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("BSA Row 6: CIMEX Search Investigation");
        System.out.println("=".repeat(80));
        
        System.out.println("\nConsultant says: 'There are more results in OFAC like CORPORACION CIMEX S.A. (2 distinct SDN hits), FINANCIERA CIMEX S.A'");
        System.out.println("\nTesting search: 'CIMEX'");
        
        List<SearchResult> results = searchService.search("CIMEX", 20, 0.70);
        
        System.out.println("\nResults: " + results.size() + " entities found");
        System.out.println("-".repeat(80));
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            System.out.printf("%2d. [%.1f%%] %s (ID: %s)%n", 
                i + 1,
                result.score() * 100,
                result.entity().name(),
                result.entity().sourceId()
            );
        }
        
        // Check for specific entities
        boolean hasCorporacion = results.stream()
            .anyMatch(r -> r.entity().name().toUpperCase().contains("CORPORACION CIMEX"));
        
        boolean hasFinanciera = results.stream()
            .anyMatch(r -> r.entity().name().toUpperCase().contains("FINANCIERA CIMEX"));
        
        System.out.println("\n" + "-".repeat(80));
        System.out.println("'CORPORACION CIMEX' found: " + hasCorporacion);
        System.out.println("'FINANCIERA CIMEX' found: " + hasFinanciera);
        
        if (!hasCorporacion || !hasFinanciera) {
            System.out.println("\n⚠️  Consultant is correct - some CIMEX entities not returned");
        }
    }
}
