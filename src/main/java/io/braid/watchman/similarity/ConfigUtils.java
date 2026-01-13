package io.braid.watchman.similarity;

/**
 * Configuration utility functions for parsing environment variables.
 * 
 * Ported from Go: internal/stringscore/jaro_winkler.go
 * - readFloat(override string, value float64) float64
 * - readInt(override string, value int) int
 * 
 * These functions provide consistent handling of configuration overrides
 * with fallback to default values.
 */
public class ConfigUtils {

    /**
     * Parse a float from a string override, or return default value.
     * 
     * @param override String value to parse (typically from environment variable)
     * @param defaultValue Default value to return if override is null/empty
     * @return Parsed float or default value
     * @throws NumberFormatException if override is non-empty but invalid
     */
    public static double readFloat(String override, double defaultValue) {
        if (override == null || override.isEmpty()) {
            return defaultValue;
        }
        
        try {
            return Double.parseDouble(override);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Unable to parse \"" + override + "\" as double");
        }
    }

    /**
     * Parse an integer from a string override, or return default value.
     * 
     * @param override String value to parse (typically from environment variable)
     * @param defaultValue Default value to return if override is null/empty
     * @return Parsed integer or default value
     * @throws NumberFormatException if override is non-empty but invalid
     */
    public static int readInt(String override, int defaultValue) {
        if (override == null || override.isEmpty()) {
            return defaultValue;
        }
        
        try {
            return Integer.parseInt(override);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Unable to parse \"" + override + "\" as int");
        }
    }

    private ConfigUtils() {
        // Utility class, no instantiation
    }
}
