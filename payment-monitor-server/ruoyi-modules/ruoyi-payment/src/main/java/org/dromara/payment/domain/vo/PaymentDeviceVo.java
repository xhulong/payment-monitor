package org.dromara.payment.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.payment.domain.PmDevice;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 设备视图。
 */
@Data
@AutoMapper(target = PmDevice.class)
public class PaymentDeviceVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long merchantId;
    private String merchantCode;
    private String merchantName;
    private String deviceName;
    private String androidIdHash;
    private String appVersion;
    private String parserVersion;
    private String status;
    private OffsetDateTime pairedAt;
    private OffsetDateTime lastSeenAt;
    private OffsetDateTime lastUploadAt;
    private Integer pendingCount;
    private Integer retryingCount;
    private Integer rejectedCount;
    private OffsetDateTime lastSyncAt;
    private String lastIp;
    private Boolean monitoringEnabled;
    private Boolean listenerConnected;
    private Boolean foregroundRunning;
    private Boolean notificationAccessGranted;
    private Boolean batteryOptimizationIgnored;
    private OffsetDateTime lastNotificationAt;
    private String lastHealthIssue;
    private OffsetDateTime healthUpdatedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Boolean online;
    private List<DeviceHeartbeatVo> recentHeartbeats;
}
