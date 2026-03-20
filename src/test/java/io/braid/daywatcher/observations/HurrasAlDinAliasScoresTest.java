package io.braid.daywatcher.observations;

import io.braid.daywatcher.index.EntityIndex;
import io.braid.daywatcher.model.Entity;
import io.braid.daywatcher.similarity.SimilarityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class HurrasAlDinAliasScoresTest {

    @Autowired
    private EntityIndex entityIndex;

    @Autowired
    private SimilarityService similarityService;

    @Test
    void showAllAliasScores() {
        System.out.println("\n================================================================");
        System.out.println("HURRAS AL-DIN ALIAS SCORES FOR 'AL QA'IDA'");
        System.out.println("================================================================\n");

        Entity entity = entityIndex.getAll().stream()
            .filter(e -> e.name().equals("HURRAS AL-DIN"))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Entity not found"));

        String query = "AL QA'IDA";
        
        System.out.println("Query: " + query);
        System.out.println("Entity: " + entity.name());
        System.out.println("\nAll Alias Scores:");
        System.out.println("----------------------------------------------------------------");

        List<String> aliases = entity.altNames();
        double maxScore = 0;
        String bestAlias = null;
        
        for (String alias : aliases) {
            double score = similarityService.tokenizedSimilarity(query, alias);
            System.out.println(String.format("[%.2f%%] %s", score * 100, alias));
            
            if (score > maxScore) {
                maxScore = score;
                bestAlias = alias;
            }
        }

        System.out.println("\n================================================================");
        System.out.println("BEST MATCH: " + bestAlias);
        System.out.println("Score: " + String.format("%.2f%%", maxScore * 100));
        System.out.println("\nThis is the alias that will be set as matchedAlias");
    }
}
