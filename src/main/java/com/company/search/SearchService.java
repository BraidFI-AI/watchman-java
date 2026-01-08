package com.company.search;

import java.util.*;
import java.util.stream.Collectors;
import java.text.Normalizer;

public class SearchService {
    
    private static final double MINIMUM_SCORE_THRESHOLD = 0.6;
    private static final Set<String> ENTITY_SUFFIXES = Set.of("LLC", "SP", "INC", "CORP", "LTD");
    
    private final EntityRepository entityRepository;
    
    public SearchService(EntityRepository entityRepository) {
        this.entityRepository = entityRepository;
    }
    
    public List<SearchResult> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        String normalizedQuery = normalizeQuery(query);
        List<String> queryTokens = tokenize(normalizedQuery);
        
        List<Entity> candidates = entityRepository.findCandidates(normalizedQuery);
        
        return candidates.stream()
                .map(entity -> scoreEntity(entity, queryTokens, normalizedQuery))
                .filter(result -> result.getScore() >= MINIMUM_SCORE_THRESHOLD)
                .filter(result -> isValidMatch(result, queryTokens))
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .collect(Collectors.toList());
    }
    
    private String normalizeQuery(String query) {
        // Normalize unicode characters
        String normalized = Normalizer.normalize(query, Normalizer.Form.NFD);
        
        // Remove diacritical marks and normalize whitespace
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                              .replaceAll("\\s+", " ")
                              .trim()
                              .toUpperCase();
        
        return normalized;
    }
    
    private List<String> tokenize(String text) {
        return Arrays.stream(text.split("\\s+"))
                    .filter(token -> !token.isEmpty())
                    .map(this::cleanToken)
                    .filter(token -> token.length() > 0)
                    .collect(Collectors.toList());
    }
    
    private String cleanToken(String token) {
        // Remove punctuation but preserve hyphens within words
        return token.replaceAll("^[^\\w-]+|[^\\w-]+$", "")
                   .replaceAll("[^\\w-]", "");
    }
    
    private SearchResult scoreEntity(Entity entity, List<String> queryTokens, String normalizedQuery) {
        String normalizedEntityName = normalizeQuery(entity.getName());
        List<String> entityTokens = tokenize(normalizedEntityName);
        
        double score = calculateScore(queryTokens, entityTokens, normalizedQuery, normalizedEntityName);
        
        return new SearchResult(entity, score);
    }
    
    private double calculateScore(List<String> queryTokens, List<String> entityTokens, 
                                 String normalizedQuery, String normalizedEntityName) {
        
        // Exact match gets highest score
        if (normalizedQuery.equals(normalizedEntityName)) {
            return 1.0;
        }
        
        // Calculate token-based score
        double tokenScore = calculateTokenScore(queryTokens, entityTokens);
        
        // Calculate substring score
        double substringScore = calculateSubstringScore(normalizedQuery, normalizedEntityName);
        
        // Weighted combination
        double combinedScore = (tokenScore * 0.7) + (substringScore * 0.3);
        
        // Apply entity suffix penalty for mismatched entity types
        combinedScore = applyEntitySuffixPenalty(queryTokens, entityTokens, combinedScore);
        
        return Math.min(1.0, combinedScore);
    }
    
    private double calculateTokenScore(List<String> queryTokens, List<String> entityTokens) {
        if (queryTokens.isEmpty() || entityTokens.isEmpty()) {
            return 0.0;
        }
        
        Set<String> querySet = new HashSet<>(queryTokens);
        Set<String> entitySet = new HashSet<>(entityTokens);
        
        // Count exact matches
        int exactMatches = 0;
        for (String queryToken : querySet) {
            if (entitySet.contains(queryToken)) {
                exactMatches++;
            }
        }
        
        // Require all significant query tokens to have matches
        double coverage = (double) exactMatches / querySet.size();
        
        // Penalize if entity has many extra tokens
        double lengthPenalty = Math.min(1.0, (double) queryTokens.size() / entityTokens.size());
        
        return coverage * lengthPenalty;
    }
    
    private double calculateSubstringScore(String query, String entityName) {
        if (entityName.contains(query)) {
            return 0.8 * ((double) query.length() / entityName.length());
        }
        
        // Check for partial matches
        String[] queryParts = query.split("\\s+");
        int matches = 0;
        for (String part : queryParts) {
            if (part.length() >= 3 && entityName.contains(part)) {
                matches++;
            }
        }
        
        return matches > 0 ? 0.4 * ((double) matches / queryParts.length) : 0.0;
    }
    
    private double applyEntitySuffixPenalty(List<String> queryTokens, List<String> entityTokens, 
                                          double currentScore) {
        String queryEntityType = findEntityType(queryTokens);
        String entityEntityType = findEntityType(entityTokens);
        
        // If query specifies an entity type but entity has different type, apply penalty
        if (queryEntityType != null && entityEntityType != null && 
            !queryEntityType.equals(entityEntityType)) {
            return currentScore * 0.3; // Significant penalty for entity type mismatch
        }
        
        // If query specifies entity type but entity has none, moderate penalty
        if (queryEntityType != null && entityEntityType == null) {
            return currentScore * 0.7;
        }
        
        return currentScore;
    }
    
    private String findEntityType(List<String> tokens) {
        return tokens.stream()
                    .filter(ENTITY_SUFFIXES::contains)
                    .findFirst()
                    .orElse(null);
    }
    
    private boolean isValidMatch(SearchResult result, List<String> queryTokens) {
        // Additional validation to prevent false positives
        
        // Must have minimum score
        if (result.getScore() < MINIMUM_SCORE_THRESHOLD) {
            return false;
        }
        
        // For entity-type specific queries, be more strict
        if (queryTokens.stream().anyMatch(ENTITY_SUFFIXES::contains)) {
            return result.getScore() >= 0.8; // Higher threshold for entity-specific queries
        }
        
        return true;
    }
}