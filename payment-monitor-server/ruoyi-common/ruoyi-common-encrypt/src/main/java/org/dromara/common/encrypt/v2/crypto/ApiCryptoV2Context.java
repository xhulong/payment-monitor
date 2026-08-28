package org.dromara.common.encrypt.v2.crypto;

/**
 * Per-request key context retained only for the request/response lifecycle.
 */
public record ApiCryptoV2Context(
    String kid,
    String jti,
    long requestTimestamp,
    byte[] masterKey,
    String method,
    String path
) {
}
