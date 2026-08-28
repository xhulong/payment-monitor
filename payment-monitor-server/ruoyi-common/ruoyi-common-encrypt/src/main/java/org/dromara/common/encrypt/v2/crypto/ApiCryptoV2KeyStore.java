package org.dromara.common.encrypt.v2.crypto;

import org.dromara.common.encrypt.v2.config.ApiCryptoV2Properties;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-process view of the configured RSA wrapping keys.
 *
 * <p>Production deployments should provide key material through a secret
 * provider. The local PEM/base64 form exists for development and test wiring;
 * it is never written back to configuration or logs.</p>
 */
public class ApiCryptoV2KeyStore {

    private final Map<String, KeyEntry> keys;
    private final String activeKid;

    public ApiCryptoV2KeyStore(ApiCryptoV2Properties properties, Environment environment) {
        if (!"local-secret".equalsIgnoreCase(properties.getKeyProvider())) {
            throw new IllegalStateException(
                "Unsupported api-crypto-v2 key provider: " + properties.getKeyProvider());
        }

        List<ApiCryptoV2Properties.KeyMaterial> configured = new ArrayList<>(properties.getKeys());
        if (configured.isEmpty()) {
            String publicKey = resolveKeyValue(
                properties.getPublicKey(),
                properties.getPublicKeyFile(),
                "public key");
            String privateKey = resolveKeyValue(
                properties.getPrivateKey(),
                properties.getPrivateKeyFile(),
                "private key");
            if (hasText(publicKey) || hasText(privateKey)) {
                if (!hasText(publicKey) || !hasText(privateKey)) {
                    throw new IllegalStateException(
                        "api-crypto-v2 local-secret provider requires both public and private keys");
                }
                ApiCryptoV2Properties.KeyMaterial single = new ApiCryptoV2Properties.KeyMaterial();
                single.setKid(properties.getActiveKid());
                single.setPublicKey(publicKey);
                single.setPrivateKey(privateKey);
                single.setActive(true);
                configured.add(single);
            }
        }

        if (configured.isEmpty() && properties.isAllowEphemeralDevKey() && !isProduction(environment)) {
            configured.add(generateEphemeralKey(properties.getActiveKid()));
        }

        if (configured.isEmpty()) {
            throw new IllegalStateException(
                "api-crypto-v2 is enabled but no RSA key material is configured");
        }

        Map<String, KeyEntry> loaded = new LinkedHashMap<>();
        for (ApiCryptoV2Properties.KeyMaterial material : configured) {
            String publicKeyValue = resolveKeyValue(
                material.getPublicKey(),
                material.getPublicKeyFile(),
                "public key for " + material.getKid());
            String privateKeyValue = resolveKeyValue(
                material.getPrivateKey(),
                material.getPrivateKeyFile(),
                "private key for " + material.getKid());
            if (!hasText(material.getKid()) || !hasText(publicKeyValue)) {
                throw new IllegalStateException("api-crypto-v2 key requires kid and publicKey");
            }
            if (loaded.containsKey(material.getKid())) {
                throw new IllegalStateException("Duplicate api-crypto-v2 kid: " + material.getKid());
            }
            PublicKey publicKey = readPublicKey(publicKeyValue);
            PrivateKey privateKey = hasText(privateKeyValue)
                ? readPrivateKey(privateKeyValue)
                : null;
            if (!(publicKey instanceof RSAPublicKey rsaPublicKey)) {
                throw new IllegalStateException("api-crypto-v2 public key is not RSA");
            }
            validateRsaSize(rsaPublicKey);
            if (privateKey != null) {
                if (!(privateKey instanceof RSAPrivateKey rsaPrivateKey)) {
                    throw new IllegalStateException("api-crypto-v2 private key is not RSA");
                }
                validateRsaSize(rsaPrivateKey);
                validateKeyPair(publicKey, privateKey);
            }
            loaded.put(material.getKid(), new KeyEntry(
                material.getKid(),
                publicKey,
                privateKey,
                material.isActive() || material.getKid().equals(properties.getActiveKid()),
                material.isDecryptOnly()));
        }
        this.keys = Map.copyOf(loaded);

        List<KeyEntry> activeKeys = keys.values().stream()
            .filter(KeyEntry::active)
            .sorted(Comparator.comparing(KeyEntry::kid))
            .toList();
        if (activeKeys.size() != 1) {
            throw new IllegalStateException("api-crypto-v2 requires exactly one active RSA key");
        }
        if (activeKeys.getFirst().decryptOnly()) {
            throw new IllegalStateException("api-crypto-v2 active RSA key cannot be decrypt-only");
        }
        if (activeKeys.getFirst().privateKey() == null) {
            throw new IllegalStateException(
                "api-crypto-v2 active RSA key requires private key access");
        }
        this.activeKid = activeKeys.getFirst().kid();
    }

    public String activeKid() {
        return activeKid;
    }

    public KeyEntry activeKey() {
        return requireKey(activeKid);
    }

    public KeyEntry requireDecryptKey(String kid) {
        KeyEntry entry = requireKey(kid);
        if (entry.privateKey() == null) {
            throw new ApiCryptoV2Exception("Key is not available for decryption");
        }
        return entry;
    }

    public List<Map<String, String>> jwks() {
        return keys.values().stream()
            .sorted(Comparator.comparing(KeyEntry::kid))
            .map(this::toJwk)
            .toList();
    }

    private KeyEntry requireKey(String kid) {
        if (!hasText(kid)) {
            throw new ApiCryptoV2Exception("Missing key id");
        }
        KeyEntry entry = keys.get(kid);
        if (entry == null) {
            throw new ApiCryptoV2Exception("Unknown key id");
        }
        return entry;
    }

    private Map<String, String> toJwk(KeyEntry entry) {
        RSAPublicKey rsaPublicKey = (RSAPublicKey) entry.publicKey();
        Map<String, String> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("kid", entry.kid());
        jwk.put("use", "enc");
        jwk.put("alg", ApiCryptoV2Crypto.RSA_ALGORITHM);
        jwk.put("n", ApiCryptoV2Crypto.base64Url(unsigned(rsaPublicKey.getModulus())));
        jwk.put("e", ApiCryptoV2Crypto.base64Url(unsigned(rsaPublicKey.getPublicExponent())));
        return jwk;
    }

    private static byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] result = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, result, 0, result.length);
            return result;
        }
        return bytes;
    }

    private static ApiCryptoV2Properties.KeyMaterial generateEphemeralKey(String kid) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            ApiCryptoV2Properties.KeyMaterial material = new ApiCryptoV2Properties.KeyMaterial();
            material.setKid(kid);
            material.setPublicKey(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
            material.setPrivateKey(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            material.setActive(true);
            return material;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate development api-crypto-v2 key", e);
        }
    }

    private static PublicKey readPublicKey(String encoded) {
        try {
            byte[] bytes = decodeKey(encoded);
            return KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid api-crypto-v2 RSA public key", e);
        }
    }

    private static PrivateKey readPrivateKey(String encoded) {
        try {
            byte[] bytes = decodeKey(encoded);
            return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid api-crypto-v2 RSA private key", e);
        }
    }

    private static byte[] decodeKey(String encoded) {
        String normalized = encoded
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(normalized);
    }

    private static void validateRsaSize(java.security.interfaces.RSAKey key) {
        if (key.getModulus().bitLength() < 2048) {
            throw new IllegalStateException("api-crypto-v2 RSA key must be at least 2048 bits");
        }
    }

    private static void validateKeyPair(PublicKey publicKey, PrivateKey privateKey) {
        byte[] probe = ApiCryptoV2Crypto.randomBytes(32);
        byte[] decrypted = null;
        try {
            decrypted = ApiCryptoV2Crypto.rsaOaep256Decrypt(
                ApiCryptoV2Crypto.rsaOaep256Encrypt(probe, publicKey),
                privateKey);
            if (!Arrays.equals(probe, decrypted)) {
                throw new IllegalStateException(
                    "api-crypto-v2 RSA public and private keys do not match");
            }
        } catch (ApiCryptoV2Exception e) {
            throw new IllegalStateException(
                "api-crypto-v2 RSA public and private keys do not match",
                e);
        } finally {
            Arrays.fill(probe, (byte) 0);
            if (decrypted != null) {
                Arrays.fill(decrypted, (byte) 0);
            }
        }
    }

    private static String resolveKeyValue(String inlineValue, String file, String label) {
        if (hasText(inlineValue) && hasText(file)) {
            throw new IllegalStateException(
                "api-crypto-v2 " + label + " must use either inline value or file, not both");
        }
        if (hasText(inlineValue)) {
            return inlineValue;
        }
        if (!hasText(file)) {
            return null;
        }
        try {
            return Files.readString(Path.of(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read api-crypto-v2 " + label + " file", e);
        }
    }

    private static boolean isProduction(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record KeyEntry(
        String kid,
        PublicKey publicKey,
        PrivateKey privateKey,
        boolean active,
        boolean decryptOnly
    ) {
    }
}
