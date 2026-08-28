package org.dromara.payment.integration.epay.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class PaymentIntegrationSecretMetadataVo {
    private Long id;
    private Integer secretVersion;
    private String status;
    private OffsetDateTime activatedAt;
    private OffsetDateTime retiredAt;
    private OffsetDateTime revokedAt;
}
