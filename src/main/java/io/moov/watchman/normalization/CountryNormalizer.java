package io.moov.watchman.normalization;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 19 GREEN: Country normalization
 * 
 * Normalizes country codes and names to standard forms using ISO 3166.
 * Matches Go implementation: internal/norm/country.go
 * 
 * Algorithm:
 * 1. Try input as ISO 3166 code (alpha-2 or alpha-3)
 * 2. Check overrides map for preferred names
 * 3. Try input as country name lookup
 * 4. Return override or input if no match
 */
public class CountryNormalizer {

    /**
     * Country code overrides matching Go implementation.
     * Maps specific codes to preferred display names for sanctions screening.
     */
    private static final Map<String, String> COUNTRY_CODE_OVERRIDES = new HashMap<>();
    
    static {
        COUNTRY_CODE_OVERRIDES.put("CZ", "Czech Republic");
        COUNTRY_CODE_OVERRIDES.put("GB", "United Kingdom");
        COUNTRY_CODE_OVERRIDES.put("IR", "Iran");
        COUNTRY_CODE_OVERRIDES.put("KP", "North Korea");
        COUNTRY_CODE_OVERRIDES.put("KR", "South Korea");
        COUNTRY_CODE_OVERRIDES.put("MD", "Moldova");
        COUNTRY_CODE_OVERRIDES.put("MF", "Saint Martin");
        COUNTRY_CODE_OVERRIDES.put("RU", "Russia");
        COUNTRY_CODE_OVERRIDES.put("SX", "Saint Martin");
        COUNTRY_CODE_OVERRIDES.put("SY", "Syria");
        COUNTRY_CODE_OVERRIDES.put("TR", "Turkey");
        COUNTRY_CODE_OVERRIDES.put("TW", "Taiwan");
        COUNTRY_CODE_OVERRIDES.put("UK", "United Kingdom");  // Non-ISO alias
        COUNTRY_CODE_OVERRIDES.put("US", "United States");
        COUNTRY_CODE_OVERRIDES.put("USA", "United States");
        COUNTRY_CODE_OVERRIDES.put("VE", "Venezuela");
        COUNTRY_CODE_OVERRIDES.put("VG", "Virgin Islands");
        COUNTRY_CODE_OVERRIDES.put("VI", "Virgin Islands");
        COUNTRY_CODE_OVERRIDES.put("VN", "Vietnam");
    }

    /**
     * Normalizes country input to standard country name.
     * 
     * @param input Country code (ISO 3166 alpha-2/alpha-3) or country name
     * @return Normalized country name, or empty string if input is null/empty
     */
    public static String normalize(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }

        String trimmed = input.trim();
        String upperInput = trimmed.toUpperCase();

        // Try input as ISO 3166 code
        String fromCode = getCountryNameFromCode(upperInput);
        if (fromCode != null) {
            // Check for override
            String override = COUNTRY_CODE_OVERRIDES.get(upperInput);
            return override != null ? override : fromCode;
        }

        // Try input as country name (find code, then return official name)
        String code = getCodeFromCountryName(trimmed);
        if (code != null) {
            String override = COUNTRY_CODE_OVERRIDES.get(code);
            if (override != null) {
                return override;
            }
            String name = getCountryNameFromCode(code);
            return name != null ? name : trimmed;
        }

        // Check if the uppercase input itself is an override key
        String override = COUNTRY_CODE_OVERRIDES.get(upperInput);
        return override != null ? override : trimmed;
    }

    /**
     * Gets country display name from ISO 3166 code (alpha-2 or alpha-3).
     * 
     * @param code ISO country code (e.g., "US", "USA", "GB", "GBR")
     * @return Country display name, or null if not found
     */
    private static String getCountryNameFromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }

        // Try as alpha-2 code
        if (code.length() == 2) {
            try {
                Locale locale = new Locale("", code);
                String name = locale.getDisplayCountry(Locale.ENGLISH);
                // Java returns the code itself if not found
                return !name.isEmpty() && !name.equals(code) ? name : null;
            } catch (Exception e) {
                return null;
            }
        }

        // Try as alpha-3 code by checking all locales
        if (code.length() == 3) {
            for (String isoCountry : Locale.getISOCountries()) {
                Locale locale = new Locale("", isoCountry);
                if (locale.getISO3Country().equalsIgnoreCase(code)) {
                    return locale.getDisplayCountry(Locale.ENGLISH);
                }
            }
        }

        return null;
    }

    /**
     * Finds ISO 3166 alpha-2 code from country name.
     * 
     * @param name Country name (e.g., "United States", "Germany")
     * @return ISO alpha-2 code, or null if not found
     */
    private static String getCodeFromCountryName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        String lowerName = name.toLowerCase();

        // Check all ISO countries
        for (String isoCountry : Locale.getISOCountries()) {
            Locale locale = new Locale("", isoCountry);
            String displayName = locale.getDisplayCountry(Locale.ENGLISH);
            if (displayName.equalsIgnoreCase(name)) {
                return isoCountry.toUpperCase();
            }
        }

        return null;
    }
}
