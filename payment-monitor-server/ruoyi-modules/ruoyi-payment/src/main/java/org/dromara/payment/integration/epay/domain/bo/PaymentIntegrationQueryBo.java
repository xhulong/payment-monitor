package org.dromara.payment.integration.epay.domain.bo;

import lombok.Data;

@Data
public class PaymentIntegrationQueryBo {
    private Long merchantId;
    private String integrationName;
    private String pid;
    private String status;
}
