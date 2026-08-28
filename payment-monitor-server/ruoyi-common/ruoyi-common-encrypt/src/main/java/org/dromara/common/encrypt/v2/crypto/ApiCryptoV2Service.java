package org.dromara.common.encrypt.v2.crypto;

import org.dromara.common.encrypt.v2.config.ApiCryptoV2Properties;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.UUID;

/**
 * Request and response envelope processing for api-crypto-v2.
 */
public class ApiCryptoV2Service {

    public static final int VERSION = 2;
    public static final String CONTENT_TYPE = "application/vnd.paymentmonitor.crypto+json";
    public static final String VERSION_HEADER = "X-Api-Crypto-Version";
    public static final String ERROR_CODE = "API_CRYPTO_INVALID";

    private final ApiCryptoV2Properties properties;
    private final ApiCryptoV2KeyStore keyStore;
    private final JsonMapper jsonMapper;

    public ApiCryptoV2Service(
        ApiCryptoV2Properties properties,
        ApiCryptoV2KeyStore keyStore,
        JsonMapper jsonMapper
    ) {
        this.properties = properties;
        this.keyStore = keyStore;
        this.jsonMapper = jsonMapper;
    }

    public DecodedRequest decryptRequest(
        byte[] requestBody,
        String method,
        String path
    ) {
        try {
            ApiCryptoV2Envelope envelope = jsonMapper.readValue(requestBody, ApiCryptoV2Envelope.class);
            validateRequestEnvelope(envelope);

            ApiCryptoV2KeyStore.KeyEntry key = keyStore.requireDecryptKey(envelope.kid());
            PrivateKey privateKey = key.privateKey();
            byte[] masterKey = ApiCryptoV2Crypto.rsaOaep256Decrypt(
                ApiCryptoV2Crypto.decodeBase64Url(envelope.wrappedKey()),
                privateKey);
            if (masterKey.length != 32) {
                throw new ApiCryptoV2Exception("Invalid master key length");
            }

            String normalizedMethod = method.toUpperCase();
            String aad = ApiCryptoV2Crypto.requestAad(
                VERSION,
                normalizedMethod,
                path,
                envelope.kid(),
                envelope.jti(),
                envelope.ts());
            byte[] plaintext = ApiCryptoV2Crypto.decrypt(
                ApiCryptoV2Crypto.decodeBase64Url(envelope.ciphertext()),
                ApiCryptoV2Crypto.decodeBase64Url(envelope.tag()),
                ApiCryptoV2Crypto.decodeBase64Url(envelope.iv()),
                masterKey,
                aad.getBytes(StandardCharsets.UTF_8));

            return new DecodedRequest(
                plaintext,
                new ApiCryptoV2Context(
                    envelope.kid(),
                    envelope.jti(),
                    envelope.ts(),
                    masterKey,
                    normalizedMethod,
                    path));
        } catch (ApiCryptoV2Exception e) {
            throw e;
        } catch (Exception e) {
            throw new ApiCryptoV2Exception("Invalid api-crypto-v2 request", e);
        }
    }

    public byte[] encryptResponse(
        byte[] responseBody,
        ApiCryptoV2Context context,
        int status
    ) {
        long timestamp = Instant.now().getEpochSecond();
        byte[] responseKey = ApiCryptoV2Crypto.deriveResponseKey(
            context.masterKey(),
            context.jti(),
            context.method(),
            context.path());
        String aad = ApiCryptoV2Crypto.responseAad(
            VERSION,
            status,
            context.path(),
            context.jti(),
            timestamp);
        ApiCryptoV2Crypto.GcmCipher encrypted = ApiCryptoV2Crypto.encrypt(
            responseBody,
            responseKey,
            aad.getBytes(StandardCharsets.UTF_8));
        ApiCryptoV2Envelope envelope = new ApiCryptoV2Envelope(
            VERSION,
            context.kid(),
            context.jti(),
            timestamp,
            status,
            null,
            ApiCryptoV2Crypto.base64Url(encrypted.iv()),
            ApiCryptoV2Crypto.base64Url(encrypted.ciphertext()),
            ApiCryptoV2Crypto.base64Url(encrypted.tag()));
        return jsonMapper.writeValueAsString(envelope).getBytes(StandardCharsets.UTF_8);
    }

    private void validateRequestEnvelope(ApiCryptoV2Envelope envelope) {
        if (envelope == null
            || !Integer.valueOf(VERSION).equals(envelope.v())
            || isBlank(envelope.kid())
            || isBlank(envelope.jti())
            || envelope.ts() == null
            || envelope.ts() <= 0
            || isBlank(envelope.wrappedKey())
            || isBlank(envelope.iv())
            || isBlank(envelope.ciphertext())
            || isBlank(envelope.tag())) {
            throw new ApiCryptoV2Exception("Invalid api-crypto-v2 envelope");
        }
        try {
            UUID.fromString(envelope.jti());
        } catch (IllegalArgumentException e) {
            throw new ApiCryptoV2Exception("Invalid request id", e);
        }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - envelope.ts()) > properties.getClockSkewSeconds()) {
            throw new ApiCryptoV2Exception("Request timestamp outside allowed window");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record DecodedRequest(byte[] plaintext, ApiCryptoV2Context context) {
    }
}
