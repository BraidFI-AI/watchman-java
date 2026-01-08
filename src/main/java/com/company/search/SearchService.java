package com.company.search;

import java.util.List;
import java.util.stream.Collectors;

public class SearchService {
    
    private static final double RELEVANCE_THRESHOLD = 0.75; // Increased from 0.5 to match Go
    private static final int MAX_RESULTS = 10; // Limited to match Go behavior
    
    public List<SearchResult> search(String query) {
        List<SearchResult> rawResults = performRawSearch(query);
        
        return rawResults.stream()
            .filter(result -> result.getRelevanceScore() >= RELEVANCE_THRESHOLD)
            .filter(this::isValidResult)
            .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
            .limit(MAX_RESULTS)
            .collect(Collectors.toList());
    }
    
    private boolean isValidResult(SearchResult result) {
        // Add stricter validation to match Go implementation
        if (result.getName() == null || result.getName().trim().isEmpty()) {
            return false;
        }
        
        // Filter out results with insufficient text content
        if (result.getName().length() < 3) {
            return false;
        }
        
        return true;
    }
    
    private List<SearchResult> performRawSearch(String query) {
        // Existing search implementation
        return searchIndex.query(query);
    }
}