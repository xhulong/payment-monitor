package org.dromara.payment.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 设备查询条件。
 */
@Data
public class PaymentDeviceQueryBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long merchantId;
    private String deviceName;
    private String status;
    private Boolean online;
}
