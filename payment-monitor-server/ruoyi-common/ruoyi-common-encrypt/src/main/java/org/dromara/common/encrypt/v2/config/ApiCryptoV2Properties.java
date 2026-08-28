package org.dromara.common.encrypt.v2.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the opt-in api-crypto-v2 transport.
 */
@Data
@ConfigurationProperties(prefix = "api-crypto-v2")
public class ApiCryptoV2Properties {

    private boolean enabled;

    private String jwksPath = "/api/v2/crypto/jwks";

    private String activeKid = "payment-monitor-rsa-2026-01";

    private String keyProvider = "local-secret";

    private String publicKey;

    private String privateKey;

    private String publicKeyFile;

    private String privateKeyFile;

    private boolean allowEphemeralDevKey;

    private boolean failOnPlaintext = true;

    private int maxBodyBytes = 1024 * 1024;

    private long clockSkewSeconds = 120;

    private long replayTtlSeconds = 180;

    private List<KeyMaterial> keys = new ArrayList<>();

    @Data
    public static class KeyMaterial {

        private String kid;

        private String publicKey;

        private String privateKey;

        private String publicKeyFile;

        private String privateKeyFile;

        private boolean active;

        private boolean decryptOnly;
    }
}
