package com.company.search;

import java.util.*;
import java.util.stream.Collectors;

public class SearchService {
    private static final double MINIMUM_SCORE_THRESHOLD = 0.5;
    private static final int MAX_RESULTS = 10;
    
    private final List<SearchableEntity> entities;
    
    public SearchService(List<SearchableEntity> entities) {
        this.entities = entities;
    }
    
    public List<SearchResult> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        String normalizedQuery = normalizeQuery(query);
        
        return entities.stream()
            .map(entity -> calculateScore(entity, normalizedQuery))
            .filter(result -> result.getScore() >= MINIMUM_SCORE_THRESHOLD)
            .sorted((r1, r2) -> Double.compare(r2.getScore(), r1.getScore()))
            .limit(MAX_RESULTS)
            .collect(Collectors.toList());
    }
    
    private SearchResult calculateScore(SearchableEntity entity, String query) {
        String normalizedEntityName = normalizeQuery(entity.getName());
        
        // Fixed scoring algorithm to match Go implementation
        double score = 0.0;
        
        // Exact match gets highest score
        if (normalizedEntityName.equals(query)) {
            score = 1.0;
        }
        // Contains check with position weighting (matching Go logic)
        else if (normalizedEntityName.contains(query)) {
            int position = normalizedEntityName.indexOf(query);
            double positionWeight = 1.0 - (position / (double) normalizedEntityName.length());
            double lengthRatio = (double) query.length() / normalizedEntityName.length();
            score = 0.8 * positionWeight * lengthRatio;
        }
        // Word boundary matching
        else {
            String[] queryWords = query.split("\\s+");
            String[] entityWords = normalizedEntityName.split("\\s+");
            
            int matchedWords = 0;
            for (String queryWord : queryWords) {
                for (String entityWord : entityWords) {
                    if (entityWord.startsWith(queryWord) || entityWord.contains(queryWord)) {
                        matchedWords++;
                        break;
                    }
                }
            }
            
            if (matchedWords > 0) {
                score = 0.6 * ((double) matchedWords / queryWords.length);
            }
        }
        
        return new SearchResult(entity, score);
    }
    
    private String normalizeQuery(String input) {
        return input.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", "")
            .replaceAll("\\s+", " ")
            .trim();
    }
}