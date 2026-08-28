package org.dromara.payment.integration.epay.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class PaymentIntegrationRouteVo {
    private Long id;
    private Long integrationId;
    private String payType;
    private String platform;
    private Long qrAssetId;
    private String qrAssetName;
    private String qrAssetCode;
    private Integer priority;
    private String status;
    private OffsetDateTime updatedAt;
}
