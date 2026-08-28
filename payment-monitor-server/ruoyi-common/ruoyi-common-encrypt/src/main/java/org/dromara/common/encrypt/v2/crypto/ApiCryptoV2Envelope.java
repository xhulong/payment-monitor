package org.dromara.common.encrypt.v2.crypto;

/**
 * JSON envelope used by api-crypto-v2.
 */
public record ApiCryptoV2Envelope(
    Integer v,
    String kid,
    String jti,
    Long ts,
    Integer status,
    String wrappedKey,
    String iv,
    String ciphertext,
    String tag
) {
}
