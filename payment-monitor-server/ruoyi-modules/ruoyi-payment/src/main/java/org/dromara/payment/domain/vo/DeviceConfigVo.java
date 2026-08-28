package org.dromara.payment.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.dromara.payment.config.PaymentProperties;

import java.time.OffsetDateTime;

/**
 * Runtime settings consumed by Android clients.
 */
@Data
@AllArgsConstructor
public class DeviceConfigVo {
    private int heartbeatIntervalSeconds;
    private int onlineThresholdSeconds;
    private int maxBatchSize;
    private int maxRequestBytes;
    private boolean rawPayloadUploadEnabled;
    private String deviceRole;
    private String platformScope;
    private Integer minSupportedVersionCode;
    private OffsetDateTime enforcementAt;
    private String downloadUrl;
    private String updateMode;

    public static DeviceConfigVo from(PaymentProperties properties) {
        return from(properties, null, null);
    }

    public static DeviceConfigVo from(
        PaymentProperties properties,
        String deviceRole,
        String platformScope
    ) {
        return new DeviceConfigVo(
            properties.getHeartbeat().getIntervalSeconds(),
            properties.getHeartbeat().getOnlineThresholdSeconds(),
            properties.getEvents().getMaxBatchSize(),
            properties.getSecurity().getMaxRequestBytes(),
            properties.getEvents().isRawPayloadUploadEnabled(),
            deviceRole,
            platformScope,
            null,
            null,
            null,
            null);
    }
}
