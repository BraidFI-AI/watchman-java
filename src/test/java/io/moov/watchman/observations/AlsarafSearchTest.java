package io.moov.watchman.observations;

import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class AlsarafSearchTest {

    @Autowired
    private SearchService searchService;

    @Test
    void searchAlsarafDirect() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("ALSARAF Direct Search");
        System.out.println("=".repeat(80));
        
        List<SearchResult> results = searchService.search("ALSARAF HAWALA OFFICE", 10, 0.88);
        
        System.out.println("\nTotal results: " + results.size());
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            System.out.printf("%2d. [%.1f%%] %s%n", i + 1, r.score() * 100, r.entity().name());
            if (r.matchedAlias() != null) {
                System.out.println("    Matched Alias: " + r.matchedAlias());
            }
        }
    }

    @Test
    void searchOfficeForAlsaraf() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Search 'OFFICE' - Looking for ALSARAF");
        System.out.println("=".repeat(80));
        
        List<SearchResult> results = searchService.search("OFFICE", 50, 0.88);
        
        System.out.println("\nTotal results: " + results.size());
        
        boolean found = false;
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String name = r.entity().name();
            String alias = r.matchedAlias() != null ? r.matchedAlias() : "";
            
            if (name.toUpperCase().contains("WADI ALRRAFIDAYN") || 
                name.toUpperCase().contains("ALSARAF") ||
                alias.toUpperCase().contains("ALSARAF")) {
                found = true;
                System.out.printf("%2d. [%.1f%%] %s%n", i + 1, r.score() * 100, name);
                if (r.matchedAlias() != null) {
                    System.out.println("    Matched Alias: " + r.matchedAlias());
                }
                System.out.println("    ✅ FOUND ALSARAF");
            }
        }
        
        if (!found) {
            System.out.println("\n❌ ALSARAF/WADI ALRRAFIDAYN NOT FOUND in 50 results");
        }
    }
}
