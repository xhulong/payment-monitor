package org.dromara.payment.domain.vo;

import java.time.OffsetDateTime;

public record PaymentIntegrationOrderVo(
    Long orderId,
    Long merchantId,
    String merchantOrderNo,
    String platform,
    Long qrAssetId,
    long requestedAmountMinor,
    long payableAmountMinor,
    String status,
    String publicToken,
    String qrContent,
    OffsetDateTime expiresAt,
    OffsetDateTime paidAt
) {
}
