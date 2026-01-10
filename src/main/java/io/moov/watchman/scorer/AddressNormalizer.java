package io.moov.watchman.scorer;

import io.moov.watchman.model.Address;
import io.moov.watchman.model.PreparedAddress;

import java.util.List;

/**
 * TDD Phase 7 - STUB
 * Address normalization utilities
 * 
 * Ported from Go: pkg/search/models.go (lines 356-391)
 */
public class AddressNormalizer {
    
    /**
     * Normalizes a single address into PreparedAddress format.
     * 
     * Operations:
     * - Lowercase all text fields
     * - Remove commas (addressCleaner)
     * - Normalize country via CountryNormalizer
     * - Tokenize line1, line2, city into field arrays
     * 
     * @param addr Raw address
     * @return PreparedAddress with normalized fields
     */
    public static PreparedAddress normalizeAddress(Address addr) {
        throw new UnsupportedOperationException("Not implemented yet - TDD RED phase");
    }
    
    /**
     * Normalizes multiple addresses in batch.
     * 
     * @param addresses List of raw addresses
     * @return List of PreparedAddresses (empty list if input is null/empty)
     */
    public static List<PreparedAddress> normalizeAddresses(List<Address> addresses) {
        throw new UnsupportedOperationException("Not implemented yet - TDD RED phase");
    }
}
