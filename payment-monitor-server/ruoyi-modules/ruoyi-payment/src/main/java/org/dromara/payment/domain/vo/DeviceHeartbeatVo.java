package org.dromara.payment.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.payment.domain.PmDeviceHeartbeat;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target = PmDeviceHeartbeat.class)
public class DeviceHeartbeatVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private OffsetDateTime heartbeatAt;
    private String appVersion;
    private String parserVersion;
    private Integer pendingCount;
    private Integer retryingCount;
    private Integer rejectedCount;
    private OffsetDateTime lastSyncAt;
    private String clientIp;
    private Boolean monitoringEnabled;
    private Boolean listenerConnected;
    private Boolean foregroundRunning;
    private Boolean notificationAccessGranted;
    private Boolean batteryOptimizationIgnored;
    private OffsetDateTime lastNotificationAt;
    private String healthIssue;
}
