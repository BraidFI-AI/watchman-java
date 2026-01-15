package io.moov.watchman.config;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.index.InMemoryEntityIndex;
import io.moov.watchman.parser.*;
import io.moov.watchman.search.EntityScorer;
import io.moov.watchman.search.EntityScorerImpl;
import io.moov.watchman.search.SearchService;
import io.moov.watchman.search.SearchServiceImpl;
import io.moov.watchman.similarity.JaroWinklerSimilarity;
import io.moov.watchman.similarity.SimilarityService;
import io.moov.watchman.similarity.TextNormalizer;
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
            new io.moov.watchman.similarity.PhoneticFilter(true),
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
    public EntityScorer entityScorer(SimilarityService similarityService) {
        return new EntityScorerImpl(similarityService);
    }

    @Bean
    public SearchService searchService(EntityIndex entityIndex, EntityScorer entityScorer) {
        return new SearchServiceImpl(entityIndex, entityScorer);
    }
}
