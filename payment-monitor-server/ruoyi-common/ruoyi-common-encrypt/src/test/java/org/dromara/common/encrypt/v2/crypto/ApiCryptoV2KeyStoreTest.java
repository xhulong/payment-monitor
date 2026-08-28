package org.dromara.common.encrypt.v2.crypto;

import org.dromara.common.encrypt.v2.config.ApiCryptoV2Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("dev")
class ApiCryptoV2KeyStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsLocalSecretKeyPairFromReadOnlyMountPaths() throws Exception {
        KeyPair pair = keyPair();
        Path publicKey = write("public.der.b64", pair.getPublic().getEncoded());
        Path privateKey = write("private.der.b64", pair.getPrivate().getEncoded());
        ApiCryptoV2Properties properties = new ApiCryptoV2Properties();
        properties.setActiveKid("mounted-key");
        properties.setPublicKeyFile(publicKey.toString());
        properties.setPrivateKeyFile(privateKey.toString());

        ApiCryptoV2KeyStore keyStore = new ApiCryptoV2KeyStore(
            properties,
            new MockEnvironment().withProperty("spring.profiles.active", "prod"));

        assertEquals("mounted-key", keyStore.activeKid());
    }

    @Test
    void rejectsMismatchedLocalSecretKeyPair() throws Exception {
        KeyPair publicPair = keyPair();
        KeyPair privatePair = keyPair();
        ApiCryptoV2Properties properties = new ApiCryptoV2Properties();
        properties.setPublicKey(Base64.getEncoder().encodeToString(
            publicPair.getPublic().getEncoded()));
        properties.setPrivateKey(Base64.getEncoder().encodeToString(
            privatePair.getPrivate().getEncoded()));

        assertThrows(
            IllegalStateException.class,
            () -> new ApiCryptoV2KeyStore(properties, new MockEnvironment()));
    }

    @Test
    void publishesCurrentAndPreviousKeysDuringRotation() throws Exception {
        KeyPair previousPair = keyPair();
        KeyPair currentPair = keyPair();
        ApiCryptoV2Properties.KeyMaterial previous = material(
            "payment-monitor-rsa-previous",
            previousPair,
            false,
            true);
        ApiCryptoV2Properties.KeyMaterial current = material(
            "payment-monitor-rsa-current",
            currentPair,
            true,
            false);
        ApiCryptoV2Properties properties = new ApiCryptoV2Properties();
        properties.setActiveKid(current.getKid());
        properties.setKeys(List.of(previous, current));

        ApiCryptoV2KeyStore keyStore = new ApiCryptoV2KeyStore(
            properties,
            new MockEnvironment().withProperty("spring.profiles.active", "prod"));
        byte[] masterKey = ApiCryptoV2Crypto.randomBytes(32);
        byte[] wrappedWithPrevious = ApiCryptoV2Crypto.rsaOaep256Encrypt(
            masterKey,
            previousPair.getPublic());

        assertEquals(current.getKid(), keyStore.activeKid());
        assertEquals(2, keyStore.jwks().size());
        assertArrayEquals(
            masterKey,
            ApiCryptoV2Crypto.rsaOaep256Decrypt(
                wrappedWithPrevious,
                keyStore.requireDecryptKey(previous.getKid()).privateKey()));
    }

    private Path write(String name, byte[] value) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, Base64.getEncoder().encodeToString(value));
        return path;
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private ApiCryptoV2Properties.KeyMaterial material(
        String kid,
        KeyPair pair,
        boolean active,
        boolean decryptOnly
    ) {
        ApiCryptoV2Properties.KeyMaterial material = new ApiCryptoV2Properties.KeyMaterial();
        material.setKid(kid);
        material.setPublicKey(Base64.getEncoder().encodeToString(
            pair.getPublic().getEncoded()));
        material.setPrivateKey(Base64.getEncoder().encodeToString(
            pair.getPrivate().getEncoded()));
        material.setActive(active);
        material.setDecryptOnly(decryptOnly);
        return material;
    }
}
