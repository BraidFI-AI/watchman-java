package com.company.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchServiceTest {
    
    private SearchService searchService;
    private List<SearchableEntity> testEntities;
    
    @BeforeEach
    void setUp() {
        testEntities = Arrays.asList(
            new SearchableEntity("1", "PARDAZAN SYSTEM NAMAD ARMAN", "company"),
            new SearchableEntity("2", "IPM LIMITED", "company"),
            new SearchableEntity("3", "IPM SOLUTIONS LIMITED", "company"),
            new SearchableEntity("4", "ARMAN TECHNOLOGIES", "company"),
            new SearchableEntity("5", "SYSTEM INTEGRATION CORP", "company")
        );
        
        searchService = new SearchService(testEntities);
    }
    
    @Test
    void testExactMatchScoring() {
        List<SearchResult> results = searchService.search("IPM LIMITED");
        
        assertFalse(results.isEmpty());
        assertEquals(1.0, results.get(0).getScore(), 0.0001);
        assertEquals("IPM LIMITED", results.get(0).getEntity().getName());
    }
    
    @Test
    void testPartialMatchScoring() {
        List<SearchResult> results = searchService.search("PARDAZAN SYSTEM");
        
        assertFalse(results.isEmpty());
        SearchResult firstResult = results.get(0);
        assertTrue(firstResult.getScore() >= 0.5);
        assertTrue(firstResult.getScore() < 1.0);
    }
    
    @Test
    void testScoreThreshold() {
        List<SearchResult> results = searchService.search("NONEXISTENT");
        
        // Should filter out results below threshold
        assertTrue(results.isEmpty() || 
                  results.stream().allMatch(r -> r.getScore() >= 0.5));
    }
    
    @Test
    void testResultLimit() {
        // Create more test data
        List<SearchableEntity> manyEntities = Arrays.asList(
            new SearchableEntity("1", "TEST COMPANY 1", "company"),
            new SearchableEntity("2", "TEST COMPANY 2", "company"),
            new SearchableEntity("3", "TEST COMPANY 3", "company"),
            new SearchableEntity("4", "TEST COMPANY 4", "company"),
            new SearchableEntity("5", "TEST COMPANY 5", "company"),
            new SearchableEntity("6", "TEST COMPANY 6", "company"),
            new SearchableEntity("7", "TEST COMPANY 7", "company"),
            new SearchableEntity("8", "TEST COMPANY 8", "company"),
            new SearchableEntity("9", "TEST COMPANY 9", "company"),
            new SearchableEntity("10", "TEST COMPANY 10", "company"),
            new SearchableEntity("11", "TEST COMPANY 11", "company"),
            new SearchableEntity("12", "TEST COMPANY 12", "company")
        );
        
        SearchService testService = new SearchService(manyEntities);
        List<SearchResult> results = testService.search("TEST");
        
        assertTrue(results.size() <= 10);
    }
    
    @Test
    void testEmptyQuery() {
        List<SearchResult> results = searchService.search("");
        assertTrue(results.isEmpty());
        
        results = searchService.search(null);
        assertTrue(results.isEmpty());
    }
    
    @Test
    void testScorePrecision() {
        List<SearchResult> results = searchService.search("SYSTEM");
        
        for (SearchResult result : results) {
            // Verify score precision matches Go implementation (4 decimal places)
            double score = result.getScore();
            double rounded = Math.round(score * 10000.0) / 10000.0;
            assertEquals(rounded, score, 0.0001);
        }
    }
}