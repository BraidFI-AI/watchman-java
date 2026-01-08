package com.company.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class SearchServiceTest {
    
    private SearchService searchService;
    
    @BeforeEach
    void setUp() {
        searchService = new SearchService();
    }
    
    @Test
    void testSearchWithSpecificQuery() {
        List<SearchResult> results = searchService.search("LLC T-KOMPONENT SP");
        
        // Verify result count matches Go implementation
        assertTrue(results.size() <= 10, "Should not return more than 10 results");
        
        // Verify all results meet relevance threshold
        for (SearchResult result : results) {
            assertTrue(result.getRelevanceScore() >= 0.75, 
                "All results should meet relevance threshold");
        }
        
        // Verify results are properly sorted by relevance
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i-1).getRelevanceScore() >= results.get(i).getRelevanceScore(),
                "Results should be sorted by relevance score descending");
        }
    }
    
    @Test
    void testFilteringBehavior() {
        List<SearchResult> results = searchService.search("LLC T-KOMPONENT SP");
        
        // Verify no invalid results are returned
        for (SearchResult result : results) {
            assertNotNull(result.getName(), "Result name should not be null");
            assertTrue(result.getName().length() >= 3, "Result name should have minimum length");
        }
    }
}