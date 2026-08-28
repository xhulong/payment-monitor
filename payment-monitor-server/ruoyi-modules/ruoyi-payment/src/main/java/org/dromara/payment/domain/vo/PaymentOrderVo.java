package org.dromara.payment.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.payment.domain.PmPaymentOrder;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@AutoMapper(target = PmPaymentOrder.class)
public class PaymentOrderVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long merchantId;
    private String merchantCode;
    private String merchantName;
    private String merchantOrderNo;
    private String platform;
    private Long qrAssetId;
    private String qrAssetName;
    private Long requestedAmountMinor;
    private Long payableAmountMinor;
    private Integer amountOffsetMinor;
    private String currency;
    private String status;
    private String publicToken;
    private String payUrl;
    private String subject;
    private String customerNote;
    private Long matchedEventId;
    private Long transactionId;
    private String confirmationStatus;
    private OffsetDateTime confirmedAt;
    private Long confirmedBy;
    private String confirmationSource;
    private String confirmationNote;
    private String amountSlotStatus;
    private OffsetDateTime amountSlotCoolingUntil;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime paidAt;
    private OffsetDateTime cancelledAt;
    private OffsetDateTime updatedAt;
    private List<OrderMatchAuditVo> matchHistory;
}
