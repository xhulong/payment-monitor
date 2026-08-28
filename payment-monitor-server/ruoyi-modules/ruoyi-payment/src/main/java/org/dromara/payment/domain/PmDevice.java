package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 支付通知监控设备。
 */
@Data
@TableName("pm_device")
public class PmDevice implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long id;
    private Long merchantId;
    private String deviceName;
    private String androidIdHash;
    private String appVersion;
    private Integer appVersionCode;
    private OffsetDateTime updateRequiredAt;
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
}
