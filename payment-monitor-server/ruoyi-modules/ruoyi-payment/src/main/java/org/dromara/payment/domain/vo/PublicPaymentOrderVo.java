package org.dromara.payment.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class PublicPaymentOrderVo {
    private String merchantOrderNo;
    private String platform;
    private Long payableAmountMinor;
    private String currency;
    private String status;
    private String subject;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime paidAt;
    private Long transactionId;
    private String confirmationStatus;
    private OffsetDateTime confirmedAt;
    private String confirmationSource;
    private String qrImageUrl;
}
