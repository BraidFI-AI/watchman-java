package com.company.search;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SearchResult {
    private final SearchableEntity entity;
    private final double score;
    
    public SearchResult(SearchableEntity entity, double score) {
        this.entity = entity;
        // Round to 4 decimal places to match Go precision
        this.score = new BigDecimal(score)
            .setScale(4, RoundingMode.HALF_UP)
            .doubleValue();
    }
    
    public SearchableEntity getEntity() {
        return entity;
    }
    
    public double getScore() {
        return score;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        SearchResult that = (SearchResult) obj;
        return Double.compare(that.score, score) == 0 && 
               Objects.equals(entity, that);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(entity, score);
    }
    
    @Override
    public String toString() {
        return String.format("SearchResult{entity=%s, score=%.4f}", entity, score);
    }
}