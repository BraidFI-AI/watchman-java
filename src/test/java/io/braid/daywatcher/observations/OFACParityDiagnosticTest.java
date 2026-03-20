package io.braid.daywatcher.observations;

import io.braid.daywatcher.index.EntityIndex;
import io.braid.daywatcher.model.Entity;
import io.braid.daywatcher.search.EntityScorer;
import io.braid.daywatcher.search.SearchService;
import io.braid.daywatcher.model.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Diagnostic test to understand why certain entities don't appear in search results
 * when they appear on OFAC.gov for the same query.
 * 
 * REGULATORY CONTEXT:
 * Auditors and BSA consultants compare Watchman results to OFAC.gov as the authoritative source.
 * Any discrepancy is viewed as a compliance weakness, regardless of technical reasoning.
 * 
 * GOAL:
 * Achieve parity with OFAC.gov search behavior.
 */
@SpringBootTest
@DisplayName("OFAC Parity Diagnostic")
class OFACParityDiagnosticTest {

    @Autowired
    private EntityIndex entityIndex;
    
    @Autowired
    private EntityScorer entityScorer;
    
    @Autowired
    private SearchService searchService;

    @Test
    @DisplayName("Row 22: Why doesn't 'TALIBAN' return 'TEHRIK-E TALIBAN PAKISTAN'?")
    void diagnoseTalibanTehrikE() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("DIAGNOSTIC: TALIBAN → TEHRIK-E TALIBAN PAKISTAN");
        System.out.println("=".repeat(80));
        
        // Find TEHRIK-E TALIBAN PAKISTAN in the index
        Entity tehrikE = entityIndex.getAll().stream()
            .filter(e -> e.name().toUpperCase().contains("TEHRIK-E TALIBAN PAKISTAN"))
            .findFirst()
            .orElse(null);
        
        if (tehrikE == null) {
            System.out.println("\n❌ CRITICAL: 'TEHRIK-E TALIBAN PAKISTAN' NOT FOUND IN INDEX");
            System.out.println("This entity must be ingested from OFAC data.");
            return;
        }
        
        System.out.println("\n✅ Entity found in index:");
        System.out.println("   ID: " + tehrikE.sourceId());
        System.out.println("   Name: " + tehrikE.name());
        System.out.println("   Aliases: " + tehrikE.altNames().stream().limit(3).collect(Collectors.joining(", ")));
        
        // Score it against "TALIBAN" query
        double score = entityScorer.score("TALIBAN", tehrikE);
        
        System.out.println("\n📊 Score against 'TALIBAN' query: " + String.format("%.2f%%", score * 100));
        System.out.println("   Default minMatch threshold: 88%");
        
        if (score < 0.88) {
            System.out.println("\n⚠️  ISSUE: Score below threshold - entity filtered out");
            System.out.println("   Entity contains 'TALIBAN' token but overall similarity too low");
        }
        
        // Check if it appears in actual search results
        List<SearchResult> results = searchService.search("TALIBAN", 20, 0.70);
        boolean inResults = results.stream()
            .anyMatch(r -> r.entity().sourceId().equals(tehrikE.sourceId()));
        
        System.out.println("\n🔍 Appears in search('TALIBAN', limit=20, minMatch=0.70): " + inResults);
        
        if (!inResults) {
            // Try with lower threshold
            List<SearchResult> lowerResults = searchService.search("TALIBAN", 50, 0.50);
            boolean inLowerResults = lowerResults.stream()
                .anyMatch(r -> r.entity().sourceId().equals(tehrikE.sourceId()));
            System.out.println("   Appears with minMatch=0.50, limit=50: " + inLowerResults);
        }
        
        System.out.println("\n💡 RECOMMENDATION:");
        System.out.println("   Implement substring token matching: if query token appears in entity name,");
        System.out.println("   ensure entity scores above threshold or apply score boost.");
    }

    @Test
    @DisplayName("Row 6: Why doesn't 'CIMEX' return 'CORPORACION CIMEX S.A.'?")
    void diagnoseCimexCorporacion() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("DIAGNOSTIC: CIMEX → CORPORACION CIMEX S.A.");
        System.out.println("=".repeat(80));
        
        // Find CORPORACION CIMEX entities
        List<Entity> corporacionEntities = entityIndex.getAll().stream()
            .filter(e -> e.name().toUpperCase().contains("CORPORACION CIMEX"))
            .collect(Collectors.toList());
        
        if (corporacionEntities.isEmpty()) {
            System.out.println("\n❌ CRITICAL: 'CORPORACION CIMEX' entities NOT FOUND IN INDEX");
            return;
        }
        
        System.out.println("\n✅ Found " + corporacionEntities.size() + " CORPORACION CIMEX entities:");
        corporacionEntities.forEach(e -> {
            System.out.println("   ID: " + e.sourceId() + " | Name: " + e.name());
            double score = entityScorer.score("CIMEX", e);
            System.out.println("      Score vs 'CIMEX': " + String.format("%.2f%%", score * 100));
        });
        
        // Check search results
        List<SearchResult> results = searchService.search("CIMEX", 20, 0.88);
        System.out.println("\n🔍 Search results for 'CIMEX' (limit=20, minMatch=0.88): " + results.size() + " results");
        
        long corporacionCount = results.stream()
            .filter(r -> r.entity().name().toUpperCase().contains("CORPORACION"))
            .count();
        
        System.out.println("   Results containing 'CORPORACION': " + corporacionCount);
        
        if (corporacionCount == 0) {
            System.out.println("\n⚠️  ISSUE: CORPORACION CIMEX entities missing from results");
            System.out.println("   Likely: score below threshold or limit cutoff");
        }
    }

    @Test
    @DisplayName("Row 21: Why doesn't 'AL QA'IDA' return 'AL-QAIDA GROUP OF JIHAD IN IRAQ'?")
    void diagnoseAlQaidaJihad() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("DIAGNOSTIC: AL QA'IDA → AL-QAIDA GROUP OF JIHAD IN IRAQ");
        System.out.println("=".repeat(80));
        
        // Find entities with "JIHAD IN IRAQ" in name or aliases
        List<Entity> jihadEntities = entityIndex.getAll().stream()
            .filter(e -> {
                String name = e.name().toUpperCase();
                String aliases = String.join(" ", e.altNames()).toUpperCase();
                return name.contains("JIHAD") && name.contains("IRAQ") ||
                       aliases.contains("JIHAD") && aliases.contains("IRAQ");
            })
            .collect(Collectors.toList());
        
        System.out.println("\n✅ Found " + jihadEntities.size() + " entities containing 'JIHAD...IRAQ':");
        jihadEntities.forEach(e -> {
            System.out.println("   ID: " + e.sourceId() + " | Name: " + e.name());
            
            // Check if it has AL-QAIDA alias
            boolean hasAlQaidaAlias = e.altNames().stream()
                .anyMatch(alias -> alias.toUpperCase().contains("AL-QAIDA") || alias.toUpperCase().contains("AL QAIDA"));
            
            if (hasAlQaidaAlias) {
                System.out.println("      ✅ Has AL-QAIDA alias");
                double score = entityScorer.score("AL QA'IDA", e);
                System.out.println("      Score vs 'AL QA'IDA': " + String.format("%.2f%%", score * 100));
            }
        });
        
        // Check search results
        List<SearchResult> results = searchService.search("AL QA'IDA", 20, 0.88);
        long jihadCount = results.stream()
            .filter(r -> r.entity().name().toUpperCase().contains("JIHAD") && 
                        r.entity().name().toUpperCase().contains("IRAQ"))
            .count();
        
        System.out.println("\n🔍 Search 'AL QA'IDA' returns " + jihadCount + " entities with 'JIHAD...IRAQ'");
        
        if (jihadCount == 0) {
            System.out.println("\n⚠️  ISSUE: JIHAD IN IRAQ entities missing from results");
        }
    }

    @Test
    @DisplayName("Summary: What percentage of entities with query tokens are returned?")
    void summaryCoverageAnalysis() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("COVERAGE ANALYSIS: Substring Token Matching");
        System.out.println("=".repeat(80));
        
        String[] testQueries = {"TALIBAN", "CIMEX", "AL QA'IDA", "OFFICE 39", "OTKRITIE"};
        
        for (String query : testQueries) {
            // Find all entities containing query tokens
            String normalizedQuery = query.toUpperCase().replaceAll("[^A-Z0-9\\s]", " ");
            String[] queryTokens = normalizedQuery.split("\\s+");
            
            List<Entity> entitiesWithToken = entityIndex.getAll().stream()
                .filter(e -> {
                    String name = e.name().toUpperCase();
                    for (String token : queryTokens) {
                        if (token.length() >= 4 && name.contains(token)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
            
            // How many appear in search results?
            List<SearchResult> results = searchService.search(query, 20, 0.88);
            
            long matchedCount = entitiesWithToken.stream()
                .filter(e -> results.stream()
                    .anyMatch(r -> r.entity().sourceId().equals(e.sourceId())))
                .count();
            
            double coverage = entitiesWithToken.isEmpty() ? 0 : 
                (double) matchedCount / entitiesWithToken.size() * 100;
            
            System.out.printf("\n%-15s | %2d entities contain token | %2d in results | %.0f%% coverage%n",
                query, entitiesWithToken.size(), matchedCount, coverage);
            
            if (coverage < 100) {
                System.out.println("   ⚠️  Missing entities:");
                entitiesWithToken.stream()
                    .filter(e -> results.stream()
                        .noneMatch(r -> r.entity().sourceId().equals(e.sourceId())))
                    .limit(3)
                    .forEach(e -> System.out.println("      - " + e.name() + " (score: " + 
                        String.format("%.1f%%", entityScorer.score(query, e) * 100) + ")"));
            }
        }
    }
}
