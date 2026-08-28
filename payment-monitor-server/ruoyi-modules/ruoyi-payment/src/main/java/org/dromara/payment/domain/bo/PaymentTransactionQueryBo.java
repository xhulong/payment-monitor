package org.dromara.payment.domain.bo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class PaymentTransactionQueryBo {
    private Long merchantId;
    private String platform;
    private String status;
    private String confirmationStatus;
    private Long orderId;
    private Long eventId;
    private OffsetDateTime beginTime;
    private OffsetDateTime endTime;
}
