package io.moov.watchman.model;

import org.junit.jupiter.api.Test;

public class DebugNormalizationTest {
    
    @Test
    void debugPunctuationRemoval() {
        Entity entity = Entity.of("test", "O'Brien, James-Michael", EntityType.PERSON, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        PreparedFields prepared = normalized.preparedFields();
        
        System.out.println("\n=== PUNCTUATION TEST ===");
        System.out.println("Input: O'Brien, James-Michael");
        System.out.println("Normalized names:");
        prepared.normalizedNames().forEach(name -> System.out.println("  - " + name));
    }
    
    @Test
    void debugWordCombinations() {
        Entity entity = Entity.of("test", "Jean de la Cruz", EntityType.PERSON, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        PreparedFields prepared = normalized.preparedFields();
        
        System.out.println("\n=== WORD COMBINATIONS TEST ===");
        System.out.println("Input: Jean de la Cruz");
        System.out.println("Word combinations:");
        prepared.wordCombinations().forEach(combo -> System.out.println("  - " + combo));
    }
    
    @Test
    void debugCompanyTitles() {
        Entity entity = Entity.of("test", "Acme Corporation LLC", EntityType.BUSINESS, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        PreparedFields prepared = normalized.preparedFields();
        
        System.out.println("\n=== COMPANY TITLES TEST ===");
        System.out.println("Input: Acme Corporation LLC");
        System.out.println("Names without company titles:");
        prepared.normalizedNamesWithoutCompanyTitles().forEach(name -> System.out.println("  - " + name));
    }
}
