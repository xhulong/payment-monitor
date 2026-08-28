package org.dromara.payment.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 设备配对结果。
 */
@Data
public class PairDeviceVo {
    private Long deviceId;
    private String deviceSecret;
    private Integer credentialVersion;
    private Integer heartbeatIntervalSeconds;
    private Integer onlineThresholdSeconds;
    private Integer maxBatchSize;
    private Integer maxRequestBytes;
    private Boolean rawPayloadUploadEnabled;
    private String merchantCode;
    private String merchantName;
    private String deviceRole;
    private String platformScope;
    private Integer minSupportedVersionCode;
    private OffsetDateTime enforcementAt;
    private String downloadUrl;
    private String updateMode;

    public PairDeviceVo(
        Long deviceId,
        String deviceSecret,
        Integer credentialVersion,
        Integer heartbeatIntervalSeconds,
        Integer onlineThresholdSeconds,
        Integer maxBatchSize,
        Integer maxRequestBytes,
        Boolean rawPayloadUploadEnabled,
        String merchantCode,
        String merchantName,
        String deviceRole,
        String platformScope
    ) {
        this(deviceId, deviceSecret, credentialVersion, heartbeatIntervalSeconds,
            onlineThresholdSeconds, maxBatchSize, maxRequestBytes,
            rawPayloadUploadEnabled, merchantCode, merchantName, deviceRole,
            platformScope, null, null, null, null);
    }

    public PairDeviceVo(
        Long deviceId,
        String deviceSecret,
        Integer credentialVersion,
        Integer heartbeatIntervalSeconds,
        Integer onlineThresholdSeconds,
        Integer maxBatchSize,
        Integer maxRequestBytes,
        Boolean rawPayloadUploadEnabled,
        String merchantCode,
        String merchantName,
        String deviceRole,
        String platformScope,
        Integer minSupportedVersionCode,
        OffsetDateTime enforcementAt,
        String downloadUrl,
        String updateMode
    ) {
        this.deviceId = deviceId;
        this.deviceSecret = deviceSecret;
        this.credentialVersion = credentialVersion;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        this.onlineThresholdSeconds = onlineThresholdSeconds;
        this.maxBatchSize = maxBatchSize;
        this.maxRequestBytes = maxRequestBytes;
        this.rawPayloadUploadEnabled = rawPayloadUploadEnabled;
        this.merchantCode = merchantCode;
        this.merchantName = merchantName;
        this.deviceRole = deviceRole;
        this.platformScope = platformScope;
        this.minSupportedVersionCode = minSupportedVersionCode;
        this.enforcementAt = enforcementAt;
        this.downloadUrl = downloadUrl;
        this.updateMode = updateMode;
    }
}
