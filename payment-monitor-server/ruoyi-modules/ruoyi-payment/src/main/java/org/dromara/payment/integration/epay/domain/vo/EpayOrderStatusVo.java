package org.dromara.payment.integration.epay.domain.vo;

import java.time.OffsetDateTime;

public record EpayOrderStatusVo(
    String tradeNo,
    String outTradeNo,
    String platform,
    long requestedAmountMinor,
    long payableAmountMinor,
    String status,
    String confirmationStatus,
    boolean success,
    boolean returnReady,
    String qrImageUrl,
    OffsetDateTime expiresAt,
    OffsetDateTime paidAt
) {
}