package org.dromara.payment.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class DeviceAssignmentVo {
    private Long id;
    private Long merchantId;
    private String merchantCode;
    private String merchantName;
    private String platform;
    private Long deviceId;
    private String deviceName;
    private String role;
    private Integer priority;
    private Boolean enabled;
    private Boolean healthy;
    private Boolean effectiveObserver;
    private OffsetDateTime lastSeenAt;
    private String healthIssue;
}
