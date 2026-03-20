package io.braid.daywatcher.observations;

import io.braid.daywatcher.index.EntityIndex;
import io.braid.daywatcher.model.Entity;
import io.braid.daywatcher.model.ScoreBreakdown;
import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.search.EntityScorer;
import io.braid.daywatcher.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

@SpringBootTest
class TEGComprehensiveDebugTest {

    @Autowired
    private EntityIndex entityIndex;
    
    @Autowired
    private SearchService searchService;
    
    @Autowired
    private EntityScorer entityScorer;

    @Test
    void comprehensiveTEGDebug() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("COMPREHENSIVE T.E.G. LIMITED DEBUG - Finding the Regression");
        System.out.println("=".repeat(100));
        
        // Step 1: Check if entity exists in index
        System.out.println("\n[STEP 1] Check if T.E.G. LIMITED exists in entity index");
        List<Entity> allEntities = entityIndex.getAll();
        System.out.println("Total entities in index: " + allEntities.size());
        
        Optional<Entity> tegEntityOpt = allEntities.stream()
            .filter(e -> e.name().equals("T.E.G. LIMITED"))
            .findFirst();
        
        if (!tegEntityOpt.isPresent()) {
            System.out.println("❌ CRITICAL: T.E.G. LIMITED NOT FOUND IN INDEX!");
            System.out.println("This is the root cause - entity is missing from index.");
            return;
        }
        
        Entity tegEntity = tegEntityOpt.get();
        System.out.println("✅ Entity found in index:");
        System.out.println("   ID: " + tegEntity.id());
        System.out.println("   Name: " + tegEntity.name());
        System.out.println("   Type: " + tegEntity.type());
        
        // Step 2: Check normalized fields
        System.out.println("\n[STEP 2] Check entity normalization");
        if (tegEntity.preparedFields() == null) {
            System.out.println("❌ CRITICAL: preparedFields is NULL - entity not normalized!");
            return;
        }
        
        System.out.println("✅ Prepared fields exist:");
        System.out.println("   Normalized primary: " + tegEntity.preparedFields().normalizedPrimaryName());
        System.out.println("   Normalized alts: " + tegEntity.preparedFields().normalizedAltNames());
        
        // Step 3: Score the entity directly
        System.out.println("\n[STEP 3] Score T.E.G. LIMITED directly with EntityScorer");
        Entity queryEntity = Entity.of(null, "TEG LIMITED", null, null);
        ScoreBreakdown breakdown = entityScorer.scoreWithBreakdown(queryEntity, tegEntity, null);
        System.out.println("   Query: \"TEG LIMITED\"");
        System.out.println("   Score: " + String.format("%.4f", breakdown.totalWeightedScore()));
        System.out.println("   Name score: " + breakdown.nameScore());
        
        if (breakdown.totalWeightedScore() < 0.88) {
            System.out.println("❌ REGRESSION: Score is below 88% threshold!");
            System.out.println("   This entity would be filtered out during search.");
        } else {
            System.out.println("✅ Score passes 88% threshold");
        }
        
        // Step 4: Run actual search
        System.out.println("\n[STEP 4] Run actual SearchService.search()");
        List<SearchResult> results = searchService.search("TEG LIMITED", 50, 0.88);
        System.out.println("   Results returned: " + results.size());
        
        boolean foundInResults = results.stream()
            .anyMatch(r -> r.entity().name().equals("T.E.G. LIMITED"));
        
        if (foundInResults) {
            System.out.println("✅ T.E.G. LIMITED found in search results!");
            SearchResult tegResult = results.stream()
                .filter(r -> r.entity().name().equals("T.E.G. LIMITED"))
                .findFirst().get();
            int position = results.indexOf(tegResult) + 1;
            System.out.println("   Position: " + position);
            System.out.println("   Score: " + String.format("%.4f", tegResult.score()));
        } else {
            System.out.println("❌ CRITICAL REGRESSION: T.E.G. LIMITED NOT in search results!");
            System.out.println("\n   Top 10 results returned instead:");
            for (int i = 0; i < Math.min(10, results.size()); i++) {
                SearchResult r = results.get(i);
                System.out.printf("   %2d. [%.2f%%] %s%n", 
                    i + 1, r.score() * 100, r.entity().name());
            }
        }
        
        // Step 5: Check with lower threshold
        System.out.println("\n[STEP 5] Try search with lower threshold (70%)");
        List<SearchResult> resultsLowThreshold = searchService.search("TEG LIMITED", 50, 0.70);
        boolean foundWithLowerThreshold = resultsLowThreshold.stream()
            .anyMatch(r -> r.entity().name().equals("T.E.G. LIMITED"));
        
        if (foundWithLowerThreshold) {
            SearchResult tegResult = resultsLowThreshold.stream()
                .filter(r -> r.entity().name().equals("T.E.G. LIMITED"))
                .findFirst().get();
            System.out.println("✅ Found with 70% threshold");
            System.out.println("   Position: " + (resultsLowThreshold.indexOf(tegResult) + 1));
            System.out.println("   Score: " + String.format("%.4f", tegResult.score()));
            System.out.println("\n⚠️  DIAGNOSIS: Entity scores between 70-88%, getting filtered!");
        } else {
            System.out.println("❌ Still not found even at 70% threshold");
            System.out.println("   This suggests a deeper pipeline issue.");
        }
        
        System.out.println("\n" + "=".repeat(100));
        System.out.println("DEBUG COMPLETE");
        System.out.println("=".repeat(100));
    }
}
