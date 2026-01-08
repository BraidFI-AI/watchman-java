package com.company.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SearchServiceTest {
    
    @Mock
    private EntityRepository entityRepository;
    
    private SearchService searchService;
    
    @BeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        searchService = new SearchService(entityRepository);
    }
    
    @Test
    void testLlcTKomponentSpQuery() {
        // Setup test data - entities that might cause false positives
        Entity exactMatch = new Entity("1", "LLC T-KOMPONENT SP");
        Entity partialMatch1 = new Entity("2", "T-KOMPONENT LLC");
        Entity partialMatch2 = new Entity("3", "KOMPONENT TRADING SP");
        Entity falsePositive = new Entity("4", "T-MOBILE KOMPONENTE LLC");
        
        when(entityRepository.findCandidates(anyString()))
            .thenReturn(Arrays.asList(exactMatch, partialMatch1, partialMatch2, falsePositive));
        
        List<SearchResult> results = searchService.search("LLC T-KOMPONENT SP");
        
        // Should only return high-quality matches, not extra results
        assertTrue(results.size() <= 2, "Too many results returned - Java should match Go behavior");
        
        // Exact match should be first
        if (!results.isEmpty()) {
            assertEquals("LLC T-KOMPONENT SP", results.get(0).getEntity().getName());
            assertTrue(results.get(0).getScore() >= 0.8, "Exact match should have high score");
        }
        
        // Verify no low-quality matches are included
        for (SearchResult result : results) {
            assertTrue(result.getScore() >= 0.6, "All results should meet minimum quality threshold");
        }
    }
    
    @Test
    void testEntityTypeMismatchPenalty() {
        Entity llcEntity = new Entity("1", "KOMPONENT LLC");
        Entity spEntity = new Entity("2", "KOMPONENT SP");
        
        when(entityRepository.findCandidates(anyString()))
            .thenReturn(Arrays.asList(llcEntity, spEntity));
        
        List<SearchResult> results = searchService.search("LLC KOMPONENT SP");
        
        // Should heavily penalize entity type mismatches
        for (SearchResult result : results) {
            if (result.getEntity().getName().contains("LLC") && !result.getEntity().getName().endsWith("SP")) {
                assertTrue(result.getScore() < 0.5, "Entity type mismatch should be heavily penalized");
            }
        }
    }
    
    @Test
    void testEmptyQuery() {
        List<SearchResult> results = searchService.search("");
        assertTrue(results.isEmpty());
        
        results = searchService.search(null);
        assertTrue(results.isEmpty());
    }
    
    @Test
    void testMinimumScoreThreshold() {
        Entity lowQualityMatch = new Entity("1", "SOME UNRELATED COMPANY LLC");
        
        when(entityRepository.findCandidates(anyString()))
            .thenReturn(Arrays.asList(lowQualityMatch));
        
        List<SearchResult> results = searchService.search("LLC T-KOMPONENT SP");
        
        // Should filter out low-quality matches
        assertTrue(results.isEmpty() || results.stream().allMatch(r -> r.getScore() >= 0.6));
    }
}