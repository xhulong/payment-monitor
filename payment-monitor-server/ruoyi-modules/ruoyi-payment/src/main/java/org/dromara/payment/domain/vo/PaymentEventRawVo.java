package org.dromara.payment.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentEventRawVo {
    private Long eventId;
    private boolean masked;
    private String rawPayload;
}
