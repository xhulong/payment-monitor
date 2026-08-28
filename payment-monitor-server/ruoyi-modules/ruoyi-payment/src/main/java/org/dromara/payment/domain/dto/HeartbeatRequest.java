package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 设备心跳请求。
 */
@Data
public class HeartbeatRequest {
    @Size(max = 32)
    private String appVersion;
    @Min(1)
    private Integer appVersionCode;
    @Size(max = 32)
    private String parserVersion;
    @Min(0)
    private Integer pendingCount;
    @Min(0)
    private Integer retryingCount;
    @Min(0)
    private Integer rejectedCount;
    private OffsetDateTime lastSyncAt;
    private Boolean monitoringEnabled;
    private Boolean listenerConnected;
    private Boolean foregroundRunning;
    private Boolean notificationAccessGranted;
    private Boolean batteryOptimizationIgnored;
    private OffsetDateTime lastNotificationAt;
    @Size(max = 64)
    private String healthIssue;
}
