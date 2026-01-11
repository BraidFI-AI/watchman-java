package io.moov.watchman.phase22;

/**
 * String array utility functions matching Go's internal/stringscore/jaro_winkler.go
 * 
 * Phase 22: Zone 3 Perfect Parity
 * 
 * Functions:
 * 1. sumLength() - Sums character lengths of all strings in array
 * 2. tokenSlicesEqual() - Compares two string arrays for exact equality
 * 
 * These match the exact Go implementations for 100% parity.
 */
public class StringArrayUtils {

    /**
     * Sums the character lengths of all strings in the array.
     * 
     * Go equivalent: sumLength() in internal/stringscore/jaro_winkler.go
     * 
     * ```go
     * func sumLength(strs []string) int {
     *     totalLength := 0
     *     for _, str := range strs {
     *         totalLength += len(str)
     *     }
     *     return totalLength
     * }
     * ```
     * 
     * @param strs Array of strings
     * @return Total character count across all strings
     */
    public static int sumLength(String[] strs) {
        int totalLength = 0;
        for (String str : strs) {
            totalLength += str.length();
        }
        return totalLength;
    }

    /**
     * Compares two string arrays for exact equality (element-by-element).
     * 
     * Go equivalent: tokenSlicesEqual() in internal/stringscore/jaro_winkler.go
     * 
     * ```go
     * func tokenSlicesEqual(a, b []string) bool {
     *     if len(a) != len(b) {
     *         return false
     *     }
     *     for i := range a {
     *         if a[i] != b[i] {
     *             return false
     *         }
     *     }
     *     return true
     * }
     * ```
     * 
     * @param a First string array
     * @param b Second string array
     * @return true if arrays are identical (same length, same content in same order)
     */
    public static boolean tokenSlicesEqual(String[] a, String[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!a[i].equals(b[i])) {
                return false;
            }
        }
        return true;
    }
}
