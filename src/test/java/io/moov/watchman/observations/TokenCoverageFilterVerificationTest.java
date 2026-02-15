package io.moov.watchman.observations;

import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class TokenCoverageFilterVerificationTest {

    @Autowired
    private SearchService searchService;

    @Test
    void verifyFilterBehavior() {
        System.out.println("\n================================================================");
        System.out.println("TOKEN COVERAGE FILTER VERIFICATION");
        System.out.println("================================================================\n");

        // Test 1: 3-token query with weak matches (should filter out)
        System.out.println("TEST 1: Randy San Nicolas (3 tokens)");
        System.out.println("Expected: 0 results (all matches are single-token 'San'/'Hassan')");
        List<SearchResult> test1 = searchService.search("Randy San Nicolas", 20, 0.88);
        System.out.println("Actual: " + test1.size() + " results");
        System.out.println("✓ " + (test1.size() == 0 ? "PASS" : "FAIL") + "\n");

        // Test 2: 2-token query (should NOT filter - coverage rule doesn't apply)
        System.out.println("TEST 2: AL QA'IDA (2 tokens)");
        System.out.println("Expected: Multiple results (no coverage filter applied)");
        List<SearchResult> test2 = searchService.search("AL QA'IDA", 10, 0.88);
        System.out.println("Actual: " + test2.size() + " results");
        System.out.println("✓ " + (test2.size() > 0 ? "PASS" : "FAIL") + "\n");

        // Test 3: 1-token query (should NOT filter - coverage rule doesn't apply)
        System.out.println("TEST 3: TALIBAN (1 token)");
        System.out.println("Expected: Multiple results (no coverage filter applied)");
        List<SearchResult> test3 = searchService.search("TALIBAN", 10, 0.88);
        System.out.println("Actual: " + test3.size() + " results");
        System.out.println("✓ " + (test3.size() > 0 ? "PASS" : "FAIL") + "\n");

        // Test 4: 5-token query with weak matches
        System.out.println("TEST 4: John Paul Smith Hassan Ahmed (5 tokens)");
        System.out.println("Expected: 0 or very few results (weak single-token matches filtered)");
        List<SearchResult> test4 = searchService.search("John Paul Smith Hassan Ahmed", 20, 0.88);
        System.out.println("Actual: " + test4.size() + " results");
        if (test4.size() > 0) {
            System.out.println("Results:");
            for (int i = 0; i < Math.min(5, test4.size()); i++) {
                System.out.println("  " + (i+1) + ". " + test4.get(i).entity().name());
            }
        }
        System.out.println();

        System.out.println("================================================================");
        System.out.println("SUMMARY: Token coverage filter");
        System.out.println("  - Applies to: 3+ token queries scoring >= 95%");
        System.out.println("  - Requires: >= 40% query token coverage");
        System.out.println("  - Preserves: 1-2 token queries (AL-QAIDA cases)");
        System.out.println("  - Filters: Weak single-token matches in multi-token names");
        System.out.println("================================================================");
    }
}
