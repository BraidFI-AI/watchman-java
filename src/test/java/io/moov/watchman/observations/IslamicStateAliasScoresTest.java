package io.moov.watchman.observations;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.search.EntityScorer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class IslamicStateAliasScoresTest {

    @Autowired
    private EntityIndex entityIndex;

    @Autowired
    private EntityScorer scorer;

    @Test
    void scoreAllAliasesForAlQaidaQuery() {
        System.out.println("\n================================================================");
        System.out.println("ISLAMIC STATE - Check highest-scoring aliases for 'AL QA'IDA'");
        System.out.println("================================================================\n");

        Entity entity = entityIndex.getAll().stream()
            .filter(e -> e.name().equals("ISLAMIC STATE OF IRAQ AND THE LEVANT"))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Entity not found"));

        String query = "AL QA'IDA";

        System.out.println("Query: '" + query + "'");
        System.out.println("Entity: " + entity.name());
        System.out.println("\nAliases containing 'JIHAD' and 'IRAQ':");
        System.out.println("----------------------------------------------------------------");
        
        if (entity.altNames() != null) {
            entity.altNames().stream()
                .filter(alias -> alias.toUpperCase().contains("JIHAD") && alias.toUpperCase().contains("IRAQ"))
                .forEach(alias -> System.out.println("  ⭐ " + alias));
        }

        System.out.println("\nAliases containing 'AL-QAIDA' or 'AL QAIDA' or 'QA'IDAT':");
        System.out.println("----------------------------------------------------------------");
        
        if (entity.altNames() != null) {
            entity.altNames().stream()
                .filter(alias -> alias.toUpperCase().matches(".*AL[- ]QA[']*IDA.*"))
                .forEach(alias -> System.out.println("  " + alias));
        }

        System.out.println("\n================================================================");
        System.out.println("Overall entity score: " + String.format("%.2f%%", scorer.score(query, entity) * 100));
    }
}
