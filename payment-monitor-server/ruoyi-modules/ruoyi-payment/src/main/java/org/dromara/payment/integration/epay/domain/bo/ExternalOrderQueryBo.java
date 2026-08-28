package org.dromara.payment.integration.epay.domain.bo;

import lombok.Data;

@Data
public class ExternalOrderQueryBo {
    private Long merchantId;
    private Long integrationId;
    private String externalOrderNo;
    private String gatewayTradeNo;
    private String riskStatus;
}
