package io.moov.watchman.performance;

import io.moov.watchman.WatchmanApplication;
import io.moov.watchman.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

/**
 * Quick performance check for measuring optimization impact
 */
@SpringBootTest(classes = WatchmanApplication.class)
public class QuickPerformanceCheck {

    @Autowired
    private SearchService searchService;

    @Test
    public void measureSearchPerformance() {
        System.out.println("Starting performance test...");
        
        // Warm-up
        for (int i = 0; i < 5; i++) {
            searchService.search("John Smith", 10, 0.85);
        }
        System.out.println("Warm-up complete");

        // Real test
        List<String> testNames = List.of(
            "Muhammad Ali",
            "Vladimir Putin",
            "Kim Jong Un",
            "Nicolas Maduro",
            "Bashar Al-Assad",
            "Hassan Nasrallah",
            "Qasem Soleimani",
            "Ali Khamenei",
            "Osama Bin Laden",
            "Al-Qa'ida"
        );

        System.out.println("Starting test with " + testNames.size() + " names");
        long startTime = System.currentTimeMillis();
        int totalResults = 0;
        
        for (String name : testNames) {
            var results = searchService.search(name, 10, 0.60);  // Lower threshold to 60%
            totalResults += results.size();
            System.out.println("  " + name + ": " + results.size() + " results");
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        double searchesPerSec = (testNames.size() * 1000.0) / duration;
        double avgTimeMs = duration / (double) testNames.size();
        
        System.out.println("\n================================================================================");
        System.out.println("QUICK PERFORMANCE CHECK");
        System.out.println("================================================================================");
        System.out.println("Searches: " + testNames.size());
        System.out.println("Total Results: " + totalResults);
        System.out.println("Total Time: " + duration + " ms");
        System.out.println("Avg Time per Search: " + String.format("%.2f", avgTimeMs) + " ms");
        System.out.println("Throughput: " + String.format("%.2f", searchesPerSec) + " names/sec");
        System.out.println("================================================================================\n");
    }
}
