package io.moov.watchman.observations;

import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Diagnostic investigation for Row 17 & Row 24 search failures.
 * 
 * Goal: Understand why target entities not appearing in search results.
 * 
 * Questions:
 * 1. Do entities appear with higher limit (50, 100, 200)?
 * 2. Why are all results scoring 1.0?
 * 3. What are the normalized forms of queries and entity names?
 */
@SpringBootTest
@DisplayName("Row 17 & 24 - Diagnostic Investigation")
class Row17Row24DiagnosticTest {

    @Autowired
    private SearchService searchService;

    @Test
    @DisplayName("Row 17: Search 'CK ID' with limit=100 to see if entity appears")
    void diagnosticCkIdHigherLimit() {
        List<SearchResult> results = searchService.search("CK ID", 100, 0.70);
        
        System.out.println("\n=== Diagnostic: 'CK ID' with limit=100 ===");
        System.out.println("Total results: " + results.size());
        
        // Check if target entity 55865 appears anywhere
        boolean found = false;
        int position = -1;
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            if ("55865".equals(result.entity().sourceId())) {
                found = true;
                position = i + 1;
                System.out.println("\n✅ FOUND entity 55865 at position " + position);
                System.out.println("   Score: " + result.score());
                System.out.println("   Name: " + result.entity().name());
                break;
            }
        }
        
        if (!found) {
            System.out.println("\n❌ Entity 55865 NOT FOUND even with limit=100");
            System.out.println("\nTop 10 results:");
            for (int i = 0; i < Math.min(10, results.size()); i++) {
                SearchResult r = results.get(i);
                System.out.printf("%d. [Score: %.4f] %s%n", i + 1, r.score(), r.entity().name());
            }
        }
        
        // Check score distribution
        long perfectScores = results.stream().filter(r -> r.score() == 1.0).count();
        System.out.println("\nScore distribution:");
        System.out.println("  Perfect scores (1.0): " + perfectScores + "/" + results.size());
    }

    @Test
    @DisplayName("Row 24: Search 'SMARTMET LLC' with limit=100 to see if entity appears")
    void diagnosticSmartmetHigherLimit() {
        List<SearchResult> results = searchService.search("SMARTMET LLC", 100, 0.70);
        
        System.out.println("\n=== Diagnostic: 'SMARTMET LLC' with limit=100 ===");
        System.out.println("Total results: " + results.size());
        
        // Check if target entity 47769 appears anywhere
        boolean found = false;
        int position = -1;
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            if ("47769".equals(result.entity().sourceId())) {
                found = true;
                position = i + 1;
                System.out.println("\n✅ FOUND entity 47769 at position " + position);
                System.out.println("   Score: " + result.score());
                System.out.println("   Name: " + result.entity().name());
                break;
            }
        }
        
        if (!found) {
            System.out.println("\n❌ Entity 47769 NOT FOUND even with limit=100");
            System.out.println("\nTop 10 results:");
            for (int i = 0; i < Math.min(10, results.size()); i++) {
                SearchResult r = results.get(i);
                System.out.printf("%d. [Score: %.4f] %s%n", i + 1, r.score(), r.entity().name());
            }
        }
        
        // Check score distribution
        long perfectScores = results.stream().filter(r -> r.score() == 1.0).count();
        System.out.println("\nScore distribution:");
        System.out.println("  Perfect scores (1.0): " + perfectScores + "/" + results.size());
    }

    @Test
    @DisplayName("Row 17: Search with lower threshold to see scoring behavior")
    void diagnosticCkIdLowerThreshold() {
        List<SearchResult> results = searchService.search("CK ID", 50, 0.50);
        
        System.out.println("\n=== Diagnostic: 'CK ID' with threshold=0.50 ===");
        System.out.println("Total results: " + results.size());
        
        // Find target
        results.stream()
            .filter(r -> "55865".equals(r.entity().sourceId()))
            .findFirst()
            .ifPresentOrElse(
                result -> {
                    int pos = results.indexOf(result) + 1;
                    System.out.println("✅ Found at position " + pos + " with score " + result.score());
                },
                () -> System.out.println("❌ Still not found with 0.50 threshold")
            );
        
        // Show score range
        if (!results.isEmpty()) {
            double minScore = results.stream().mapToDouble(SearchResult::score).min().orElse(0);
            double maxScore = results.stream().mapToDouble(SearchResult::score).max().orElse(0);
            System.out.println("Score range: " + minScore + " to " + maxScore);
        }
    }

    @Test
    @DisplayName("Row 24: Search with lower threshold to see scoring behavior")
    void diagnosticSmartmetLowerThreshold() {
        List<SearchResult> results = searchService.search("SMARTMET LLC", 50, 0.50);
        
        System.out.println("\n=== Diagnostic: 'SMARTMET LLC' with threshold=0.50 ===");
        System.out.println("Total results: " + results.size());
        
        // Find target
        results.stream()
            .filter(r -> "47769".equals(r.entity().sourceId()))
            .findFirst()
            .ifPresentOrElse(
                result -> {
                    int pos = results.indexOf(result) + 1;
                    System.out.println("✅ Found at position " + pos + " with score " + result.score());
                },
                () -> System.out.println("❌ Still not found with 0.50 threshold")
            );
        
        // Show score range
        if (!results.isEmpty()) {
            double minScore = results.stream().mapToDouble(SearchResult::score).min().orElse(0);
            double maxScore = results.stream().mapToDouble(SearchResult::score).max().orElse(0);
            System.out.println("Score range: " + minScore + " to " + maxScore);
        }
    }

    @Test
    @DisplayName("Row 17: Search variations to understand tokenization")
    void diagnosticCkIdVariations() {
        System.out.println("\n=== Testing CK ID variations ===");
        
        String[] queries = {"CK ID", "CKID", "CK ID CO", "CK ID CO LTD", "CK ID CO. LTD"};
        
        for (String query : queries) {
            List<SearchResult> results = searchService.search(query, 50, 0.70);
            
            boolean found = results.stream()
                .anyMatch(r -> "55865".equals(r.entity().sourceId()));
            
            System.out.printf("Query: %-20s | Results: %3d | Entity 55865: %s%n",
                "\"" + query + "\"",
                results.size(),
                found ? "✅ FOUND" : "❌ NOT FOUND");
        }
    }

    @Test
    @DisplayName("Row 24: Search variations to understand tokenization")
    void diagnosticSmartmetVariations() {
        System.out.println("\n=== Testing SMARTMET variations ===");
        
        String[] queries = {
            "SMARTMET",
            "SMARTMET LLC",
            "LLC SMARTMET",
            "LIMITED LIABILITY COMPANY SMARTMET",
            "LIMITED LIABILITY SMARTMET"
        };
        
        for (String query : queries) {
            List<SearchResult> results = searchService.search(query, 50, 0.70);
            
            boolean found = results.stream()
                .anyMatch(r -> "47769".equals(r.entity().sourceId()));
            
            System.out.printf("Query: %-40s | Results: %3d | Entity 47769: %s%n",
                "\"" + query + "\"",
                results.size(),
                found ? "✅ FOUND" : "❌ NOT FOUND");
        }
    }
}
