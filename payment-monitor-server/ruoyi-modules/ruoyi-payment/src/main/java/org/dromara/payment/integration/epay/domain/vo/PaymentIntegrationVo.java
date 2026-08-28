package org.dromara.payment.integration.epay.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class PaymentIntegrationVo {
    private Long id;
    private Long merchantId;
    private String merchantCode;
    private String merchantName;
    private String integrationCode;
    private String integrationName;
    private String protocol;
    private String profile;
    private String pid;
    private String status;
    private Integer defaultExpireSeconds;
    private String notifyMethod;
    private String callbackPolicy;
    private List<String> allowedCallbackHosts;
    private String remark;
    private Integer activeSecretVersion;
    private List<PaymentIntegrationSecretMetadataVo> secrets;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
