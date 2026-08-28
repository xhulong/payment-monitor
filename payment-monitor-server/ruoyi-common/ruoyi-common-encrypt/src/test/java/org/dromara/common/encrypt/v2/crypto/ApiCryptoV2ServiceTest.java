package org.dromara.common.encrypt.v2.crypto;

import org.dromara.common.encrypt.v2.config.ApiCryptoV2Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("dev")
class ApiCryptoV2ServiceTest {

    @Test
    void decryptsRequestAndEncryptsBoundResponse() {
        Fixture fixture = fixture();
        String path = "/auth/login";
        String method = "POST";
        String jti = UUID.randomUUID().toString();
        long timestamp = Instant.now().getEpochSecond();
        byte[] masterKey = ApiCryptoV2Crypto.randomBytes(32);
        byte[] plaintext = "{\"username\":\"admin\"}".getBytes(StandardCharsets.UTF_8);
        String requestAad = ApiCryptoV2Crypto.requestAad(
            2, method, path, fixture.keyStore().activeKid(), jti, timestamp);
        ApiCryptoV2Crypto.GcmCipher encrypted = ApiCryptoV2Crypto.encrypt(
            plaintext,
            masterKey,
            requestAad.getBytes(StandardCharsets.UTF_8));
        ApiCryptoV2Envelope request = new ApiCryptoV2Envelope(
            2,
            fixture.keyStore().activeKid(),
            jti,
            timestamp,
            null,
            ApiCryptoV2Crypto.base64Url(ApiCryptoV2Crypto.rsaOaep256Encrypt(
                masterKey,
                fixture.keyStore().activeKey().publicKey())),
            ApiCryptoV2Crypto.base64Url(encrypted.iv()),
            ApiCryptoV2Crypto.base64Url(encrypted.ciphertext()),
            ApiCryptoV2Crypto.base64Url(encrypted.tag()));

        ApiCryptoV2Service.DecodedRequest decoded = fixture.service().decryptRequest(
            fixture.jsonMapper().writeValueAsString(request).getBytes(StandardCharsets.UTF_8),
            method,
            path);
        assertArrayEquals(plaintext, decoded.plaintext());

        byte[] responsePlaintext = "{\"code\":200}".getBytes(StandardCharsets.UTF_8);
        ApiCryptoV2Envelope response = fixture.jsonMapper().readValue(
            fixture.service().encryptResponse(responsePlaintext, decoded.context(), 200),
            ApiCryptoV2Envelope.class);
        byte[] responseKey = ApiCryptoV2Crypto.deriveResponseKey(masterKey, jti, method, path);
        String responseAad = ApiCryptoV2Crypto.responseAad(
            2, 200, path, jti, response.ts());
        byte[] responseDecrypted = ApiCryptoV2Crypto.decrypt(
            ApiCryptoV2Crypto.decodeBase64Url(response.ciphertext()),
            ApiCryptoV2Crypto.decodeBase64Url(response.tag()),
            ApiCryptoV2Crypto.decodeBase64Url(response.iv()),
            responseKey,
            responseAad.getBytes(StandardCharsets.UTF_8));

        assertEquals(jti, response.jti());
        assertArrayEquals(responsePlaintext, responseDecrypted);
    }

    @Test
    void rejectsRequestBoundToDifferentPath() {
        Fixture fixture = fixture();
        String jti = UUID.randomUUID().toString();
        long timestamp = Instant.now().getEpochSecond();
        byte[] masterKey = ApiCryptoV2Crypto.randomBytes(32);
        String aad = ApiCryptoV2Crypto.requestAad(
            2, "POST", "/auth/login", fixture.keyStore().activeKid(), jti, timestamp);
        ApiCryptoV2Crypto.GcmCipher encrypted = ApiCryptoV2Crypto.encrypt(
            "{}".getBytes(StandardCharsets.UTF_8),
            masterKey,
            aad.getBytes(StandardCharsets.UTF_8));
        ApiCryptoV2Envelope request = new ApiCryptoV2Envelope(
            2,
            fixture.keyStore().activeKid(),
            jti,
            timestamp,
            null,
            ApiCryptoV2Crypto.base64Url(ApiCryptoV2Crypto.rsaOaep256Encrypt(
                masterKey,
                fixture.keyStore().activeKey().publicKey())),
            ApiCryptoV2Crypto.base64Url(encrypted.iv()),
            ApiCryptoV2Crypto.base64Url(encrypted.ciphertext()),
            ApiCryptoV2Crypto.base64Url(encrypted.tag()));

        assertThrows(
            ApiCryptoV2Exception.class,
            () -> fixture.service().decryptRequest(
                fixture.jsonMapper().writeValueAsString(request).getBytes(StandardCharsets.UTF_8),
                "POST",
                "/auth/mfa/verify"));
    }

    private Fixture fixture() {
        ApiCryptoV2Properties properties = new ApiCryptoV2Properties();
        properties.setEnabled(true);
        properties.setAllowEphemeralDevKey(true);
        ApiCryptoV2KeyStore keyStore = new ApiCryptoV2KeyStore(
            properties,
            new MockEnvironment().withProperty("spring.profiles.active", "dev"));
        JsonMapper jsonMapper = JsonMapper.builder().build();
        return new Fixture(
            keyStore,
            jsonMapper,
            new ApiCryptoV2Service(properties, keyStore, jsonMapper));
    }

    private record Fixture(
        ApiCryptoV2KeyStore keyStore,
        JsonMapper jsonMapper,
        ApiCryptoV2Service service
    ) {
    }
}
