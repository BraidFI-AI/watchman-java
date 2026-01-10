package io.moov.watchman.model;

import io.moov.watchman.similarity.LanguageDetector;
import io.moov.watchman.similarity.TextNormalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a sanctioned entity from OFAC or other watchlists.
 * This is the core domain model for sanctions screening.
 */
public record Entity(
    String id,
    String name,
    EntityType type,
    SourceList source,
    String sourceId,
    Person person,
    Business business,
    Organization organization,
    Aircraft aircraft,
    Vessel vessel,
    ContactInfo contact,
    List<Address> addresses,
    List<CryptoAddress> cryptoAddresses,
    List<String> altNames,
    List<GovernmentId> governmentIds,
    SanctionsInfo sanctionsInfo,
    List<HistoricalInfo> historicalInfo,
    String remarks,
    PreparedFields preparedFields
) {
    /**
     * Creates an Entity with minimal required fields.
     */
    public static Entity of(String id, String name, EntityType type, SourceList source) {
        return new Entity(
            id, name, type, source, id,
            null, null, null, null, null,
            null, List.of(), List.of(), List.of(), List.of(),
            null, List.of(), null, null
        );
    }
    
    /**
     * Normalizes the entity by pre-computing all normalized fields for efficient searching.
     * This should be called at index time, not search time.
     * 
     * Ported from Go: pkg/search/models.go Entity.Normalize() method
     * 
     * @return A new Entity with preparedFields populated
     */
    public Entity normalize() {
        return normalize(new LanguageDetector(), new TextNormalizer());
    }
    
    /**
     * Normalizes with injected dependencies (for testing).
     */
    Entity normalize(LanguageDetector languageDetector, TextNormalizer normalizer) {
        // If already normalized, return as-is
        if (this.preparedFields != null && this.preparedFields.normalizedPrimaryName() != null 
                && !this.preparedFields.normalizedPrimaryName().isEmpty()) {
            return this;
        }
        
        // Normalize primary name
        String normalizedPrimary = "";
        if (name != null && !name.isEmpty()) {
            String reordered = reorderSDNName(name);
            String preprocessed = reordered.replace("'", "").replace("'", "");
            normalizedPrimary = normalizer.lowerAndRemovePunctuation(preprocessed);
            normalizedPrimary = normalizedPrimary.replaceAll("\\s+", " ").trim();
        }
        
        // Normalize alternate names (separate from primary)
        List<String> normalizedAlts = List.of();
        if (altNames != null && !altNames.isEmpty()) {
            normalizedAlts = altNames.stream()
                .map(this::reorderSDNName)
                .map(s -> s.replace("'", "").replace("'", ""))
                .map(normalizer::lowerAndRemovePunctuation)
                .map(s -> s.replaceAll("\\s+", " ").trim())
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        }
        
        // Detect language (needed for stopword removal)
        String detectedLanguage = languageDetector.detect(name);
        
        // Collect ALL names for generating combinations/stopwords/titles
        List<String> allNormalizedNames = new ArrayList<>();
        if (!normalizedPrimary.isEmpty()) {
            allNormalizedNames.add(normalizedPrimary);
        }
        allNormalizedNames.addAll(normalizedAlts);
        
        // Generate word combinations (e.g., "de la" -> "dela")
        List<String> wordCombinations = allNormalizedNames.stream()
            .flatMap(name -> generateWordCombinations(name).stream())
            .distinct()
            .collect(Collectors.toList());
        
        // Remove stopwords (use detected language)
        List<String> namesWithoutStopwords = allNormalizedNames.stream()
            .map(n -> normalizer.removeStopwords(n, detectedLanguage))
            .filter(s -> !s.isEmpty())
            .distinct()
            .collect(Collectors.toList());
        
        // Remove company titles
        List<String> namesWithoutCompanyTitles = allNormalizedNames.stream()
            .map(this::removeCompanyTitles)
            .filter(s -> !s.isEmpty())
            .distinct()
            .collect(Collectors.toList());
        
        // Normalize addresses
        List<String> normalizedAddresses = addresses != null ? addresses.stream()
            .map(addr -> {
                String fullAddr = String.format("%s %s %s %s %s", 
                    addr.line1() != null ? addr.line1() : "",
                    addr.city() != null ? addr.city() : "",
                    addr.state() != null ? addr.state() : "",
                    addr.postalCode() != null ? addr.postalCode() : "",
                    addr.country() != null ? addr.country() : ""
                ).trim();
                return normalizer.lowerAndRemovePunctuation(fullAddr);
            })
            .filter(s -> !s.isEmpty())
            .distinct()
            .collect(Collectors.toList()) : List.of();
        
        PreparedFields prepared = new PreparedFields(
            normalizedPrimary,
            normalizedAlts,
            namesWithoutStopwords,
            namesWithoutCompanyTitles,
            wordCombinations,
            normalizedAddresses,
            detectedLanguage
        );
        
        return new Entity(
            id, name, type, source, sourceId,
            person, business, organization, aircraft, vessel,
            contact, addresses, cryptoAddresses, altNames, governmentIds,
            sanctionsInfo, historicalInfo, remarks, prepared
        );
    }
    
    /**
     * Generates word combinations by merging small connecting words.
     * E.g., "Jean de la Cruz" -> ["jean de la cruz", "jean dela cruz", "jean delacruz"]
     * 
     * Ported from Go: internal/stringscore/jaro_winkler.go GenerateWordCombinations()
     */
    private List<String> generateWordCombinations(String name) {
        List<String> combinations = new ArrayList<>();
        combinations.add(name); // Original
        
        String[] words = name.split("\\s+");
        if (words.length < 2) {
            return combinations;
        }
        
        // Combine short connecting words (2 chars or less) with adjacent words
        List<String> particles = List.of("de", "la", "el", "du", "van", "von", "der", "da", "di", "dos", "das");
        
        // First pass: combine consecutive particles like "de la" -> "dela"
        List<String> processed = new ArrayList<>();
        for (int i = 0; i < words.length; i++) {
            if (particles.contains(words[i].toLowerCase())) {
                // Collect all consecutive particles
                StringBuilder particleGroup = new StringBuilder(words[i]);
                while (i + 1 < words.length && particles.contains(words[i + 1].toLowerCase())) {
                    particleGroup.append(words[i + 1]);
                    i++;
                }
                processed.add(particleGroup.toString());
            } else {
                processed.add(words[i]);
            }
        }
        
        if (processed.size() < words.length) {
            combinations.add(String.join(" ", processed));
        }
        
        // Second pass: combine all particles with the following word
        // E.g., "jean dela cruz" -> "jean delacruz"
        List<String> fullyCollapsed = new ArrayList<>();
        for (int i = 0; i < words.length; i++) {
            if (particles.contains(words[i].toLowerCase()) && i + 1 < words.length) {
                // Merge all consecutive particles with the next non-particle word
                StringBuilder merged = new StringBuilder();
                while (i < words.length && particles.contains(words[i].toLowerCase())) {
                    merged.append(words[i]);
                    i++;
                }
                // Append the non-particle word
                if (i < words.length) {
                    merged.append(words[i]);
                }
                fullyCollapsed.add(merged.toString());
            } else {
                fullyCollapsed.add(words[i]);
            }
        }
        
        if (fullyCollapsed.size() < words.length) {
            combinations.add(String.join(" ", fullyCollapsed));
        }
        
        return combinations;
    }
    
    /**
     * Reorders SDN-style names from "LAST, FIRST" to "FIRST LAST" format.
     * E.g., "SMITH, John Michael" -> "John Michael SMITH"
     * 
     * Ported from Go: internal/prepare/pipeline_reorder.go ReorderSDNName()
     */
    private String reorderSDNName(String name) {
        if (name == null || !name.contains(",")) {
            return name;
        }
        
        // Split on comma
        String[] parts = name.split(",", 2);
        if (parts.length != 2) {
            return name;
        }
        
        // Trim and reorder: "LAST, FIRST" -> "FIRST LAST"
        String lastName = parts[0].trim();
        String firstName = parts[1].trim();
        
        return firstName + " " + lastName;
    }
    
    /**
     * Removes common company titles like LLC, INC, CORP, etc.
     * Iteratively removes all matching suffixes until none remain.
     * 
     * Ported from Go: internal/prepare/pipeline_company_name_cleanup.go RemoveCompanyTitles()
     */
    private String removeCompanyTitles(String name) {
        String cleaned = name;
        boolean changed = true;
        
        // Common company suffixes to remove (check most specific/longest first)
        String[] titles = {
            " incorporated", " corporation", " l l c", " limited", " company",
            " llc", " inc", " corp", " ltd", " co", " sa", " srl", " gmbh"
        };
        
        // Keep removing suffixes until no more matches found
        while (changed) {
            changed = false;
            for (String title : titles) {
                if (cleaned.endsWith(title)) {
                    cleaned = cleaned.substring(0, cleaned.length() - title.length()).trim();
                    changed = true;
                    break;  // Start over with the new cleaned string
                }
            }
        }
        
        return cleaned;
    }
}
