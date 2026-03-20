package io.braid.daywatcher.config;

import io.braid.daywatcher.index.EntityIndex;
import io.braid.daywatcher.index.InMemoryEntityIndex;
import io.braid.daywatcher.parser.*;
import io.braid.daywatcher.search.EntityScorer;
import io.braid.daywatcher.search.EntityScorerImpl;
import io.braid.daywatcher.search.SearchService;
import io.braid.daywatcher.search.SearchServiceImpl;
import io.braid.daywatcher.similarity.JaroWinklerSimilarity;
import io.braid.daywatcher.similarity.SimilarityService;
import io.braid.daywatcher.similarity.TextNormalizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for Watchman application beans.
 */
@Configuration
public class WatchmanConfig {

    @Bean
    public TextNormalizer textNormalizer() {
        return new TextNormalizer();
    }

    @Bean
    public SimilarityService similarityService(SimilarityConfig config) {
        return new JaroWinklerSimilarity(
            new TextNormalizer(),
            new io.braid.daywatcher.similarity.PhoneticFilter(true),
            config
        );
    }

    @Bean
    public EntityIndex entityIndex() {
        return new InMemoryEntityIndex();
    }

    @Bean
    public OFACParser ofacParser() {
        return new OFACParserImpl();
    }

    @Bean
    public CSLParser cslParser() {
        return new CSLParserImpl();
    }

    @Bean
    public EUCSLParser euCslParser() {
        return new EUCSLParserImpl();
    }

    @Bean
    public UKCSLParser ukCslParser() {
        return new UKCSLParserImpl();
    }

    @Bean
    public EntityScorer entityScorer(SimilarityService similarityService, WeightConfig weightConfig) {
        return new EntityScorerImpl(similarityService, weightConfig);
    }

    @Bean
    public SearchService searchService(EntityIndex entityIndex, EntityScorer entityScorer, 
                                       AutoClearanceConfig autoClearanceConfig, SearchConfig searchConfig,
                                       io.braid.daywatcher.scorer.AddressComparer addressComparer) {
        return new SearchServiceImpl(entityIndex, entityScorer, autoClearanceConfig, searchConfig, addressComparer);
    }
}
