package org.dromara.payment.integration.epay.domain.bo;

import lombok.Data;

@Data
public class ProtocolCallbackQueryBo {
    private Long merchantId;
    private Long integrationId;
    private String deliveryId;
    private String status;
    private Long bindingId;
}
