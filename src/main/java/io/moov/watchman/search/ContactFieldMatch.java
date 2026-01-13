package io.moov.watchman.search;

/**
 * Result of contact field comparison.
 * 
 * Ported from Go: contactFieldMatch struct in similarity_exact.go
 * 
 * @param matches    Number of matches found
 * @param totalQuery Total query values
 * @param score      Match ratio (matches / totalQuery)
 */
public record ContactFieldMatch(int matches, int totalQuery, double score) {}
