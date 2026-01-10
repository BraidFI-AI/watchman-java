package io.moov.watchman.scorer;

import io.moov.watchman.model.Address;
import io.moov.watchman.model.PreparedAddress;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * TDD Phase 7 - GREEN PHASE
 * Address normalization utilities
 * 
 * Ported from Go: pkg/search/models.go (lines 356-391)
 */
public class AddressNormalizer {
    
    // Country code normalization (subset of Go's norm.Country)
    private static final Map<String, String> COUNTRY_OVERRIDES = Map.ofEntries(
        Map.entry("US", "United States"),
        Map.entry("USA", "United States"),
        Map.entry("UK", "United Kingdom"),
        Map.entry("GB", "United Kingdom")
    );
    
    /**
     * Normalizes a single address into PreparedAddress format.
     * 
     * Operations:
     * - Lowercase all text fields
     * - Remove commas (addressCleaner)
     * - Normalize country codes to full names
     * - Tokenize line1, line2, city into field arrays
     * 
     * @param addr Raw address
     * @return PreparedAddress with normalized fields
     */
    public static PreparedAddress normalizeAddress(Address addr) {
        if (addr == null) {
            return PreparedAddress.empty();
        }
        
        // Normalize text fields: lowercase + remove commas
        String line1 = normalizeField(addr.line1());
        String line2 = normalizeField(addr.line2());
        String city = normalizeField(addr.city());
        String state = normalizeField(addr.state());
        String postalCode = normalizeField(addr.postalCode());
        String country = normalizeCountry(addr.country());
        
        // Tokenize fields (split on whitespace)
        List<String> line1Fields = tokenize(line1);
        List<String> line2Fields = tokenize(line2);
        List<String> cityFields = tokenize(city);
        
        return new PreparedAddress(
            line1, line1Fields,
            line2, line2Fields,
            city, cityFields,
            state, postalCode, country
        );
    }
    
    /**
     * Normalizes multiple addresses in batch.
     * 
     * @param addresses List of raw addresses
     * @return List of PreparedAddresses (empty list if input is null/empty)
     */
    public static List<PreparedAddress> normalizeAddresses(List<Address> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return List.of();
        }
        
        List<PreparedAddress> result = new ArrayList<>(addresses.size());
        for (Address addr : addresses) {
            result.add(normalizeAddress(addr));
        }
        return result;
    }
    
    /**
     * Normalizes a text field: lowercase + remove commas.
     * Go uses addressCleaner = strings.NewReplacer(",", "")
     */
    private static String normalizeField(String field) {
        if (field == null || field.isEmpty()) {
            return "";
        }
        return field.toLowerCase().replace(",", "");
    }
    
    /**
     * Normalizes country codes to full names, then lowercase.
     * Go: strings.ToLower(norm.Country(addr.Country))
     * 
     * Simple implementation - handles common codes (US/USA → United States)
     */
    private static String normalizeCountry(String country) {
        if (country == null || country.isEmpty()) {
            return "";
        }
        
        // Check if it's a known code
        String upperCountry = country.toUpperCase();
        String normalized = COUNTRY_OVERRIDES.getOrDefault(upperCountry, country);
        return normalized.toLowerCase();
    }
    
    /**
     * Tokenizes a string into words (split on whitespace).
     * Go: strings.Fields(str)
     */
    private static List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(text.split("\\s+"));
    }
}
