package org.dromara.payment.domain.vo;

import java.time.OffsetDateTime;

public record MerchantOrderVo(
    String merchantOrderNo,
    String platform,
    String qrAssetCode,
    Long requestedAmountMinor,
    Long payableAmountMinor,
    String currency,
    String status,
    String payUrl,
    OffsetDateTime expiresAt,
    OffsetDateTime paidAt,
    OffsetDateTime cancelledAt,
    Long transactionId,
    String confirmationStatus,
    OffsetDateTime confirmedAt,
    String confirmationSource
) {
}
