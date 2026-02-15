package io.moov.watchman.observations;

import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class Office39SearchTest {

    @Autowired
    private SearchService searchService;

    @Test
    void searchOffice() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("ROW 35: OFFICE Search (limit=20, minMatch=0.88)");
        System.out.println("=".repeat(80));
        
        List<SearchResult> results = searchService.search("OFFICE", 20, 0.88);
        
        System.out.println("\nTotal results: " + results.size());
        System.out.println();
        
        boolean foundOffice39 = false;
        boolean foundCentralPublic = false;
        boolean foundCyberCrime = false;
        boolean foundAlsaraf = false;
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String name = r.entity().name();
            System.out.printf("%2d. [%.1f%%] %s%n", i + 1, r.score() * 100, name);
            
            if (name.toUpperCase().contains("OFFICE 39") || name.toUpperCase().contains("OFFICE #39")) {
                foundOffice39 = true;
                System.out.println("    ✅ OFFICE 39");
            }
            if (name.toUpperCase().contains("CENTRAL PUBLIC PROSECUTORS OFFICE")) {
                foundCentralPublic = true;
                System.out.println("    ✅ CENTRAL PUBLIC PROSECUTORS OFFICE");
            }
            if (name.toUpperCase().contains("CYBER CRIME OFFICE")) {
                foundCyberCrime = true;
                System.out.println("    ✅ CYBER CRIME OFFICE");
            }
            if (name.toUpperCase().contains("ALSARAF")) {
                foundAlsaraf = true;
                System.out.println("    ✅ ALSARAF HAWALA OFFICE");
            }
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("OFFICE 39 in top 20: " + foundOffice39);
        System.out.println("CENTRAL PUBLIC PROSECUTORS OFFICE in top 20: " + foundCentralPublic);
        System.out.println("CYBER CRIME OFFICE in top 20: " + foundCyberCrime);
        System.out.println("ALSARAF HAWALA OFFICE in top 20: " + foundAlsaraf);
        System.out.println("=".repeat(80));
    }
}
