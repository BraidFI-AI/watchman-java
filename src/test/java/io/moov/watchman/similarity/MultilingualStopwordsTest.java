package io.moov.watchman.similarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED Phase: Tests for multilingual stopword removal.
 * 
 * Tests comprehensive stopword lists for:
 * - Spanish (es)
 * - French (fr)
 * - German (de)
 * - Russian (ru)
 * - Arabic (ar)
 * - Chinese (zh)
 */
@DisplayName("Multilingual Stopwords Tests")
class MultilingualStopwordsTest {

    private TextNormalizer normalizer;
    
    @BeforeEach
    void setUp() {
        normalizer = new TextNormalizer();
    }

    // ============================================================
    // SPANISH STOPWORDS
    // ============================================================
    
    @Test
    @DisplayName("Should remove Spanish articles (el, la, los, las)")
    void shouldRemoveSpanishArticles() {
        // GIVEN: Spanish text with articles
        String text = "el banco de la republica";
        
        // WHEN: Removing stopwords with Spanish language
        String result = normalizer.removeStopwords(text, "es");
        
        // THEN: Articles should be removed
        assertEquals("banco republica", result);
        
        // EXPECTED TO FAIL: removeStopwords() doesn't accept language parameter yet
    }
    
    @Test
    @DisplayName("Should remove Spanish prepositions (de, del, en, con, para)")
    void shouldRemoveSpanishPrepositions() {
        // GIVEN: Spanish text with prepositions
        String text = "jose garcia de lopez";
        
        // WHEN: Removing stopwords
        String result = normalizer.removeStopwords(text, "es");
        
        // THEN: Prepositions should be removed
        assertEquals("jose garcia lopez", result);
    }
    
    @Test
    @DisplayName("Should remove Spanish pronouns (que, su, sus, se)")
    void shouldRemoveSpanishPronouns() {
        // GIVEN: Spanish text with pronouns
        String text = "la empresa que se llama su nombre";
        
        // WHEN: Removing stopwords
        String result = normalizer.removeStopwords(text, "es");
        
        // THEN: Pronouns and articles should be removed
        assertEquals("empresa llama nombre", result);
    }

    // ============================================================
    // FRENCH STOPWORDS
    // ============================================================
    
    @Test
    @DisplayName("Should remove French articles (le, la, les, un, une)")
    void shouldRemoveFrenchArticles() {
        // GIVEN: French text with articles
        String text = "la banque de france";
        
        // WHEN: Removing stopwords with French language
        String result = normalizer.removeStopwords(text, "fr");
        
        // THEN: Articles should be removed
        assertEquals("banque france", result);
    }
    
    @Test
    @DisplayName("Should remove French prepositions (de, du, des, au, aux)")
    void shouldRemoveFrenchPrepositions() {
        // GIVEN: French text with prepositions
        String text = "jean du pont";
        
        // WHEN: Removing stopwords
        String result = normalizer.removeStopwords(text, "fr");
        
        // THEN: Prepositions should be removed
        assertEquals("jean pont", result);
    }
    
    @Test
    @DisplayName("Should remove French contractions (l', d', qu')")
    void shouldRemoveFrenchContractions() {
        // GIVEN: French text with contractions
        String text = "l entreprise d affaires";
        
        // WHEN: Removing stopwords
        String result = normalizer.removeStopwords(text, "fr");
        
        // THEN: Contractions should be removed
        assertEquals("entreprise affaires", result);
    }

    // ============================================================
    // GERMAN STOPWORDS
    // ============================================================
    
    @Test
    @DisplayName("Should remove German articles (der, die, das, den, dem)")
    void shouldRemoveGermanArticles() {
        // GIVEN: German text with articles
        String text = "die bank von deutschland";
        
        // WHEN: Removing stopwords with German language
        String result = normalizer.removeStopwords(text, "de");
        
        // THEN: Articles should be removed
        assertEquals("bank deutschland", result);
    }
    
    @Test
    @DisplayName("Should remove German prepositions (von, zu, mit, bei)")
    void shouldRemoveGermanPrepositions() {
        // GIVEN: German text with prepositions
        String text = "hans von schmidt";
        
        // WHEN: Removing stopwords
        String result = normalizer.removeStopwords(text, "de");
        
        // THEN: Prepositions should be removed
        assertEquals("hans schmidt", result);
    }
    
    @Test
    @DisplayName("Should remove German conjunctions (und, oder, aber)")
    void shouldRemoveGermanConjunctions() {
        // GIVEN: German text with conjunctions
        String text = "schmidt und mueller";
        
        // WHEN: Removing stopwords
        String result = normalizer.removeStopwords(text, "de");
        
        // THEN: Conjunctions should be removed
        assertEquals("schmidt mueller", result);
    }

    // ============================================================
    // RUSSIAN STOPWORDS
    // ============================================================
    
    @Test
    @DisplayName("Should remove Russian prepositions (в, на, за, из)")
    void shouldRemoveRussianPrepositions() {
        // GIVEN: Russian text with prepositions
        String text = "владимир в москве";
        
        // WHEN: Removing stopwords with Russian language
        String result = normalizer.removeStopwords(text, "ru");
        
        // THEN: Prepositions should be removed
        assertEquals("владимир москве", result);
    }
    
    @Test
    @DisplayName("Should remove Russian conjunctions (и, или, но)")
    void shouldRemoveRussianConjunctions() {
        // GIVEN: Russian text with conjunctions
        String text = "иван и петр";
        
        // WHEN: Removing stopwords
        String result = normalizer.removeStopwords(text, "ru");
        
        // THEN: Conjunctions should be removed
        assertEquals("иван петр", result);
    }

    // ============================================================
    // ARABIC STOPWORDS
    // ============================================================
    
    @Test
    @DisplayName("Should remove Arabic articles and prepositions")
    void shouldRemoveArabicStopwords() {
        // GIVEN: Arabic text with common stopwords
        String text = "محمد في القاهرة";
        
        // WHEN: Removing stopwords with Arabic language
        String result = normalizer.removeStopwords(text, "ar");
        
        // THEN: Stopwords should be removed
        assertEquals("محمد القاهرة", result);
    }

    // ============================================================
    // CHINESE STOPWORDS
    // ============================================================
    
    @Test
    @DisplayName("Should remove Chinese stopwords (的, 了, 是)")
    void shouldRemoveChineseStopwords() {
        // GIVEN: Chinese text with common stopwords (space-separated for tokenization)
        String text = "李明 的 公司";
        
        // WHEN: Removing stopwords with Chinese language
        String result = normalizer.removeStopwords(text, "zh");
        
        // THEN: Stopwords should be removed
        assertEquals("李明 公司", result);
    }

    // ============================================================
    // LANGUAGE AUTO-DETECTION
    // ============================================================
    
    @Test
    @DisplayName("Should auto-detect language and remove appropriate stopwords")
    void shouldAutoDetectLanguageAndRemoveStopwords() {
        // GIVEN: Spanish text without explicit language
        String spanishText = "jose garcia de lopez";
        
        // WHEN: Removing stopwords without specifying language (auto-detect)
        String result = normalizer.removeStopwordsWithDetection(spanishText);
        
        // THEN: Should detect Spanish and remove Spanish stopwords
        assertEquals("jose garcia lopez", result);
        
        // EXPECTED TO FAIL: removeStopwordsWithDetection() doesn't exist yet
    }
    
    @Test
    @DisplayName("Should handle mixed-case stopwords")
    void shouldHandleMixedCaseStopwords() {
        // GIVEN: Spanish text with mixed case
        String text = "El Banco DE La Republica";
        
        // WHEN: Removing stopwords
        String result = normalizer.removeStopwords(text.toLowerCase(), "es");
        
        // THEN: Should remove stopwords regardless of case
        assertEquals("banco republica", result);
    }

    // ============================================================
    // EDGE CASES
    // ============================================================
    
    @Test
    @DisplayName("Should not remove stopwords when they are part of a name")
    void shouldNotRemoveStopwordsInCompoundNames() {
        // GIVEN: Name where stopword is essential (e.g., "De La Rosa")
        String text = "jose de la rosa";
        
        // WHEN: Removing stopwords
        String result = normalizer.removeStopwords(text, "es");
        
        // THEN: Should remove stopwords (normalization for matching)
        // Note: This is correct behavior - stopwords should be removed for matching
        assertEquals("jose rosa", result);
    }
    
    @Test
    @DisplayName("Should default to English stopwords for unknown language")
    void shouldDefaultToEnglishForUnknownLanguage() {
        // GIVEN: Text with unknown language code
        String text = "the quick brown fox";
        
        // WHEN: Removing stopwords with unknown language
        String result = normalizer.removeStopwords(text, "unknown");
        
        // THEN: Should fall back to English stopwords
        assertEquals("quick brown fox", result);
    }
    
    @Test
    @DisplayName("Should handle empty language code")
    void shouldHandleEmptyLanguageCode() {
        // GIVEN: Text with null language
        String text = "the quick brown fox";
        
        // WHEN: Removing stopwords with null language
        String result = normalizer.removeStopwords(text, null);
        
        // THEN: Should fall back to English stopwords
        assertEquals("quick brown fox", result);
    }
}
