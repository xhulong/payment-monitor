package org.dromara.common.encrypt.v2.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("dev")
class ApiCryptoV2CryptoTest {

    @Test
    void wrapsAndDecryptsAesMasterKeyWithRsaOaep256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        byte[] masterKey = ApiCryptoV2Crypto.randomBytes(32);

        byte[] wrapped = ApiCryptoV2Crypto.rsaOaep256Encrypt(masterKey, pair.getPublic());
        byte[] unwrapped = ApiCryptoV2Crypto.rsaOaep256Decrypt(wrapped, pair.getPrivate());

        assertArrayEquals(masterKey, unwrapped);
    }

    @Test
    void authenticatesRequestAndResponseWithDifferentAesKeys() {
        byte[] masterKey = ApiCryptoV2Crypto.randomBytes(32);
        String jti = "f5f1e202-7d46-4ce7-96ee-7b9ebf8d7f70";
        String method = "POST";
        String path = "/auth/login";
        byte[] requestAad = ApiCryptoV2Crypto.requestAad(
            2, method, path, "test-kid", jti, 1784390000L)
            .getBytes(StandardCharsets.UTF_8);
        byte[] requestPlaintext = "{\"username\":\"admin\"}".getBytes(StandardCharsets.UTF_8);

        ApiCryptoV2Crypto.GcmCipher requestCiphertext = ApiCryptoV2Crypto.encrypt(
            requestPlaintext,
            masterKey,
            requestAad);
        byte[] requestDecrypted = ApiCryptoV2Crypto.decrypt(
            requestCiphertext.ciphertext(),
            requestCiphertext.tag(),
            requestCiphertext.iv(),
            masterKey,
            requestAad);

        byte[] responseKey = ApiCryptoV2Crypto.deriveResponseKey(masterKey, jti, method, path);
        byte[] responseAad = ApiCryptoV2Crypto.responseAad(
            2, 200, path, jti, 1784390001L)
            .getBytes(StandardCharsets.UTF_8);
        ApiCryptoV2Crypto.GcmCipher responseCiphertext = ApiCryptoV2Crypto.encrypt(
            "{\"code\":200}".getBytes(StandardCharsets.UTF_8),
            responseKey,
            responseAad);
        byte[] responseDecrypted = ApiCryptoV2Crypto.decrypt(
            responseCiphertext.ciphertext(),
            responseCiphertext.tag(),
            responseCiphertext.iv(),
            responseKey,
            responseAad);

        assertArrayEquals(requestPlaintext, requestDecrypted);
        assertArrayEquals(
            "{\"code\":200}".getBytes(StandardCharsets.UTF_8),
            responseDecrypted);
        assertThrows(
            ApiCryptoV2Exception.class,
            () -> ApiCryptoV2Crypto.decrypt(
                responseCiphertext.ciphertext(),
                responseCiphertext.tag(),
                responseCiphertext.iv(),
                masterKey,
                responseAad));
    }

    @Test
    void rejectsTamperedCiphertext() {
        byte[] key = ApiCryptoV2Crypto.randomBytes(32);
        byte[] aad = "aad".getBytes(StandardCharsets.UTF_8);
        ApiCryptoV2Crypto.GcmCipher encrypted = ApiCryptoV2Crypto.encrypt(
            "secret".getBytes(StandardCharsets.UTF_8),
            key,
            aad);
        byte[] tampered = Arrays.copyOf(encrypted.ciphertext(), encrypted.ciphertext().length);
        tampered[0] ^= 1;

        assertThrows(
            ApiCryptoV2Exception.class,
            () -> ApiCryptoV2Crypto.decrypt(
                tampered,
                encrypted.tag(),
                encrypted.iv(),
                key,
                aad));
    }
}
