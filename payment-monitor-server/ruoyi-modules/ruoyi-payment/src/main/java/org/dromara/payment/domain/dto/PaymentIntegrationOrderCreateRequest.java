package org.dromara.payment.domain.dto;

public record PaymentIntegrationOrderCreateRequest(
    Long merchantId,
    String merchantOrderNo,
    String platform,
    Long qrAssetId,
    long amountMinor,
    Integer expiresSeconds,
    String subject,
    String customerNote
) {
}
