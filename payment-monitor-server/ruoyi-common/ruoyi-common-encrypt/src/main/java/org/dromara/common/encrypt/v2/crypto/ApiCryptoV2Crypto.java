package org.dromara.common.encrypt.v2.crypto;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cryptographic primitives for api-crypto-v2.
 *
 * <p>This class deliberately exposes only fixed algorithms and fixed encodings.
 * It does not reuse the legacy ECB-based helpers.</p>
 */
public final class ApiCryptoV2Crypto {

    public static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPPadding";
    public static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    public static final String RSA_ALGORITHM = "RSA-OAEP-256";
    public static final String AES_ALGORITHM = "AES-256-GCM";
    public static final String HKDF_ALGORITHM = "HKDF-SHA256";

    private static final int AES_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final byte[] HKDF_SALT =
        sha256("payment-monitor/api-crypto-v2".getBytes(StandardCharsets.UTF_8));

    private ApiCryptoV2Crypto() {
    }

    public static byte[] randomBytes(int length) {
        byte[] value = new byte[length];
        SECURE_RANDOM.nextBytes(value);
        return value;
    }

    public static byte[] rsaOaep256Encrypt(byte[] plaintext, PublicKey publicKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParameterSpec());
            return cipher.doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            throw new ApiCryptoV2Exception("RSA encryption failed", e);
        }
    }

    public static byte[] rsaOaep256Decrypt(byte[] ciphertext, PrivateKey privateKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParameterSpec());
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new ApiCryptoV2Exception("RSA decryption failed", e);
        }
    }

    public static GcmCipher encrypt(byte[] plaintext, byte[] key, byte[] aad) {
        validateAesKey(key);
        byte[] iv = randomBytes(GCM_IV_BYTES);
        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad);
            byte[] encrypted = cipher.doFinal(plaintext);
            int tagBytes = GCM_TAG_BITS / 8;
            return new GcmCipher(
                iv,
                Arrays.copyOf(encrypted, encrypted.length - tagBytes),
                Arrays.copyOfRange(encrypted, encrypted.length - tagBytes, encrypted.length));
        } catch (GeneralSecurityException e) {
            throw new ApiCryptoV2Exception("AES-GCM encryption failed", e);
        }
    }

    public static byte[] decrypt(
        byte[] ciphertext,
        byte[] tag,
        byte[] iv,
        byte[] key,
        byte[] aad
    ) {
        validateAesKey(key);
        if (iv == null || iv.length != GCM_IV_BYTES || tag == null || tag.length != GCM_TAG_BITS / 8) {
            throw new ApiCryptoV2Exception("Invalid AES-GCM parameters");
        }
        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad);
            byte[] combined = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
            System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);
            return cipher.doFinal(combined);
        } catch (GeneralSecurityException e) {
            throw new ApiCryptoV2Exception("AES-GCM decryption failed", e);
        }
    }

    public static byte[] deriveResponseKey(byte[] masterKey, String jti, String method, String path) {
        validateAesKey(masterKey);
        byte[] prk = hmac(HKDF_SALT, masterKey);
        String info = "response|" + jti + "|" + method + "|" + path;
        return hkdfExpand(prk, info.getBytes(StandardCharsets.UTF_8), AES_KEY_BYTES);
    }

    public static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public static byte[] decodeBase64Url(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiCryptoV2Exception("Missing encoded value");
        }
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new ApiCryptoV2Exception("Invalid encoded value", e);
        }
    }

    public static String requestAad(
        int version,
        String method,
        String path,
        String kid,
        String jti,
        long timestamp
    ) {
        return version + "\nREQUEST\n" + method + "\n" + path + "\n"
            + kid + "\n" + jti + "\n" + timestamp;
    }

    public static String responseAad(
        int version,
        int status,
        String path,
        String jti,
        long timestamp
    ) {
        return version + "\nRESPONSE\n" + status + "\n" + path + "\n"
            + jti + "\n" + timestamp;
    }

    public static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (GeneralSecurityException e) {
            throw new ApiCryptoV2Exception("SHA-256 unavailable", e);
        }
    }

    public record GcmCipher(byte[] iv, byte[] ciphertext, byte[] tag) {
    }

    private static OAEPParameterSpec oaepParameterSpec() {
        return new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);
    }

    private static void validateAesKey(byte[] key) {
        if (key == null || key.length != AES_KEY_BYTES) {
            throw new ApiCryptoV2Exception("AES-256 key required");
        }
    }

    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) {
        byte[] result = new byte[length];
        byte[] previous = new byte[0];
        int offset = 0;
        int counter = 1;
        while (offset < length) {
            byte[] input = new byte[previous.length + info.length + 1];
            System.arraycopy(previous, 0, input, 0, previous.length);
            System.arraycopy(info, 0, input, previous.length, info.length);
            input[input.length - 1] = (byte) counter++;
            previous = hmac(prk, input);
            int copy = Math.min(previous.length, length - offset);
            System.arraycopy(previous, 0, result, offset, copy);
            offset += copy;
        }
        return result;
    }

    private static byte[] hmac(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (GeneralSecurityException e) {
            throw new ApiCryptoV2Exception("HMAC-SHA256 unavailable", e);
        }
    }
}
