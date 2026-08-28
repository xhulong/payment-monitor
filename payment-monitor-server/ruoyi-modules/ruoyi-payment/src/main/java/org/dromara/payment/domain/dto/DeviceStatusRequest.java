package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 设备状态修改请求。
 */
@Data
public class DeviceStatusRequest {
    @Pattern(regexp = "0|1")
    private String status;
    private boolean revokeCredential;
}
