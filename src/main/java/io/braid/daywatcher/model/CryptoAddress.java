package io.braid.daywatcher.model;

/**
 * Cryptocurrency address associated with an entity.
 */
public record CryptoAddress(
    String currency,
    String address
) {
    public static CryptoAddress of(String currency, String address) {
        return new CryptoAddress(currency, address);
    }
}
