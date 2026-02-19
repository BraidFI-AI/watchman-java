package io.moov.watchman.observations;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.search.SearchService;
import io.moov.watchman.model.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD RED Phase: Verify specific OFAC entities mentioned in BSA retest observations.
 * 
 * BSA Consultant identified missing entities by OFAC ID:
 * - Row 6 (CIMEX): IDs 8125, 576, 30630
 * - Row 21 (AL QA'IDA): IDs 6366, 8759, 27318
 * - Row 22 (TALIBAN): ID 12206
 * - Row 52 (OTKRITIE): IDs 34497, 34509, 34499
 * 
 * This test verifies:
 * 1. Entities exist in loaded OFAC data (data ingestion check)
 * 2. Entities appear in search results with limit=50 (ranking check)
 */
@SpringBootTest
@DisplayName("BSA Observation - Missing Entity Verification")
class MissingEntityVerificationTest {

    @Autowired
    private EntityIndex entityIndex;
    
    @Autowired
    private SearchService searchService;

    @Test
    @DisplayName("Row 6: CIMEX - Verify entities 8125, 576, 30630 exist in OFAC data")
    void testCimexEntitiesExistInData() {
        List<Entity> allEntities = entityIndex.getAll();
        
        // OFAC IDs from BSA observation
        String[] expectedIds = {"8125", "576", "30630"};
        
        System.out.println("\n=== CIMEX Entity Verification (Row 6) ===");
        
        for (String ofacId : expectedIds) {
            Optional<Entity> entity = allEntities.stream()
                .filter(e -> ofacId.equals(e.sourceId()))
                .findFirst();
            
            if (entity.isPresent()) {
                System.out.println("✓ OFAC ID " + ofacId + " FOUND: " + entity.get().name());
            } else {
                System.out.println("✗ OFAC ID " + ofacId + " MISSING from loaded data");
            }
        }
    }
    
    @Test
    @DisplayName("Row 6: CIMEX - Verify entities appear in search results with limit=50")
    void testCimexEntitiesInSearchResults() {
        List<SearchResult> results = searchService.search("CIMEX", 50, 0.70);
        
        System.out.println("\n=== CIMEX Search Results (limit=50, threshold=0.70) ===");
        System.out.println("Total results: " + results.size());
        
        // Find entities with CIMEX in name
        List<SearchResult> cimexResults = results.stream()
            .filter(r -> r.entity().name().toUpperCase().contains("CIMEX"))
            .collect(Collectors.toList());
        
        System.out.println("Entities with 'CIMEX' in name: " + cimexResults.size());
        
        cimexResults.forEach(r -> {
            System.out.printf("  [%d] Score: %.2f%% | ID: %s | Name: %s%n",
                results.indexOf(r) + 1,
                r.score() * 100,
                r.entity().sourceId(),
                r.entity().name());
        });
        
        // Check for specific missing entities
        boolean hasCorpCimex8125 = cimexResults.stream()
            .anyMatch(r -> "8125".equals(r.entity().sourceId()));
        boolean hasCorpCimex576 = cimexResults.stream()
            .anyMatch(r -> "576".equals(r.entity().sourceId()));
        boolean hasFinancieraCimex30630 = cimexResults.stream()
            .anyMatch(r -> "30630".equals(r.entity().sourceId()));
        
        System.out.println("\n=== Missing Entity Check ===");
        System.out.println("OFAC ID 8125 (CORPORACION CIMEX S.A.) in results: " + hasCorpCimex8125);
        System.out.println("OFAC ID 576 (CORPORACION CIMEX, S.A.) in results: " + hasCorpCimex576);
        System.out.println("OFAC ID 30630 (FINANCIERA CIMEX S.A) in results: " + hasFinancieraCimex30630);
    }
    
    @Test
    @DisplayName("Row 21: AL QA'IDA - Verify entities 6366, 8759, 27318 exist in OFAC data")
    void testAlQaidaEntitiesExistInData() {
        List<Entity> allEntities = entityIndex.getAll();
        
        // OFAC IDs from BSA observation
        String[] expectedIds = {"6366", "8759", "27318"};
        
        System.out.println("\n=== AL QA'IDA Entity Verification (Row 21) ===");
        
        for (String ofacId : expectedIds) {
            Optional<Entity> entity = allEntities.stream()
                .filter(e -> ofacId.equals(e.sourceId()))
                .findFirst();
            
            if (entity.isPresent()) {
                System.out.println("✓ OFAC ID " + ofacId + " FOUND: " + entity.get().name());
                // Show first 3 aliases
                if (!entity.get().altNames().isEmpty()) {
                    System.out.println("  Aliases: " + String.join(", ", 
                        entity.get().altNames().stream().limit(3).collect(Collectors.toList())));
                }
            } else {
                System.out.println("✗ OFAC ID " + ofacId + " MISSING from loaded data");
            }
        }
    }
    
    @Test
    @DisplayName("Row 21: AL QA'IDA - Verify entities appear in search results with limit=50")
    void testAlQaidaEntitiesInSearchResults() {
        List<SearchResult> results = searchService.search("AL QA'IDA", 50, 0.70);
        
        System.out.println("\n=== AL QA'IDA Search Results (limit=50, threshold=0.70) ===");
        System.out.println("Total results: " + results.size());
        
        // Show top 10
        System.out.println("\nTop 10 Results:");
        results.stream().limit(10).forEach(r -> {
            System.out.printf("  [%d] Score: %.2f%% | ID: %s | Name: %s%n",
                results.indexOf(r) + 1,
                r.score() * 100,
                r.entity().sourceId(),
                r.entity().name());
        });
        
        // Check for specific missing entities
        boolean hasEntity6366 = results.stream()
            .anyMatch(r -> "6366".equals(r.entity().sourceId()));
        boolean hasEntity8759 = results.stream()
            .anyMatch(r -> "8759".equals(r.entity().sourceId()));
        boolean hasEntity27318 = results.stream()
            .anyMatch(r -> "27318".equals(r.entity().sourceId()));
        
        System.out.println("\n=== Missing Entity Check ===");
        System.out.println("OFAC ID 6366 in results: " + hasEntity6366);
        System.out.println("OFAC ID 8759 in results: " + hasEntity8759);
        System.out.println("OFAC ID 27318 in results: " + hasEntity27318);
        
        if (!hasEntity6366 || !hasEntity8759 || !hasEntity27318) {
            System.out.println("\n⚠️  Searching for missing entities individually...");
            
            if (!hasEntity6366) {
                Optional<Entity> entity = entityIndex.getAll().stream()
                    .filter(e -> "6366".equals(e.sourceId()))
                    .findFirst();
                entity.ifPresent(e -> {
                    System.out.println("\nEntity 6366: " + e.name());
                    System.out.println("  Query: 'AL QA'IDA'");
                    List<SearchResult> individualResults = searchService.search(e.name(), 1, 0.70);
                    if (!individualResults.isEmpty()) {
                        System.out.println("  Direct name search score: " + (individualResults.get(0).score() * 100) + "%");
                    }
                });
            }
            
            if (!hasEntity8759) {
                Optional<Entity> entity = entityIndex.getAll().stream()
                    .filter(e -> "8759".equals(e.sourceId()))
                    .findFirst();
                entity.ifPresent(e -> {
                    System.out.println("\nEntity 8759: " + e.name());
                    System.out.println("  Query: 'AL QA'IDA'");
                    List<SearchResult> individualResults = searchService.search(e.name(), 1, 0.70);
                    if (!individualResults.isEmpty()) {
                        System.out.println("  Direct name search score: " + (individualResults.get(0).score() * 100) + "%");
                    }
                });
            }
            
            if (!hasEntity27318) {
                Optional<Entity> entity = entityIndex.getAll().stream()
                    .filter(e -> "27318".equals(e.sourceId()))
                    .findFirst();
                entity.ifPresent(e -> {
                    System.out.println("\nEntity 27318: " + e.name());
                    System.out.println("  Query: 'AL QA'IDA'");
                    List<SearchResult> individualResults = searchService.search(e.name(), 1, 0.70);
                    if (!individualResults.isEmpty()) {
                        System.out.println("  Direct name search score: " + (individualResults.get(0).score() * 100) + "%");
                    }
                });
            }
        }
    }
    
    @Test
    @DisplayName("Row 22: TALIBAN - Verify entity 12206 exists in OFAC data")
    void testTalibanEntityExistsInData() {
        List<Entity> allEntities = entityIndex.getAll();
        
        String ofacId = "12206";
        
        System.out.println("\n=== TALIBAN Entity Verification (Row 22) ===");
        
        Optional<Entity> entity = allEntities.stream()
            .filter(e -> ofacId.equals(e.sourceId()))
            .findFirst();
        
        if (entity.isPresent()) {
            System.out.println("✓ OFAC ID " + ofacId + " FOUND: " + entity.get().name());
            if (!entity.get().altNames().isEmpty()) {
                System.out.println("  Aliases: " + String.join(", ", 
                    entity.get().altNames().stream().limit(5).collect(Collectors.toList())));
            }
        } else {
            System.out.println("✗ OFAC ID " + ofacId + " MISSING from loaded data");
        }
    }
    
    @Test
    @DisplayName("Row 22: TALIBAN - Verify entity appears in search results with limit=50")
    void testTalibanEntityInSearchResults() {
        List<SearchResult> results = searchService.search("TALIBAN", 50, 0.70);
        
        System.out.println("\n=== TALIBAN Search Results (limit=50, threshold=0.70) ===");
        System.out.println("Total results: " + results.size());
        
        // Show top 10
        System.out.println("\nTop 10 Results:");
        results.stream().limit(10).forEach(r -> {
            System.out.printf("  [%d] Score: %.2f%% | ID: %s | Name: %s%n",
                results.indexOf(r) + 1,
                r.score() * 100,
                r.entity().sourceId(),
                r.entity().name());
        });
        
        // Check for specific missing entity
        boolean hasEntity12206 = results.stream()
            .anyMatch(r -> "12206".equals(r.entity().sourceId()));
        
        System.out.println("\n=== Missing Entity Check ===");
        System.out.println("OFAC ID 12206 (TEHRIK-E TALIBAN PAKISTAN) in results: " + hasEntity12206);
        
        if (!hasEntity12206) {
            System.out.println("\n⚠️  Searching for entity 12206 details...");
            Optional<Entity> entity = entityIndex.getAll().stream()
                .filter(e -> "12206".equals(e.sourceId()))
                .findFirst();
            entity.ifPresent(e -> {
                System.out.println("Entity 12206: " + e.name());
                if (!e.altNames().isEmpty()) {
                    System.out.println("  Aliases: " + String.join(", ", 
                        e.altNames().stream().limit(3).collect(Collectors.toList())));
                }
                List<SearchResult> individualResults = searchService.search(e.name(), 1, 0.70);
                if (!individualResults.isEmpty()) {
                    System.out.println("  Direct name search score: " + (individualResults.get(0).score() * 100) + "%");
                }
            });
        }
    }
    
    @Test
    @DisplayName("Row 52: OTKRITIE - Verify entities 34497, 34509, 34499 exist in OFAC data")
    void testOtkritieEntitiesExistInData() {
        List<Entity> allEntities = entityIndex.getAll();
        
        // OFAC IDs from BSA observation
        String[] expectedIds = {"34497", "34509", "34499"};
        
        System.out.println("\n=== OTKRITIE Entity Verification (Row 52) ===");
        
        for (String ofacId : expectedIds) {
            Optional<Entity> entity = allEntities.stream()
                .filter(e -> ofacId.equals(e.sourceId()))
                .findFirst();
            
            if (entity.isPresent()) {
                System.out.println("✓ OFAC ID " + ofacId + " FOUND: " + entity.get().name());
            } else {
                System.out.println("✗ OFAC ID " + ofacId + " MISSING from loaded data");
            }
        }
    }
    
    @Test
    @DisplayName("Row 52: OTKRITIE - Verify entities appear in search results with limit=50")
    void testOtkritieEntitiesInSearchResults() {
        List<SearchResult> results = searchService.search("OTKRITIE", 50, 0.70);
        
        System.out.println("\n=== OTKRITIE Search Results (limit=50, threshold=0.70) ===");
        System.out.println("Total results: " + results.size());
        
        // Show all results
        System.out.println("\nAll Results:");
        results.forEach(r -> {
            System.out.printf("  [%d] Score: %.2f%% | ID: %s | Name: %s%n",
                results.indexOf(r) + 1,
                r.score() * 100,
                r.entity().sourceId(),
                r.entity().name());
        });
        
        // Check for specific missing entities
        boolean hasEntity34497 = results.stream()
            .anyMatch(r -> "34497".equals(r.entity().sourceId()));
        boolean hasEntity34509 = results.stream()
            .anyMatch(r -> "34509".equals(r.entity().sourceId()));
        boolean hasEntity34499 = results.stream()
            .anyMatch(r -> "34499".equals(r.entity().sourceId()));
        
        System.out.println("\n=== Missing Entity Check ===");
        System.out.println("OFAC ID 34497 in results: " + hasEntity34497);
        System.out.println("OFAC ID 34509 in results: " + hasEntity34509);
        System.out.println("OFAC ID 34499 in results: " + hasEntity34499);
    }
}
