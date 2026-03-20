package io.braid.daywatcher.observations;

import io.braid.daywatcher.index.EntityIndex;
import io.braid.daywatcher.model.Entity;
import io.braid.daywatcher.search.EntityScorer;
import io.braid.daywatcher.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class BSAEntityScoreVerificationTest {

    @Autowired
    private EntityIndex entityIndex;

    @Autowired
    private EntityScorer scorer;

    @Autowired
    private SearchService searchService;

    @Test
    void listAllEntitiesWithAlQaidaAliases() {
        System.out.println("\n================================================================");
        System.out.println("ENTITIES WITH AL-QA*IDA ALIASES");
        System.out.println("================================================================\n");

        List<Entity> entities =entityIndex.getAll().stream()
            .filter(e -> e.altNames() != null && e.altNames().stream()
                .anyMatch(alt -> alt.matches(".*AL[- ]QA[']*IDA.*")))
            .toList();

        System.out.println("Found " + entities.size() + " entities with AL-QA*IDA variants in aliases\n");

        for (Entity entity : entities) {
            double score = scorer.score("AL QA'IDA", entity);
            System.out.println(String.format("[%5.1f%%] %s", score * 100, entity.name()));
            
            // Show matching aliases
            entity.altNames().stream()
                .filter(alt -> alt.matches(".*AL[- ]QA[']*IDA.*"))
                .forEach(alt -> System.out.println("         → " + alt));
            System.out.println();
        }

        System.out.println("\nNow running actual search for 'AL QA'IDA' (limit=10, minMatch=0.88):");
        var results = searchService.search("AL QA'IDA", 10, 0.88);
        for (int i = 0; i < results.size(); i++) {
            var result = results.get(i);
            System.out.println(String.format("%2d. [%5.1f%%] %s", 
                i + 1, result.score() * 100, result.entity().name()));
        }
    }
}
