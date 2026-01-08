package com.company.search;

public class SearchResult {
    private String name;
    private double relevanceScore;
    private String description;
    
    public SearchResult(String name, String description) {
        this.name = name;
        this.description = description;
        this.relevanceScore = calculateRelevanceScore();
    }
    
    private double calculateRelevanceScore() {
        // Align scoring with Go implementation - more conservative scoring
        double score = 0.0;
        
        if (name != null && !name.trim().isEmpty()) {
            // Base score reduced to match Go behavior
            score = 0.6; // Reduced from 0.8
            
            // Adjust for name length - penalize very short names more
            if (name.length() < 5) {
                score *= 0.7; // Increased penalty
            }
        }
        
        return Math.min(score, 1.0);
    }
    
    public String getName() {
        return name;
    }
    
    public double getRelevanceScore() {
        return relevanceScore;
    }
    
    public String getDescription() {
        return description;
    }
}