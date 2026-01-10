package io.moov.watchman.model;

import io.moov.watchman.normalize.PhoneNormalizer;
import io.moov.watchman.scorer.AddressNormalizer;
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
        
        // Detect language (needed for stopword removal)
        String detectedLanguage = languageDetector.detect(name);
        
        // Normalize primary name (with stopword removal)
        String normalizedPrimary = "";
        if (name != null && !name.isEmpty()) {
            String reordered = reorderSDNName(name);
            String preprocessed = reordered.replace("'", "").replace("'", "");
            String withPunctRemoved = normalizer.lowerAndRemovePunctuation(preprocessed);
            withPunctRemoved = withPunctRemoved.replaceAll("\\s+", " ").trim();
            // Phase 17: Remove stopwords from normalized primary name
            normalizedPrimary = normalizer.removeStopwords(withPunctRemoved, detectedLanguage);
        }
        
        // Normalize alternate names (with stopword removal, per-name language detection)
        List<String> normalizedAlts = List.of();
        if (altNames != null && !altNames.isEmpty()) {
            normalizedAlts = altNames.stream()
                .map(altName -> {
                    // Detect language on original alt name
                    String altLang = languageDetector.detect(altName);
                    // Then normalize
                    String reordered = reorderSDNName(altName);
                    String preprocessed = reordered.replace("'", "").replace("'", "");
                    String withPunctRemoved = normalizer.lowerAndRemovePunctuation(preprocessed);
                    withPunctRemoved = withPunctRemoved.replaceAll("\\s+", " ").trim();
                    // Remove stopwords using detected language for this alt name
                    return normalizer.removeStopwords(withPunctRemoved, altLang);
                })
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        }
        
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
        
        // Phase 17: Normalize addresses using AddressNormalizer
        List<Address> normalizedAddressList = new ArrayList<>();
        List<String> normalizedAddressStrings = new ArrayList<>();
        if (addresses != null && !addresses.isEmpty()) {
            for (Address addr : addresses) {
                // Normalize address fields
                String line1 = normalizeAddressField(addr.line1());
                String line2 = normalizeAddressField(addr.line2());
                String city = normalizeAddressField(addr.city());
                String state = normalizeAddressField(addr.state());
                String postalCode = addr.postalCode() != null ? addr.postalCode().toLowerCase() : null;
                String country = addr.country() != null ? addr.country().toLowerCase() : null;
                
                Address normalizedAddr = new Address(line1, line2, city, state, postalCode, country);
                normalizedAddressList.add(normalizedAddr);
                
                // Also create string representation for PreparedFields
                String fullAddr = String.format("%s %s %s %s %s",
                    line1 != null ? line1 : "",
                    city != null ? city : "",
                    state != null ? state : "",
                    postalCode != null ? postalCode : "",
                    country != null ? country : ""
                ).trim();
                if (!fullAddr.isEmpty()) {
                    normalizedAddressStrings.add(fullAddr);
                }
            }
        }
        
        // Phase 17: Normalize contact info phones using PhoneNormalizer
        ContactInfo normalizedContact = contact;
        if (contact != null) {
            String normalizedPhone = PhoneNormalizer.normalizePhoneNumber(contact.phoneNumber());
            String normalizedFax = PhoneNormalizer.normalizePhoneNumber(contact.faxNumber());
            normalizedContact = new ContactInfo(
                contact.emailAddress(),
                normalizedPhone,
                normalizedFax,
                contact.website()
            );
        }
        
        PreparedFields prepared = new PreparedFields(
            normalizedPrimary,
            normalizedAlts,
            namesWithoutStopwords,
            namesWithoutCompanyTitles,
            wordCombinations,
            normalizedAddressStrings,
            detectedLanguage
        );
        
        return new Entity(
            id, name, type, source, sourceId,
            person, business, organization, aircraft, vessel,
            normalizedContact, normalizedAddressList, cryptoAddresses, altNames, governmentIds,
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
    
    /**
     * Normalizes an address field: lowercase and remove punctuation.
     * Phase 17: Used for normalizing address fields during Entity.normalize()
     */
    private String normalizeAddressField(String field) {
        if (field == null || field.isEmpty()) {
            return null;
        }
        // Lowercase and remove commas and periods
        return field.toLowerCase().replace(",", "").replace(".", "");
    }
}
