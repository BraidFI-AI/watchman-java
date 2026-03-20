package io.braid.daywatcher.performance;

import io.braid.daywatcher.batch.BatchScreeningItem;
import io.braid.daywatcher.batch.BatchScreeningRequest;
import io.braid.daywatcher.batch.BatchScreeningResponse;
import io.braid.daywatcher.batch.BatchScreeningService;
import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.search.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

/**
 * Performance profiling test for BSA scoring optimization.
 * 
 * Phase 1.2: Run profiling tests to measure where time is spent.
 * 
 * Usage: Run with VM argument -Dwatchman.performance.profiling=true
 * 
 * Expected output:
 * - Per-operation timing breakdown
 * - PreparedFields hit rate
 * - Identification of slowest operations
 */
@SpringBootTest
class SearchPerformanceProfilingTest {

    @Autowired
    private SearchService searchService;
    
    @Autowired
    private BatchScreeningService batchScreeningService;

    // Sample test names representing typical search patterns
    private static final String[] TEST_NAMES = {
        // Common names (1-2 tokens)
        "MUHAMMAD ALI",
        "JOSE GARCIA",
        "ABDUL RAHMAN",
        
        // Medium names (3-4 tokens)
        "OSAMA BIN LADEN",
        "SADDAM HUSSEIN AL-TIKRITI",
        "QASEM SOLEIMANI",
        
        // Complex names (5+ tokens)
        "AHMED ABDULLAH SALEH AL-KHABURI",
        "MUHAMMAD JAAFAR QASIR",
        
        // Entity names
        "AL-QAIDA",
        "TALIBAN",
        "ISLAMIC STATE",
        "HIZBALLAH",
        
        // Specific OFAC entities for accuracy
        "CECOEX",
        "T.E.G. LIMITED",
        "SMARTMET LLC",
        
        // Edge cases
        "CK ID",
        "AL-QUDS",
        "PIJ",
        "IRA",
        
        // Clean names (should return few/no results)
        "JOHN SMITH",
        "MARY JOHNSON",
        "ROBERT WILLIAMS"
    };

    @BeforeEach
    void resetTimers() {
        PerformanceTimers.reset();
    }

    @Test
    void profileSearchPerformance_SmallBatch() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("BATCH SEARCH PERFORMANCE PROFILING - SMALL BATCH (25 names)");
        System.out.println("=".repeat(80));
        
        // Build batch request with all test names
        List<BatchScreeningItem> items = new ArrayList<>();
        for (int i = 0; i < TEST_NAMES.length; i++) {
            items.add(BatchScreeningItem.of("req-" + i, TEST_NAMES[i]));
        }
        
        BatchScreeningRequest request = BatchScreeningRequest.builder()
            .items(items)
            .minMatch(0.85)
            .limit(20)
            .build();
        
        long startTime = System.currentTimeMillis();
        BatchScreeningResponse response = batchScreeningService.screen(request);
        long totalTime = System.currentTimeMillis() - startTime;
        
        int totalResults = response.results().stream()
            .mapToInt(r -> r.matches().size())
            .sum();
        
        double avgTimePerSearch = totalTime / (double) TEST_NAMES.length;
        double throughput = (TEST_NAMES.length * 1000.0) / totalTime;
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("BATCH SEARCH SUMMARY:");
        System.out.println("  Total names screened: " + TEST_NAMES.length);
        System.out.println("  Batch size: " + TEST_NAMES.length);
        System.out.println("  Total matches found: " + totalResults);
        System.out.println("  Total time: " + totalTime + " ms");
        System.out.println("  Avg time per name: " + String.format("%.2f", avgTimePerSearch) + " ms");
        System.out.println("  Throughput: " + String.format("%.2f", throughput) + " names/sec");
        System.out.println("=".repeat(80));
        
        PerformanceTimers.printReport();
        
        analyzeResults();
    }

    @Test
    void profileSearchPerformance_MediumBatch() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("BATCH SEARCH PERFORMANCE PROFILING - MEDIUM BATCH (100 names)");
        System.out.println("=".repeat(80));
        
        // Build batch with 100 names (repeat test names)
        List<BatchScreeningItem> items = new ArrayList<>();
        int count = 0;
        while (items.size() < 100) {
            for (String name : TEST_NAMES) {
                items.add(BatchScreeningItem.of("req-" + count++, name));
                if (items.size() >= 100) break;
            }
        }
        
        BatchScreeningRequest request = BatchScreeningRequest.builder()
            .items(items)
            .minMatch(0.85)
            .limit(20)
            .build();
        
        long startTime = System.currentTimeMillis();
        BatchScreeningResponse response = batchScreeningService.screen(request);
        long totalTime = System.currentTimeMillis() - startTime;
        
        int totalResults = response.results().stream()
            .mapToInt(r -> r.matches().size())
            .sum();
        
        double avgTimePerSearch = totalTime / (double) items.size();
        double throughput = (items.size() * 1000.0) / totalTime;
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("BATCH SEARCH SUMMARY:");
        System.out.println("  Total names screened: " + items.size());
        System.out.println("  Batch size: " + items.size());
        System.out.println("  Total matches found: " + totalResults);
        System.out.println("  Total time: " + totalTime + " ms");
        System.out.println("  Avg time per name: " + String.format("%.2f", avgTimePerSearch) + " ms");
        System.out.println("  Throughput: " + String.format("%.2f", throughput) + " names/sec");
        System.out.println("=".repeat(80));
        
        PerformanceTimers.printReport();
        
        analyzeResults();
    }

    @Test
    void profileBatchPerformance_10kNames() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("BATCH API PERFORMANCE TEST - 10,000 NAMES (Production Volume)");
        System.out.println("Testing in batches of 1000 (max batch size)");
        System.out.println("=".repeat(80));
        
        // Build 10k names (repeat test names)
        List<BatchScreeningItem> allItems = new ArrayList<>();
        int count = 0;
        while (allItems.size() < 10000) {
            for (String name : TEST_NAMES) {
                allItems.add(BatchScreeningItem.of("req-" + count++, name));
                if (allItems.size() >= 10000) break;
            }
        }
        
        System.out.println("\nProcessing 10 batches of 1000 names each...\n");
        
        long overallStart = System.currentTimeMillis();
        int totalMatches = 0;
        List<Long> batchTimes = new ArrayList<>();
        
        // Process in batches of 1000 (max batch size)
        for (int batchNum = 0; batchNum < 10; batchNum++) {
            int startIdx = batchNum * 1000;
            int endIdx = Math.min(startIdx + 1000, allItems.size());
            List<BatchScreeningItem> batchItems = allItems.subList(startIdx, endIdx);
            
            BatchScreeningRequest request = BatchScreeningRequest.builder()
                .items(batchItems)
                .minMatch(0.85)
                .limit(20)
                .build();
            
            long batchStart = System.currentTimeMillis();
            BatchScreeningResponse response = batchScreeningService.screen(request);
            long batchTime = System.currentTimeMillis() - batchStart;
            batchTimes.add(batchTime);
            
            int batchMatches = response.results().stream()
                .mapToInt(r -> r.matches().size())
                .sum();
            totalMatches += batchMatches;
            
            double batchThroughput = (batchItems.size() * 1000.0) / batchTime;
            System.out.println(String.format("Batch %2d: %4d names in %5d ms (%.0f names/sec, %d matches)",
                batchNum + 1, batchItems.size(), batchTime, batchThroughput, batchMatches));
        }
        
        long totalTime = System.currentTimeMillis() - overallStart;
        double avgTimePerName = totalTime / (double) allItems.size();
        double overallThroughput = (allItems.size() * 1000.0) / totalTime;
        
        // Calculate batch time statistics
        double avgBatchTime = batchTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long minBatchTime = batchTimes.stream().mapToLong(Long::longValue).min().orElse(0);
        long maxBatchTime = batchTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("10K BATCH TEST SUMMARY:");
        System.out.println("  Total names screened: " + allItems.size());
        System.out.println("  Number of batches: 10");
        System.out.println("  Batch size: 1000");
        System.out.println("  Total matches found: " + totalMatches);
        System.out.println("  Total time: " + totalTime + " ms (" + String.format("%.1f", totalTime/1000.0) + " sec)");
        System.out.println("  Avg time per name: " + String.format("%.2f", avgTimePerName) + " ms");
        System.out.println("  THROUGHPUT: " + String.format("%.2f", overallThroughput) + " names/sec");
        System.out.println("\n  Batch timing:");
        System.out.println("    Min batch time: " + minBatchTime + " ms");
        System.out.println("    Avg batch time: " + String.format("%.0f", avgBatchTime) + " ms");
        System.out.println("    Max batch time: " + maxBatchTime + " ms");
        System.out.println("\n  TARGET COMPARISON:");
        System.out.println("    Target (Portage bank): 60 names/sec");
        System.out.println("    Actual throughput: " + String.format("%.2f", overallThroughput) + " names/sec");
        double percentOfTarget = (overallThroughput / 60.0) * 100;
        if (overallThroughput >= 60) {
            System.out.println("    Status: ✅ EXCEEDS TARGET by " + String.format("%.0fx", overallThroughput / 60.0));
        } else {
            System.out.println("    Status: ⚠️  Below target (" + String.format("%.1f%%", percentOfTarget) + ")");
        }
        System.out.println("=".repeat(80));
    }

    private void analyzeResults() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("PERFORMANCE ANALYSIS & RECOMMENDATIONS:");
        System.out.println("=".repeat(80));
        
        // Check PreparedFields usage
        PerformanceTimer preparedHits = PerformanceTimers.get("scorer.compareName_preparedFields_hit");
        PerformanceTimer preparedMisses = PerformanceTimers.get("scorer.compareName_preparedFields_miss");
        
        int hits = preparedHits.getCallCount();
        int misses = preparedMisses.getCallCount();
        int total = hits + misses;
        
        if (total > 0) {
            double hitRate = (hits * 100.0) / total;
            System.out.println("\nPreparedFields Hit Rate:");
            System.out.println("  Hits: " + hits + " (" + String.format("%.1f%%", hitRate) + ")");
            System.out.println("  Misses: " + misses + " (" + String.format("%.1f%%", 100 - hitRate) + ")");
            
            if (hitRate < 50) {
                System.out.println("  ⚠️  WARNING: Low hit rate! PreparedFields may not be populated correctly.");
                System.out.println("  → FIX: Verify Entity.normalize() populates PreparedFields during data load");
                System.out.println("  → IMPACT: Could improve performance by 30-40%");
            } else if (hitRate < 95) {
                System.out.println("  ℹ️  INFO: Partial hit rate. Some entities may be missing PreparedFields.");
            } else {
                System.out.println("  ✅ PreparedFields working correctly");
            }
        }
        
        // Analyze time distribution
        PerformanceTimer overall = PerformanceTimers.get("search.overall");
        PerformanceTimer scoring = PerformanceTimers.get("search.entity_scoring_loop");
        PerformanceTimer similarity = PerformanceTimers.get("similarity.tokenizedSimilarity");
        PerformanceTimer bestPair = PerformanceTimers.get("similarity.bestPairJaro");
        PerformanceTimer phonetic = PerformanceTimers.get("phonetic.phoneticSetsMatch");
        PerformanceTimer collapse = PerformanceTimers.get("tokens.collapseAcronyms");
        PerformanceTimer filter = PerformanceTimers.get("tokens.filterShortTokens");
        
        if (overall.getCallCount() > 0) {
            double totalMs = overall.getTotalMillis();
            
            System.out.println("\nTime Distribution:");
            printPercentage("  Entity scoring loop", scoring.getTotalMillis(), totalMs);
            printPercentage("  Similarity calculations", similarity.getTotalMillis(), totalMs);
            printPercentage("  Best pair matching", bestPair.getTotalMillis(), totalMs);
            printPercentage("  Phonetic matching", phonetic.getTotalMillis(), totalMs);
            printPercentage("  Token collapsing", collapse.getTotalMillis(), totalMs);
            printPercentage("  Token filtering", filter.getTotalMillis(), totalMs);
            
            // Recommendations
            System.out.println("\nTop Optimization Opportunities:");
            
            List<OptimizationOpportunity> opportunities = new ArrayList<>();
            
            double scoringPct = (scoring.getTotalMillis() / totalMs) * 100;
            if (scoringPct > 80) {
                opportunities.add(new OptimizationOpportunity(
                    1, "Implement pre-filtering", scoringPct,
                    "Scoring loop takes " + String.format("%.1f%%", scoringPct) + " of time. " +
                    "Add token-based pre-filter to skip obviously irrelevant entities."
                ));
            }
            
            double similarityPct = (similarity.getTotalMillis() / totalMs) * 100;
            if (similarityPct > 30) {
                opportunities.add(new OptimizationOpportunity(
                    2, "Cache query processing", similarityPct,
                    "Similarity calculations take " + String.format("%.1f%%", similarityPct) + ". " +
                    "Process query once and reuse for all entities."
                ));
            }
            
            double phoneticPct = (phonetic.getTotalMillis() / totalMs) * 100;
            if (phoneticPct > 10) {
                opportunities.add(new OptimizationOpportunity(
                    3, "Cache Soundex codes", phoneticPct,
                    "Phonetic matching takes " + String.format("%.1f%%", phoneticPct) + ". " +
                    "Pre-compute and store Soundex codes in PreparedFields."
                ));
            }
            
            double tokenPct = (collapse.getTotalMillis() + filter.getTotalMillis()) / totalMs * 100;
            if (tokenPct > 5) {
                opportunities.add(new OptimizationOpportunity(
                    4, "Combine token processing", tokenPct,
                    "Token operations take " + String.format("%.1f%%", tokenPct) + ". " +
                    "Merge collapse + filter into single pass."
                ));
            }
            
            if (!opportunities.isEmpty()) {
                for (OptimizationOpportunity opp : opportunities) {
                    System.out.println(String.format("\n  %d. %s (%.1f%% of time)", 
                        opp.priority, opp.name, opp.percentTime));
                    System.out.println("     " + opp.description);
                }
            } else {
                System.out.println("  No major bottlenecks identified. System performing well!");
            }
        }
        
        System.out.println("\n" + "=".repeat(80) + "\n");
    }
    
    private void printPercentage(String label, double value, double total) {
        if (total <= 0) return;
        double pct = (value / total) * 100;
        String bar = "█".repeat(Math.min(50, (int) (pct / 2)));
        System.out.println(String.format("%-30s: %6.1f ms (%5.1f%%) %s", 
            label, value, pct, bar));
    }
    
    private static class OptimizationOpportunity {
        int priority;
        String name;
        double percentTime;
        String description;
        
        OptimizationOpportunity(int priority, String name, double percentTime, String description) {
            this.priority = priority;
            this.name = name;
            this.percentTime = percentTime;
            this.description = description;
        }
    }
}
