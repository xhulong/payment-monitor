package org.dromara.common.encrypt.v2.crypto;

/**
 * Internal protocol exception. Public responses intentionally collapse all
 * protocol failures to API_CRYPTO_INVALID.
 */
public class ApiCryptoV2Exception extends RuntimeException {

    public ApiCryptoV2Exception(String message) {
        super(message);
    }

    public ApiCryptoV2Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
