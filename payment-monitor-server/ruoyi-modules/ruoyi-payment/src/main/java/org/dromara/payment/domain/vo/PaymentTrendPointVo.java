package org.dromara.payment.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentTrendPointVo {
    private String bucket;
    private long eventCount;
    private long incomeCount;
    private long incomeAmountMinor;
}
