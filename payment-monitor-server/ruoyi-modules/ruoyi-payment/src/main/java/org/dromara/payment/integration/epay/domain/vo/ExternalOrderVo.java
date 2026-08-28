package org.dromara.payment.integration.epay.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ExternalOrderVo {
    private Long id;
    private Long merchantId;
    private String merchantCode;
    private String merchantName;
    private Long integrationId;
    private String integrationName;
    private Long orderId;
    private String internalOrderNo;
    private String externalOrderNo;
    private String gatewayTradeNo;
    private String payType;
    private String platform;
    private Long requestAmountMinor;
    private Long payableAmountMinor;
    private String orderStatus;
    private String confirmationStatus;
    private String callbackPolicy;
    private String callbackStatus;
    private String riskStatus;
    private String riskReason;
    private OffsetDateTime createdAt;
    private OffsetDateTime paidAt;
}
