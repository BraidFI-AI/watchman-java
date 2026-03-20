package io.braid.daywatcher.observations;

import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class AlQaidaSyriaPositionTest {

    @Autowired
    private SearchService searchService;

    @Test
    void findHurrasAlDinPosition() {
        System.out.println("\n================================================================");
        System.out.println("FIND HURRAS AL-DIN POSITION IN 'AL QA'IDA' SEARCH");
        System.out.println("================================================================\n");

        List<SearchResult> results = searchService.search("AL QA'IDA", 100, 0.70);

        int position = -1;
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).entity().name().equals("HURRAS AL-DIN")) {
                position = i + 1;
                System.out.println("✓ FOUND at position " + position);
                System.out.println("  Score: " + String.format("%.2f%%", results.get(i).score() * 100));
                System.out.println("  Matched Alias: " + results.get(i).matchedAlias());
                break;
            }
        }

        if (position == -1) {
            System.out.println("❌ NOT FOUND in top 100 (threshold=0.70)");
            
            // Try even lower threshold
            System.out.println("\nTrying threshold=0.50...");
            List<SearchResult> moreResults = searchService.search("AL QA'IDA", 200, 0.50);
            for (int i = 0; i < moreResults.size(); i++) {
                if (moreResults.get(i).entity().name().equals("HURRAS AL-DIN")) {
                    position = i + 1;
                    System.out.println("✓ FOUND at position " + position);
                    System.out.println("  Score: " + String.format("%.2f%%", moreResults.get(i).score() * 100));
                    break;
                }
            }
        }

        System.out.println("\n================================================================");
        if (position > 0 && position <= 10) {
            System.out.println("✅ SUCCESS: HURRAS AL-DIN in consultant's top 10 view");
        } else if (position > 10) {
            System.out.println("❌ PROBLEM: HURRAS AL-DIN at position " + position + " (outside top 10)");
            System.out.println("   Needs ranking boost or higher score to reach top 10");
        } else {
            System.out.println("❌ CRITICAL: HURRAS AL-DIN not visible at all");
        }
    }
}
