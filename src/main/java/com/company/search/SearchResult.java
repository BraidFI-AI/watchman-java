package com.company.search;

public class SearchResult {
    private final Entity entity;
    private final double score;
    
    public SearchResult(Entity entity, double score) {
        this.entity = entity;
        this.score = score;
    }
    
    public Entity getEntity() {
        return entity;
    }
    
    public double getScore() {
        return score;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        SearchResult that = (SearchResult) o;
        return Double.compare(that.score, score) == 0 && 
               entity.equals(that.entity);
    }
    
    @Override
    public int hashCode() {
        return entity.hashCode() * 31 + Double.hashCode(score);
    }
}