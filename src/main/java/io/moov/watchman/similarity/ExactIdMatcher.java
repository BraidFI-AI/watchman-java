package io.moov.watchman.similarity;

import io.moov.watchman.model.*;

import java.util.List;

/**
 * Exact identifier matching functions for sanctions screening.
 * 
 * Phase 9 Implementation - Exact ID Matching
 * Ported from: pkg/search/similarity_exact.go
 * 
 * Provides high-confidence matching based on exact identifier matches:
 * - Government IDs (passports, tax IDs, national IDs)
 * - Vessel identifiers (IMO numbers, call signs, MMSI)
 * - Aircraft identifiers (serial numbers, ICAO codes)
 * - Cryptocurrency addresses
 */
public class ExactIdMatcher {
    
    /**
     * Compares Person-specific government IDs with type, country, and identifier matching.
     * 
     * @param query The query Person
     * @param index The index Person
     * @param weight The weight to apply to this comparison
     * @return IdMatchResult with score, match status, and field count
     */
    public static IdMatchResult comparePersonExactIDs(Person query, Person index, double weight) {
        if (query == null || index == null) {
            return new IdMatchResult(0.0, weight, false, false, 0);
        }
        
        List<GovernmentId> qIDs = query.governmentIds();
        List<GovernmentId> iIDs = index.governmentIds();
        
        if (qIDs.isEmpty() || iIDs.isEmpty()) {
            return new IdMatchResult(0.0, weight, false, false, 0);
        }
        
        int fieldsCompared = 1;
        double totalWeight = 15.0;
        double score = 0.0;
        boolean hasMatch = false;
        
        // Check for exact match: type, country, and identifier must all match
        for (GovernmentId qID : qIDs) {
            for (GovernmentId iID : iIDs) {
                if (qID.type() == iID.type() &&
                    equalsIgnoreCase(qID.country(), iID.country()) &&
                    equalsIgnoreCase(normalizeIdentifier(qID.identifier()), normalizeIdentifier(iID.identifier()))) {
                    score = 15.0;
                    hasMatch = true;
                    break;
                }
            }
            if (hasMatch) break;
        }
        
        double finalScore = totalWeight > 0 ? score / totalWeight : 0.0;
        
        return new IdMatchResult(
            finalScore,
            weight,
            hasMatch,
            finalScore > 0.99,
            fieldsCompared
        );
    }
    
    /**
     * Compares Business-specific government IDs (tax IDs, registration numbers).
     * 
     * @param query The query Business
     * @param index The index Business
     * @param weight The weight to apply
     * @return IdMatchResult with score and match status
     */
    public static IdMatchResult compareBusinessExactIDs(Business query, Business index, double weight) {
        if (query == null || index == null) {
            return new IdMatchResult(0.0, weight, false, false, 0);
        }
        
        List<GovernmentId> qIDs = query.governmentIds();
        List<GovernmentId> iIDs = index.governmentIds();
        
        if (qIDs.isEmpty() || iIDs.isEmpty()) {
            return new IdMatchResult(0.0, weight, false, false, 0);
        }
        
        int fieldsCompared = 1;
        double totalWeight = 15.0;
        double score = 0.0;
        boolean hasMatch = false;
        
        // Check for exact match
        for (GovernmentId qID : qIDs) {
            for (GovernmentId iID : iIDs) {
                if (qID.type() == iID.type() &&
                    equalsIgnoreCase(qID.country(), iID.country()) &&
                    equalsIgnoreCase(normalizeIdentifier(qID.identifier()), normalizeIdentifier(iID.identifier()))) {
                    score = 15.0;
                    hasMatch = true;
                    break;
                }
            }
            if (hasMatch) break;
        }
        
        double finalScore = totalWeight > 0 ? score / totalWeight : 0.0;
        
        return new IdMatchResult(
            finalScore,
            weight,
            hasMatch,
            finalScore > 0.99,
            fieldsCompared
        );
    }
    
    /**
     * Compares Organization-specific government IDs.
     * 
     * @param query The query Organization
     * @param index The index Organization
     * @param weight The weight to apply
     * @return IdMatchResult with score and match status
     */
    public static IdMatchResult compareOrgExactIDs(Organization query, Organization index, double weight) {
        if (query == null || index == null) {
            return new IdMatchResult(0.0, weight, false, false, 0);
        }
        
        List<GovernmentId> qIDs = query.governmentIds();
        List<GovernmentId> iIDs = index.governmentIds();
        
        if (qIDs.isEmpty() || iIDs.isEmpty()) {
            return new IdMatchResult(0.0, weight, false, false, 0);
        }
        
        int fieldsCompared = 1;
        double totalWeight = 15.0;
        double score = 0.0;
        boolean hasMatch = false;
        
        // Check for exact match
        for (GovernmentId qID : qIDs) {
            for (GovernmentId iID : iIDs) {
                if (qID.type() == iID.type() &&
                    equalsIgnoreCase(qID.country(), iID.country()) &&
                    equalsIgnoreCase(normalizeIdentifier(qID.identifier()), normalizeIdentifier(iID.identifier()))) {
                    score = 15.0;
                    hasMatch = true;
                    break;
                }
            }
            if (hasMatch) break;
        }
        
        double finalScore = totalWeight > 0 ? score / totalWeight : 0.0;
        
        return new IdMatchResult(
            finalScore,
            weight,
            hasMatch,
            finalScore > 0.99,
            fieldsCompared
        );
    }
    
    /**
     * Compares Vessel-specific identifiers with weighted scoring:
     * - IMO Number: weight 15.0 (highest)
     * - Call Sign: weight 12.0
     * - MMSI: weight 12.0
     * 
     * Final score is weighted average: sum(scores) / sum(weights)
     * 
     * @param query The query Vessel
     * @param index The index Vessel
     * @param weight The weight to apply to final score
     * @return IdMatchResult with weighted score
     */
    public static IdMatchResult compareVesselExactIDs(Vessel query, Vessel index, double weight) {
        if (query == null || index == null) {
            return new IdMatchResult(0.0, weight, false, false, 0);
        }
        
        int fieldsCompared = 0;
        double totalWeight = 0.0;
        double score = 0.0;
        boolean hasMatch = false;
        
        // IMO Number (highest weight)
        if (query.imoNumber() != null && !query.imoNumber().isEmpty()) {
            fieldsCompared++;
            totalWeight += 15.0;
            if (equalsIgnoreCase(query.imoNumber(), index.imoNumber())) {
                score += 15.0;
                hasMatch = true;
            }
        }
        
        // Call Sign
        if (query.callSign() != null && !query.callSign().isEmpty()) {
            fieldsCompared++;
            totalWeight += 12.0;
            if (equalsIgnoreCase(query.callSign(), index.callSign())) {
                score += 12.0;
                hasMatch = true;
            }
        }
        
        // MMSI
        if (query.mmsi() != null && !query.mmsi().isEmpty()) {
            fieldsCompared++;
            totalWeight += 12.0;
            if (equalsIgnoreCase(query.mmsi(), index.mmsi())) {
                score += 12.0;
                hasMatch = true;
            }
        }
        
        double finalScore = totalWeight > 0 ? score / totalWeight : 0.0;
        
        return new IdMatchResult(
            finalScore,
            weight,
            hasMatch,
            finalScore > 0.99,
            fieldsCompared
        );
    }
    
    /**
     * Compares Aircraft-specific identifiers with weighted scoring:
     * - Serial Number: weight 15.0 (highest)
     * - ICAO Code: weight 12.0
     * 
     * Final score is weighted average: sum(scores) / sum(weights)
     * 
     * @param query The query Aircraft
     * @param index The index Aircraft
     * @param weight The weight to apply to final score
     * @return IdMatchResult with weighted score
     */
    public static IdMatchResult compareAircraftExactIDs(Aircraft query, Aircraft index, double weight) {
        if (query == null || index == null) {
            return new IdMatchResult(0.0, weight, false, false, 0);
        }
        
        int fieldsCompared = 0;
        double totalWeight = 0.0;
        double score = 0.0;
        boolean hasMatch = false;
        
        // Serial Number (highest weight)
        if (query.serialNumber() != null && !query.serialNumber().isEmpty()) {
            fieldsCompared++;
            totalWeight += 15.0;
            if (equalsIgnoreCase(query.serialNumber(), index.serialNumber())) {
                score += 15.0;
                hasMatch = true;
            }
        }
        
        // ICAO Code
        if (query.icaoCode() != null && !query.icaoCode().isEmpty()) {
            fieldsCompared++;
            totalWeight += 12.0;
            if (equalsIgnoreCase(query.icaoCode(), index.icaoCode())) {
                score += 12.0;
                hasMatch = true;
            }
        }
        
        double finalScore = totalWeight > 0 ? score / totalWeight : 0.0;
        
        return new IdMatchResult(
            finalScore,
            weight,
            hasMatch,
            finalScore > 0.99,
            fieldsCompared
        );
    }
    
    /**
     * Compares Person government IDs with country validation and scoring:
     * - Score 1.0: Identifier + country both match (exact=true)
     * - Score 0.9: Identifier matches, one country missing (exact=false)
     * - Score 0.7: Identifier matches, countries differ (exact=false)
     * - Score 0.0: Identifier doesn't match
     * 
     * Returns best match when multiple IDs present.
     * 
     * @param query The query Person
     * @param index The index Person
     * @param weight The weight to apply
     * @return IdMatchResult with country-validated score
     */
    public static IdMatchResult comparePersonGovernmentIDs(Person query, Person index, double weight) {
        if (query == null || index == null) {
            return new IdMatchResult(0.0, weight, false, false, 0);
        }
        
        List<GovernmentId> qIDs = query.governmentIds();
        List<GovernmentId> iIDs = index.governmentIds();
        
        if (qIDs.isEmpty() || iIDs.isEmpty()) {
            return new IdMatchResult(0.0, weight, false, false, 0);
        }
        
        int fieldsCompared = 1;
        IdComparison bestMatch = new IdComparison(0.0, false, false, false);
        
        for (GovernmentId qID : qIDs) {
            for (GovernmentId iID : iIDs) {
                IdComparison match = compareIdentifiers(
                    qID.identifier(),
                    iID.identifier(),
                    qID.country(),
                    iID.country()
                );
                
                if (match.found() && match.score() > bestMatch.score()) {
                    bestMatch = match;
                }
                
                if (bestMatch.exact()) {
                    break;
                }
            }
            if (bestMatch.exact()) {
                break;
            }
        }
        
        return new IdMatchResult(
            bestMatch.score(),
            weight,
            bestMatch.found(),
            bestMatch.exact(),
            fieldsCompared
        );
    }
    
    /**
     * Compares Business government IDs with same country validation as Person.
     * 
     * @param query The query Business
     * @param index The index Business
     * @param weight The weight to apply
     * @return IdMatchResult with country-validated score
     */
    public static IdMatchResult compareBusinessGovernmentIDs(Business query, Business index, double weight) {
        if (query == null || index == null) {
            return new IdMatchResult(0.0, weight, false, false, 0);
        }
        
        List<GovernmentId> qIDs = query.governmentIds();
        List<GovernmentId> iIDs = index.governmentIds();
        
        if (qIDs.isEmpty() || iIDs.isEmpty()) {
            return new IdMatchResult(0.0, weight, false, false, 0);
        }
        
        int fieldsCompared = 1;
        IdComparison bestMatch = new IdComparison(0.0, false, false, false);
        
        for (GovernmentId qID : qIDs) {
            for (GovernmentId iID : iIDs) {
                IdComparison match = compareIdentifiers(
                    qID.identifier(),
                    iID.identifier(),
                    qID.country(),
                    iID.country()
                );
                
                if (match.found() && match.score() > bestMatch.score()) {
                    bestMatch = match;
                }
                
                if (bestMatch.exact()) {
                    break;
                }
            }
            if (bestMatch.exact()) {
                break;
            }
        }
        
        return new IdMatchResult(
            bestMatch.score(),
            weight,
            bestMatch.found(),
            bestMatch.exact(),
            fieldsCompared
        );
    }
    
    /**
     * Compares Organization government IDs with same country validation as Person.
     * 
     * @param query The query Organization
     * @param index The index Organization
     * @param weight The weight to apply
     * @return IdMatchResult with country-validated score
     */
    public static IdMatchResult compareOrgGovernmentIDs(Organization query, Organization index, double weight) {
        if (query == null || index == null) {
            return new IdMatchResult(0.0, weight, false, false, 0);
        }
        
        List<GovernmentId> qIDs = query.governmentIds();
        List<GovernmentId> iIDs = index.governmentIds();
        
        if (qIDs.isEmpty() || iIDs.isEmpty()) {
            return new IdMatchResult(0.0, weight, false, false, 0);
        }
        
        int fieldsCompared = 1;
        IdComparison bestMatch = new IdComparison(0.0, false, false, false);
        
        for (GovernmentId qID : qIDs) {
            for (GovernmentId iID : iIDs) {
                IdComparison match = compareIdentifiers(
                    qID.identifier(),
                    iID.identifier(),
                    qID.country(),
                    iID.country()
                );
                
                if (match.found() && match.score() > bestMatch.score()) {
                    bestMatch = match;
                }
                
                if (bestMatch.exact()) {
                    break;
                }
            }
            if (bestMatch.exact()) {
                break;
            }
        }
        
        return new IdMatchResult(
            bestMatch.score(),
            weight,
            bestMatch.found(),
            bestMatch.exact(),
            fieldsCompared
        );
    }
    
    /**
     * Compares cryptocurrency addresses with currency validation.
     * 
     * Matching rules:
     * - If both have currency specified: currency AND address must match
     * - If one or both have empty currency: only address must match
     * - Empty addresses are skipped
     * - Case-insensitive comparison
     * 
     * @param queryAddresses Query crypto addresses
     * @param indexAddresses Index crypto addresses
     * @param weight The weight to apply
     * @return IdMatchResult with match status (score 0.0 or 1.0)
     */
    public static IdMatchResult compareCryptoAddresses(List<CryptoAddress> queryAddresses, List<CryptoAddress> indexAddresses, double weight) {
        if (queryAddresses == null || queryAddresses.isEmpty() || 
            indexAddresses == null || indexAddresses.isEmpty()) {
            return new IdMatchResult(0.0, weight, false, false, 0);
        }
        
        int fieldsCompared = 1;
        
        for (CryptoAddress qAddr : queryAddresses) {
            // Skip empty addresses
            if (qAddr.address() == null || qAddr.address().isEmpty()) {
                continue;
            }
            
            for (CryptoAddress iAddr : indexAddresses) {
                // Both have currency specified - need both to match
                if (qAddr.currency() != null && !qAddr.currency().isEmpty() &&
                    iAddr.currency() != null && !iAddr.currency().isEmpty()) {
                    // Check currency first
                    if (!equalsIgnoreCase(qAddr.currency(), iAddr.currency())) {
                        continue;
                    }
                }
                
                // Check addresses
                if (equalsIgnoreCase(qAddr.address(), iAddr.address())) {
                    return new IdMatchResult(1.0, weight, true, true, fieldsCompared);
                }
            }
        }
        
        return new IdMatchResult(0.0, weight, false, false, fieldsCompared);
    }
    
    /**
     * Normalizes an identifier by removing hyphens.
     * Used for comparing government IDs that may have different formatting.
     * 
     * Example: "12-34-56" -> "123456"
     * 
     * @param id The identifier to normalize
     * @return Normalized identifier without hyphens
     */
    public static String normalizeIdentifier(String id) {
        if (id == null) {
            return "";
        }
        return id.replace("-", "");
    }
    
    /**
     * Core identifier comparison logic with country validation.
     * 
     * Scoring rules:
     * - Identifiers don't match: score 0.0, found=false
     * - Identifiers match, both countries match: score 1.0, exact=true, hasCountry=true
     * - Identifiers match, both no country: score 1.0, exact=true, hasCountry=false
     * - Identifiers match, one country missing: score 0.9, exact=false, hasCountry=true
     * - Identifiers match, countries differ: score 0.7, exact=false, hasCountry=true
     * 
     * @param queryId Query identifier
     * @param indexId Index identifier
     * @param queryCountry Query country code (may be null or empty)
     * @param indexCountry Index country code (may be null or empty)
     * @return IdComparison with score and validation flags
     */
    public static IdComparison compareIdentifiers(String queryId, String indexId, String queryCountry, String indexCountry) {
        // Early return if identifiers don't match
        if (!equalsIgnoreCase(queryId, indexId)) {
            return new IdComparison(0.0, false, false, false);
        }
        
        // Normalize null countries to empty strings
        String qCountry = queryCountry == null ? "" : queryCountry;
        String iCountry = indexCountry == null ? "" : indexCountry;
        
        // If neither has country, it's an exact match but flag no country
        if (qCountry.isEmpty() && iCountry.isEmpty()) {
            return new IdComparison(1.0, true, true, false);
        }
        
        // If only one has country, slight penalty
        if ((qCountry.isEmpty() && !iCountry.isEmpty()) || (!qCountry.isEmpty() && iCountry.isEmpty())) {
            return new IdComparison(0.9, true, false, true);
        }
        
        // Both have country - check if they match
        if (equalsIgnoreCase(qCountry, iCountry)) {
            return new IdComparison(1.0, true, true, true);
        }
        
        // Countries don't match - significant penalty but still count as a match
        return new IdComparison(0.7, true, false, true);
    }
    
    /**
     * Helper method for case-insensitive string comparison with null safety.
     */
    private static boolean equalsIgnoreCase(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;
        return s1.equalsIgnoreCase(s2);
    }
}
