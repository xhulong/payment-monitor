package org.dromara.payment.domain.bo;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 支付事件查询条件。
 */
@Data
public class PaymentEventQueryBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long merchantId;
    private String platform;
    private String direction;
    private String parseStatus;
    private String status;
    private String duplicateStatus;
    private String keyword;
    private Long deviceId;
    private Long amountMinor;
    private Long minAmountMinor;
    private Long maxAmountMinor;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime beginTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime endTime;
}
