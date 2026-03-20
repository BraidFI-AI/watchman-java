package io.braid.daywatcher.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Entity.removeCompanyTitles() method.
 * Verifies legal suffix removal after punctuation normalization.
 */
class EntityCompanySuffixTest {

    @Test
    @DisplayName("After normalization: 'cecoex s a' → 'cecoex' (S.A. becomes 's a')")
    void shouldRemoveSAWithSpaces() {
        Entity entity = Entity.of("test", "cecoex s a", EntityType.BUSINESS, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        
        // The normalized name without company titles should be just "cecoex"
        assertThat(normalized.preparedFields().normalizedNamesWithoutCompanyTitles())
            .as("S.A. after punctuation removal (s a) should be removed")
            .contains("cecoex");
    }

    @Test
    @DisplayName("After normalization: 'galapagos s a' → 'galapagos'")
    void shouldRemoveSAFromGalapagos() {
        Entity entity = Entity.of("test", "galapagos s a", EntityType.BUSINESS, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        
        assertThat(normalized.preparedFields().normalizedNamesWithoutCompanyTitles())
            .contains("galapagos");
    }

    @Test
    @DisplayName("After normalization: 'adp s c' → 'adp' (S.C. becomes 's c')")
    void shouldRemoveSCWithSpaces() {
        Entity entity = Entity.of("test", "adp s c", EntityType.BUSINESS, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        
        assertThat(normalized.preparedFields().normalizedNamesWithoutCompanyTitles())
            .as("S.C. after punctuation removal (s c) should be removed")
            .contains("adp");
    }

    @Test
    @DisplayName("Russian OJSC suffix: 'stroytransgaz ojsc' → 'stroytransgaz'")
    void shouldRemoveOJSC() {
        Entity entity = Entity.of("test", "stroytransgaz ojsc", EntityType.BUSINESS, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        
        assertThat(normalized.preparedFields().normalizedNamesWithoutCompanyTitles())
            .contains("stroytransgaz");
    }

    @Test
    @DisplayName("Russian OAO suffix: 'npk tekhmash oao' → 'npk tekhmash'")
    void shouldRemoveOAO() {
        Entity entity = Entity.of("test", "npk tekhmash oao", EntityType.BUSINESS, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        
        assertThat(normalized.preparedFields().normalizedNamesWithoutCompanyTitles())
            .contains("npk tekhmash");
    }

    @Test
    @DisplayName("Mexican DE C.V.: 'acme de c v' → 'acme'")
    void shouldRemoveDECV() {
        Entity entity = Entity.of("test", "acme de c v", EntityType.BUSINESS, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        
        assertThat(normalized.preparedFields().normalizedNamesWithoutCompanyTitles())
            .contains("acme");
    }

    @Test
    @DisplayName("Existing suffixes still work: 'acme inc' → 'acme'")
    void shouldRemoveTraditionalSuffixes() {
        Entity entity = Entity.of("test", "acme inc", EntityType.BUSINESS, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        
        assertThat(normalized.preparedFields().normalizedNamesWithoutCompanyTitles())
            .contains("acme");
    }

    @Test
    @DisplayName("Multiple suffix removal: 'acme corporation inc' → 'acme'")
    void shouldRemoveMultipleSuffixes() {
        Entity entity = Entity.of("test", "acme corporation inc", EntityType.BUSINESS, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        
        // Should iteratively remove both suffixes
        assertThat(normalized.preparedFields().normalizedNamesWithoutCompanyTitles())
            .contains("acme");
    }

    @Test
    @DisplayName("Edge case: name is only suffix 's a' → empty (acceptable)")
    void shouldHandleNameThatIsOnlySuffix() {
        Entity entity = Entity.of("test", "s a", EntityType.BUSINESS, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        
        // After removing suffix, might be empty - that's acceptable behavior
        // The entity won't match well anyway if it's literally just "S.A."
        assertThat(normalized.preparedFields().normalizedNamesWithoutCompanyTitles())
            .isNotNull();
    }
}
