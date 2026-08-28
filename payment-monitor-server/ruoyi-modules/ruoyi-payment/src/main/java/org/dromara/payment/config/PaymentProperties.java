package org.dromara.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付事件中心配置。
 */
@Data
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    private int protocolVersion = 1;
    private int qrSchema = 1;
    private String publicBaseUrl = "http://127.0.0.1:8080";
    private Security security = new Security();
    private Pairing pairing = new Pairing();
    private Heartbeat heartbeat = new Heartbeat();
    private Events events = new Events();
    private Webhook webhook = new Webhook();
    private MerchantApi merchantApi = new MerchantApi();
    private Onboarding onboarding = new Onboarding();
    private AccountRecovery accountRecovery = new AccountRecovery();
    private MailOutbox mailOutbox = new MailOutbox();
    private MailSettings mailSettings = new MailSettings();
    private AccountMfa accountMfa = new AccountMfa();
    private AppRelease appRelease = new AppRelease();
    private EasyPay easyPay = new EasyPay();

    @Data
    public static class Security {
        private String masterKey;
        private long timestampSkewSeconds = 300;
        private long nonceTtlSeconds = 600;
        private int maxRequestBytes = 1_048_576;
    }

    @Data
    public static class Pairing {
        private long ttlSeconds = 300;
        private int rateLimitPerMinute = 5;
    }

    @Data
    public static class Heartbeat {
        private int intervalSeconds = 60;
        private int onlineThresholdSeconds = 180;
    }

    @Data
    public static class Events {
        private int maxBatchSize = 100;
        private boolean rawPayloadUploadEnabled = false;
    }

    @Data
    public static class Webhook {
        private boolean enabled = true;
        private boolean allowHttp = false;
        private boolean allowPrivateNetwork = false;
        private int connectTimeoutSeconds = 5;
        private int requestTimeoutSeconds = 10;
        private int maxResponseBytes = 65_536;
        private int maxAttempts = 12;
        private int maxBatchSize = 20;
        private long workerDelayMs = 1000;
        private long lockTimeoutSeconds = 120;
    }

    @Data
    public static class MerchantApi {
        private int apiVersion = 1;
        private int rateLimitPerMinute = 120;
        private long timestampSkewSeconds = 300;
        private long nonceTtlSeconds = 600;
        private int maxRequestBytes = 262_144;
    }

    @Data
    public static class Onboarding {
        private String agreementVersion = "2026-07";
        private String privacyVersion = "2026-07";
        private int rejectionCooldownDays = 7;
        private int invitationTtlHours = 24;
        private int emailCodeTtlMinutes = 5;
    }

    @Data
    public static class AccountRecovery {
        private String codePepper;
        private int maxAttempts = 5;
    }

    @Data
    public static class MailOutbox {
        private boolean enabled = true;
        private int maxAttempts = 8;
        private int maxBatchSize = 20;
        private long workerDelayMs = 1000;
        private long lockTimeoutSeconds = 120;
        private long initialRetrySeconds = 30;
        private long maxRetrySeconds = 21_600;
        private String activeKeyId = "mail-outbox-2026-01";
        private String activeKey;
        private String previousKeyId;
        private String previousKey;
    }

    @Data
    public static class MailSettings {
        private String activeKeyId = "mail-settings-2026-01";
        private String activeKey;
        private String previousKeyId;
        private String previousKey;
    }

    @Data
    public static class AccountMfa {
        private String masterKey;
        private int stepUpTtlSeconds = 300;
        private int setupTtlSeconds = 600;
        private String issuer = "LuLuPay";
    }

    @Data
    public static class EasyPay {
        private boolean enabled = true;
        private boolean allowHttp = false;
        private boolean allowPrivateNetwork = false;
        private boolean requireHttpsQuery = true;
        private int connectTimeoutSeconds = 5;
        private int requestTimeoutSeconds = 10;
        private int maxResponseBytes = 65_536;
        private int maxAttempts = 12;
        private int maxBatchSize = 20;
        private long workerDelayMs = 1000;
        private long lockTimeoutSeconds = 120;
        private String activeKeyId = "payment-integration-2026-01";
        private String activeKey;
        private String previousKeyId;
        private String previousKey;
    }

    @Data
    public static class AppRelease {
        private String endpoint = "http://minio:9000";
        private String region = "us-east-1";
        private String accessKey;
        private String secretKey;
        private String bucket = "payment-monitor-private";
        private long signedUrlTtlSeconds = 300;
        private int pairedDeviceGraceDays = 7;
        private String downloadSigningSecret;
        private String expectedPackageName = "com.xhulong.paymentmonitor";
        private String expectedSigningCertificateSha256;
    }
}
