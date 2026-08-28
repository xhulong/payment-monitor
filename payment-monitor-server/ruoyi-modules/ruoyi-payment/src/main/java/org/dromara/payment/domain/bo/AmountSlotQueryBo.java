package org.dromara.payment.domain.bo;

import lombok.Data;

@Data
public class AmountSlotQueryBo {
    private Long merchantId;
    private String platform;
    private String status;
    private Long payableAmountMinor;
    private Long orderId;
}
