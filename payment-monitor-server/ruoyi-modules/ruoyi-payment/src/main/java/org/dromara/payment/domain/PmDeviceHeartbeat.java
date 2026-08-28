package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Device heartbeat history used for troubleshooting online and queue state.
 */
@Data
@TableName("pm_device_heartbeat")
public class PmDeviceHeartbeat implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long id;
    private Long merchantId;
    private Long deviceId;
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
